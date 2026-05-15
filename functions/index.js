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
  const fixed = {
    "3": 450,
    "5": 750,
    "10": 1500,
    "15": 2250,
  };
  if (fixed[key]) {
    return fixed[key];
  }
  if (String(key).startsWith("m")) {
    const n = parseInt(String(key).slice(1), 10);
    if (Number.isFinite(n) && n >= 1 && n <= 180) {
      return Math.round(n * 150);
    }
  }
  return 750;
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
        videos.map(async (video) => {
          const metrics = calculateMetrics(video);

          let summary =
            "Trending creator niche with rising audience interest.";

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
            momentum: getMomentum(metrics.virality),
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

      const targetWords = targetWordsForDurationKey(durationKey);

      const prompt =
        `Write a professional YouTube script.\n\n` +
        `Title: ${title}\n` +
        `Tone: ${tone}\n` +
        `Target Length: ${targetWords} words\n\n` +
        `Return ONLY valid JSON.\n\n` +
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
        }`;

      const data = await callGroq(
        prompt,
        groqKey,
        2200,
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
            duration: humanDurationForKey(durationKey, params.durationLabel),
            tone,
          },
        });
      }

      const bodyText =
        sectionsToBodyText(parsed?.sections) ||
        (parsed?.intro || "").trim() ||
        "";

      return res.json({
        script: {
          title,
          hook:
            parsed?.hook ||
            "Welcome back! Today we're diving into something crazy.",
          intro: parsed?.intro || "",
          body: bodyText,
          outro:
            parsed?.outro ||
            "Subscribe for more awesome content.",
          cta: "Subscribe and turn on notifications.",
          duration: humanDurationForKey(durationKey, params.durationLabel),
          tone,
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
        `Generate YouTube SEO content.\n\n` +
        `Primary topic / context:\n${topic}\n\n` +
        (workingTitle ? `Working title: ${workingTitle}\n\n` : "") +
        (scriptDraft
          ? `Script excerpt:\n${scriptDraft.slice(0, 2000)}\n\n`
          : "") +
        `Return ONLY valid JSON.\n\n` +
        `{
          "titles": [],
          "descriptions": [],
          "tags": []
        }`;

      const data = await callGroq(
        prompt,
        groqKey,
        1200,
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
function calculateMetrics(video) {
  const views = Number(video.statistics?.viewCount || 0);
  const likes = Number(video.statistics?.likeCount || 0);
  const comments = Number(video.statistics?.commentCount || 0);

  const publishedAt = new Date(video.snippet.publishedAt);

  const daysOld =
    (Date.now() - publishedAt.getTime()) /
    (1000 * 60 * 60 * 24);

  const safeDays = Math.max(daysOld, 1);

  const viewsPerDay = views / safeDays;

  const engagement =
    (likes + comments) / Math.max(views, 1);

  const virality = Math.min(
    100,
    Math.round((viewsPerDay / 3000) * 60 + engagement * 40)
  );

  const competition = Math.min(
    100,
    Math.round((likes / Math.max(views, 1)) * 120)
  );

  const opportunity = Math.round(
    Math.max(
      0,
      Math.min(
        100,
        0.6 * virality + 0.4 * (100 - competition)
      )
    )
  );

  return {
    virality,
    competition,
    opportunity,
  };
}

/* =================================================
   🚀 MOMENTUM
================================================= */
function getMomentum(virality) {
  if (virality >= 85) return "Exploding";
  if (virality >= 70) return "Growing Fast";
  if (virality >= 50) return "Stable";
  if (virality >= 30) return "Rising";
  return "Low";
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
      `Explain this YouTube trend in ONE short sentence under 20 words.\n\n` +
      `Title: ${title}\n` +
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

    return "Trending creator niche with rising audience interest.";
  }
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