package com.github.jershell.shadcn.components.promptkit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.composeunstyled.theme.Theme
import com.github.jershell.shadcn.theme.ColorProps
import com.github.jershell.shadcn.theme.ColorTokens
import com.github.jershell.shadcn.theme.TypographyStyles
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class TextStreamMode { Typewriter, Fade }

/** Timing is independent of incoming network chunks. A chunk is measured in Unicode clusters. */
@Immutable
data class TextStreamOptions(
    val mode: TextStreamMode = TextStreamMode.Typewriter,
    val speed: Int = 20,
    val fadeDurationMillis: Int? = null,
    val segmentDelayMillis: Long? = null,
    val characterChunkSize: Int? = null,
    val animationsEnabled: Boolean = true,
) {
    init {
        require(speed in 1..100) { "speed must be between 1 and 100" }
        require(fadeDurationMillis == null || fadeDurationMillis >= 0)
        require(segmentDelayMillis == null || segmentDelayMillis >= 0)
        require(characterChunkSize == null || characterChunkSize > 0)
    }

    val fadeDuration: Int get() = if (!animationsEnabled) 0 else
        fadeDurationMillis ?: (1000 / sqrt(speed.toDouble())).roundToInt()
    val segmentDelay: Long get() = if (!animationsEnabled) 0 else
        segmentDelayMillis ?: (100 / sqrt(speed.toDouble())).roundToInt().coerceAtLeast(1).toLong()
    val chunkSize: Int get() = characterChunkSize ?: if (speed < 25) 1 else
        ((speed - 25) / 10f).roundToInt().coerceAtLeast(1)
}

@Immutable
data class TextStreamSegment(val text: String, val index: Int)

/**
 * Reusable, cancellation-safe text presentation state. [pause] backpressures Flow collection;
 * [reset] cancels it and clears presentation; [restart] recollects the original Flow.
 * Use a cold/replayable Flow if restarting must reproduce its original contents.
 * Completion is delivered once, after the final fade, and never for cancellation or failure.
 * All methods must be called from the owning UI dispatcher.
 */
@Stable
class TextStreamState(
    private val scope: CoroutineScope,
    options: TextStreamOptions = TextStreamOptions(),
    onComplete: () -> Unit = {},
    onError: (Throwable) -> Unit = {},
) {
    var displayedText: String by mutableStateOf("")
        private set
    var segments: List<TextStreamSegment> by mutableStateOf(emptyList())
        private set
    var isComplete: Boolean by mutableStateOf(false)
        private set
    var isRunning: Boolean by mutableStateOf(false)
        private set
    var isPaused: Boolean by mutableStateOf(false)
        private set
    var error: Throwable? by mutableStateOf(null)
        private set
    var options: TextStreamOptions by mutableStateOf(options)
        private set
    var generation: Long by mutableLongStateOf(0L)
        private set

    private var completion = onComplete
    private var failure = onError
    private var job: Job? = null
    private var source: (suspend TextStreamState.() -> Unit)? = null
    private val resumed = MutableStateFlow(true)

    fun updateOptions(options: TextStreamOptions) { this.options = options }
    fun updateCallbacks(onComplete: () -> Unit, onError: (Throwable) -> Unit) {
        completion = onComplete
        failure = onError
    }

    fun start(text: String) {
        source = { reveal(text) }
        restart()
    }

    fun start(textStream: Flow<String>) {
        source = {
            // A provider can split a UTF-16 surrogate pair across network chunks.
            var pending = ""
            textStream.collect { chunk ->
                awaitResume()
                val text = pending + chunk
                val holdLast = text.lastOrNull()?.isHighSurrogate() == true
                pending = if (holdLast) text.takeLast(1) else ""
                reveal(if (holdLast) text.dropLast(1) else text)
            }
            if (pending.isNotEmpty()) reveal("\uFFFD")
        }
        restart()
    }

    fun pause() {
        if (isRunning) { isPaused = true; resumed.value = false }
    }

    fun resume() { isPaused = false; resumed.value = true }

    fun reset() {
        generation++
        job?.cancel()
        job = null
        resume()
        displayedText = ""
        segments = emptyList()
        isComplete = false
        isRunning = false
        error = null
    }

    fun restart() {
        reset()
        val run = source ?: return
        if (!scope.isActive) return
        val owner = generation
        isRunning = true
        val task = scope.launch(start = CoroutineStart.LAZY) {
            try {
                run()
                awaitResume()
                if (options.mode == TextStreamMode.Fade) delay(options.fadeDuration.toLong())
                awaitResume()
                ensureActive()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Throwable) {
                if (owner == generation) {
                    error = cause
                    isRunning = false
                    failure(cause)
                }
                return@launch
            }
            if (owner == generation) {
                isComplete = true
                isRunning = false
                completion()
            }
        }
        // Also covers cancellation before the launch body is ever dispatched. Generation
        // ownership prevents an older job's completion handler changing its replacement.
        job = task
        task.invokeOnCompletion {
            if (owner == generation) {
                isRunning = false
                isPaused = false
                resumed.value = true
            }
        }
        task.start()
    }

    /** Alias retained for the upstream hook's imperative API. */
    fun startStreaming() = restart()
    fun getFadeDuration(): Int = options.fadeDuration
    fun getSegmentDelay(): Long = options.segmentDelay

    private suspend fun awaitResume() {
        resumed.first { it }
        currentCoroutineContext().ensureActive()
    }

    private suspend fun reveal(text: String) {
        var position = 0
        while (position < text.length) {
            awaitResume()
            val current = options
            var end = position
            if (!current.animationsEnabled) {
                end = text.length
            } else if (current.mode == TextStreamMode.Fade) {
                val whitespace = text[position].isWhitespace()
                do { end = nextTextClusterEnd(text, end) }
                while (end < text.length && text[end].isWhitespace() == whitespace)
            } else {
                repeat(current.chunkSize) {
                    if (end < text.length) end = nextTextClusterEnd(text, end)
                }
            }
            val next = text.substring(position, end)
            displayedText += next
            segments = segments + TextStreamSegment(next, segments.size)
            position = end
            if (current.segmentDelay > 0) delay(current.segmentDelay)
        }
    }
}

/** Avoids splitting surrogate pairs, combining marks, emoji modifiers, flags and ZWJ emoji. */
internal fun nextTextClusterEnd(text: String, start: Int): Int {
    fun codePointAt(index: Int): Int {
        val high = text[index]
        return if (high.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate())
            0x10000 + ((high.code - 0xD800) shl 10) + text[index + 1].code - 0xDC00
        else high.code
    }
    fun advance(index: Int): Int = index + if (codePointAt(index) > 0xFFFF) 2 else 1
    var end = advance(start)
    val first = codePointAt(start)
    if (first in 0x1F1E6..0x1F1FF && end < text.length && codePointAt(end) in 0x1F1E6..0x1F1FF)
        end = advance(end)
    while (end < text.length) {
        val code = codePointAt(end)
        val category = text[end].category
        when {
            code == 0x200D && end + 1 < text.length -> end = advance(end + 1)
            code in 0xFE00..0xFE0F || code in 0x1F3FB..0x1F3FF ||
                category == CharCategory.NON_SPACING_MARK ||
                category == CharCategory.COMBINING_SPACING_MARK ||
                category == CharCategory.ENCLOSING_MARK -> end = advance(end)
            else -> return end
        }
    }
    return end
}

@Composable
private fun rememberTextStreamHolder(
    options: TextStreamOptions,
    onComplete: () -> Unit,
    onError: (Throwable) -> Unit,
): TextStreamState {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { TextStreamState(scope, options, onComplete, onError) }
    SideEffect {
        state.updateOptions(options)
        state.updateCallbacks(onComplete, onError)
    }
    DisposableEffect(state) { onDispose { state.reset() } }
    return state
}

@Composable
fun rememberTextStream(
    text: String,
    options: TextStreamOptions = TextStreamOptions(animationsEnabled = LocalPromptKitMotionEnabled.current),
    onComplete: () -> Unit = {},
    onError: (Throwable) -> Unit = {},
): TextStreamState {
    val state = rememberTextStreamHolder(options, onComplete, onError)
    LaunchedEffect(state, text) { state.start(text) }
    return state
}

@Composable
fun rememberTextStream(
    textStream: Flow<String>,
    options: TextStreamOptions = TextStreamOptions(animationsEnabled = LocalPromptKitMotionEnabled.current),
    onComplete: () -> Unit = {},
    onError: (Throwable) -> Unit = {},
): TextStreamState {
    val state = rememberTextStreamHolder(options, onComplete, onError)
    LaunchedEffect(state, textStream) { state.start(textStream) }
    return state
}

@Composable
fun useTextStream(text: String, options: TextStreamOptions = TextStreamOptions(animationsEnabled = LocalPromptKitMotionEnabled.current),
    onComplete: () -> Unit = {}, onError: (Throwable) -> Unit = {}): TextStreamState =
    rememberTextStream(text, options, onComplete, onError)

@Composable
fun useTextStream(textStream: Flow<String>, options: TextStreamOptions = TextStreamOptions(animationsEnabled = LocalPromptKitMotionEnabled.current),
    onComplete: () -> Unit = {}, onError: (Throwable) -> Unit = {}): TextStreamState =
    rememberTextStream(textStream, options, onComplete, onError)

@Composable
fun ResponseStream(
    text: String,
    modifier: Modifier = Modifier,
    options: TextStreamOptions = TextStreamOptions(animationsEnabled = LocalPromptKitMotionEnabled.current),
    onComplete: () -> Unit = {},
    onError: (Throwable) -> Unit = {},
    style: TextStyle = TypographyStyles.textSmRegular,
) = ResponseStream(rememberTextStream(text, options, onComplete, onError), modifier, style)

@Composable
fun ResponseStream(
    textStream: Flow<String>,
    modifier: Modifier = Modifier,
    options: TextStreamOptions = TextStreamOptions(animationsEnabled = LocalPromptKitMotionEnabled.current),
    onComplete: () -> Unit = {},
    onError: (Throwable) -> Unit = {},
    style: TextStyle = TypographyStyles.textSmRegular,
) = ResponseStream(rememberTextStream(textStream, options, onComplete, onError), modifier, style)

/** Render a state obtained from [rememberTextStream], including imperative pause/restart controls. */
@Composable
fun ResponseStream(state: TextStreamState, modifier: Modifier = Modifier,
    style: TextStyle = TypographyStyles.textSmRegular) {
    val foreground = Theme[ColorProps][ColorTokens.foreground]
    val content: AnnotatedString = if (state.options.mode == TextStreamMode.Fade) {
        val alphas = state.segments.map { segment ->
            key(state.generation, segment.index) {
                val alpha = remember { Animatable(if (state.options.animationsEnabled) 0f else 1f) }
                LaunchedEffect(state.options.animationsEnabled) {
                    alpha.animateTo(1f, tween(state.options.fadeDuration))
                }
                segment to alpha.value
            }
        }
        buildAnnotatedString {
            alphas.forEach { (segment, alpha) ->
                val start = length
                append(segment.text)
                addStyle(SpanStyle(color = foreground.copy(alpha = alpha)), start, length)
            }
        }
    } else AnnotatedString(state.displayedText)
    BasicText(content, modifier, style.copy(color = foreground))
}
