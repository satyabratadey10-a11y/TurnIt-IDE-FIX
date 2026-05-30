package com.turnit.ide.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.FunctionDeclaration
import com.google.ai.client.generativeai.type.FunctionType
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.text

class GeminiAgent(
    modelName: String,
    apiKey: String
) {
    private val tools = listOf(
        Tool(
            functionDeclarations = listOf(
                FunctionDeclaration(
                    name = "create_or_update_file",
                    description = "Creates a new file or overwrites an existing file with complete code.",
                    parameters = listOf(
                        Schema(
                            name = "path",
                            description = "Relative path to the file inside the workspace.",
                            type = FunctionType.STRING
                        ),
                        Schema(
                            name = "content",
                            description = "The complete raw code to write into the file.",
                            type = FunctionType.STRING
                        )
                    ),
                    requiredParameters = listOf("path", "content")
                ),
                FunctionDeclaration(
                    name = "execute_shell_command",
                    description = "Executes a bash/shell command in the IDE terminal.",
                    parameters = listOf(
                        Schema(
                            name = "command",
                            description = "The exact shell command to run.",
                            type = FunctionType.STRING
                        )
                    ),
                    requiredParameters = listOf("command")
                )
            )
        )
    )

    private val model = GenerativeModel(
        modelName = modelName,
        apiKey = apiKey,
        tools = tools
    )

    fun startChat(history: List<ChatMessage>) = model.startChat(
        history = history.map { message ->
            val role = if (message.role == "assistant") "model" else message.role
            content(role) { text(message.content) }
        }
    )
}
