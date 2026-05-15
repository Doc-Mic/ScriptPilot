package com.mic.scriptpilot.data.repository

import android.util.Log
import com.mic.scriptpilot.data.remote.LongScriptRequest
import com.mic.scriptpilot.data.remote.ScriptPilotApi
import com.mic.scriptpilot.data.remote.ShortScriptRequest
import com.mic.scriptpilot.data.remote.logParsed
import com.mic.scriptpilot.data.remote.resolveCreateScriptPayload
import com.mic.scriptpilot.data.remote.resolveShortScriptText
import com.mic.scriptpilot.data.remote.toScriptOutline
import com.mic.scriptpilot.domain.model.ScriptOutline
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApiScriptRepository @Inject constructor(
    private val scriptPilotApi: ScriptPilotApi,
) : ScriptRepository {
    override suspend fun generateLongFormScript(
        idea: String,
        durationLabel: String,
        tone: String,
    ): ScriptOutline =
        withContext(Dispatchers.IO) {
            val response =
                scriptPilotApi.generateLongScript(
                    LongScriptRequest(
                        idea = idea,
                        durationLabel = durationLabel,
                        tone = tone,
                    ),
                )
            Log.d(TAG, "generateLongScript raw: hasScript=${response.script != null}, legacy=${response.legacyLongForm != null}, error=${response.error}")
            val payload =
                response.resolveCreateScriptPayload(
                    operation = "generateLongScript",
                    ideaTitle = idea.trim().take(200),
                    durationLabel = durationLabel,
                    tone = tone,
                )
            val outline = payload.toScriptOutline()
            logParsed(
                "generateLongScript",
                "hookLen=${outline.hook.length}, introLen=${outline.intro.length}, bodyLen=${outline.body.length}, outroLen=${outline.outro.length}",
            )
            outline
        }

    override suspend fun generateShortScript(topic: String): String =
        withContext(Dispatchers.IO) {
            val response = scriptPilotApi.generateShortScript(ShortScriptRequest(topic = topic))
            Log.d(TAG, "generateShortScript raw: hasScript=${!response.script.isNullOrBlank()}, legacy=${response.legacyShort != null}, error=${response.error}")
            val text = response.resolveShortScriptText("generateShortScript")
            logParsed("generateShortScript", "chars=${text.length}")
            text
        }

    private companion object {
        const val TAG = "ApiScriptRepository"
    }
}
