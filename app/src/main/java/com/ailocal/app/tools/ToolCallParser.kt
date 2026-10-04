package com.ailocal.app.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Builds the system prompt and extracts a [ToolCall] from raw model output.
 *
 * Small models (like the 0.5B default) cannot be relied on for native
 * function-calling formats - we keep this deliberately simple: a single
 * well-defined JSON object, optionally surrounded by prose, which we
 * extract with a tolerant brace-matching scan rather than requiring the
 * model to emit *only* JSON.
 */
object ToolCallParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun buildSystemPrompt(basePrompt: String): String {
        return buildString {
            appendLine(basePrompt.trim())
            appendLine()
            appendLine("When the user asks you to perform a device action, respond with ONLY a JSON object in this exact shape:")
            appendLine("""{"type":"action","action":"<action_id>","parameters":{"key":"value"}}""")
            appendLine()
            appendLine(ActionRegistry.buildToolPromptSection())
            appendLine("If no device action is needed, just answer normally in plain text.")
            appendLine("Never claim an action succeeded unless the app's tool result confirms it.")
        }
    }

    /**
     * Scans [text] for the first balanced {...} span and tries to parse it
     * as a [ToolCall]. Returns null if no valid tool call JSON is present,
     * in which case the caller should treat [text] as a normal plain-text
     * reply.
     */
    fun extractToolCall(text: String): ToolCall? {
        val candidate = findFirstJsonObject(text) ?: return null
        return try {
            val element: JsonElement = json.parseToJsonElement(candidate)
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return null
            val type = obj["type"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: return null
            if (type != "action") return null
            val action = obj["action"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: return null
            val paramsObj = obj["parameters"] as? kotlinx.serialization.json.JsonObject
            val params = paramsObj?.toMap() ?: emptyMap()
            ToolCall(type = type, action = action, parameters = params)
        } catch (e: Exception) {
            null
        }
    }

    /** Returns the model's reply text with the tool-call JSON (if any) stripped out, for display. */
    fun stripToolCallJson(text: String): String {
        val candidate = findFirstJsonObject(text) ?: return text
        return text.replace(candidate, "").trim()
    }

    private fun findFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null
        var depth = 0
        var inString = false
        var escapeNext = false
        for (i in start until text.length) {
            val c = text[i]
            if (escapeNext) { escapeNext = false; continue }
            when {
                c == '\\' && inString -> escapeNext = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
