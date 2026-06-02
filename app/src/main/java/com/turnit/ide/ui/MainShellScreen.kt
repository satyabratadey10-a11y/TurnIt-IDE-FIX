package com.turnit.ide.ui

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.client.generativeai.type.FunctionCallPart
import com.google.ai.client.generativeai.type.FunctionResponsePart
import com.google.ai.client.generativeai.type.Part
import com.google.ai.client.generativeai.type.TextPart
import com.google.ai.client.generativeai.type.content
import com.turnit.ide.R
import com.turnit.ide.ai.AiModel
import com.turnit.ide.ai.AiChatClient
import com.turnit.ide.ai.ChatMessage
import com.turnit.ide.ai.GeminiAgent
import com.turnit.ide.engine.ShellEngine
import com.turnit.ide.security.CommandFirewall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

enum class IdePane { TERMINAL, EDITOR, FILE_TREE }

private const val CHAT_PLACEHOLDER_TEXT = "Type your message..."
private const val CHAT_SPLIT_DEFAULT_WEIGHT = 0.7f
private const val CHAT_SPLIT_MIN_WEIGHT = 0.4f
private const val CHAT_SPLIT_MAX_WEIGHT = 0.85f
private val CHAT_SPLITTER_WIDTH = 8.dp
private val CHAT_SPLITTER_COLOR = IdeColors.Border
private val CHAT_BUBBLE_MAX_WIDTH = 280.dp
private val CHAT_CLI_BACKGROUND_COLOR = Color(0xFF0D1117)
private val CHAT_CLI_SURFACE_COLOR = Color(0xFF161B22)
private val CHAT_USER_COLOR = Color(0xFF3FB950)
private val CHAT_ASSISTANT_COLOR = Color(0xFF58A6FF)
private val CHAT_SYSTEM_COLOR = Color(0xFFF85149)
private val CHAT_CONTENT_COLOR = Color(0xFFC9D1D9)
private const val FILE_TREE_INDENT = "  "
private const val FILE_TREE_DIR_ICON = "📁"
private const val FILE_TREE_FILE_ICON = "📄"
private const val TERMINAL_PROMPT_SUFFIX = " \$ "
private const val TERMINAL_EXECUTION_RESTORE_DELAY_MS = 1_000L
private const val SHELL_SESSION_POLL_INTERVAL_MS = 200L
private const val SHELL_SESSION_INACTIVE_CHECK_LIMIT = 10

private val webAccessClient = OkHttpClient.Builder()
    .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
    .build()

private data class PendingAction(
    val messageId: Long,
    val command: String,
    val deferred: CompletableDeferred<JSONObject>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShellScreen(
    onRunBuild: () -> Unit = {},
    onStopBuild: () -> Unit = {},
    isBuildRunning: Boolean = false
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var activePane by remember { mutableStateOf(IdePane.TERMINAL) }
    var leftPaneWeight by remember { mutableFloatStateOf(CHAT_SPLIT_DEFAULT_WEIGHT) }

    val shellEngine = remember { ShellEngine(context) }
    val consoleLogs = remember {
        mutableStateListOf(
            "TurnIt IDE Shell Engine (v2.0)\n",
            "Waiting for command...\n"
        )
    }
    var terminalInput by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    var currentDir by remember { mutableStateOf("~") }
    var executionResetJob by remember { mutableStateOf<Job?>(null) }
    var executionNonce by remember { mutableStateOf(0) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var hasShellStarted by remember { mutableStateOf(false) }
    var isCapturingCommandOutput by remember { mutableStateOf(false) }
    val commandOutputBuffer = remember { StringBuilder() }
    val commandOutputLock = remember { Any() }

    val testCompileCommand = "echo 'Testing Compilers...'; gcc --version; javac -version; pwd; ls -la"
    val startShellSession = {
        if (!isRunning && !hasShellStarted) {
            hasShellStarted = true
            isRunning = true
            onRunBuild()
            shellEngine.startShell()
            activeJob = scope.launch {
                var sawActiveSession = false
                var consecutiveInactiveChecks = 0
                while (hasShellStarted) {
                    val sessionActive = shellEngine.isSessionActive == true
                    if (sessionActive) {
                        sawActiveSession = true
                        consecutiveInactiveChecks = 0
                    } else {
                        consecutiveInactiveChecks += 1
                        // Stop when a previously active session ends, or when startup never
                        // transitions to active within the configured grace window.
                        if (sawActiveSession || consecutiveInactiveChecks >= SHELL_SESSION_INACTIVE_CHECK_LIMIT) {
                            isRunning = false
                            hasShellStarted = false
                            onStopBuild()
                            break
                        }
                    }
                    delay(SHELL_SESSION_POLL_INTERVAL_MS)
                }
            }
        }
    }


    LaunchedEffect(Unit) {
        shellEngine.setOutputCallback { output ->
            consoleLogs.add(output)
            if (isCapturingCommandOutput) {
                synchronized(commandOutputLock) {
                    commandOutputBuffer.append(output).append("\n")
                }
            }
        }
        startShellSession()
    }
    
    val runCommand: (String) -> Boolean = run@{ command ->
        val trimmed = command.trim()
        if (trimmed.isBlank()) return@run false
        activePane = IdePane.TERMINAL
        if (!isRunning) {
            startShellSession()
            consoleLogs.add("[Native shell is starting, please retry command]\n")
            return@run false
        }
        consoleLogs.add("\n$ $trimmed\n")
        if (shellEngine.isSessionActive != true) {
            consoleLogs.add("[Failed to send input to native shell]\n")
            return@run false
        }
        shellEngine.sendInput(trimmed)
        true
    }
    val handleRunClick = { runCommand(testCompileCommand) }
    val handleTerminalSubmit = {
        val command = terminalInput.trim()
        if (command.isNotBlank()) {
            terminalInput = ""
            executionResetJob?.cancel()
            executionNonce += 1
            val submitNonce = executionNonce
            isExecuting = true
            if (command.startsWith("cd ")) {
                val targetDir = command.removePrefix("cd ").trim()
                if (targetDir.isNotBlank()) {
                    currentDir = targetDir
                }
            }
            val submitted = runCommand(command)
            if (!submitted) {
                isExecuting = false
            } else {
                executionResetJob = scope.launch {
                    delay(TERMINAL_EXECUTION_RESTORE_DELAY_MS)
                    if (executionNonce == submitNonce) {
                        isExecuting = false
                    }
                }
            }
        }
    }

    val handleStopClick = {
        if (isRunning) {
            shellEngine.stop()
            activeJob?.cancel()
            consoleLogs.add("\n[Process Killed by User]\n")
            isRunning = false
            hasShellStarted = false
            onStopBuild()
        }
    }

    val addCustomModelOption = remember {
        AiModel(
            name = "+ Add Custom Model",
            modelId = "",
            apiUrl = "",
            apiKey = ""
        )
    }
    val modelOptions = remember {
        val geminiOpenAiEndpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
        mutableStateListOf(
            AiModel(
                "Gemini 3 Flash",
                "gemini-3-flash",
                geminiOpenAiEndpoint,
                ""
            ),
            AiModel(
                "Gemini 2.5 Fast",
                "gemini-2.5-flash",
                geminiOpenAiEndpoint,
                ""
            ),
            AiModel(
                "qwen-plus",
                "qwen-plus",
                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                ""
            ),
            addCustomModelOption
        )
    }
    var selectedModel by remember { mutableStateOf(modelOptions.first()) }
    var showCustomModelDialog by remember { mutableStateOf(false) }
    var customModelName by remember { mutableStateOf("") }
    var customModelId by remember { mutableStateOf("") }
    var customModelUrl by remember { mutableStateOf("") }
    var customModelApiKey by remember { mutableStateOf("") }
    val clearCustomModelInputs = {
        customModelName = ""
        customModelId = ""
        customModelUrl = ""
        customModelApiKey = ""
    }
    val isCustomModelUrlValid = remember(customModelUrl) {
        val trimmedUrl = customModelUrl.trim()
        val parsedUrl = Uri.parse(trimmedUrl)
        parsedUrl.scheme == "https" && !parsedUrl.host.isNullOrBlank()
    }
    val isCustomModelInputValid =
        customModelName.isNotBlank() &&
            customModelId.isNotBlank() &&
            isCustomModelUrlValid
    val chatMessages = remember {
        mutableStateListOf(
            ChatMessage(role = "assistant", content = "Welcome to TurnIt AI assistant.")
        )
    }
    var isAgentThinking by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var currentGenerationJob by remember { mutableStateOf<Job?>(null) }
    var consecutiveToolCalls by remember { mutableStateOf(0) }
    var chatInput by remember { mutableStateOf("") }
    val stopGeneration: () -> Unit = {
        val action = pendingAction
        action?.deferred?.cancel()
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        isAgentThinking = false
        pendingAction = null
        if (action != null) {
            val pendingIndex = chatMessages.indexOfFirst { it.id == action.messageId }
            if (pendingIndex >= 0) {
                val message = chatMessages[pendingIndex]
                chatMessages[pendingIndex] = message.copy(isPendingAction = false)
            }
        }
        chatMessages.add(
            ChatMessage(
                role = "system",
                content = "[System] Generation forcefully aborted by user."
            )
        )
    }
    val sendChatPrompt = send@{
        val prompt = chatInput.trim()
        if (prompt.isBlank()) {
            return@send
        }
        consecutiveToolCalls = 0
        val action = pendingAction
        if (action != null) {
            val decision = prompt.firstOrNull()?.lowercaseChar()
            if (decision == 'y' || decision == 'n') {
                val feedback = prompt.drop(1).trim()
                chatMessages.add(ChatMessage(role = "user", content = prompt))
                chatInput = ""
                pendingAction = null
                val pendingIndex = chatMessages.indexOfFirst { it.id == action.messageId }
                if (pendingIndex >= 0) {
                    val message = chatMessages[pendingIndex]
                    chatMessages[pendingIndex] = message.copy(isPendingAction = false)
                }
                scope.launch {
                    if (decision == 'y') {
                        val command = action.command.trim()
                        val output = if (command.isBlank()) {
                            "Missing command argument."
                        } else {
                            synchronized(commandOutputLock) {
                                commandOutputBuffer.setLength(0)
                            }
                            isCapturingCommandOutput = true
                            val submitted = runCommand(command)
                            if (!submitted) {
                                isCapturingCommandOutput = false
                                "Command could not be executed."
                            } else {
                                delay(TERMINAL_EXECUTION_RESTORE_DELAY_MS)
                                isCapturingCommandOutput = false
                                synchronized(commandOutputLock) { commandOutputBuffer.toString() }.trim()
                            }
                        }
                        action.deferred.complete(
                            JSONObject()
                                .put("status", "approved")
                                .put("feedback", feedback)
                                .put("output", output)
                        )
                    } else {
                        action.deferred.complete(
                            JSONObject()
                                .put("status", "denied")
                                .put("feedback", feedback)
                        )
                    }
                }
                return@send
            }
        }
        val modelSnapshot = selectedModel
        val chatHistorySnapshot = chatMessages.toList()
        val isGeminiModel = modelSnapshot.apiUrl.contains("generativelanguage.googleapis.com", ignoreCase = true) ||
            modelSnapshot.modelId.startsWith("gemini", ignoreCase = true)

        chatMessages.add(ChatMessage(role = "user", content = prompt))
        chatInput = ""
        isAgentThinking = true

        val loadingBubble = ChatMessage(role = "assistant", content = "...")
        val loadingBubbleId = loadingBubble.id
        chatMessages.add(loadingBubble)

        currentGenerationJob = scope.launch {
            try {
                val response = try {
                    if (isGeminiModel) {
                        if (modelSnapshot.apiKey.isBlank()) {
                            "Error: Gemini API key is required for tool-enabled chat."
                        } else {
                            val geminiAgent = GeminiAgent(modelSnapshot.modelId, modelSnapshot.apiKey)
                            val chat = geminiAgent.startChat(chatHistorySnapshot)
                            var geminiResponse = chat.sendMessage(prompt)
                            var responseParts: List<Part> = geminiResponse.candidates
                                .firstOrNull()
                                ?.content
                                ?.parts
                                .orEmpty()
                            var functionCalls = responseParts.mapNotNull { part ->
                                part as? FunctionCallPart
                            }
                            while (functionCalls.isNotEmpty()) {
                                consecutiveToolCalls += 1
                                if (consecutiveToolCalls > 5) {
                                    stopGeneration()
                                    chatMessages.add(
                                        ChatMessage(
                                            role = "system",
                                            content = "[Firewall] Agent loop limit exceeded (5+ consecutive actions). Execution halted to prevent hallucination runaway."
                                        )
                                    )
                                    return@launch
                                }
                                val functionCall = functionCalls.first()
                                if (functionCall.name == "execute_shell_command") {
                                    val command = extractGeminiArg(functionCall.args, "command")?.trim().orEmpty()
                                    val firewallResult = CommandFirewall.analyzeCommand(command)
                                    if (firewallResult is com.turnit.ide.security.FirewallResult.Blocked) {
                                        val toolResult = JSONObject()
                                            .put("error", "SYSTEM FIREWALL BLOCKED COMMAND: ${firewallResult.reason}")
                                        geminiResponse = chat.sendMessage(
                                            content("function") {
                                                part(FunctionResponsePart(functionCall.name, toolResult))
                                            }
                                        )
                                    } else {
                                        val deferred = CompletableDeferred<JSONObject>()
                                        val pendingMessage = ChatMessage(
                                            role = "assistant",
                                            content = "Command approval required.",
                                            isPendingAction = true,
                                            pendingCommand = command
                                        )
                                        chatMessages.add(pendingMessage)
                                        pendingAction = PendingAction(
                                            messageId = pendingMessage.id,
                                            command = command,
                                            deferred = deferred
                                        )
                                        isAgentThinking = false
                                        val loadingBubbleIndex = chatMessages.indexOfLast { it.id == loadingBubbleId }
                                        if (loadingBubbleIndex >= 0) {
                                            chatMessages.removeAt(loadingBubbleIndex)
                                        }
                                        val toolResult = deferred.await()
                                        isAgentThinking = true
                                        geminiResponse = chat.sendMessage(
                                            content("function") {
                                                part(FunctionResponsePart(functionCall.name, toolResult))
                                            }
                                        )
                                    }
                                    responseParts = geminiResponse.candidates
                                        .firstOrNull()
                                        ?.content
                                        ?.parts
                                        .orEmpty()
                                    functionCalls = responseParts.mapNotNull { part ->
                                        part as? FunctionCallPart
                                    }
                                } else {
                                    val functionResult = handleGeminiFunctionCall(
                                        functionCall = functionCall,
                                        workspaceRoot = context.filesDir,
                                        runCommand = runCommand
                                    )
                                    geminiResponse = chat.sendMessage(
                                        content("function") {
                                            part(FunctionResponsePart(functionCall.name, functionResult))
                                        }
                                    )
                                    responseParts = geminiResponse.candidates
                                        .firstOrNull()
                                        ?.content
                                        ?.parts
                                        .orEmpty()
                                    functionCalls = responseParts.mapNotNull { part ->
                                        part as? FunctionCallPart
                                    }
                                }
                            }
                            responseParts
                                .mapNotNull { part -> (part as? TextPart)?.text }
                                .joinToString("")
                                .ifBlank { null }
                                ?: "Error: Empty response body"
                        }
                    } else {
                        AiChatClient.sendMessage(
                            model = modelSnapshot,
                            chatHistory = chatHistorySnapshot,
                            newPrompt = prompt
                        )
                    }
                } catch (e: CancellationException) {
                    null
                } catch (e: Exception) {
                    "Error: ${e.message ?: "Unable to contact AI service"}"
                }
                if (!response.isNullOrBlank()) {
                    chatMessages.add(ChatMessage(role = "assistant", content = response))
                }
            } finally {
                isAgentThinking = false
                val loadingBubbleIndex = chatMessages.indexOfLast { it.id == loadingBubbleId }
                if (loadingBubbleIndex >= 0) {
                    chatMessages.removeAt(loadingBubbleIndex)
                }
                currentGenerationJob = null
            }
        }
    }

    val resolvePendingAction: (Long) -> Unit = { messageId ->
        val pendingIndex = chatMessages.indexOfFirst { it.id == messageId }
        if (pendingIndex >= 0) {
            val message = chatMessages[pendingIndex]
            chatMessages[pendingIndex] = message.copy(isPendingAction = false)
        }
    }

    val handleApproveAction: (ChatMessage) -> Unit = { message ->
        val action = pendingAction
        if (action != null && action.messageId == message.id) {
            pendingAction = null
            resolvePendingAction(message.id)
            scope.launch {
                val command = action.command.trim()
                val result = if (command.isBlank()) {
                    JSONObject().put("status", "error").put("message", "Missing command argument.")
                } else {
                    synchronized(commandOutputLock) {
                        commandOutputBuffer.setLength(0)
                    }
                    isCapturingCommandOutput = true
                    val submitted = runCommand(command)
                    if (!submitted) {
                        isCapturingCommandOutput = false
                        JSONObject().put("status", "error").put("message", "Command could not be executed.")
                    } else {
                        delay(TERMINAL_EXECUTION_RESTORE_DELAY_MS)
                        isCapturingCommandOutput = false
                        val outputSnapshot = synchronized(commandOutputLock) { commandOutputBuffer.toString() }
                        JSONObject()
                            .put("status", "success")
                            .put("command", command)
                            .put("output", outputSnapshot.trim())
                    }
                }
                action.deferred.complete(result)
            }
        }
    }

    val handleDenyAction: (ChatMessage) -> Unit = { message ->
        val action = pendingAction
        if (action != null && action.messageId == message.id) {
            pendingAction = null
            resolvePendingAction(message.id)
            action.deferred.complete(
                JSONObject().put("error", "User denied permission to run this command")
            )
        }
    }

    ModalNavigationDrawer(
        modifier = Modifier.fillMaxSize(),
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(250.dp)
                    .liquidGlassBackground(),
                drawerContainerColor = IdeColors.BgSurface,
                drawerContentColor = IdeColors.TextPrimary
            ) {
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.PostAdd, contentDescription = "New Chat") },
                    label = { Text("New Chat") },
                    selected = false,
                    onClick = {
                        chatMessages.clear()
                        chatMessages.add(ChatMessage(role = "assistant", content = "New chat started."))
                        chatInput = ""
                        currentGenerationJob?.cancel()
                        currentGenerationJob = null
                        isAgentThinking = false
                        pendingAction = null
                        consecutiveToolCalls = 0
                        scope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = IdeColors.TextSecondary,
                        unselectedIconColor = IdeColors.TextMuted
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.History, contentDescription = "History") },
                    label = { Text("History") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = IdeColors.TextSecondary,
                        unselectedIconColor = IdeColors.TextMuted
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Key, contentDescription = "API Key Settings") },
                    label = { Text("API Key Settings") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = IdeColors.TextSecondary,
                        unselectedIconColor = IdeColors.TextMuted
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            containerColor = IdeColors.Bg,
            topBar = {
                val rainbowShift = rememberInfiniteTransition(label = "brand_shift")
                val shift by rainbowShift.animateFloat(
                    initialValue = 0f,
                    targetValue = 1000f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(4000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "brand_shift_anim"
                )
                TopAppBar(
                    title = {
                        Text(
                            text = "TurnIt",
                            style = TextStyle(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFFF3B3B),
                                        Color(0xFF3BFF4F),
                                        Color(0xFF3B82FF),
                                        Color(0xFFFF3B3B)
                                    ),
                                    start = Offset(shift - 300f, 0f),
                                    end = Offset(shift + 300f, 0f)
                                ),
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Menu",
                                tint = IdeColors.TextSecondary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (isRunning || isBuildRunning) handleStopClick() else handleRunClick()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = if (isRunning || isBuildRunning) IdeColors.AccentOrange else IdeColors.AccentGreen
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = IdeColors.BgSurface
                    )
                )
            }
        ) { pad ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
            ) {
                val totalWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val rightPaneWeight = 1f - leftPaneWeight

                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(leftPaneWeight)
                            .fillMaxHeight()
                            .background(IdeColors.Bg)
                    ) {
                        PaneTabStrip(
                            activePane = activePane,
                            onSelect = { activePane = it }
                        )
                        HorizontalDivider(color = IdeColors.Border, thickness = 1.dp)
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (activePane) {
                                IdePane.TERMINAL -> TerminalConsoleView(
                                    logs = consoleLogs,
                                    input = terminalInput,
                                    isExecuting = isExecuting,
                                    currentDir = currentDir,
                                    onInputChange = { terminalInput = it },
                                    onSubmit = handleTerminalSubmit
                                )
                                IdePane.EDITOR -> CodeEditorView()
                                IdePane.FILE_TREE -> FileTreePane(filesDir = context.filesDir)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(CHAT_SPLITTER_WIDTH)
                            .background(CHAT_SPLITTER_COLOR)
                            .pointerInput(totalWidthPx) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    val delta = dragAmount / totalWidthPx
                                    leftPaneWeight =
                                        (leftPaneWeight + delta).coerceIn(CHAT_SPLIT_MIN_WEIGHT, CHAT_SPLIT_MAX_WEIGHT)
                                }
                            }
                    )

                    Box(
                        modifier = Modifier
                            .weight(rightPaneWeight)
                            .fillMaxHeight()
                            .liquidGlassBackground(
                                fallbackColor = IdeColors.BgSurface
                            )
                    ) {
                        ChatPane(
                            modifier = Modifier.fillMaxSize(),
                            selectedModel = selectedModel,
                            modelOptions = modelOptions,
                            onModelSelected = { model ->
                                if (model == addCustomModelOption) {
                                    clearCustomModelInputs()
                                    showCustomModelDialog = true
                                } else {
                                    selectedModel = model
                                }
                            },
                            messages = chatMessages,
                            input = chatInput,
                            onInputChange = { chatInput = it },
                            onSend = sendChatPrompt,
                            onStopGeneration = stopGeneration,
                            isAgentThinking = isAgentThinking,
                            onApproveCommand = handleApproveAction,
                            onDenyCommand = handleDenyAction
                        )
                    }
                }
            }
        }
    }

    if (showCustomModelDialog) {
        AlertDialog(
            onDismissRequest = {
                showCustomModelDialog = false
                clearCustomModelInputs()
            },
            title = { Text("Add Custom Model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customModelName,
                        onValueChange = { customModelName = it },
                        label = { Text("Model Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customModelId,
                        onValueChange = { customModelId = it },
                        label = { Text("Model ID (API)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customModelUrl,
                        onValueChange = { customModelUrl = it },
                        label = { Text("API Provider URL") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customModelApiKey,
                        onValueChange = { customModelApiKey = it },
                        label = { Text("API Key (Optional)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newModel = AiModel(
                            name = customModelName.trim(),
                            modelId = customModelId.trim(),
                            apiUrl = customModelUrl.trim(),
                            apiKey = customModelApiKey.trim(),
                            isCustom = true
                        )
                        modelOptions.add(modelOptions.size - 1, newModel)
                        selectedModel = newModel
                        showCustomModelDialog = false
                        clearCustomModelInputs()
                    },
                    enabled = isCustomModelInputValid
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCustomModelDialog = false
                        clearCustomModelInputs()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChatPane(
    modifier: Modifier = Modifier,
    selectedModel: AiModel,
    modelOptions: List<AiModel>,
    onModelSelected: (AiModel) -> Unit,
    messages: List<ChatMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStopGeneration: () -> Unit,
    isAgentThinking: Boolean,
    onApproveCommand: (ChatMessage) -> Unit,
    onDenyCommand: (ChatMessage) -> Unit
) {
    var modelMenuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val thinkingTransition = rememberInfiniteTransition(label = "thinking")
    val thinkingAlpha by thinkingTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinking_alpha"
    )

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CHAT_CLI_BACKGROUND_COLOR)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CHAT_CLI_SURFACE_COLOR)
                .border(1.dp, IdeColors.Border, RoundedCornerShape(10.dp))
                .clickable { modelMenuOpen = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = selectedModel.name,
                color = IdeColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            androidx.compose.material3.DropdownMenu(
                expanded = modelMenuOpen,
                onDismissRequest = { modelMenuOpen = false }
            ) {
                modelOptions.forEach { model ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Text(
                                text = model.name,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        onClick = {
                            onModelSelected(model)
                            modelMenuOpen = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                val isUser = message.role == "user"
                val isSystem = message.role == "system"
                val prefix = if (isUser) {
                    "➜ user: "
                } else if (isSystem) {
                    "[system]: "
                } else {
                    "⚡ turnit: "
                }
                val prefixColor = if (isUser) {
                    Color.Green
                } else if (isSystem) {
                    CHAT_SYSTEM_COLOR
                } else {
                    Color.Cyan
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = CHAT_BUBBLE_MAX_WIDTH),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = prefix,
                                color = prefixColor,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = message.content,
                                color = CHAT_CONTENT_COLOR,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (!isSystem && !isUser && message.isPendingAction && !message.pendingCommand.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CHAT_CLI_SURFACE_COLOR)
                                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = message.pendingCommand,
                                    color = Color(0xFF38BDF8),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onApproveCommand(message) },
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = IdeColors.AccentGreen
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Approve & Run", fontFamily = FontFamily.Monospace)
                                    }
                                    Button(
                                        onClick = { onDenyCommand(message) },
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = IdeColors.AccentOrange
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Deny", fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (isAgentThinking) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(CHAT_CLI_SURFACE_COLOR)
                                .border(1.dp, IdeColors.Border, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "⚡ turnit: thinking...",
                                color = CHAT_ASSISTANT_COLOR.copy(alpha = thinkingAlpha),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(
                            text = CHAT_PLACEHOLDER_TEXT,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = true,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CHAT_CLI_SURFACE_COLOR,
                        unfocusedContainerColor = CHAT_CLI_SURFACE_COLOR,
                        disabledContainerColor = CHAT_CLI_SURFACE_COLOR,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = IdeColors.TextMuted,
                        focusedPlaceholderColor = IdeColors.TextMuted,
                        unfocusedPlaceholderColor = IdeColors.TextMuted,
                        disabledPlaceholderColor = IdeColors.TextMuted,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color.White
                    )
                )
                Spacer(Modifier.width(8.dp))
                if (isAgentThinking) {
                    IconButton(onClick = onStopGeneration) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Stop responding",
                            tint = CHAT_SYSTEM_COLOR
                        )
                    }
                } else {
                    IconButton(onClick = onSend, enabled = input.isNotBlank()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (input.isNotBlank()) CHAT_USER_COLOR else IdeColors.TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalConsoleView(
    logs: List<String>,
    input: String,
    isExecuting: Boolean,
    currentDir: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeColors.Bg)
            .padding(8.dp)
    ) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f)
            ) {
                items(logs) { line ->
                    Text(
                        text = line,
                        color = IdeColors.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isExecuting) {
                Text(
                    text = "Executing command...",
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.semantics {
                        contentDescription = "Executing command"
                    }
                )
            } else {
                Text(
                    text = "$currentDir$TERMINAL_PROMPT_SUFFIX",
                    color = Color.Green,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    label = { Text("Command") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = { onSubmit() }
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onSubmit) {
                    Icon(
                        imageVector = Icons.Filled.Terminal,
                        contentDescription = "Run command",
                        tint = IdeColors.AccentGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeEditorView() {
    var text by remember { mutableStateOf("fun main() {\n    println(\"Hello TurnIt!\")\n}") }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeColors.Bg)
    ) {
        Box(
            modifier = Modifier
                .background(IdeColors.BgSurface)
                .fillMaxHeight()
                .width(48.dp)
                .padding(8.dp),
            contentAlignment = Alignment.TopStart
        ) {
            LazyColumn {
                items(30) { lineNumber ->
                    Text(
                        text = (lineNumber + 1).toString(),
                        color = IdeColors.TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }

        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(8.dp),
            textStyle = TextStyle(
                color = IdeColors.TextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
            )
        )
    }
}

@Composable
private fun PaneTabStrip(
    activePane: IdePane,
    onSelect: (IdePane) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(IdeColors.BgSurface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tabs: List<Pair<IdePane, String>> = listOf(
            IdePane.TERMINAL to "TERMINAL",
            IdePane.EDITOR to "EDITOR",
            IdePane.FILE_TREE to "FILES"
        )
        tabs.forEach { (pane, label) ->
            PaneTab(
                label = label,
                isActive = pane == activePane,
                onClick = { onSelect(pane) }
            )
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun PaneTab(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isActive) IdeColors.AccentBlue.copy(alpha = 0.12f) else Color.Transparent
    val border = if (isActive) IdeColors.AccentBlue.copy(alpha = 0.60f) else IdeColors.Border
    val textColor = if (isActive) IdeColors.AccentBlue else IdeColors.TextMuted

    Box(
        modifier = Modifier
            .height(26.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun FileTreePane(filesDir: File) {
    val entries by produceState(initialValue = emptyList<FileTreeEntry>(), key1 = filesDir.absolutePath) {
        value = withContext(Dispatchers.IO) {
            buildFileTreeEntries(filesDir)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeColors.Bg)
            .padding(8.dp)
    ) {
        Text(
            text = filesDir.absolutePath,
            color = IdeColors.TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        text = "(empty)",
                        color = IdeColors.TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            } else {
                items(entries) { entry ->
                    Text(
                        text = entry.renderLabel,
                        color = IdeColors.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private data class FileTreeEntry(
    val file: File,
    val depth: Int,
    val renderLabel: String
)

private fun buildFileTreeEntries(root: File): List<FileTreeEntry> {
    val items = mutableListOf<FileTreeEntry>()
    fun visit(node: File, depth: Int) {
        val children = node.listFiles()
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .orEmpty()
        children.forEach { child ->
            items.add(
                FileTreeEntry(
                    file = child,
                    depth = depth,
                    renderLabel = "${FILE_TREE_INDENT.repeat(depth)}${if (child.isDirectory) FILE_TREE_DIR_ICON else FILE_TREE_FILE_ICON} ${child.name}"
                )
            )
            if (child.isDirectory) {
                visit(child, depth + 1)
            }
        }
    }
    visit(root, 0)
    return items
}

private suspend fun handleGeminiFunctionCall(
    functionCall: FunctionCallPart,
    workspaceRoot: File,
    runCommand: (String) -> Boolean
): JSONObject {
    val args = functionCall.args
    return when (functionCall.name) {
        "create_or_update_file" -> {
            val path = extractGeminiArg(args, "path")?.trim().orEmpty()
            val content = extractGeminiArg(args, "content").orEmpty()
            if (path.isBlank()) {
                JSONObject().put("status", "error").put("message", "Missing path argument.")
            } else {
                val targetFile = resolveWorkspaceFile(workspaceRoot, path)
                if (targetFile == null) {
                    JSONObject().put("status", "error").put("message", "Invalid path outside workspace.")
                } else {
                    try {
                        withContext(Dispatchers.IO) {
                            targetFile.parentFile?.mkdirs()
                            targetFile.writeText(content)
                        }
                        JSONObject().put("status", "success").put("path", path)
                    } catch (e: Exception) {
                        JSONObject().put("status", "error").put("message", e.message ?: "Write failed.")
                    }
                }
            }
        }
        "execute_shell_command" -> {
            JSONObject().put("status", "error").put("message", "Command approval required.")
        }
        "google_search" -> {
            val query = extractGeminiArg(args, "query")?.trim().orEmpty()
            if (query.isBlank()) {
                JSONObject().put("status", "error").put("message", "Missing query argument.")
            } else {
                performWebSearch(query)
            }
        }
        "fetch_webpage" -> {
            val url = extractGeminiArg(args, "url")?.trim().orEmpty()
            if (url.isBlank()) {
                JSONObject().put("status", "error").put("message", "Missing url argument.")
            } else {
                fetchWebpageContent(url)
            }
        }
        else -> JSONObject().put("status", "error").put("message", "Unknown tool ${functionCall.name}.")
    }
}

private fun resolveWorkspaceFile(workspaceRoot: File, relativePath: String): File? {
    if (relativePath.isBlank() || File(relativePath).isAbsolute) {
        return null
    }
    return try {
        val canonicalRoot = workspaceRoot.canonicalFile
        val canonicalTarget = File(canonicalRoot, relativePath).canonicalFile
        val rootPath = canonicalRoot.path.trimEnd(File.separatorChar) + File.separator
        if (!canonicalTarget.path.startsWith(rootPath)) {
            null
        } else {
            canonicalTarget
        }
    } catch (_: Exception) {
        null
    }
}

private fun extractGeminiArg(args: Map<String, Any?>, key: String): String? {
    val raw = args[key] ?: return null
    if (raw is String) {
        return raw
    }
    val value = raw.toString().trim()
    return if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
        value.substring(1, value.length - 1)
    } else {
        value
    }
}

private suspend fun performWebSearch(query: String): JSONObject {
    val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
    val searchUrl = "https://www.google.com/search?q=$encodedQuery&hl=en&num=5"
    return fetchWebpageContent(searchUrl).also { result ->
        if (result.optString("status") == "success") {
            val content = result.optString("content")
            result.remove("content")
            result.put("results", content)
            result.put("query", query)
        }
    }
}

private suspend fun fetchWebpageContent(url: String): JSONObject {
    if (!isHttpUrl(url)) {
        return JSONObject().put("status", "error").put("message", "Only http/https URLs are supported.")
    }
    return withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "TurnItIDE/1.0")
            .build()
        try {
            webAccessClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    JSONObject()
                        .put("status", "error")
                        .put("message", "HTTP ${response.code} ${response.message}")
                } else {
                    JSONObject()
                        .put("status", "success")
                        .put("content", body)
                        .put("url", url)
                }
            }
        } catch (e: Exception) {
            JSONObject().put("status", "error").put("message", e.message ?: "Request failed.")
        }
    }
}

private fun isHttpUrl(url: String): Boolean {
    return try {
        val parsed = Uri.parse(url)
        parsed.scheme?.equals("http", ignoreCase = true) == true ||
            parsed.scheme?.equals("https", ignoreCase = true) == true
    } catch (_: Exception) {
        false
    }
}

@Composable
private fun PlaceholderPane(label: String, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeColors.Bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = accent.copy(alpha = 0.4f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
