package com.github.jershell.shadcn.components.promptkit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.github.jershell.shadcn.components.button.*
import com.github.jershell.shadcn.theme.*
import io.github.vinceglb.filekit.PlatformFile

@Immutable
data class PromptSuggestionCategory(val name: String, val suggestions: List<String>)

/** Selecting a category shows its suggestions; selecting a suggestion fills the editable draft. */
@Composable
fun PromptInputWithSuggestions(
    categories: List<PromptSuggestionCategory>,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: PromptInputState = rememberPromptInputState(),
    isLoading: Boolean = false,
    onStop: (() -> Unit)? = null,
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TwDimensions.paddingPxToken3)) {
        PromptInput(state, onSubmit, isLoading = isLoading) {
            PromptInputTextarea()
            PromptInputActions { Spacer(Modifier.weight(1f)); PromptInputSubmit(onStop = onStop) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TwDimensions.paddingPxToken2),
            verticalArrangement = Arrangement.spacedBy(TwDimensions.paddingPxToken2)) {
            categories.forEach { category ->
                PromptSuggestion(category.name, onClick = {
                    selectedCategory = if (selectedCategory == category.name) null else category.name
                }, variant = if (selectedCategory == category.name) ButtonVariant.Secondary else ButtonVariant.Outline)
            }
        }
        categories.firstOrNull { it.name == selectedCategory }?.suggestions?.forEach { suggestion ->
            PromptSuggestion(suggestion, onClick = { state.value = suggestion }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun PromptAutocompleteHighlight(
    suggestions: List<String>,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: PromptInputState = rememberPromptInputState(),
    maxSuggestions: Int = 8,
) {
    require(maxSuggestions > 0)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TwDimensions.paddingPxToken2)) {
        PromptInput(state, onSubmit) {
            PromptInputTextarea()
            PromptInputActions { Spacer(Modifier.weight(1f)); PromptInputSubmit() }
        }
        if (state.value.isNotBlank()) suggestions.asSequence()
            .filter { it.contains(state.value.trim(), ignoreCase = true) }.take(maxSuggestions).forEach { suggestion ->
                PromptSuggestion(suggestion, onClick = { state.value = suggestion }, highlight = state.value,
                    modifier = Modifier.fillMaxWidth())
            }
    }
}

/** Actions without callbacks are omitted; the SDK does not claim to record audio or search on its own. */
@Composable
fun PromptInputWithActions(
    onSubmit: (String) -> Unit,
    onFilesAdded: (List<PlatformFile>) -> Unit,
    modifier: Modifier = Modifier,
    state: PromptInputState = rememberPromptInputState(),
    isLoading: Boolean = false,
    onStop: (() -> Unit)? = null,
    searchEnabled: Boolean = false,
    onSearchChange: ((Boolean) -> Unit)? = null,
    onVoiceInput: (() -> Unit)? = null,
    onMoreActions: (() -> Unit)? = null,
    uploadPolicy: FileUploadPolicy = FileUploadPolicy(),
) {
    val upload = rememberFileUploadState()
    FileUpload(onFilesAdded, modifier, state = upload, policy = uploadPolicy) {
        Column {
            PromptInput(state, onSubmit, isLoading = isLoading) {
                PromptInputTextarea()
                PromptInputActions {
                    FileUploadTrigger()
                    if (onSearchChange != null) PromptInputAction("Toggle web search", { onSearchChange(!searchEnabled) }) {
                        ButtonText(if (searchEnabled) "Search on" else "Search")
                    }
                    if (onVoiceInput != null) PromptInputAction("Voice input", onVoiceInput) { ButtonText("Voice") }
                    if (onMoreActions != null) PromptInputAction("More actions", onMoreActions) { ButtonText("More") }
                    Spacer(Modifier.weight(1f))
                    PromptInputSubmit(onStop = onStop)
                }
            }
            upload.lastError?.let { BasicText(it, style = TypographyStyles.textXsRegular.copy(color = resolvePromptKitColors().foreground)) }
        }
        FileUploadContent(Modifier.matchParentSize())
    }
}
