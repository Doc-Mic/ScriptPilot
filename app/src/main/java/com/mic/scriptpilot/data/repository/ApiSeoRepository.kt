package com.mic.scriptpilot.data.repository

import android.util.Log
import com.mic.scriptpilot.data.remote.ScriptPilotApi
import com.mic.scriptpilot.data.remote.SeoRequest
import com.mic.scriptpilot.data.remote.descriptionsList
import com.mic.scriptpilot.data.remote.effectiveTags
import com.mic.scriptpilot.data.remote.effectiveTitles
import com.mic.scriptpilot.data.remote.logParsed
import com.mic.scriptpilot.data.remote.requireSeoPayload
import com.mic.scriptpilot.domain.model.SeoGeneration
import com.mic.scriptpilot.domain.model.SeoResultKind
import com.mic.scriptpilot.domain.model.SeoResultLine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ApiSeoRepository @Inject constructor(
    private val scriptPilotApi: ScriptPilotApi,
) : SeoRepository {
    override suspend fun generate(
        scriptDraft: String,
        workingTitle: String,
        topicHint: String,
    ): SeoGeneration =
        withContext(Dispatchers.IO) {
            val response =
                scriptPilotApi.generateSeo(
                    SeoRequest(
                        scriptDraft = scriptDraft,
                        workingTitle = workingTitle,
                        topicHint = topicHint,
                    ),
                )
            Log.d(
                TAG,
                "generateSeo raw: titles=${response.effectiveTitles().size}, descriptions=${response.descriptionsList().size}, tags=${response.effectiveTags().size}, error=${response.error}, legacy=${response.legacy != null}",
            )
            response.requireSeoPayload("generateSeo")

            val descriptionLines =
                response.descriptionsList().mapNotNull { text ->
                    val trimmed = text.trim()
                    if (trimmed.isEmpty()) {
                        null
                    } else {
                        SeoResultLine(
                            id = UUID.randomUUID().toString(),
                            kind = SeoResultKind.DESCRIPTION,
                            text = trimmed,
                        )
                    }
                }

            val result =
                SeoGeneration(
                    titles = response.effectiveTitles().toLines(SeoResultKind.TITLE),
                    descriptions = descriptionLines,
                    tags = response.effectiveTags().toLines(SeoResultKind.TAG),
                )
            logParsed(
                "generateSeo",
                "titles=${result.titles.size}, descriptions=${result.descriptions.size}, tags=${result.tags.size}",
            )
            result
        }

    private companion object {
        const val TAG = "ApiSeoRepository"
    }
}

private fun List<String>.toLines(kind: SeoResultKind): List<SeoResultLine> =
    mapNotNull { text ->
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            null
        } else {
            SeoResultLine(
                id = UUID.randomUUID().toString(),
                kind = kind,
                text = trimmed,
            )
        }
    }
