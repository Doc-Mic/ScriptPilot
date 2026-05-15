package com.mic.scriptpilot.data.repository

import android.util.Log
import com.mic.scriptpilot.data.remote.IdeaRequest
import com.mic.scriptpilot.data.remote.ScriptPilotApi
import com.mic.scriptpilot.data.remote.ScriptPilotApiException
import com.mic.scriptpilot.data.remote.logParsed
import com.mic.scriptpilot.data.remote.requireIdeasPayload
import com.mic.scriptpilot.domain.model.IdeaItem
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApiIdeaRepository @Inject constructor(
    private val scriptPilotApi: ScriptPilotApi,
) : IdeaRepository {
    override suspend fun generateIdeas(topic: String, style: String): List<IdeaItem> =
        withContext(Dispatchers.IO) {
            val response = scriptPilotApi.generateIdeas(IdeaRequest(topic = topic, style = style))
            Log.d(TAG, "generateIdeas raw: ideas=${response.ideas.size}, error=${response.error}")
            response.requireIdeasPayload("generateIdeas")

            val mapped =
                response.ideas.mapIndexedNotNull { index, dto ->
                    val title = dto.title?.trim().orEmpty()
                    if (title.isEmpty()) {
                        Log.w(TAG, "generateIdeas skipping row $index: empty title")
                        null
                    } else {
                        val hook = dto.hook?.trim().orEmpty()
                        val angle = dto.angle?.trim().orEmpty()
                        val audience = dto.targetAudience?.trim().orEmpty()
                        val format = dto.format?.trim().orEmpty()
                        val subtitle =
                            buildList {
                                if (hook.isNotEmpty()) add(hook)
                                if (angle.isNotEmpty()) add(angle)
                                if (audience.isNotEmpty()) add("Audience: $audience")
                                if (format.isNotEmpty()) add("Format: $format")
                            }.joinToString("\n\n")
                                .trim()
                        IdeaItem(
                            id = "${System.currentTimeMillis()}_${index}_${UUID.randomUUID()}",
                            title = title,
                            angle = subtitle,
                        )
                    }
                }
            logParsed("generateIdeas", "items=${mapped.size}")
            if (response.ideas.isNotEmpty() && mapped.isEmpty()) {
                Log.e(TAG, "generateIdeas: server returned ${response.ideas.size} rows but none parsed")
                throw ScriptPilotApiException(
                    "generateIdeas: response contained no usable idea titles.",
                )
            }
            mapped
        }

    private companion object {
        const val TAG = "ApiIdeaRepository"
    }
}
