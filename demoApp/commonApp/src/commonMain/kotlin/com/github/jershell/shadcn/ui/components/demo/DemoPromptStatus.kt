package com.github.jershell.shadcn.ui.components.demo

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.github.jershell.shadcn.components.button.*
import com.github.jershell.shadcn.components.promptkit.*
import com.github.jershell.shadcn.components.typography.H4
import com.github.jershell.shadcn.components.typography.Muted
import com.github.jershell.shadcn.theme.BaseTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Interactive coverage of all prompt-kit status, reasoning and response presentation families. */
@Composable
fun DemoPromptStatus() {
    var motion by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("Actions are ready") }
    var toolState by remember { mutableStateOf(ToolState.InputAvailable) }
    var selectedFeedback by remember { mutableStateOf(Feedback.None) }
    var feedbackVisible by remember { mutableStateOf(true) }
    var thinking by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(TextStreamMode.Typewriter) }
    val options = TextStreamOptions(mode = mode, speed = 60, animationsEnabled = motion)
    val stream = rememberTextStream("Compose controls can present a response one cluster at a time: café 👩‍💻.", options)
    val chunks = remember {
        flow {
            emit("A provider-neutral ")
            delay(200)
            emit("Flow can stream ")
            delay(200)
            emit("the same interface.")
        }
    }
    val flowStream = rememberTextStream(chunks, options)

    CompositionLocalProvider(LocalPromptKitMotionEnabled provides motion) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BaseTokens.token24)) {
            H4("Response streaming")
            Muted("Both local text and Flow streams support cancellation, pause, resume and restart.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(BaseTokens.token8),
                verticalArrangement = Arrangement.spacedBy(BaseTokens.token8)) {
                Button({ motion = !motion }, size = ButtonSize.Sm) { ButtonText(if (motion) "Reduce motion" else "Animate") }
                Button({ mode = if (mode == TextStreamMode.Typewriter) TextStreamMode.Fade else TextStreamMode.Typewriter },
                    variant = ButtonVariant.Outline, size = ButtonSize.Sm) { ButtonText(mode.name) }
                Button({ if (stream.isPaused) stream.resume() else stream.pause() }, size = ButtonSize.Sm) {
                    ButtonText(if (stream.isPaused) "Resume" else "Pause")
                }
                Button({ stream.restart(); flowStream.restart() }, variant = ButtonVariant.Outline, size = ButtonSize.Sm) { ButtonText("Restart both") }
                Button({ stream.reset(); flowStream.reset() }, variant = ButtonVariant.Ghost, size = ButtonSize.Sm) { ButtonText("Reset") }
            }
            ResponseStream(stream)
            ResponseStream(flowStream)

            H4("Reasoning and progress")
            Reasoning(isStreaming = stream.isRunning, defaultOpen = true) {
                ReasoningTrigger("Response explanation")
                ReasoningContent("**Streaming state** opens this region; completion closes it. You can also toggle it yourself.", markdown = true)
            }
            Steps {
                StepsTrigger("3 preparation steps")
                StepsContent {
                    StepsItem("Read the request")
                    StepsItem("Check the available sources")
                    StepsItem("Compose the response")
                }
            }
            ChainOfThought {
                Step("source", defaultOpen = true) {
                    ChainOfThoughtTrigger("Source lookup")
                    ChainOfThoughtContent { ChainOfThoughtItem("Located the relevant reference material.") }
                }
                Step("review") {
                    ChainOfThoughtTrigger("Review")
                    ChainOfThoughtContent { ChainOfThoughtItem("Cross-checked the result before presenting it.") }
                }
            }

            H4("Tool lifecycle")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(BaseTokens.token8)) {
                ToolState.entries.forEach { state ->
                    Button({ toolState = state }, variant = if (toolState == state) ButtonVariant.Default else ButtonVariant.Outline,
                        size = ButtonSize.Sm) { ButtonText(state.name) }
                }
            }
            Tool(ToolPart("search_docs", toolState,
                input = buildJsonObject { put("query", "Compose semantics") },
                output = if (toolState == ToolState.OutputAvailable) buildJsonObject { put("matches", 3) } else null,
                toolCallId = "demo-search-1", errorText = if (toolState == ToolState.OutputError) "The example service is offline." else null),
                defaultOpen = true)
            Source("https://www.prompt-kit.com/", onOpenLink = { notice = "Source requested: $it" }) {
                SourceContent("Prompt-kit", "The original component designs and behavior contracts.")
            }

            H4("Messages and feedback")
            SystemMessage(notice, cta = SystemMessageAction("Reset message", { notice = "Actions are ready" }, ButtonVariant.Outline))
            SystemMessage("Review the result before continuing.", variant = SystemMessageVariant.Warning, fill = true)
            SystemMessage("A recoverable connection error.", variant = SystemMessageVariant.Error,
                cta = SystemMessageAction("Retry", { notice = "Retry requested" }))
            if (thinking) ThinkingBar(onStop = { thinking = false; notice = "Stopped" }, stopLabel = "Stop",
                onClick = { notice = "Thinking details requested" })
            else Button({ thinking = true }, size = ButtonSize.Sm) { ButtonText("Start thinking") }
            if (feedbackVisible) FeedbackBar(selection = selectedFeedback,
                onSelectionChange = { selectedFeedback = it; notice = "Feedback: ${it.name}" },
                onClose = { feedbackVisible = false })
            else Button({ feedbackVisible = true }, size = ButtonSize.Sm) { ButtonText("Show feedback") }

            H4("All loader variants")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(BaseTokens.token24),
                verticalArrangement = Arrangement.spacedBy(BaseTokens.token16)) {
                LoaderVariant.entries.forEach { variant ->
                    Column(verticalArrangement = Arrangement.spacedBy(BaseTokens.token8)) {
                        Muted(variant.name)
                        Loader(variant = variant)
                    }
                }
            }
            TextShimmer("Custom shimmer spread and duration", spread = 35f, durationMillis = 2500)
        }
    }
}
