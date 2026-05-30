package com.turnit.ide.agent

import org.json.JSONArray
import org.json.JSONObject

data class IdeTool(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>
)

val coreTools: List<IdeTool> = listOf(
    IdeTool(
        name = "create_or_update_file",
        description = "Creates a new file or overwrites an existing file with complete code.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "path" to mapOf(
                    "type" to "string",
                    "description" to "Relative path to the file inside the workspace."
                ),
                "content" to mapOf(
                    "type" to "string",
                    "description" to "The complete raw code to write into the file."
                )
            ),
            "required" to listOf("path", "content")
        )
    ),
    IdeTool(
        name = "execute_shell_command",
        description = "Executes a bash/shell command in the IDE terminal.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "command" to mapOf(
                    "type" to "string",
                    "description" to "The exact shell command to run."
                )
            ),
            "required" to listOf("command")
        )
    ),
    IdeTool(
        name = "google_search",
        description = "Searches the web for up-to-date information, documentation, or code solutions.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "query" to mapOf(
                    "type" to "string",
                    "description" to "Search query text."
                )
            ),
            "required" to listOf("query")
        )
    ),
    IdeTool(
        name = "fetch_webpage",
        description = "Fetches the raw text content of a specific URL.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "url" to mapOf(
                    "type" to "string",
                    "description" to "The URL to fetch."
                )
            ),
            "required" to listOf("url")
        )
    ),
    IdeTool(
        name = "reasoning_scratchpad",
        description = "Use this tool to think step-by-step or output reasoning before taking a complex action. This helps structure your thoughts.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "thought_process" to mapOf(
                    "type" to "string",
                    "description" to "Your detailed reasoning."
                )
            ),
            "required" to listOf("thought_process")
        )
    )
)

fun List<IdeTool>.toOpenAIToolSchema(): JSONArray {
    val array = JSONArray()
    forEach { tool ->
        val functionObject = JSONObject()
            .put("name", tool.name)
            .put("description", tool.description)
            .put("parameters", JSONObject(tool.parameters))
        array.put(
            JSONObject()
                .put("type", "function")
                .put("function", functionObject)
        )
    }
    return array
}
