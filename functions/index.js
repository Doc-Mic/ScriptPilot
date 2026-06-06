// VERSION 3 - FULLY FIXED + STABLE

const { setGlobalOptions } = require("firebase-functions");
const { onRequest } = require("firebase-functions/https");
const logger = require("firebase-functions/logger");
const { defineSecret } = require("firebase-functions/params");

const fetch = require("node-fetch");

// 🔐 SECURE API KEYS
const YOUTUBE_KEY = defineSecret("YOUTUBE_API_KEY");
const GROQ_KEY = defineSecret("GROQ_API_KEY");

setGlobalOptions({ maxInstances: 10 });

function mergeRequestParams(req) {
  const q = req.query || {};
  const body =
    req.body && typeof req.body === "object" && !Array.isArray(req.body)
      ? req.body
      : {};
  return { ...q, ...body };
}

function sendOptions(req, res) {
  if (req.method === "OPTIONS") {
    res.status(204).send("");
    return true;
  }
  return false;
}

function stripCodeFences(text) {
  if (!text) return "";
  return text
    .replace(/```json/gi, "")
    .replace(/```/g, "")
    .trim();
}

function normalizeIdeasFromParsed(parsed) {
  if (Array.isArray(parsed)) {
    return { ideas: parsed };
  }
  if (parsed && typeof parsed === "object") {
    if (Array.isArray(parsed.trends) && !Array.isArray(parsed.ideas)) {
      logger.warn(
        "generateIdeas: model returned trends-shaped JSON; ignoring so we do not echo Find Trends."
      );
      return { ideas: [] };
    }
    if (Array.isArray(parsed.data) && !Array.isArray(parsed.ideas)) {
      const arr = parsed.data;
      const first = Array.isArray(arr) && arr.length ? arr[0] : null;
      const looksLikeIdea =
        first &&
        typeof first === "object" &&
        ("hook" in first || "angle" in first || "targetAudience" in first);
      if (looksLikeIdea) {
        return { ideas: arr };
      }
      logger.warn(
        "generateIdeas: data[] payload does not look like ideas; ignoring."
      );
      return { ideas: [] };
    }
    if (Array.isArray(parsed.ideas)) {
      return parsed;
    }
    return { ideas: [] };
  }
  return { ideas: [] };
}

/**
 * Turn Groq output into a single readable sentence (handles JSON / fences).
 */
function coercePlainSentenceFromAi(raw, maxLen = 240) {
  const fallback =
    "Trending creator niche with rising audience interest.";
  if (!raw || typeof raw !== "string") return fallback;
  let t = stripCodeFences(raw).trim();
  if (!t) return fallback;
  if (t.startsWith("{") && t.includes("}")) {
    try {
      const obj = JSON.parse(t);
      const keys = [
        "summary",
        "sentence",
        "trend",
        "text",
        "reason",
        "explanation",
        "description",
        "message",
        "insight",
      ];
      let picked = "";
      for (const k of keys) {
        const v = obj[k];
        if (typeof v === "string" && v.trim()) {
          picked = v.trim();
          break;
        }
      }
      if (!picked) {
        const firstStr = Object.values(obj).find(
          (x) => typeof x === "string" && x.trim().length > 0
        );
        if (firstStr) picked = String(firstStr).trim();
      }
      if (picked) t = picked;
    } catch (_) {
      /* keep t */
    }
  }
  t = t.replace(/^[\s"'`]+|[\s"'`]+$/g, "").replace(/\s+/g, " ").trim();
  if (!t) return fallback;
  return t.length > maxLen ? `${t.slice(0, maxLen - 1)}…` : t;
}

function mapTrendResult(r, category, region) {
  return {
    title: r.title,
    category: String(category),
    region,
    score: r.opportunity,
    source: r.channel,
    reason: coercePlainSentenceFromAi(r.summary || "", 280),
    thumbnail: r.thumbnail || "",
    publishedAt: r.publishedAt || "",
    virality: r.virality,
    competition: r.competition,
    opportunity: r.opportunity,
    momentum: r.momentum,
    viewCount: r.views || 0,
    likeCount: r.likes || 0,
    commentCount: r.comments || 0,
    rank: r.rank || 0,
  };
}

function resolveDurationKey(label, queryDur) {
  const raw = `${queryDur || ""} ${label || ""}`.trim();
  const lower = raw.toLowerCase();
  const minMatch = lower.match(/(\d+)\s*min\b/);
  if (minMatch) {
    const n = parseInt(minMatch[1], 10);
    if (n === 3) return "3";
    if (n === 5) return "5";
    if (n === 10) return "10";
    if (n === 15) return "15";
    if (n >= 1 && n <= 180) {
      return `m${n}`;
    }
  }
  return "5";
}

function targetWordsForDurationKey(key) {
  return durationProfileForKey(key).targetWords;
}

function durationProfileForKey(key) {
  const fixed = {
    "3": { minutes: 3, minWords: 350, maxWords: 450, targetWords: 400 },
    "5": { minutes: 5, minWords: 650, maxWords: 850, targetWords: 750 },
    "10": { minutes: 10, minWords: 1300, maxWords: 1700, targetWords: 1500 },
    "15": { minutes: 15, minWords: 2000, maxWords: 2600, targetWords: 2300 },
  };
  if (fixed[key]) return fixed[key];

  if (String(key).startsWith("m")) {
    const n = parseInt(String(key).slice(1), 10);
    if (Number.isFinite(n) && n >= 1 && n <= 180) {
      const minWords = Math.round(n * 130);
      const maxWords = Math.round(n * 160);
      return {
        minutes: n,
        minWords,
        maxWords,
        targetWords: Math.round((minWords + maxWords) / 2),
      };
    }
  }
  return fixed["5"];
}

function structureGuidanceForDuration(minutes) {
  if (minutes <= 3) {
    return [
      "Use a fast hook, concise intro, 2 tight body beats, and a short outro.",
      "Keep examples brief; every sentence should move the viewer forward.",
      "Suggested distribution: hook 8%, intro 12%, body 65%, outro/CTA 15%.",
    ].join("\n");
  }
  if (minutes <= 5) {
    return [
      "Use a clear hook, short intro, 3 focused body sections, and a compact outro.",
      "Include one practical example or contrast, but avoid padding.",
      "Suggested distribution: hook 7%, intro 13%, body 68%, outro/CTA 12%.",
    ].join("\n");
  }
  if (minutes <= 10) {
    return [
      "Use a strong hook, paced intro, 4-5 body sections, transitions, and a useful outro.",
      "Add examples, mini-stories, objections, and practical takeaways where relevant.",
      "Suggested distribution: hook 5%, intro 10%, body 75%, outro/CTA 10%.",
    ].join("\n");
  }
  return [
    "Use a cinematic hook, a confident setup, 6-8 body sections, smooth transitions, examples, and a satisfying outro.",
    "Develop the topic with storytelling, context, stakes, examples, counterpoints, and actionable takeaways.",
    "Suggested distribution: hook 4%, intro 8%, body 80%, outro/CTA 8%.",
  ].join("\n");
}

function humanDurationForKey(key, labelForFallback) {
  if (String(key).startsWith("m")) {
    const n = parseInt(String(key).slice(1), 10);
    return Number.isFinite(n) && n > 0 ? `${n} min` : "5 min";
  }
  switch (String(key)) {
    case "3":
      return "3 min";
    case "5":
      return "5 min";
    case "10":
      return "10 min";
    case "15":
      return "15 min";
    default: {
      const mm = (labelForFallback || "").toString().match(/(\d+)\s*min/i);
      if (mm) {
        return `${parseInt(mm[1], 10)} min`;
      }
      return "5 min";
    }
  }
}

function sectionsToBodyText(sections) {
  if (!Array.isArray(sections)) return "";
  return sections
    .map((s) => {
      const h = (s.heading || "").trim();
      const c = (s.content || "").trim();
      if (h && c) return `## ${h}\n\n${c}`;
      if (c) return c;
      if (h) return `## ${h}`;
      return "";
    })
    .filter(Boolean)
    .join("\n\n");
}

function createScriptPrompt(title, tone, durationLabel, profile) {
  return [
    "Write a complete, production-ready YouTube script.",
    "",
    `Title / idea: ${title}`,
    `Tone: ${tone}`,
    `Target spoken duration: approximately ${durationLabel}.`,
    `Target word count: ${profile.minWords}-${profile.maxWords} words, aiming near ${profile.targetWords} words.`,
    "Assume natural YouTube narration at roughly 130-160 spoken words per minute.",
    "",
    "Pacing and structure:",
    structureGuidanceForDuration(profile.minutes),
    "",
    "Style rules:",
    "- Conversational, clear, creator-friendly YouTube narration.",
    "- Avoid unnecessary fluff, filler phrases, repeated intros, and generic motivational padding.",
    "- Use smooth transitions between ideas.",
    "- The body must contain enough spoken copy to hit the requested duration.",
    "- Do not summarize with placeholders; write the actual script words the creator can read aloud.",
    "",
    "Return ONLY valid JSON in this exact shape:",
    `{
      "hook": "",
      "intro": "",
      "sections": [
        {
          "heading": "",
          "content": ""
        }
      ],
      "outro": ""
    }`,
  ].join("\n");
}

function repairScriptPrompt(title, tone, durationLabel, profile, script, wordCount) {
  const direction =
    wordCount < profile.minWords
      ? "Expand the script naturally with more useful examples, transitions, and explanation. Do not add filler."
      : "Compress the script while preserving the hook, key points, pacing, and creator usefulness.";

  return [
    "Revise this YouTube script so it matches the requested spoken duration.",
    "",
    `Title / idea: ${title}`,
    `Tone: ${tone}`,
    `Target spoken duration: approximately ${durationLabel}.`,
    `Required word range: ${profile.minWords}-${profile.maxWords} words, aiming near ${profile.targetWords} words.`,
    `Current estimated word count: ${wordCount}.`,
    direction,
    "",
    "Keep the same JSON shape. Return ONLY valid JSON.",
    "",
    JSON.stringify(
      {
        hook: script.hook,
        intro: script.intro,
        sections: script.sections,
        outro: script.outro,
      },
      null,
      2
    ),
  ].join("\n");
}

function normalizeScriptPayload(parsed) {
  const sections = Array.isArray(parsed?.sections) ? parsed.sections : [];
  const bodyText =
    sectionsToBodyText(sections) ||
    (parsed?.body || "").trim() ||
    (parsed?.intro || "").trim() ||
    "";

  return {
    hook: (parsed?.hook || "").trim(),
    intro: (parsed?.intro || "").trim(),
    sections,
    body: bodyText,
    outro: (parsed?.outro || "").trim(),
  };
}

function estimateScriptWordCount(script) {
  const text = [
    script?.hook,
    script?.intro,
    script?.body,
    script?.outro,
  ]
    .filter(Boolean)
    .join(" ");

  const words = text.match(/[A-Za-z0-9]+(?:['-][A-Za-z0-9]+)?/g);
  return words ? words.length : 0;
}

function isWordCountInRange(wordCount, profile) {
  return wordCount >= profile.minWords && wordCount <= profile.maxWords;
}

function maxTokensForScript(profile) {
  return Math.min(7600, Math.max(1800, Math.ceil(profile.maxWords * 1.65)));
}

function safeSeoFromParsed(parsed) {
  const titles = Array.isArray(parsed?.titles) ? parsed.titles : [];
  let descriptions = [];
  if (Array.isArray(parsed?.descriptions)) {
    descriptions = parsed.descriptions;
  } else if (parsed?.description) {
    descriptions = [parsed.description];
  }
  const tags = Array.isArray(parsed?.tags) ? parsed.tags : [];
  return { titles, descriptions, tags };
}

const CATEGORY_MAP = {
  "all": "0",
  "music": "10",
  "pets": "15",
  "animals": "15",
  "pets & animals": "15",
  "sports": "17",
  "gaming": "20",
  "games": "20",
  "people": "22",
  "blogs": "22",
  "people & blogs": "22",
  "comedy": "23",
  "entertainment": "24",
  "news": "25",
  "politics": "25",
  "news & politics": "25",
  "howto": "26",
  "style": "26",
  "howto & style": "26",
  "fashion": "26",
  "education": "27",
  "learning": "27",
  "science": "28",
  "technology": "28",
  "tech": "28",
  "science & technology": "28",
  "ai": "28",
  "artificial intelligence": "28",
  "film": "1",
  "movies": "1",
  "film & animation": "1",
  "autos": "2",
  "vehicles": "2",
  "autos & vehicles": "2",
  "travel": "19",
  "events": "29",
  "nonprofits": "29",
  "0": "0",
  "1": "1",
  "2": "2",
  "10": "10",
  "15": "15",
  "17": "17",
  "19": "19",
  "20": "20",
  "22": "22",
  "23": "23",
  "24": "24",
  "25": "25",
  "26": "26",
  "27": "27",
  "28": "28",
  "29": "29",
};

/* =================================================
   🔥 1. FIND TRENDS
================================================= */
exports.findTrends = onRequest(
  { secrets: [YOUTUBE_KEY, GROQ_KEY] },
  async (req, res) => {
    setCors(res);
    if (sendOptions(req, res)) return;

    const params = mergeRequestParams(req);

    try {
      const {
        category = "0",
        region = "US",
        maxResults = "20",
      } = params;

      const youtubeKey = YOUTUBE_KEY.value();
      const groqKey = GROQ_KEY.value();

      const categoryId = resolveCategoryId(category);

      logger.info("Find Trends called", {
        category,
        categoryId,
        region,
      });

      const videos = await fetchTrendingVideos(
        region,
        categoryId,
        parseInt(maxResults, 10),
        youtubeKey
      );

      if (!videos.length) {
        return res.json({
          trends: [],
          message: "No trending videos found.",
        });
      }

      const results = await Promise.all(
        videos.map(async (video, index) => {
          const metrics = calculateMetrics(video, index, categoryId);

          let summary = createLocalTrendSummary(video.snippet.title, metrics);

          try {
            summary = await generateGroqSummary(
              video.snippet.title,
              metrics,
              groqKey
            );
          } catch (err) {
            logger.error("Trend Summary Error:", err);
          }

          return {
            title: video.snippet.title,
            channel: video.snippet.channelTitle,
            publishedAt: video.snippet.publishedAt,
            thumbnail:
              video.snippet?.thumbnails?.high?.url || "",
            virality: metrics.virality,
            competition: metrics.competition,
            opportunity: metrics.opportunity,
            momentum: metrics.momentum,
            views: metrics.views,
            likes: metrics.likes,
            comments: metrics.comments,
            rank: metrics.rank,
            summary,
          };
        })
      );

      results.sort((a, b) => b.opportunity - a.opportunity);

      const trends = results.map((r) => mapTrendResult(r, category, region));

      return res.json({ trends });

    } catch (error) {
      logger.error("Find Trends Error:", error);

      return res.status(500).json({
        trends: [],
        error: error.message || "Failed to fetch trends",
      });
    }
  }
);

/* =================================================
   💡 2. GENERATE IDEAS
================================================= */
exports.generateIdeas = onRequest(
  { secrets: [GROQ_KEY] },
  async (req, res) => {
    setCors(res);
    if (sendOptions(req, res)) return;

    try {
      const params = mergeRequestParams(req);
      const topic = (params.topic || "").toString().trim();
      const audience =
        (params.style || params.audience || "general audience").toString();

      if (!topic) {
        return res.status(400).json({
          ideas: [],
          error: "topic is required",
        });
      }

      const groqKey = GROQ_KEY.value();

      const prompt =
        `Generate 4 UNIQUE viral YouTube video ideas.\n\n` +
        `Topic (use this exact theme in every idea): ${topic}\n` +
        `Audience: ${audience}\n\n` +
        `Requirements:\n` +
        `- Every idea must be different\n` +
        `- Each title must clearly reflect the topic above (not unrelated trending news)\n` +
        `- Avoid generic concepts\n` +
        `- Use curiosity gaps\n` +
        `- Modern YouTube style\n\n` +
        `Return ONLY valid JSON.\n\n` +
        `{
          "ideas": [
            {
              "title": "",
              "hook": "",
              "angle": "",
              "targetAudience": "",
              "format": ""
            }
          ]
        }`;

      const data = await callGroq(
        prompt,
        groqKey,
        900,
        1.1
      );

      const text =
        data?.choices?.[0]?.message?.content?.trim() || "";

      let parsed;
      try {
        parsed = normalizeIdeasFromParsed(extractJSON(text));
      } catch (parseErr) {
        logger.error("Generate Ideas parse:", parseErr);
        return res.json({
          ideas: [],
          error: "We could not read the AI response. Please try again.",
        });
      }

      const ideas = Array.isArray(parsed.ideas) ? parsed.ideas : [];

      logger.info("generateIdeas ok", {
        topicPreview: topic.slice(0, 80),
        ideaCount: ideas.length,
      });

      return res.json({
        ideas,
      });

    } catch (error) {
      logger.error("Generate Ideas Error:", error);

      return res.status(500).json({
        ideas: [],
        error: error.message || "Failed to generate ideas",
      });
    }
  }
);

/* =================================================
   📝 3. CREATE SCRIPT
================================================= */
exports.createScript = onRequest(
  { secrets: [GROQ_KEY] },
  async (req, res) => {
    setCors(res);
    if (sendOptions(req, res)) return;

    try {
      const params = mergeRequestParams(req);
      const title =
        (params.idea || params.title || "").toString().trim();
      const tone = (params.tone || "friendly").toString();
      const durationKey = resolveDurationKey(
        params.durationLabel,
        params.duration
      );

      if (!title) {
        return res.status(400).json({
          script: null,
          error: "idea or title is required",
        });
      }

      const groqKey = GROQ_KEY.value();

      const durationProfile = durationProfileForKey(durationKey);
      const durationLabel = humanDurationForKey(durationKey, params.durationLabel);

      const data = await callGroq(
        createScriptPrompt(title, tone, durationLabel, durationProfile),
        groqKey,
        maxTokensForScript(durationProfile),
        0.8
      );

      const text =
        data?.choices?.[0]?.message?.content?.trim() || "";

      let parsed;
      try {
        parsed = extractJSON(text);
      } catch (parseErr) {
        logger.error("Create Script parse:", parseErr);
        return res.json({
          script: {
            title,
            hook: "We could not parse the AI script. Please try again.",
            intro: "",
            body: "",
            outro: "",
            cta: "",
            duration: durationLabel,
            tone,
          },
        });
      }

      let normalizedScript = normalizeScriptPayload(parsed);
      let wordCount = estimateScriptWordCount(normalizedScript);

      if (!isWordCountInRange(wordCount, durationProfile)) {
        logger.info("Create Script word count outside target; retrying once", {
          duration: durationLabel,
          wordCount,
          minWords: durationProfile.minWords,
          maxWords: durationProfile.maxWords,
        });

        try {
          const repairData = await callGroq(
            repairScriptPrompt(
              title,
              tone,
              durationLabel,
              durationProfile,
              normalizedScript,
              wordCount
            ),
            groqKey,
            maxTokensForScript(durationProfile),
            0.65
          );
          const repairText =
            repairData?.choices?.[0]?.message?.content?.trim() || "";
          const repairParsed = extractJSON(repairText);
          normalizedScript = normalizeScriptPayload(repairParsed);
          wordCount = estimateScriptWordCount(normalizedScript);
        } catch (repairErr) {
          logger.error("Create Script repair failed:", repairErr);
        }
      }

      return res.json({
        script: {
          title,
          hook:
            normalizedScript.hook ||
            "Welcome back! Today we're diving into something crazy.",
          intro: normalizedScript.intro || "",
          body: normalizedScript.body,
          outro:
            normalizedScript.outro ||
            "Subscribe for more awesome content.",
          cta: "Subscribe and turn on notifications.",
          duration: durationLabel,
          tone,
          targetWords: durationProfile.targetWords,
          wordCount,
        },
      });

    } catch (error) {
      logger.error("Create Script Error:", error);

      return res.status(500).json({
        script: null,
        error: error.message || "Failed to create script",
      });
    }
  }
);

/* =================================================
   🎬 4. SHORTS MODE
================================================= */
exports.createShort = onRequest(
  { secrets: [GROQ_KEY] },
  async (req, res) => {
    setCors(res);
    if (sendOptions(req, res)) return;

    try {
      const params = mergeRequestParams(req);
      const topic = (params.topic || "").toString().trim();

      if (!topic) {
        return res.status(400).json({
          script: "",
          error: "topic is required",
        });
      }

      const groqKey = GROQ_KEY.value();

      const prompt =
        `Write a viral YouTube Shorts script.\n\n` +
        `Topic: ${topic}\n\n` +
        `Rules:\n` +
        `- Max 150 words\n` +
        `- Fast paced\n` +
        `- Strong hook\n` +
        `- Strong CTA\n\n` +
        `Return ONLY valid JSON.\n\n` +
        `{
          "hook": "",
          "body": "",
          "cta": ""
        }`;

      const data = await callGroq(
        prompt,
        groqKey,
        500,
        0.9
      );

      const text =
        data?.choices?.[0]?.message?.content?.trim() || "";

      let parsed;
      try {
        parsed = extractJSON(text);
      } catch (parseErr) {
        logger.error("Create Short parse:", parseErr);
        return res.json({
          script:
            "We could not read the AI response. Please tap generate again.",
        });
      }

      const hook = (parsed?.hook || "").trim();
      const body = (parsed?.body || "").trim();
      const cta = (parsed?.cta || "").trim();
      const script = [hook, body, cta].filter(Boolean).join("\n\n");

      return res.json({
        script: script || hook || body || cta || "",
      });

    } catch (error) {
      logger.error("Create Short Error:", error);

      return res.status(500).json({
        script: "",
        error: error.message || "Failed to create short",
      });
    }
  }
);

/* =================================================
   🔍 5. SEO ASSISTANT
================================================= */
exports.seoAssistant = onRequest(
  { secrets: [GROQ_KEY] },
  async (req, res) => {
    setCors(res);
    if (sendOptions(req, res)) return;

    try {
      const params = mergeRequestParams(req);
      const scriptDraft = (params.scriptDraft || "").toString().trim();
      const workingTitle = (params.workingTitle || "").toString().trim();
      const topicHint = (params.topicHint || "").toString().trim();
      const topicParam = (params.topic || "").toString().trim();
      const contentTypes = Array.isArray(params.contentTypes)
        ? params.contentTypes.filter(Boolean).join(", ")
        : (params.contentTypes || "").toString().trim();

      const topic =
        topicParam ||
        [workingTitle, topicHint].find((t) => t && t.length > 0) ||
        (scriptDraft ? scriptDraft.slice(0, 500) : "");

      if (!topic) {
        return res.status(400).json({
          titles: [],
          descriptions: [],
          tags: [],
          error: "Provide script draft, title, topic hint, or topic.",
        });
      }

      const groqKey = GROQ_KEY.value();

      const prompt =
        `Generate a premium YouTube SEO packaging pack for a creator.\n\n` +
        `Primary topic / context:\n${topic}\n\n` +
        (contentTypes ? `Content types: ${contentTypes}\n\n` : "") +
        (workingTitle ? `Working title: ${workingTitle}\n\n` : "") +
        (scriptDraft
          ? `Script excerpt:\n${scriptDraft.slice(0, 2000)}\n\n`
          : "") +
        `Requirements:\n` +
        `- Generate exactly 5 title options: mix CTR focused, curiosity focused, and SEO focused.\n` +
        `- Generate one professional YouTube SEO description between 500 and 800 words. Include a strong opening hook, content summary, naturally integrated keywords, viewer value section, call-to-action, and 3-5 hashtags at the end. Do not generate multiple descriptions.\n` +
        `- The description must feel like a real YouTube workflow: one detailed optimized description, not short alternatives.\n` +
        `- Generate 15 to 20 useful YouTube SEO keyword tags based on the topic, not hashtags.\n` +
        `- Tags must be plain keywords or short phrases without # symbols.\n` +
        `- Avoid spammy repetition and avoid fake claims.\n\n` +
        `Return ONLY valid JSON.\n\n` +
        `{
          "titles": [],
          "description": "",
          "tags": []
        }`;

      const data = await callGroq(
        prompt,
        groqKey,
        2600,
        0.8
      );

      const text =
        data?.choices?.[0]?.message?.content?.trim() || "";

      let parsed;
      try {
        parsed = extractJSON(text);
      } catch (parseErr) {
        logger.error("SEO Assistant parse:", parseErr);
        return res.json({
          titles: [],
          descriptions: [],
          tags: [],
          error: "We could not read the AI response. Please try again.",
        });
      }

      const safe = safeSeoFromParsed(parsed);

      return res.json({
        titles: safe.titles,
        descriptions: safe.descriptions,
        tags: safe.tags,
      });

    } catch (error) {
      logger.error("SEO Assistant Error:", error);

      return res.status(500).json({
        titles: [],
        descriptions: [],
        tags: [],
        error: error.message || "Failed to generate SEO",
      });
    }
  }
);

/* =================================================
   🗂️ CATEGORY RESOLVER
================================================= */
function resolveCategoryId(category) {
  if (!category) return "0";

  const key = category.toString().toLowerCase().trim();

  if (CATEGORY_MAP[key]) {
    return CATEGORY_MAP[key];
  }

  if (/^\d+$/.test(key)) {
    return key;
  }

  return "0";
}

/* =================================================
   📊 FETCH TRENDING VIDEOS
================================================= */
async function fetchTrendingVideos(
  regionCode,
  categoryId,
  maxResults,
  apiKey
) {
  const url =
    `https://www.googleapis.com/youtube/v3/videos` +
    `?part=snippet,statistics` +
    `&chart=mostPopular` +
    `&regionCode=${regionCode}` +
    `&maxResults=${maxResults}` +
    `&key=${apiKey}` +
    (categoryId !== "0"
      ? `&videoCategoryId=${categoryId}`
      : "");

  const response = await fetch(url);

  const data = await response.json();

  if (!response.ok) {
    throw new Error(
      data?.error?.message || "YouTube API Error"
    );
  }

  return data.items || [];
}

/* =================================================
   📈 METRICS
================================================= */
function calculateMetrics(video, index = 0, categoryId = "0") {
  const views = Number(video.statistics?.viewCount || 0);
  const likes = Number(video.statistics?.likeCount || 0);
  const comments = Number(video.statistics?.commentCount || 0);
  const title = video.snippet?.title || "";

  const publishedAt = new Date(video.snippet.publishedAt);

  const daysOld =
    (Date.now() - publishedAt.getTime()) /
    (1000 * 60 * 60 * 24);

  const safeDays = Math.max(daysOld, 1);

  const viewsPerDay = views / safeDays;

  const engagement =
    (likes + comments * 2) / Math.max(views, 1);

  const viewPaceScore = normalizeLog(viewsPerDay, 1500, 3500000);
  const engagementScore = clamp(
    Math.round(((engagement - 0.004) / 0.075) * 100),
    18,
    100
  );
  const recencyScore = recencyToScore(safeDays);
  const rankScore = clamp(Math.round(100 - index * 3.4), 42, 100);
  const variation = deterministicVariation(
    `${title}|${video.snippet?.channelTitle || ""}|${index}`,
    -5,
    5
  );

  let virality = Math.round(
    viewPaceScore * 0.42 +
      engagementScore * 0.22 +
      recencyScore * 0.2 +
      rankScore * 0.16 +
      variation
  );
  virality = clamp(virality, 62, 98);

  if (
    viewsPerDay > 9000000 &&
    engagement > 0.08 &&
    safeDays <= 2 &&
    index <= 2
  ) {
    virality = 99;
  }

  const broadScore = broadTopicScore(title, categoryId);
  const nicheScore = nicheTopicScore(title, categoryId);
  const competitionVariation = deterministicVariation(
    `${title}|competition|${categoryId}`,
    -6,
    6
  );
  const competition = clamp(
    Math.round(
      24 +
        broadScore +
        viewPaceScore * 0.26 +
        rankScore * 0.12 -
        nicheScore -
        Math.max(0, 7 - safeDays) +
        competitionVariation
    ),
    20,
    92
  );

  const opportunityVariation = deterministicVariation(
    `${title}|opportunity|${safeDays}`,
    -4,
    4
  );
  let opportunity = Math.round(
    virality * 0.5 +
      (100 - competition) * 0.3 +
      recencyScore * 0.12 +
      nicheScore * 0.08 +
      opportunityVariation
  );
  opportunity = clamp(opportunity, 55, competition >= 72 ? 90 : 96);

  return {
    virality,
    competition,
    opportunity,
    momentum: getMomentum(virality, competition, safeDays, engagement, index),
    daysOld: safeDays,
    views,
    likes,
    comments,
    rank: index + 1,
  };
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function normalizeLog(value, min, max) {
  const safeValue = Math.max(value, 1);
  const minLog = Math.log10(min);
  const maxLog = Math.log10(max);
  const score =
    ((Math.log10(safeValue) - minLog) / (maxLog - minLog)) * 100;
  return clamp(Math.round(score), 10, 100);
}

function recencyToScore(daysOld) {
  if (daysOld <= 1) return 100;
  if (daysOld <= 3) return 91;
  if (daysOld <= 7) return 80;
  if (daysOld <= 14) return 67;
  if (daysOld <= 30) return 55;
  return 45;
}

function deterministicVariation(seed, min, max) {
  let hash = 0;
  const text = String(seed || "");
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
  }
  return min + (hash % (max - min + 1));
}

function broadTopicScore(title, categoryId) {
  const lower = String(title || "").toLowerCase();
  const broadCategories = new Set(["10", "17", "20", "24", "25"]);
  const broadKeywords = [
    "official",
    "music video",
    "trailer",
    "movie",
    "celebrity",
    "premiere",
    "live",
    "highlights",
    "breaking",
    "election",
    "world cup",
    "episode",
    "full match",
  ];
  const categoryBoost = broadCategories.has(String(categoryId)) ? 14 : 0;
  const keywordBoost = broadKeywords.some((keyword) => lower.includes(keyword))
    ? 18
    : 0;
  return categoryBoost + keywordBoost;
}

function nicheTopicScore(title, categoryId) {
  const lower = String(title || "").toLowerCase();
  const nicheCategories = new Set(["26", "27", "28", "15"]);
  const nicheKeywords = [
    "how to",
    "tutorial",
    "guide",
    "tips",
    "workflow",
    "tools",
    "review",
    "explained",
    "for beginners",
    "case study",
    "setup",
    "template",
    "automation",
  ];
  const categoryBoost = nicheCategories.has(String(categoryId)) ? 12 : 0;
  const keywordBoost = nicheKeywords.some((keyword) => lower.includes(keyword))
    ? 15
    : 0;
  return categoryBoost + keywordBoost;
}

/* =================================================
   🚀 MOMENTUM
================================================= */
function getMomentum(virality, competition = 50, daysOld = 7, engagement = 0, index = 0) {
  if (virality >= 94 && daysOld <= 3) return "Exploding";
  if (virality >= 86) return "Rising Fast";
  if (virality >= 79 && (engagement >= 0.045 || competition >= 68)) {
    return "Hot Topic";
  }
  if (virality >= 72) return "Trending";
  if (daysOld <= 5 || index >= 10) return "Emerging";
  return "Trending";
}

/* =================================================
   🤖 GROQ CALLER
================================================= */
async function callGroq(
  prompt,
  apiKey,
  maxTokens = 1000,
  temperature = 0.7,
  options = {}
) {
  const jsonObjectMode = options.jsonObjectMode !== false;

  const messages = jsonObjectMode
    ? [
        {
          role: "system",
          content: "You are a JSON API. Return ONLY valid JSON.",
        },
        {
          role: "user",
          content: prompt,
        },
      ]
    : [
        {
          role: "system",
          content:
            "You write exactly one short plain English sentence. No JSON, no markdown code fences, no bullet list, no labels like Summary:. Maximum 22 words.",
        },
        {
          role: "user",
          content: prompt,
        },
      ];

  const payload = {
    model: "llama-3.3-70b-versatile",
    messages,
    temperature,
    max_tokens: maxTokens,
    top_p: 0.95,
  };

  if (jsonObjectMode) {
    payload.response_format = { type: "json_object" };
  }

  const response = await fetch(
    "https://api.groq.com/openai/v1/chat/completions",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${apiKey}`,
      },

      body: JSON.stringify(payload),
    }
  );

  const data = await response.json();

  logger.info(
    "FULL GROQ RESPONSE:",
    JSON.stringify(data)
  );

  if (!response.ok) {
    throw new Error(
      data?.error?.message || "Groq API failed"
    );
  }

  return data;
}

/* =================================================
   🛠️ JSON EXTRACTOR
================================================= */
function extractJSON(text) {
  if (!text) {
    throw new Error("Empty AI response");
  }

  const stripped = stripCodeFences(text);

  try {
    return JSON.parse(stripped);
  } catch (_) {}

  const startObj = stripped.indexOf("{");
  const endObj = stripped.lastIndexOf("}");
  if (startObj !== -1 && endObj !== -1 && endObj > startObj) {
    const sliced = stripped.slice(startObj, endObj + 1);
    try {
      return JSON.parse(sliced);
    } catch (_) {}
  }

  const startArr = stripped.indexOf("[");
  const endArr = stripped.lastIndexOf("]");
  if (startArr !== -1 && endArr !== -1 && endArr > startArr) {
    const sliced = stripped.slice(startArr, endArr + 1);
    try {
      return JSON.parse(sliced);
    } catch (_) {}
  }

  throw new Error("Invalid JSON response");
}

/* =================================================
   🤖 TREND SUMMARY
================================================= */
async function generateGroqSummary(
  title,
  metrics,
  apiKey
) {
  try {
    const prompt =
      `Explain why this YouTube topic is trending in ONE natural sentence under 22 words. ` +
      `Mention creator opportunity. Avoid saying "viral YouTube trend".\n\n` +
      `Title: ${title}\n` +
      `Status: ${metrics.momentum}\n` +
      `Virality: ${metrics.virality}\n` +
      `Competition: ${metrics.competition}\n` +
      `Opportunity: ${metrics.opportunity}`;

    const data = await callGroq(
      prompt,
      apiKey,
      80,
      0.8,
      { jsonObjectMode: false }
    );

    const raw =
      data?.choices?.[0]?.message?.content?.trim() || "";

    return coercePlainSentenceFromAi(raw, 280);

  } catch (err) {
    logger.error("Groq Summary Error:", err);

    return createLocalTrendSummary(title, metrics);
  }
}

function createLocalTrendSummary(title, metrics) {
  const topic = coercePlainSentenceFromAi(title || "This topic", 80)
    .replace(/[.!?]+$/g, "");
  const status = metrics?.momentum || "Trending";
  const competition = Number(metrics?.competition || 50);
  const opportunity = Number(metrics?.opportunity || 70);
  const rank = Number(metrics?.rank || 0);

  if (status === "Exploding") {
    return `${topic} is accelerating quickly, giving creators a timely angle before the space gets crowded.`;
  }
  if (status === "Rising Fast") {
    return `${topic} is gaining fresh momentum, with room for focused explainers, reactions, or quick tutorials.`;
  }
  if (status === "Hot Topic") {
    return `${topic} has broad audience pull right now, so sharper niche positioning can help creators stand out.`;
  }
  if (competition >= 70) {
    return `${topic} is drawing heavy attention, making a unique hook or format especially important.`;
  }
  if (opportunity >= 82 || rank > 12) {
    return `${topic} is still opening up, creating space for practical creator angles and fast-turnaround videos.`;
  }
  return `${topic} is building steady interest, with useful room for creator-led context and audience-specific takes.`;
}

/* =================================================
   🌐 CORS
================================================= */
function setCors(res) {
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.set(
    "Access-Control-Allow-Headers",
    "Content-Type"
  );
}
