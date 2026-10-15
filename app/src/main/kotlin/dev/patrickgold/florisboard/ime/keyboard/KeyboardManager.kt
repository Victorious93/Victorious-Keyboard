/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.keyboard

import android.content.Context
import android.icu.lang.UCharacter
import android.os.SystemClock
import java.text.BreakIterator
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.core.SubtypePreset
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo
import dev.patrickgold.florisboard.ime.editor.ImeOptions
import dev.patrickgold.florisboard.ime.editor.InputAttributes
import dev.patrickgold.florisboard.ime.editor.OperationUnit
import dev.patrickgold.florisboard.ime.input.CapitalizationBehavior
import dev.patrickgold.florisboard.ime.input.InputEventDispatcher
import dev.patrickgold.florisboard.ime.input.InputKeyEventReceiver
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.FREQUENCY_DEFAULT
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import dev.patrickgold.florisboard.ime.nlp.AddToSuggestionsCandidate
import dev.patrickgold.florisboard.ime.nlp.ClipboardSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.CustomSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SlashSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.PunctuationRule
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.prediction.AutocorrectReplacement
import dev.patrickgold.florisboard.ime.nlp.prediction.AutocorrectUndo
import dev.patrickgold.florisboard.ime.nlp.prediction.LearnedWords
import dev.patrickgold.florisboard.ime.nlp.prediction.LearningGate
import dev.patrickgold.florisboard.ime.nlp.prediction.RevertCountStore
import dev.patrickgold.florisboard.ime.nlp.prediction.RevertPrivacy
import dev.patrickgold.florisboard.ime.nlp.prediction.WordPredictor
import dev.patrickgold.florisboard.ime.popup.PopupMappingComponent
import dev.patrickgold.florisboard.ime.text.composing.Composer
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardCache
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.lib.titlecase
import dev.patrickgold.florisboard.lib.uppercase
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.fleetHub
import dev.patrickgold.florisboard.slashCommands
import dev.patrickgold.florisboard.snippetManager
import dev.patrickgold.florisboard.subtypeManager
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger
import dev.patrickgold.florisboard.ime.developer.DevKeyAction
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.ai.AiFieldSnapshot
import dev.patrickgold.florisboard.ime.ai.AiPanel
import dev.patrickgold.florisboard.ime.ai.AiPhase
import dev.patrickgold.florisboard.ime.ai.AiRefusal
import dev.patrickgold.florisboard.ime.ai.AiSource
import dev.patrickgold.florisboard.ime.ai.AiTask
import dev.patrickgold.florisboard.localAiManager
import dev.patrickgold.florisboard.ime.editor.isPasswordLike
import dev.patrickgold.florisboard.ime.developer.DeveloperRowTiming
import dev.patrickgold.florisboard.ime.mode.ActiveMode
import dev.patrickgold.florisboard.ime.mode.ModeOverrides
import dev.patrickgold.florisboard.ime.snippets.CaretPlacement
import dev.patrickgold.florisboard.ime.snippets.ExpansionAction
import dev.patrickgold.florisboard.ime.snippets.ExpansionAttempt
import dev.patrickgold.florisboard.ime.snippets.ExpansionOutcome
import dev.patrickgold.florisboard.ime.snippets.ExpansionTrigger
import dev.patrickgold.florisboard.ime.snippets.ExpansionTriggers
import dev.patrickgold.florisboard.ime.snippets.InsertionPlan
import dev.patrickgold.florisboard.ime.snippets.InsertionRequest
import dev.patrickgold.florisboard.ime.snippets.Snippet
import dev.patrickgold.florisboard.ime.snippets.SnippetInsertOutcome
import dev.patrickgold.florisboard.ime.snippets.SnippetInsertion
import dev.patrickgold.florisboard.ime.snippets.SnippetLimits
import dev.patrickgold.florisboard.ime.snippets.LibraryResult
import dev.patrickgold.florisboard.ime.snippets.SnippetTemplate
import dev.patrickgold.florisboard.ime.mode.OperatingMode
import dev.patrickgold.florisboard.ime.developer.DevStep
import dev.patrickgold.florisboard.ime.developer.DeveloperKeyResolver
import dev.patrickgold.florisboard.ime.terminal.ModifierSnapshot
import dev.patrickgold.florisboard.ime.terminal.ModifierStateMachine
import dev.patrickgold.florisboard.ime.terminal.SpecialKey
import dev.patrickgold.florisboard.ime.terminal.TerminalAction
import dev.patrickgold.florisboard.ime.terminal.TerminalKeyMapper
import dev.patrickgold.florisboard.ime.terminal.TerminalModifier
import dev.patrickgold.florisboard.ime.clipedit.ClipEditorOrigin
import dev.patrickgold.florisboard.ime.clipedit.ClipEditorSave
import dev.patrickgold.florisboard.ime.clipedit.ClipEditorSession
import dev.patrickgold.florisboard.ime.clipedit.ClipEditorWrite
import dev.patrickgold.florisboard.ime.clipedit.EditorBuffer
import dev.patrickgold.florisboard.ime.clipedit.EditorField
import dev.patrickgold.florisboard.ime.clipedit.EditorKeyInput
import dev.patrickgold.florisboard.ime.clipedit.EditorKeyRouter
import dev.patrickgold.florisboard.ime.clipedit.SelectionRanges
import dev.patrickgold.florisboard.ime.clipboard.history.ClipCategory
import dev.patrickgold.florisboard.ime.clipboard.lanes.ClipLane
import dev.patrickgold.florisboard.ime.clipboard.vault.parseTagColumn
import dev.patrickgold.florisboard.ime.theme.colors.KeyboardPaletteCodec
import dev.patrickgold.florisboard.ime.theme.colors.KeyboardPalettes
import dev.patrickgold.florisboard.ime.toolbar.ShellHistoryPolicy
import dev.patrickgold.florisboard.ime.toolbar.TextTransform
import dev.patrickgold.florisboard.ime.toolbar.TextTransforms
import dev.patrickgold.florisboard.ime.toolbar.ToolbarActionId
import dev.patrickgold.florisboard.ime.toolbar.ToolbarCatalog
import dev.patrickgold.florisboard.ime.toolbar.ToolbarRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.android.showLongToastSync
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.lib.android.systemService
import org.florisboard.lib.kotlin.collectIn
import org.florisboard.lib.kotlin.collectLatestIn

private val DoubleSpacePeriodMatcher = """([^.!?‽\s]\s)""".toRegex()

class KeyboardManager(context: Context) : InputKeyEventReceiver {
    private val prefs by FlorisPreferenceStore
    private val appContext by context.appContext()
    private val clipboardManager by context.clipboardManager()
    private val editorInstance by context.editorInstance()
    private val extensionManager by context.extensionManager()
    private val nlpManager by context.nlpManager()
    private val localAi by context.localAiManager()
    private val subtypeManager by context.subtypeManager()
    private val snippetManager by context.snippetManager()
    private val slashCommands by context.slashCommands()
    private val fleet by context.fleetHub()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val layoutManager = LayoutManager(context)
    private val keyboardCache = TextKeyboardCache()

    val resources = KeyboardManagerResources()
    val activeState = ObservableKeyboardState.new()

    /** Sticky / locked / held Ctrl and Alt for terminal use. Inert unless a Ctrl/Alt key is on the active layout. */
    val terminalModifiers = ModifierStateMachine()

    /** In-keyboard clipboard/snippet editor. Null when typing still goes to the host field. */
    val clipEditor = MutableStateFlow<ClipEditorSession?>(null)

    /** Which clipboard tab to show the next time the clipboard panel opens. */
    val clipLaneRequest = MutableStateFlow<ClipLane?>(null)

    /** Tools panel replaces the key area. It is not an [ImeUiMode], so clipboard and emoji stay as they are. */
    val toolsOpen = MutableStateFlow(false)

    fun setToolsOpen(open: Boolean) {
        toolsOpen.value = open
        if (open) fleet.refresh()
    }

    /**
     * Set when vault text is pasted into the host field. Learning and shell history stay off until the
     * field identity changes. The flag is not the text.
     */
    var vaultLearningHold: Boolean = false
        private set

    private var learningFieldToken: String? = null

    /** Set only for the key that follows an autocorrect. Cleared by any other key. Not logged. */
    @Volatile private var pendingAutocorrect: AutocorrectReplacement? = null

    /** Armed/locked state of Ctrl and Alt for the developer row's indicators. */
    val activeMode: StateFlow<OperatingMode>
        field = MutableStateFlow(OperatingMode.STANDARD)

    val terminalModifierState: StateFlow<ModifierSnapshot>
        field = MutableStateFlow(ModifierSnapshot())

    init {
        terminalModifiers.onChange = { terminalModifierState.value = it }
    }
    var smartbarVisibleDynamicActionsCount by mutableIntStateOf(0)
    private var lastToastReference = WeakReference<Toast>(null)

    private val activeEvaluatorGuard = Mutex(locked = false)
    private var activeEvaluatorVersion = AtomicInteger(0)
    val activeEvaluator: StateFlow<ComputingEvaluator>
        field = MutableStateFlow<ComputingEvaluator>(DefaultComputingEvaluator)
    val activeSmartbarEvaluator: StateFlow<ComputingEvaluator>
        field = MutableStateFlow<ComputingEvaluator>(DefaultComputingEvaluator)
    val lastCharactersEvaluator: StateFlow<ComputingEvaluator>
        field = MutableStateFlow<ComputingEvaluator>(DefaultComputingEvaluator)

    val inputEventDispatcher = InputEventDispatcher.new(
        repeatableKeyCodes = intArrayOf(
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.DELETE,
            KeyCode.FORWARD_DELETE,
            KeyCode.UNDO,
            KeyCode.REDO,
        )
    ).also { it.keyEventReceiver = this }

    init {
        scope.launch(Dispatchers.Main.immediate) {
            resources.anyChangedVersion.collectIn(scope) {
                updateActiveEvaluators {
                    keyboardCache.clear()
                }
            }
            prefs.keyboard.numberRow.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators {
                    keyboardCache.clear(KeyboardMode.CHARACTERS)
                }
            }
            prefs.keyboard.hintedNumberRowEnabled.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            prefs.keyboard.hintedSymbolsEnabled.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            prefs.keyboard.utilityKeyEnabled.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            prefs.keyboard.utilityKeyAction.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            activeState.collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            subtypeManager.subtypesFlow.collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            subtypeManager.activeSubtypeFlow.collectLatestIn(scope) {
                reevaluateInputShiftState()
                updateActiveEvaluators()
                editorInstance.refreshComposing()
                resetSuggestions(editorInstance.activeContent)
            }
            clipboardManager.primaryClipFlow.collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            combine(
                prefs.modes.defaultMode.asFlow(),
                prefs.modes.autoDetect.asFlow(),
                editorInstance.activeInfoFlow,
                prefs.modes.appOverrides.asFlow(),
            ) { default, autoDetect, info, overrides ->
                ActiveMode.resolve(default, autoDetect, info.packageName, ModeOverrides.parse(overrides).overrides)
            }.collectIn(scope) { activeMode.value = it }
            prefs.devrow.lockWindowMs.asFlow().collectIn(scope) {
                terminalModifiers.doubleTapWindowMs = DeveloperRowTiming.lockWindow(it)
            }
            editorInstance.activeInfoFlow.collectIn(scope) { info ->
                terminalModifiers.reset() // nothing may stay armed or locked into another field or app
                toolsOpen.value = false
                fleet.refresh()
                val token = info.packageName + ":" + info.inputAttributes.raw
                if (learningFieldToken != null && learningFieldToken != token) {
                    vaultLearningHold = false
                    pendingAutocorrect = null
                }
                learningFieldToken = token
            }
            editorInstance.activeContentFlow.collectIn(scope) { content ->
                resetSuggestions(content)
            }
            localAi.panel.collectIn(scope) { panel ->
                if (panel != null && panel.insertWhenReady && panel.phase == dev.patrickgold.florisboard.ime.ai.AiPhase.RESULT) {
                    insertSlashAiResult(panel)
                }
            }
            prefs.devtools.enabled.asFlow().collectLatestIn(scope) {
                reevaluateDebugFlags()
            }
            prefs.devtools.showDragAndDropHelpers.asFlow().collectLatestIn(scope) {
                reevaluateDebugFlags()
            }
        }
    }

    fun updateActiveEvaluators(action: () -> Unit = { }) = scope.launch {
        activeEvaluatorGuard.withLock {
            action()
            val editorInfo = editorInstance.activeInfo
            val state = activeState.snapshot()
            val subtype = subtypeManager.activeSubtype
            val mode = state.keyboardMode
            // We need to reset the snapshot input shift state for non-character layouts, because the shift mechanic
            // only makes sense for the character layouts.
            if (mode != KeyboardMode.CHARACTERS) {
                state.inputShiftState = InputShiftState.UNSHIFTED
            }
            val computedKeyboard = keyboardCache.getOrElseAsync(mode, subtype) {
                layoutManager.computeKeyboardAsync(
                    keyboardMode = mode,
                    subtype = subtype,
                ).await()
            }
            val computingEvaluator = ComputingEvaluatorImpl(
                version = activeEvaluatorVersion.getAndAdd(1),
                keyboard = computedKeyboard,
                editorInfo = editorInfo,
                state = state,
                subtype = subtype,
            )
            for (key in computedKeyboard.keys()) {
                key.compute(computingEvaluator)
                key.computeLabelsAndDrawables(computingEvaluator)
            }
            activeEvaluator.value = computingEvaluator
            activeSmartbarEvaluator.value = computingEvaluator.asSmartbarQuickActionsEvaluator()
            if (computedKeyboard.mode == KeyboardMode.CHARACTERS) {
                lastCharactersEvaluator.value = computingEvaluator
            }
        }
    }

    fun reevaluateInputShiftState() {
        if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
            val shift = prefs.correction.autoCapitalization.get()
                && subtypeManager.activeSubtype.primaryLocale.supportsCapitalization
                && editorInstance.activeCursorCapsMode != InputAttributes.CapsMode.NONE
            activeState.inputShiftState = when {
                shift -> InputShiftState.SHIFTED_AUTOMATIC
                else -> InputShiftState.UNSHIFTED
            }
        }
    }

    fun resetSuggestions(content: EditorContent) {
        if (!(activeState.isComposingEnabled || nlpManager.isSuggestionOn())) {
            nlpManager.clearSuggestions()
            return
        }
        nlpManager.suggest(subtypeManager.activeSubtype, content)
    }

    /**
     * @return If the language switch should be shown.
     */
    fun shouldShowLanguageSwitch(): Boolean {
        return subtypeManager.subtypes.size > 1
    }

    fun executeSwipeAction(swipeAction: SwipeAction) {
        val keyData = when (swipeAction) {
            SwipeAction.CYCLE_TO_PREVIOUS_KEYBOARD_MODE -> when (activeState.keyboardMode) {
                KeyboardMode.CHARACTERS -> TextKeyData.VIEW_NUMERIC_ADVANCED
                KeyboardMode.NUMERIC_ADVANCED -> TextKeyData.VIEW_SYMBOLS2
                KeyboardMode.SYMBOLS2 -> TextKeyData.VIEW_SYMBOLS
                else -> TextKeyData.VIEW_CHARACTERS
            }
            SwipeAction.CYCLE_TO_NEXT_KEYBOARD_MODE -> when (activeState.keyboardMode) {
                KeyboardMode.CHARACTERS -> TextKeyData.VIEW_SYMBOLS
                KeyboardMode.SYMBOLS -> TextKeyData.VIEW_SYMBOLS2
                KeyboardMode.SYMBOLS2 -> TextKeyData.VIEW_NUMERIC_ADVANCED
                else -> TextKeyData.VIEW_CHARACTERS
            }
            SwipeAction.DELETE_WORD -> TextKeyData.DELETE_WORD
            SwipeAction.HIDE_KEYBOARD -> TextKeyData.IME_HIDE_UI
            SwipeAction.INSERT_SPACE -> TextKeyData.SPACE
            SwipeAction.MOVE_CURSOR_DOWN -> TextKeyData.ARROW_DOWN
            SwipeAction.MOVE_CURSOR_UP -> TextKeyData.ARROW_UP
            SwipeAction.MOVE_CURSOR_LEFT -> TextKeyData.ARROW_LEFT
            SwipeAction.MOVE_CURSOR_RIGHT -> TextKeyData.ARROW_RIGHT
            SwipeAction.MOVE_CURSOR_START_OF_LINE -> TextKeyData.MOVE_START_OF_LINE
            SwipeAction.MOVE_CURSOR_END_OF_LINE -> TextKeyData.MOVE_END_OF_LINE
            SwipeAction.MOVE_CURSOR_START_OF_PAGE -> TextKeyData.MOVE_START_OF_PAGE
            SwipeAction.MOVE_CURSOR_END_OF_PAGE -> TextKeyData.MOVE_END_OF_PAGE
            SwipeAction.SHIFT -> TextKeyData.SHIFT
            SwipeAction.REDO -> TextKeyData.REDO
            SwipeAction.UNDO -> TextKeyData.UNDO
            SwipeAction.SHOW_INPUT_METHOD_PICKER -> TextKeyData.SYSTEM_INPUT_METHOD_PICKER
            SwipeAction.SHOW_SUBTYPE_PICKER -> TextKeyData.SHOW_SUBTYPE_PICKER
            SwipeAction.SWITCH_TO_CLIPBOARD_CONTEXT -> TextKeyData.IME_UI_MODE_CLIPBOARD
            SwipeAction.SWITCH_TO_MEDIA_CONTEXT -> TextKeyData.IME_UI_MODE_MEDIA
            SwipeAction.SWITCH_TO_PREV_SUBTYPE -> TextKeyData.IME_PREV_SUBTYPE
            SwipeAction.SWITCH_TO_NEXT_SUBTYPE -> TextKeyData.IME_NEXT_SUBTYPE
            SwipeAction.SWITCH_TO_PREV_KEYBOARD -> TextKeyData.SYSTEM_PREV_INPUT_METHOD
            SwipeAction.TOGGLE_SMARTBAR_VISIBILITY -> TextKeyData.TOGGLE_SMARTBAR_VISIBILITY
            else -> null
        }
        if (keyData != null) {
            inputEventDispatcher.sendDownUp(keyData)
        }
    }

    fun commitCandidate(candidate: SuggestionCandidate) {
        when (candidate) {
            is SlashSuggestionCandidate -> {
                expandSlash(candidate)
                return
            }
            is AddToSuggestionsCandidate -> return
            else -> Unit
        }
        val editing = clipEditor.value
        if (editing != null) {
            if (candidate is ClipboardSuggestionCandidate && candidate.clipboardItem.isSensitive) return
            insertAtTarget(candidate.text.toString())
            return
        }
        if (candidate !is ClipboardSuggestionCandidate && candidate !is CustomSuggestionCandidate) {
            nlpManager.noteTypedOrCommitted(candidate.text.toString())
        }
        scope.launch {
            candidate.sourceProvider?.notifySuggestionAccepted(subtypeManager.activeSubtype, candidate)
        }
        when (candidate) {
            is ClipboardSuggestionCandidate -> editorInstance.commitClipboardItem(candidate.clipboardItem)
            is CustomSuggestionCandidate -> commitCustomPhrase(candidate.text.toString())
            else -> editorInstance.commitCompletion(candidate)
        }
    }

    /** Replaces the word being typed with a saved phrase. The phrase is not learned as a new dictionary word. */
    private fun commitCustomPhrase(text: String) {
        val content = editorInstance.activeContent
        if (content.composing.isValid) {
            editorInstance.commitCompletion(CustomSuggestionCandidate(text, suggestionId = ""))
            return
        }
        val prefix = content.textBeforeSelection.takeLastWhile { !it.isWhitespace() }
        if (prefix.isNotEmpty() && prefix.length <= 200) {
            repeat(graphemeCount(prefix)) { editorInstance.deleteBackwards(OperationUnit.CHARACTERS) }
        }
        editorInstance.commitText(text)
    }

    /**
     * Expands a slash chip. Password fields return before the argument is read.
     * Inserted text is never executed, including `/shell`.
     */
    private fun expandSlash(candidate: SlashSuggestionCandidate) {
        if (editorInstance.activeInfo.inputAttributes.isPasswordLike()) return
        if (!prefs.slash.enabled.get()) return
        if (candidate.fleet) {
            expandFleetSlash(candidate)
            return
        }
        val commands = slashCommands.snapshot()
        val offer = if (candidate.customId != null) {
            dev.patrickgold.florisboard.ime.slash.SlashOffer(candidate.commandName, "", null, candidate.customId)
        } else {
            val builtin = dev.patrickgold.florisboard.ime.slash.SlashCatalog.builtins
                .firstOrNull { it.name == candidate.commandName } ?: return
            dev.patrickgold.florisboard.ime.slash.SlashOffer(builtin.name, builtin.description, builtin.kind, null)
        }
        val action = dev.patrickgold.florisboard.ime.slash.SlashActions.resolve(
            offer,
            candidate.argument,
            commands,
            Instant.now(),
            ZoneId.systemDefault(),
        )
        when (action) {
            is dev.patrickgold.florisboard.ime.slash.SlashAction.Insert ->
                replaceSlashToken(candidate.replaceLength, action.text, action.cursorBackCodePoints)
            is dev.patrickgold.florisboard.ime.slash.SlashAction.Custom -> {
                val template = action.command.template
                val content = editorInstance.activeContent
                val vars = dev.patrickgold.florisboard.ime.slash.SlashVars(
                    date = dev.patrickgold.florisboard.ime.slash.SlashClock.date(Instant.now(), ZoneId.systemDefault()),
                    time = dev.patrickgold.florisboard.ime.slash.SlashClock.time(Instant.now(), ZoneId.systemDefault()),
                    clipboard = if (dev.patrickgold.florisboard.ime.slash.SlashTemplate.uses(template, "clipboard")) {
                        currentClipboardText().orEmpty()
                    } else {
                        ""
                    },
                    selection = if (dev.patrickgold.florisboard.ime.slash.SlashTemplate.uses(template, "selection")) {
                        content.selectedText
                    } else {
                        ""
                    },
                )
                val rendered = dev.patrickgold.florisboard.ime.slash.SlashTemplate.render(template, vars)
                replaceSlashToken(candidate.replaceLength, rendered.text, rendered.cursorBackCodePoints)
            }
            dev.patrickgold.florisboard.ime.slash.SlashAction.PasteClip -> {
                val text = currentClipboardText()
                if (text.isNullOrEmpty()) {
                    appContext.showShortToastSync(R.string.slash__empty_clip)
                } else {
                    replaceSlashToken(candidate.replaceLength, text, null)
                }
            }
            is dev.patrickgold.florisboard.ime.slash.SlashAction.Snippet -> expandSlashSnippet(candidate.replaceLength, action.name)
            is dev.patrickgold.florisboard.ime.slash.SlashAction.Ai -> {
                toolsOpen.value = false
                val token = editorInstance.activeContent.textBeforeSelection.takeLast(candidate.replaceLength)
                when (localAi.open(
                    task = action.task,
                    snapshot = aiSnapshot(),
                    askPrefill = action.prompt,
                    insertWhenReady = true,
                    replaceToken = token,
                    systemOverride = action.systemOverride,
                )) {
                    AiRefusal.NO_MODEL, AiRefusal.NATIVE_MISSING ->
                        appContext.showShortToastSync(R.string.local_ai__unavailable)
                    AiRefusal.PASSWORD -> appContext.showShortToastSync(R.string.local_ai__password)
                    else -> Unit
                }
            }
            dev.patrickgold.florisboard.ime.slash.SlashAction.OpenEditor -> {
                deleteSlashToken(candidate.replaceLength)
                openLatestClipEditor()
            }
            dev.patrickgold.florisboard.ime.slash.SlashAction.UnlockVault -> {
                deleteSlashToken(candidate.replaceLength)
                val minutes = prefs.clipboard.vaultLockAfterMinutes.get()
                appContext.startActivity(
                    android.content.Intent(appContext, dev.patrickgold.florisboard.ime.clipboard.VaultUnlockActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(
                            dev.patrickgold.florisboard.ime.clipboard.VaultUnlockActivity.EXTRA_TIMEOUT_MS,
                            minutes.coerceIn(1, 60) * 60_000L,
                        )
                    },
                )
            }
            dev.patrickgold.florisboard.ime.slash.SlashAction.Unknown ->
                appContext.showShortToastSync(R.string.slash__unknown)
        }
    }

    private fun expandFleetSlash(candidate: SlashSuggestionCandidate) {
        if (!prefs.fleet.enabled.get()) return
        if (editorInstance.activeInfo.inputAttributes.isPasswordLike()) return
        val packageName = candidate.fleetPackage?.takeIf { it.isNotBlank() } ?: return
        val rawName = candidate.fleetRawName?.takeIf { it.isNotBlank() } ?: return
        val argument = dev.patrickgold.florisboard.lib.fleet.FleetPolicy.slashText(
            candidate.argument,
            passwordLike = false,
        ) ?: return
        val token = editorInstance.activeContent.textBeforeSelection.takeLast(candidate.replaceLength)
        scope.launch {
            val reply = fleet.ask(packageName, rawName, argument)
            withContext(Dispatchers.Main) {
                if (editorInstance.activeInfo.inputAttributes.isPasswordLike()) return@withContext
                if (reply.isNullOrBlank()) {
                    appContext.showShortToastSync(R.string.fleet__unavailable)
                    return@withContext
                }
                val before = editorInstance.activeContent.textBeforeSelection
                if (token.isNotEmpty() && before.endsWith(token)) {
                    replaceSlashToken(token.length, reply, null)
                }
            }
        }
    }

    /**
     * Sends only the current selection to the app the user picked.
     * [action] is empty for Ask, or an advertised action name. Vault text is included because the tap is explicit.
     */
    fun askFleet(packageName: String, action: String = "") {
        if (editorInstance.activeInfo.inputAttributes.isPasswordLike()) {
            appContext.showShortToastSync(R.string.fleet__password)
            return
        }
        val session = clipEditor.value
        val selection = if (session != null) {
            if (session.body.hasSelection) session.body.selectedText else ""
        } else {
            editorInstance.activeContent.selectedText
        }
        val fromVault = session?.origin == dev.patrickgold.florisboard.ime.clipedit.ClipEditorOrigin.VAULT ||
            session?.sensitive == true
        val text = dev.patrickgold.florisboard.lib.fleet.FleetPolicy.askText(
            selection = selection,
            passwordLike = false,
            selectionIsVault = fromVault,
            explicitVaultPick = true,
        )
        if (text == null) {
            appContext.showShortToastSync(R.string.fleet__need_selection)
            return
        }
        scope.launch {
            val reply = fleet.ask(packageName, action, text)
            withContext(Dispatchers.Main) {
                if (editorInstance.activeInfo.inputAttributes.isPasswordLike()) return@withContext
                if (reply.isNullOrBlank()) {
                    appContext.showShortToastSync(R.string.fleet__unavailable)
                } else {
                    if (fromVault) vaultLearningHold = true
                    insertAtTarget(reply)
                }
            }
        }
    }

    /** Inserts provider snippet text. A command name asks that provider for text and does not run it. */
    fun insertFleetSnippet(packageName: String, snippet: dev.patrickgold.florisboard.lib.fleet.FleetSnippet) {
        if (editorInstance.activeInfo.inputAttributes.isPasswordLike()) {
            appContext.showShortToastSync(R.string.fleet__password)
            return
        }
        scope.launch {
            val outcome = fleet.insertSnippet(packageName, snippet)
            withContext(Dispatchers.Main) {
                if (editorInstance.activeInfo.inputAttributes.isPasswordLike()) return@withContext
                if (outcome == null || outcome.execute || outcome.text.isBlank()) {
                    appContext.showShortToastSync(R.string.fleet__unavailable)
                } else {
                    insertAtTarget(outcome.text)
                }
            }
        }
    }

    private fun expandSlashSnippet(replaceLength: Int, name: String) {
        if (name.isEmpty()) {
            deleteSlashToken(replaceLength)
            activeState.imeUiMode = ImeUiMode.SNIPPETS
            return
        }
        val snippet = snippetManager.state.value.library.snippets.firstOrNull {
            it.enabled && (it.abbreviation.equals(name, ignoreCase = true) || it.title.equals(name, ignoreCase = true))
        }
        if (snippet == null) {
            appContext.showShortToastSync(R.string.slash__snippet_missing)
            return
        }
        when (val plan = SnippetInsertion.plan(
            InsertionRequest(
                snippet = snippet,
                now = LocalDateTime.now(),
                clipboardText = if (SnippetTemplate.usesPlaceholder(snippet.body, "clipboard")) currentClipboardText() else null,
                selectionText = editorInstance.activeContent.selectedText,
                sensitiveField = false,
                datePattern = prefs.snippets.dateFormat.get().pattern,
                timePattern = prefs.snippets.timeFormat.get().pattern,
            ),
        )) {
            is InsertionPlan.Insert -> replaceSlashToken(replaceLength, plan.text, plan.cursorBackCodePoints)
            else -> appContext.showShortToastSync(R.string.slash__snippet_missing)
        }
    }

    private fun deleteSlashToken(replaceLength: Int) {
        val before = editorInstance.activeContent.textBeforeSelection
        val token = before.takeLast(replaceLength.coerceAtMost(before.length))
        if (!token.startsWith("/")) return
        repeat(graphemeCount(token)) { editorInstance.deleteBackwards(OperationUnit.CHARACTERS) }
    }

    private fun replaceSlashToken(replaceLength: Int, text: String, cursorBackCodePoints: Int?) {
        deleteSlashToken(replaceLength)
        if (text.isEmpty()) return
        editorInstance.commitText(text)
        if (cursorBackCodePoints != null) {
            moveCaretLeft(InsertionPlan.Insert(text, cursorBackCodePoints))
        }
    }

    private fun insertSlashAiResult(panel: AiPanel) {
        if (panel.output.isBlank()) return
        if (panel.fromVault) vaultLearningHold = true
        val before = editorInstance.activeContent.textBeforeSelection
        if (panel.replaceToken.isNotEmpty() && before.endsWith(panel.replaceToken)) {
            repeat(graphemeCount(panel.replaceToken)) { editorInstance.deleteBackwards(OperationUnit.CHARACTERS) }
        }
        insertAtTarget(panel.output)
        localAi.close()
    }

    fun commitGesture(word: String) {
        val text = fixCase(word)
        if (clipEditor.value != null) {
            insertAtTarget(text)
            return
        }
        editorInstance.commitGesture(text)
    }

    /**
     * Changes a word to the current case.
     * eg if [KeyboardState.isUppercase] is true, abc -> ABC
     *    if [caps]     is true, abc -> Abc
     *    otherwise            , abc -> abc
     */
    fun fixCase(word: String): String {
        return when(activeState.inputShiftState) {
            InputShiftState.CAPS_LOCK -> {
                word.uppercase(subtypeManager.activeSubtype.primaryLocale)
            }
            InputShiftState.SHIFTED_MANUAL, InputShiftState.SHIFTED_AUTOMATIC -> {
                word.titlecase(subtypeManager.activeSubtype.primaryLocale)
            }
            else -> word
        }
    }

    /**
     * Handles [KeyCode] arrow and move events, behaves differently depending on text selection.
     */
    fun handleArrow(code: Int, count: Int = 1) = editorInstance.apply {
        if (terminalModifiers.anyActive) {
            val special = when (code) {
                KeyCode.ARROW_LEFT -> SpecialKey.LEFT
                KeyCode.ARROW_RIGHT -> SpecialKey.RIGHT
                KeyCode.ARROW_UP -> SpecialKey.UP
                KeyCode.ARROW_DOWN -> SpecialKey.DOWN
                KeyCode.MOVE_START_OF_LINE -> SpecialKey.HOME
                KeyCode.MOVE_END_OF_LINE -> SpecialKey.END
                else -> null
            }
            if (special != null) {
                sendTerminalSpecial(special, count)
                return@apply
            }
        }
        val isShiftPressed = activeState.isManualSelectionMode || inputEventDispatcher.isPressed(KeyCode.SHIFT)
        val content = activeContent
        val selection = content.selection
        when (code) {
            KeyCode.ARROW_LEFT -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, meta(shift = isShiftPressed), count)
            }
            KeyCode.ARROW_RIGHT -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, meta(shift = isShiftPressed), count)
            }
            KeyCode.ARROW_UP -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_UP, meta(shift = isShiftPressed), count)
            }
            KeyCode.ARROW_DOWN -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, meta(shift = isShiftPressed), count)
            }
            KeyCode.MOVE_START_OF_PAGE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_UP, meta(alt = true, shift = isShiftPressed), count)
            }
            KeyCode.MOVE_END_OF_PAGE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, meta(alt = true, shift = isShiftPressed), count)
            }
            KeyCode.MOVE_START_OF_LINE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, meta(alt = true, shift = isShiftPressed), count)
            }
            KeyCode.MOVE_END_OF_LINE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, meta(alt = true, shift = isShiftPressed), count)
            }
        }
    }

    /**
     * Handles a [KeyCode.CLIPBOARD_SELECT] event.
     */
    private fun handleClipboardSelect() {
        val activeSelection = editorInstance.activeContent.selection
        activeState.isManualSelectionMode = if (activeSelection.isSelectionMode) {
            if (activeState.isManualSelectionMode && activeState.isManualSelectionModeStart) {
                editorInstance.setSelection(activeSelection.start, activeSelection.start)
            } else {
                editorInstance.setSelection(activeSelection.end, activeSelection.end)
            }
            false
        } else {
            !activeState.isManualSelectionMode
        }
    }

    private fun revertPreviouslyAcceptedCandidate() {
        editorInstance.phantomSpace.candidateForRevert?.let { candidateForRevert ->
            candidateForRevert.sourceProvider?.let { sourceProvider ->
                scope.launch {
                    sourceProvider.notifySuggestionReverted(
                        subtype = subtypeManager.activeSubtype,
                        candidate = candidateForRevert,
                    )
                }
            }
        }
    }

    /**
     * Handles a [KeyCode.DELETE] event.
     */
    private fun handleBackwardDelete(unit: OperationUnit) {
        if (inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
            pendingAutocorrect = null
            return handleForwardDelete(unit)
        }
        activeState.batchEdit {
            it.isManualSelectionMode = false
            it.isManualSelectionModeStart = false
            it.isManualSelectionModeEnd = false
        }
        if (unit == OperationUnit.CHARACTERS && tryRevertAutocorrect()) return
        pendingAutocorrect = null
        revertPreviouslyAcceptedCandidate()
        editorInstance.deleteBackwards(unit)
    }

    /**
     * Backspace immediately after space-to-correct puts the typed word back, including dropping
     * the space. The second time the same word is restored it is added to the user dictionary.
     * Password, incognito, and vault handoff restore the letters and store nothing.
     */
    private fun tryRevertAutocorrect(): Boolean {
        val pending = pendingAutocorrect ?: return false
        pendingAutocorrect = null
        val before = editorInstance.activeContent.textBeforeSelection
        val step = AutocorrectUndo.plan(before, pending) ?: return false
        var removed = 0
        repeat(step.deleteCount) {
            if (editorInstance.deleteBackwards(OperationUnit.CHARACTERS)) removed++
        }
        if (removed != step.deleteCount) return true
        editorInstance.commitText(step.restore)
        if (mayRememberRevert()) {
            val dir = java.io.File(appContext.noBackupFilesDir, "prediction")
            val add = RevertCountStore.note(dir, step.restore)
            if (add) addRevertedWord(step.restore)
        }
        return true
    }

    private fun mayRememberRevert(): Boolean {
        val info = editorInstance.activeInfo
        return RevertPrivacy.mayRemember(
            LearningGate.Field(
                passwordLike = info.inputAttributes.isPasswordLike(),
                incognito = activeState.isIncognitoMode,
                noPersonalizedLearning = info.imeOptions.flagNoPersonalizedLearning,
                noSuggestions = info.inputAttributes.flagTextNoSuggestions,
                vaultHandoff = vaultLearningHold,
            ),
        )
    }

    private fun addRevertedWord(word: String) {
        val key = LearnedWords.normalize(word) ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val manager = DictionaryManager.default()
                manager.loadUserDictionariesIfNecessary()
                val dao = manager.florisUserDictionaryDao() ?: return@launch
                if (dao.queryExact(key).isNotEmpty() || dao.queryExact(word).isNotEmpty()) return@launch
                dao.insert(
                    UserDictionaryEntry(
                        id = 0,
                        word = key,
                        freq = FREQUENCY_DEFAULT,
                        locale = null,
                        shortcut = null,
                    ),
                )
            } catch (_: Exception) {
                // The word is not logged. The revert count still keeps it from being replaced.
            }
        }
    }

    private fun armAutocorrectRevert(typed: String?, committed: String, withSpace: Boolean) {
        if (typed.isNullOrEmpty() || typed == committed) {
            pendingAutocorrect = null
            return
        }
        pendingAutocorrect = AutocorrectReplacement(typed, committed, withSpace)
    }

    /**
     * Handles a [KeyCode.FORWARD_DELETE] event.
     */
    private fun handleForwardDelete(unit: OperationUnit) {
        activeState.batchEdit {
            it.isManualSelectionMode = false
            it.isManualSelectionModeStart = false
            it.isManualSelectionModeEnd = false
        }
        revertPreviouslyAcceptedCandidate()
        editorInstance.deleteForwards(unit)
    }

    /**
     * Handles a [KeyCode.ENTER] event.
     */
    private fun handleEnter() {
        if (tryExpandSnippet(ExpansionTrigger.ENTER)) return
        rememberShellLine()
        nlpManager.noteTypedOrCommitted(null)
        performEnterNow()
    }

    /** The Enter key's own action, with no snippet expansion. Also used to re-send Enter after a snippet expanded. */
    private fun performEnterNow() {
        val info = editorInstance.activeInfo
        val isShiftPressed = inputEventDispatcher.isPressed(KeyCode.SHIFT)
        if (editorInstance.tryPerformEnterCommitRaw()) {
            return
        }
        if (info.imeOptions.flagNoEnterAction || info.inputAttributes.flagTextMultiLine && isShiftPressed) {
            editorInstance.performEnter()
        } else {
            when (val action = info.imeOptions.action) {
                ImeOptions.Action.DONE,
                ImeOptions.Action.GO,
                ImeOptions.Action.NEXT,
                ImeOptions.Action.PREVIOUS,
                ImeOptions.Action.SEARCH,
                ImeOptions.Action.SEND -> {
                    editorInstance.performEnterAction(action)
                }
                else -> editorInstance.performEnter()
            }
        }
    }

    /**
     * Handles a [KeyCode.LANGUAGE_SWITCH] event. Also handles if the language switch should cycle
     * FlorisBoard internal or system-wide.
     */
    private fun handleLanguageSwitch() {
        when (prefs.keyboard.utilityKeyAction.get()) {
            UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS,
            UtilityKeyAction.SWITCH_LANGUAGE -> subtypeManager.switchToNextSubtype()
            else -> FlorisImeService.switchToNextInputMethod()
        }
    }

    /**
     * Handles a [KeyCode.SHIFT] down event.
     */
    private fun handleShiftDown(data: KeyData) {
        val prefs = prefs.keyboard.capitalizationBehavior
        when (prefs.get()) {
            CapitalizationBehavior.CAPSLOCK_BY_DOUBLE_TAP -> {
                if (inputEventDispatcher.isConsecutiveDown(data)) {
                    activeState.inputShiftState = InputShiftState.CAPS_LOCK
                } else {
                    if (activeState.inputShiftState == InputShiftState.UNSHIFTED) {
                        activeState.inputShiftState = InputShiftState.SHIFTED_MANUAL
                    } else {
                        activeState.inputShiftState = InputShiftState.UNSHIFTED
                    }
                }
            }
            CapitalizationBehavior.CAPSLOCK_BY_CYCLE -> {
                activeState.inputShiftState = when (activeState.inputShiftState) {
                    InputShiftState.UNSHIFTED -> InputShiftState.SHIFTED_MANUAL
                    InputShiftState.SHIFTED_MANUAL -> InputShiftState.CAPS_LOCK
                    InputShiftState.SHIFTED_AUTOMATIC -> InputShiftState.UNSHIFTED
                    InputShiftState.CAPS_LOCK -> InputShiftState.UNSHIFTED
                }
            }
        }
    }

    /**
     * Handles a [KeyCode.SHIFT] up event.
     */
    private fun handleShiftUp(data: KeyData) {
        if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isAnyPressed() &&
            !inputEventDispatcher.isUninterruptedEventSequence(data)) {
            activeState.inputShiftState = InputShiftState.UNSHIFTED
        }
    }

    /**
     * Handles a [KeyCode.CAPS_LOCK] event.
     */
    private fun handleCapsLock() {
        activeState.inputShiftState = InputShiftState.CAPS_LOCK
    }

    /**
     * Handles a [KeyCode.SHIFT] cancel event.
     */
    private fun handleShiftCancel() {
        activeState.inputShiftState = InputShiftState.UNSHIFTED
    }

    /**
     * Handles a hardware [KeyEvent.KEYCODE_SPACE] event. Same as [handleSpace],
     * but skips handling changing to characters keyboard and double space periods.
     */
    fun handleHardwareKeyboardSpace() {
        val typed = WordPredictor.contextTokens(editorInstance.activeContent.textBeforeSelection).second
        val candidate = nlpManager.getAutoCommitCandidate()
        if (candidate == null) {
            nlpManager.noteTypedOrCommitted(null)
            pendingAutocorrect = null
        }
        candidate?.let { commitCandidate(it) }
        // Skip handling changing to characters keyboard and double space periods
        // TODO: this is whether we commit space after selecting candidate. Should be determined by SuggestionProvider
        val commitSpace = subtypeManager.activeSubtype.primaryLocale.supportsAutoSpace || candidate == null
        if (candidate != null) {
            armAutocorrectRevert(typed, candidate.text.toString(), withSpace = commitSpace)
        }
        if (commitSpace) {
            editorInstance.commitText(KeyCode.SPACE.toChar().toString())
        }
    }

    /**
     * Handles a [KeyCode.SPACE] event. Also handles the auto-correction of two space taps if
     * enabled by the user.
     */
    /**
     * Snippet expansion for space, and (each opt-in) Enter and Tab. Returns true only when a snippet was actually
     * inserted, in which case the caller must not also perform the key: this function re-sends it when the user asked
     * to keep it, unless the snippet places the caret with `{{cursor}}`.
     *
     * A miss returns false and changes nothing, so a real Enter still sends and a real Tab still tabs. Never reads a
     * sensitive field, and never logs the snippet or the text. A failure before the editor is changed returns false.
     */
    private fun tryExpandSnippet(trigger: ExpansionTrigger): Boolean {
        if (!prefs.snippets.expansionEnabled.get()) return false
        // Expansion is off in terminal mode unless the user chose otherwise (a terminal rarely wants it).
        if (activeMode.value == OperatingMode.TERMUX && !prefs.snippets.expandInTerminalMode.get()) return false
        when (trigger) {
            ExpansionTrigger.ENTER -> if (!prefs.snippets.expandOnEnter.get()) return false
            ExpansionTrigger.TAB -> if (!prefs.snippets.expandOnTab.get()) return false
            ExpansionTrigger.SPACE -> { /* space is included whenever expansion is on */ }
        }
        // A modified Enter or Tab is a real key (Shift+Enter newline, Ctrl+Tab). Do not read the field for it.
        if (trigger != ExpansionTrigger.SPACE &&
            (inputEventDispatcher.isPressed(KeyCode.SHIFT) || terminalModifiers.anyActive)
        ) return false
        val sensitive = editorInstance.activeInfo.inputAttributes.isPasswordLike()
        val keep = when (trigger) {
            ExpansionTrigger.SPACE -> prefs.snippets.keepSpace.get()
            ExpansionTrigger.ENTER -> prefs.snippets.keepEnter.get()
            ExpansionTrigger.TAB -> prefs.snippets.keepTab.get()
        }
        val attempt = ExpansionAttempt(
            enabled = true,
            triggers = ExpansionTriggers(
                space = true,
                enter = prefs.snippets.expandOnEnter.get(),
                tab = prefs.snippets.expandOnTab.get(),
            ),
            prefix = prefs.snippets.prefix.get().text,
            caseSensitive = prefs.snippets.caseSensitive.get(),
            trigger = trigger,
            sensitiveField = sensitive,
            selectionActive = editorInstance.activeContent.selection.isSelectionMode,
            modifierHeld = inputEventDispatcher.isPressed(KeyCode.SHIFT) || terminalModifiers.anyActive,
            blockedInTerminalMode = false,
            keepTrigger = keep,
        )
        if (!ExpansionAction.shouldConsider(attempt)) return false
        var changedEditor = false
        return try {
            val before = editorInstance.run { activeContent.getTextBeforeCursor(SnippetLimits.MAX_ABBREVIATION + 8) }
            val outcome = ExpansionAction.decide(attempt.copy(textBeforeCursor = before)) { match ->
                SnippetInsertion.plan(
                    InsertionRequest(
                        snippet = match.snippet,
                        now = LocalDateTime.now(),
                        clipboardText = if (SnippetTemplate.usesPlaceholder(match.snippet.body, "clipboard")) currentClipboardText() else null,
                        selectionText = null, // expansion never runs with a selection
                        sensitiveField = false,
                        datePattern = prefs.snippets.dateFormat.get().pattern,
                        timePattern = prefs.snippets.timeFormat.get().pattern,
                    )
                )
            }
            when (outcome) {
                ExpansionOutcome.PassThrough -> false
                is ExpansionOutcome.Replace -> {
                    changedEditor = true
                    val token = before.takeLast(outcome.deleteCount)
                    repeat(graphemeCount(token)) { editorInstance.deleteBackwards(OperationUnit.CHARACTERS) }
                    editorInstance.commitText(outcome.text)
                    if (outcome.cursorBackCodePoints == null) {
                        if (outcome.followWithTrigger) followExpansionTrigger(trigger)
                    } else {
                        moveCaretLeft(InsertionPlan.Insert(outcome.text, outcome.cursorBackCodePoints))
                    }
                    true
                }
            }
        } catch (_: Exception) {
            // Nothing about the snippet is logged. If the editor was not touched, the real key still happens.
            changedEditor
        }
    }

    /** Re-sends the key that completed an expansion. Does not consult expansion again. */
    private fun followExpansionTrigger(trigger: ExpansionTrigger) {
        when (trigger) {
            ExpansionTrigger.SPACE -> editorInstance.commitText(KeyCode.SPACE.toChar().toString())
            ExpansionTrigger.ENTER -> performEnterNow()
            ExpansionTrigger.TAB -> sendTerminalSpecial(SpecialKey.TAB)
        }
    }

    /** Plain clipboard text for \`{{clipboard}}\`; not for non-text clips and not for clips the platform marked sensitive. */
    private fun currentClipboardText(): String? {
        val item = clipboardManager.primaryClip ?: return null
        if (item.type != ItemType.TEXT || item.isSensitive) return null
        return item.stringRepresentation()
    }

    /**
     * Puts the caret on \`{{cursor}}\` after [insert] was committed, with plain left-arrow presses (the developer row's
     * armed Ctrl/Alt never applies: \`meta()\` is empty). Counted in grapheme clusters and skipped for huge tails, see
     * [CaretPlacement]. A failure here never undoes or hides the insertion.
     */
    private fun moveCaretLeft(insert: InsertionPlan.Insert) {
        val presses = CaretPlacement.leftPresses(insert.text, insert.cursorBackCodePoints)
        if (presses < 1) return
        try {
            editorInstance.sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, editorInstance.meta(), presses)
        } catch (_: Exception) {
            // The text is already in; the caret stays where it is.
        }
    }

    /**
     * Inserts [snippet] at the caret (replacing the current selection, like typing would) for the snippet picker.
     * Clipboard and selection are read only if the snippet uses them and never in password-like fields; the rendering
     * and its privacy rule live in the pure [SnippetInsertion]. Must be called on the main thread; never throws and
     * never logs snippet, clipboard or selection text.
     */
    fun insertSnippet(snippet: Snippet): SnippetInsertOutcome = try {
        val sensitive = editorInstance.activeInfo.inputAttributes.isPasswordLike()
        val clip = if (!sensitive && SnippetTemplate.usesPlaceholder(snippet.body, "clipboard")) currentClipboardText() else null
        val selection = if (!sensitive && SnippetTemplate.usesPlaceholder(snippet.body, "selection")) {
            // Raw editors (e.g. terminals) do not report their text, so only they need to be asked directly.
            editorInstance.activeContent.selectedText.ifEmpty {
                if (editorInstance.activeInfo.isRawInputEditor) {
                    FlorisImeService.currentInputConnection()?.getSelectedText(0)?.toString().orEmpty()
                } else {
                    ""
                }
            }
        } else {
            null
        }
        val plan = SnippetInsertion.plan(
            InsertionRequest(
                snippet = snippet,
                now = LocalDateTime.now(),
                clipboardText = clip,
                selectionText = selection,
                sensitiveField = sensitive,
                datePattern = prefs.snippets.dateFormat.get().pattern,
                timePattern = prefs.snippets.timeFormat.get().pattern,
            )
        )
        when (plan) {
            is InsertionPlan.TooLarge -> SnippetInsertOutcome.TOO_LARGE
            is InsertionPlan.NothingToInsert -> SnippetInsertOutcome.NOTHING
            is InsertionPlan.Insert -> if (editorInstance.commitText(plan.text)) {
                moveCaretLeft(plan)
                SnippetInsertOutcome.INSERTED
            } else {
                SnippetInsertOutcome.FAILED // no input connection: nothing was inserted, so do not close the panel
            }
        }
    } catch (_: Exception) {
        SnippetInsertOutcome.FAILED
    }

    private fun graphemeCount(text: String): Int {
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        var n = 0
        while (it.next() != BreakIterator.DONE) n++
        return n
    }

    private fun handleSpace(data: KeyData) {
        if (tryExpandSnippet(ExpansionTrigger.SPACE)) {
            pendingAutocorrect = null
            return
        }
        val typed = WordPredictor.contextTokens(editorInstance.activeContent.textBeforeSelection).second
        val candidate = nlpManager.getAutoCommitCandidate()
        if (candidate == null) {
            nlpManager.noteTypedOrCommitted(null)
            pendingAutocorrect = null
        }
        candidate?.let { commitCandidate(it) }
        val commitSpace = subtypeManager.activeSubtype.primaryLocale.supportsAutoSpace || candidate == null
        if (candidate != null) {
            armAutocorrectRevert(typed, candidate.text.toString(), withSpace = commitSpace)
        }
        if (prefs.keyboard.spaceBarSwitchesToCharacters.get()) {
            when (activeState.keyboardMode) {
                KeyboardMode.NUMERIC_ADVANCED,
                KeyboardMode.SYMBOLS,
                KeyboardMode.SYMBOLS2 -> {
                    activeState.keyboardMode = KeyboardMode.CHARACTERS
                }
                else -> { /* Do nothing */ }
            }
        }
        if (prefs.correction.doubleSpacePeriod.get()) {
            if (inputEventDispatcher.isConsecutiveUp(data)) {
                val text = editorInstance.run { activeContent.getTextBeforeCursor(2) }
                if (text.length == 2 && DoubleSpacePeriodMatcher.matches(text)) {
                    pendingAutocorrect = null
                    editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
                    editorInstance.commitText(". ")
                    return
                }
            }
        }
        // TODO: this is whether we commit space after selecting candidate. Should be determined by SuggestionProvider
        if (!subtypeManager.activeSubtype.primaryLocale.supportsAutoSpace &&
                candidate != null) { /* Do nothing */ } else {
            editorInstance.commitText(KeyCode.SPACE.toChar().toString())
        }
    }

    /**
     * Handles a [KeyCode.TOGGLE_INCOGNITO_MODE] event.
     */
    private suspend fun handleToggleIncognitoMode() {
        prefs.suggestion.forceIncognitoModeFromDynamic.set(!prefs.suggestion.forceIncognitoModeFromDynamic.get())
        val newState = !activeState.isIncognitoMode
        activeState.isIncognitoMode = newState
        lastToastReference.get()?.cancel()
        lastToastReference = WeakReference(
            if (newState) {
                appContext.showLongToast(
                    R.string.incognito_mode__toast_after_enabled,
                    "app_name" to appContext.getString(R.string.floris_app_name),
                )
            } else {
                appContext.showLongToast(
                    R.string.incognito_mode__toast_after_disabled,
                    "app_name" to appContext.getString(R.string.floris_app_name),
                )
            }
        )
    }

    /**
     * Handles a [KeyCode.TOGGLE_AUTOCORRECT] event.
     */
    private fun handleToggleAutocorrect() {
        lastToastReference.get()?.cancel()
        lastToastReference = WeakReference(
            appContext.showLongToastSync("Autocorrect toggle is a placeholder and not yet implemented")
        )
    }

    /**
     * Handles a [KeyCode.KANA_SWITCHER] event
     */
    private fun handleKanaSwitch() {
        activeState.batchEdit {
            it.isKanaKata = !it.isKanaKata
            it.isCharHalfWidth = false
        }
    }

    /**
     * Handles a [KeyCode.KANA_HIRA] event
     */
    private fun handleKanaHira() {
        activeState.batchEdit {
            it.isKanaKata = false
            it.isCharHalfWidth = false
        }
    }

    /**
     * Handles a [KeyCode.KANA_KATA] event
     */
    private fun handleKanaKata() {
        activeState.batchEdit {
            it.isKanaKata = true
            it.isCharHalfWidth = false
        }
    }

    /**
     * Handles a [KeyCode.KANA_HALF_KATA] event
     */
    private fun handleKanaHalfKata() {
        activeState.batchEdit {
            it.isKanaKata = true
            it.isCharHalfWidth = true
        }
    }

    /**
     * Handles a [KeyCode.CHAR_WIDTH_SWITCHER] event
     */
    private fun handleCharWidthSwitch() {
        activeState.isCharHalfWidth = !activeState.isCharHalfWidth
    }

    /**
     * Handles a [KeyCode.CHAR_WIDTH_SWITCHER] event
     */
    private fun handleCharWidthFull() {
        activeState.isCharHalfWidth = false
    }

    /**
     * Handles a [KeyCode.CHAR_WIDTH_SWITCHER] event
     */
    private fun handleCharWidthHalf() {
        activeState.isCharHalfWidth = true
    }

    /**
     * Sends [key] as a real key event, with Ctrl/Alt from [terminalModifiers] (and shift from the keyboard). Spends
     * one-shot modifiers. Never logs or stores anything about the key.
     */
    private fun sendTerminalSpecial(key: SpecialKey, count: Int = 1) {
        val mods = terminalModifiers.current(shift = inputEventDispatcher.isPressed(KeyCode.SHIFT))
        val action = TerminalKeyMapper.mapSpecial(key, mods) as TerminalAction.Key
        editorInstance.sendDownUpKeyEvent(action.keyCode, action.metaState, count)
        terminalModifiers.consume()
    }

    /** Developer-row modifier key gestures (down / up / cancel), see [ModifierStateMachine]. */
    fun terminalModifierDown(modifier: TerminalModifier) = terminalModifiers.press(modifier)
    fun terminalModifierUp(modifier: TerminalModifier) =
        terminalModifiers.release(modifier, SystemClock.uptimeMillis())
    fun terminalModifierCancel(modifier: TerminalModifier) = terminalModifiers.cancel(modifier)

    /** A tap on a developer-row key (not a modifier key). Never logs or stores what is sent. */
    fun performDeveloperKey(action: DevKeyAction) {
        if (action is DevKeyAction.Modifier) return
        val mods = terminalModifiers.current(shift = inputEventDispatcher.isPressed(KeyCode.SHIFT))
        val resolution = DeveloperKeyResolver.resolve(action, mods)
        for (step in resolution.steps) {
            when (step) {
                is DevStep.Key -> editorInstance.sendDownUpKeyEvent(step.keyCode, step.metaState)
                is DevStep.Text -> editorInstance.commitText(step.text)
                is DevStep.CursorLeft ->
                    editorInstance.sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, editorInstance.meta(), step.count)
            }
        }
        if (resolution.consumesModifiers) terminalModifiers.consume()
    }

    /**
     * A typed character while Ctrl and/or Alt is active. Returns true if it was sent as a key event (the caller must
     * then skip the normal text path), false if the character has no key mapping and should be typed as plain text.
     */
    private fun sendTerminalChar(text: String): Boolean {
        val mods = terminalModifiers.current()
        val action = TerminalKeyMapper.mapChar(text, mods) as? TerminalAction.Key ?: return false
        editorInstance.sendDownUpKeyEvent(action.keyCode, action.metaState)
        terminalModifiers.consume()
        return true
    }

    fun editClipSession(block: (ClipEditorSession) -> ClipEditorSession) {
        val current = clipEditor.value ?: return
        clipEditor.value = block(current)
    }

    fun openClipEditor(origin: ClipEditorOrigin, sourceId: String?, text: String, sensitive: Boolean) {
        clipEditor.value = ClipEditorSession(origin, sourceId, sensitive, EditorBuffer.of(text))
        activeState.imeUiMode = ImeUiMode.TEXT
    }

    fun closeClipEditor() {
        clipEditor.value = null
    }

    fun openLatestClipEditor() {
        val vaultLocked = clipboardManager.vaultLockedFlow.value
        val vault = if (vaultLocked) null else clipboardManager.vaultItemsFlow.value.maxByOrNull { it.createdAtMs }
        val regular = clipboardManager.currentHistory.all
            .filter { it.type == ItemType.TEXT && !it.text.isNullOrBlank() }
            .maxByOrNull { it.creationTimestampMs }
        if (vault != null && (regular == null || vault.createdAtMs >= regular.creationTimestampMs)) {
            openClipEditor(ClipEditorOrigin.VAULT, vault.id, vault.text, sensitive = true)
            return
        }
        if (regular != null) {
            openClipEditor(ClipEditorOrigin.REGULAR, regular.uuid, regular.text.orEmpty(), regular.isSensitive)
            return
        }
        val primary = clipboardManager.primaryClip
        if (primary != null && primary.type == ItemType.TEXT && !primary.isSensitive && !primary.text.isNullOrBlank()) {
            openClipEditor(ClipEditorOrigin.REGULAR, null, primary.text.orEmpty(), sensitive = false)
            return
        }
        if (vaultLocked && clipboardManager.vaultCountFlow.value > 0) {
            appContext.showShortToastSync(R.string.clip_editor__vault_locked)
        } else {
            appContext.showShortToastSync(R.string.clip_editor__empty)
        }
    }

    fun pasteClipEditor() {
        val session = clipEditor.value ?: return
        if (session.sensitive) vaultLearningHold = true
        editorInstance.commitText(session.body.text)
    }

    fun saveClipEditor(asNew: Boolean) {
        val session = clipEditor.value ?: return
        val text = session.body.text
        if (text.isBlank()) {
            appContext.showShortToastSync(R.string.clip_editor__empty)
            return
        }
        when (ClipEditorSave.target(session.origin, asNew, !session.sourceId.isNullOrBlank())) {
            ClipEditorWrite.UPDATE_REGULAR -> {
                val item = clipboardManager.currentHistory.all.firstOrNull { it.uuid == session.sourceId }
                if (item == null || item.type != ItemType.TEXT) {
                    clipboardManager.addNewPlaintext(text)
                } else {
                    val category = ClipCategory.entries.firstOrNull { it.name == item.category } ?: ClipCategory.GENERAL
                    val saved = clipboardManager.updateUserFields(
                        item,
                        text,
                        parseTagColumn(item.tags),
                        category,
                        item.isFavorite,
                        item.isPinned,
                    )
                    if (!saved) {
                        appContext.showShortToastSync(R.string.clip_editor__failed)
                        return
                    }
                }
            }
            ClipEditorWrite.ADD_REGULAR -> clipboardManager.addNewPlaintext(text)
            ClipEditorWrite.UPDATE_VAULT -> clipboardManager.replaceVaultText(session.sourceId.orEmpty(), text)
            ClipEditorWrite.ADD_VAULT -> clipboardManager.addVaultText(text)
            ClipEditorWrite.UPDATE_SNIPPET -> {
                val snippet = snippetManager.state.value.library.snippets.firstOrNull { it.id == session.sourceId }
                if (snippet == null) {
                    clipboardManager.addNewPlaintext(text)
                } else {
                    scope.launch {
                        val result = snippetManager.update(
                            snippet.copy(body = text, updatedAtMillis = System.currentTimeMillis()),
                        )
                        if (result !is LibraryResult.Ok) {
                            appContext.showShortToastSync(R.string.clip_editor__failed)
                        }
                    }
                }
            }
        }
        appContext.showShortToastSync(R.string.clip_editor__saved)
    }

    fun insertSavedCommand(text: String) {
        insertAtTarget(text, cursorBackFor(text))
    }

    fun performToolbarAction(id: ToolbarActionId) {
        ToolbarCatalog.symbol(id)?.let { symbol ->
            insertAtTarget(symbol, cursorBackFor(symbol))
            return
        }
        ToolbarCatalog.transform(id)?.let { op ->
            applyTransform(op)
            return
        }
        ToolbarCatalog.prompt(id)?.let { prompt ->
            insertPrompt(prompt.body)
            return
        }
        when (id) {
            ToolbarActionId.CTRL -> if (clipEditor.value == null) terminalModifiers.toggleLock(TerminalModifier.CTRL)
            ToolbarActionId.ALT -> if (clipEditor.value == null) terminalModifiers.toggleLock(TerminalModifier.ALT)
            ToolbarActionId.ESC -> sendSpecial(SpecialKey.ESCAPE)
            ToolbarActionId.TAB -> sendSpecial(SpecialKey.TAB)
            ToolbarActionId.UP -> sendSpecial(SpecialKey.UP)
            ToolbarActionId.DOWN -> sendSpecial(SpecialKey.DOWN)
            ToolbarActionId.LEFT -> sendSpecial(SpecialKey.LEFT)
            ToolbarActionId.RIGHT -> sendSpecial(SpecialKey.RIGHT)
            ToolbarActionId.HOME -> sendSpecial(SpecialKey.HOME)
            ToolbarActionId.END -> sendSpecial(SpecialKey.END)
            ToolbarActionId.PGUP -> sendSpecial(SpecialKey.PAGE_UP)
            ToolbarActionId.PGDN -> sendSpecial(SpecialKey.PAGE_DOWN)
            ToolbarActionId.CTRL_C -> sendCtrlLetter("c")
            ToolbarActionId.CTRL_D -> sendCtrlLetter("d")
            ToolbarActionId.CTRL_Z -> sendCtrlLetter("z")
            ToolbarActionId.CTRL_L -> sendCtrlLetter("l")
            ToolbarActionId.CTRL_R -> sendCtrlLetter("r")
            ToolbarActionId.SEL_WORD -> selectTarget("word")
            ToolbarActionId.SEL_LINE -> selectTarget("line")
            ToolbarActionId.SEL_ALL -> selectTarget("all")
            ToolbarActionId.UNDO -> {
                if (clipEditor.value != null) {
                    editClipSession { it.copy(body = it.body.undo(), focus = EditorField.BODY, revision = it.revision + 1) }
                } else {
                    inputEventDispatcher.sendDownUp(TextKeyData.UNDO)
                }
            }
            ToolbarActionId.REDO -> {
                if (clipEditor.value != null) {
                    editClipSession { it.copy(body = it.body.redo(), focus = EditorField.BODY, revision = it.revision + 1) }
                } else {
                    inputEventDispatcher.sendDownUp(TextKeyData.REDO)
                }
            }
            ToolbarActionId.CUT, ToolbarActionId.COPY -> {
                if (clipEditor.value != null) {
                    appContext.showShortToastSync(R.string.clip_editor__use_actions)
                } else if (id == ToolbarActionId.CUT) {
                    inputEventDispatcher.sendDownUp(TextKeyData.CLIPBOARD_CUT)
                } else {
                    inputEventDispatcher.sendDownUp(TextKeyData.CLIPBOARD_COPY)
                }
            }
            ToolbarActionId.PASTE -> {
                if (clipEditor.value != null) {
                    currentClipboardText()?.let { insertAtTarget(it) }
                } else {
                    inputEventDispatcher.sendDownUp(TextKeyData.CLIPBOARD_PASTE)
                }
            }
            ToolbarActionId.OPEN_CLIPBOARD -> {
                clipLaneRequest.value = ClipLane.REGULAR
                activeState.imeUiMode = ImeUiMode.CLIPBOARD
            }
            ToolbarActionId.OPEN_VAULT -> {
                clipLaneRequest.value = ClipLane.VAULT
                activeState.imeUiMode = ImeUiMode.CLIPBOARD
            }
            ToolbarActionId.OPEN_SNIPPETS -> activeState.imeUiMode = ImeUiMode.SNIPPETS
            ToolbarActionId.EDIT_CLIP -> openLatestClipEditor()
            ToolbarActionId.PINNED -> pastePinned()
            ToolbarActionId.ZIP -> FlorisImeService.launchSettings()
            ToolbarActionId.INCOGNITO -> inputEventDispatcher.sendDownUp(TextKeyData.TOGGLE_INCOGNITO_MODE)
            ToolbarActionId.LEARNING -> scope.launch {
                val pref = prefs.suggestion.learnFromTyping
                pref.set(!pref.get())
            }
            ToolbarActionId.BOTTOM_GAP -> scope.launch {
                val pref = prefs.keyboard.bottomGapDp
                val next = when (pref.get()) {
                    0 -> 16
                    16 -> 32
                    32 -> 48
                    else -> 0
                }
                pref.set(next)
            }
            ToolbarActionId.ONE_HAND -> inputEventDispatcher.sendDownUp(TextKeyData.TOGGLE_COMPACT_LAYOUT)
            ToolbarActionId.THEME -> scope.launch {
                val current = KeyboardPaletteCodec.decode(prefs.theme.keyboardColors.get())
                val presets = KeyboardPalettes.presets
                val index = presets.indexOfFirst { it.palette == current }
                val next = presets[(if (index < 0) 0 else index + 1) % presets.size]
                prefs.theme.keyboardColors.set(KeyboardPaletteCodec.encode(next.palette))
            }
            ToolbarActionId.VOICE -> FlorisImeService.switchToVoiceInputMethod()
            ToolbarActionId.EMOJI -> activeState.imeUiMode = ImeUiMode.MEDIA
            ToolbarActionId.SETTINGS -> FlorisImeService.launchSettings()
            ToolbarActionId.DEVROW -> scope.launch {
                val pref = prefs.devrow.terminalKeyRow
                val next = when (pref.get()) {
                    dev.patrickgold.florisboard.ime.mode.TerminalKeyRow.AUTO ->
                        dev.patrickgold.florisboard.ime.mode.TerminalKeyRow.ALWAYS
                    dev.patrickgold.florisboard.ime.mode.TerminalKeyRow.ALWAYS ->
                        dev.patrickgold.florisboard.ime.mode.TerminalKeyRow.NEVER
                    dev.patrickgold.florisboard.ime.mode.TerminalKeyRow.NEVER ->
                        dev.patrickgold.florisboard.ime.mode.TerminalKeyRow.AUTO
                }
                pref.set(next)
            }
            ToolbarActionId.COUNT -> showCounts()
            ToolbarActionId.PASTE_CODE -> {
                val clip = currentClipboardText() ?: return
                insertAtTarget(TextTransforms.fence(clip, ""))
            }
            ToolbarActionId.PAIR -> ToolbarRuntime.update(appContext) { it.copy(bracketPairing = !it.bracketPairing) }
            ToolbarActionId.AI_REWRITE -> startLocalAi(AiTask.REWRITE)
            ToolbarActionId.AI_GRAMMAR -> startLocalAi(AiTask.GRAMMAR)
            ToolbarActionId.AI_CONTINUE -> startLocalAi(AiTask.CONTINUE)
            ToolbarActionId.AI_SUMMARIZE -> startLocalAi(AiTask.SUMMARIZE)
            ToolbarActionId.AI_TRANSLATE -> startLocalAi(AiTask.TRANSLATE)
            ToolbarActionId.AI_SHELL -> startLocalAi(AiTask.SHELL)
            ToolbarActionId.AI_ASK -> startLocalAi(AiTask.ASK)
            else -> Unit
        }
    }

    private fun showCounts() {
        val counts = TextTransforms.counts(targetText())
        appContext.showShortToastSync(
            R.string.clip_editor__counts,
            "chars" to counts.characters.toString(),
            "words" to counts.words.toString(),
            "tokens" to counts.tokens.toString(),
        )
    }

    private fun pastePinned() {
        val item = clipboardManager.currentHistory.all.firstOrNull {
            it.isPinned && it.type == ItemType.TEXT && !it.isSensitive && !it.text.isNullOrBlank()
        }
        if (item == null) {
            appContext.showShortToastSync(R.string.clip_editor__empty)
            return
        }
        insertAtTarget(item.text.orEmpty())
    }

    private fun insertPrompt(template: String) {
        val filled = TextTransforms.fillPrompt(template, mapOf("selection" to targetText()))
        val session = clipEditor.value
        if (session != null) {
            clipEditor.value = session.copy(
                body = session.body.replaceAllText(filled),
                focus = EditorField.BODY,
                revision = session.revision + 1,
            )
        } else {
            editorInstance.commitText(filled)
        }
    }

    private fun applyTransform(op: TextTransform) {
        val generates = op == TextTransform.UUID || op == TextTransform.TIMESTAMP || op == TextTransform.RANDOM_HEX
        val session = clipEditor.value
        if (session != null) {
            val body = session.body
            val source = if (generates) "" else if (body.hasSelection) body.selectedText else body.text
            when (val result = TextTransforms.apply(op, source)) {
                is dev.patrickgold.florisboard.ime.toolbar.TransformResult.Ok -> {
                    val resolved = if (generates || body.hasSelection) {
                        body.insert(result.text)
                    } else {
                        body.replaceAllText(result.text)
                    }
                    clipEditor.value = session.copy(body = resolved, focus = EditorField.BODY, revision = session.revision + 1)
                }
                is dev.patrickgold.florisboard.ime.toolbar.TransformResult.Failed ->
                    appContext.showShortToastSync(R.string.toolbar__transform_failed)
            }
            return
        }
        if (!generates && hostSelection().isEmpty() && op != TextTransform.CODE_FENCE) {
            appContext.showShortToastSync(R.string.toolbar__select_text)
            return
        }
        when (val result = TextTransforms.apply(op, if (generates) "" else hostSelection())) {
            is dev.patrickgold.florisboard.ime.toolbar.TransformResult.Ok -> editorInstance.commitText(result.text)
            is dev.patrickgold.florisboard.ime.toolbar.TransformResult.Failed ->
                appContext.showShortToastSync(R.string.toolbar__transform_failed)
        }
    }

    private fun targetText(): String {
        val session = clipEditor.value
        if (session != null) {
            return if (session.body.hasSelection) session.body.selectedText else session.body.text
        }
        val content = editorInstance.activeContent
        val visible = content.textBeforeSelection + content.selectedText + content.textAfterSelection
        return content.selectedText.ifEmpty { visible }
    }

    private fun hostSelection(): String = editorInstance.activeContent.selectedText

    private fun selectTarget(which: String) {
        val session = clipEditor.value
        if (session != null) {
            val body = when (which) {
                "word" -> session.body.selectWord()
                "line" -> session.body.selectLine()
                else -> session.body.selectAll()
            }
            clipEditor.value = session.copy(body = body, focus = EditorField.BODY, revision = session.revision + 1)
            return
        }
        if (which == "all") {
            editorInstance.performClipboardSelectAll()
            return
        }
        val content = editorInstance.activeContent
        val cursor = content.localSelection.end.coerceIn(0, content.text.length)
        val (from, to) = if (which == "word") SelectionRanges.word(content.text, cursor) else SelectionRanges.line(content.text, cursor)
        val base = if (content.offset > 0) content.offset else 0
        editorInstance.setSelection(base + from, base + to)
    }

    private fun sendCtrlLetter(letter: String) {
        if (clipEditor.value != null) return
        terminalModifiers.press(TerminalModifier.CTRL)
        sendTerminalChar(letter)
        terminalModifiers.cancel(TerminalModifier.CTRL)
    }

    private fun sendSpecial(key: SpecialKey) {
        if (clipEditor.value != null) {
            val code = when (key) {
                SpecialKey.LEFT -> KeyCode.ARROW_LEFT
                SpecialKey.RIGHT -> KeyCode.ARROW_RIGHT
                SpecialKey.UP -> KeyCode.ARROW_UP
                SpecialKey.DOWN -> KeyCode.ARROW_DOWN
                SpecialKey.HOME -> KeyCode.MOVE_START_OF_LINE
                SpecialKey.END -> KeyCode.MOVE_END_OF_LINE
                SpecialKey.PAGE_UP -> KeyCode.MOVE_START_OF_PAGE
                SpecialKey.PAGE_DOWN -> KeyCode.MOVE_END_OF_PAGE
                SpecialKey.TAB -> KeyCode.TAB
                SpecialKey.ENTER -> KeyCode.ENTER
                SpecialKey.BACKSPACE -> KeyCode.DELETE
                SpecialKey.DELETE -> KeyCode.FORWARD_DELETE
                else -> null
            }
            if (code != null) {
                applyClipEditorKey(TextKeyData(type = KeyType.NAVIGATION, code = code))
            }
            return
        }
        sendTerminalSpecial(key)
    }

    private fun insertAtTarget(text: String, cursorBack: Int = 0) {
        val session = clipEditor.value
        if (session != null) {
            var buffer = session.focused().insert(text)
            if (cursorBack > 0) {
                val caret = (buffer.end - cursorBack).coerceAtLeast(0)
                buffer = buffer.copy(start = caret, end = caret)
            }
            clipEditor.value = session.withFocused(buffer)
            return
        }
        editorInstance.commitText(text)
        if (cursorBack > 0) {
            editorInstance.sendDownUpKeyEvent(android.view.KeyEvent.KEYCODE_DPAD_LEFT, 0, cursorBack)
        }
    }

    private fun cursorBackFor(text: String): Int = if (text == "()" || text == "[]" || text == "{}") 1 else 0

    private fun isCharacterKey(data: dev.patrickgold.florisboard.ime.keyboard.KeyData): Boolean =
        data.type == KeyType.CHARACTER || data.type == KeyType.NUMERIC

    private fun applyAskKey(data: dev.patrickgold.florisboard.ime.keyboard.KeyData): Boolean {
        if (!localAi.askConsumes(data.code, isCharacterKey(data))) return false
        val text = if (isCharacterKey(data) || data.code == KeyCode.SPACE) {
            runCatching { data.asString(isForDisplay = false) }.getOrDefault("")
        } else {
            ""
        }
        val extend = inputEventDispatcher.isPressed(KeyCode.SHIFT)
        return localAi.applyAskKey(
            EditorKeyInput(
                code = data.code,
                character = isCharacterKey(data),
                text = text,
                shift = extend,
                extend = extend,
            ),
        )
    }

    private fun aiSnapshot(): AiFieldSnapshot {
        val session = clipEditor.value
        val content = editorInstance.activeContent
        return AiFieldSnapshot(
            passwordLike = editorInstance.activeInfo.inputAttributes.isPasswordLike(),
            editorOpen = session != null,
            editorIsVault = session?.origin == ClipEditorOrigin.VAULT || session?.sensitive == true,
            selection = content.selectedText,
            fieldText = content.textBeforeSelection + content.selectedText + content.textAfterSelection,
            clipboard = currentClipboardText().orEmpty(),
            editorText = session?.body?.text.orEmpty(),
        )
    }

    private fun startLocalAi(task: AiTask) {
        when (localAi.open(task, aiSnapshot())) {
            AiRefusal.NO_MODEL, AiRefusal.NATIVE_MISSING ->
                appContext.showShortToastSync(R.string.local_ai__unavailable)
            AiRefusal.PASSWORD -> appContext.showShortToastSync(R.string.local_ai__password)
            else -> Unit
        }
    }

    fun localAiSnapshot(): AiFieldSnapshot = aiSnapshot()

    fun chooseLocalAiSource(source: AiSource) {
        localAi.chooseSource(source, aiSnapshot())
    }

    fun submitLocalAiAsk() {
        localAi.submitAsk(aiSnapshot())
    }

    fun insertLocalAiResult() {
        val panel = localAi.panel.value ?: return
        if (panel.output.isBlank()) return
        if (panel.fromVault) vaultLearningHold = true
        insertAtTarget(panel.output)
        localAi.close()
    }

    fun copyLocalAiResult() {
        val panel = localAi.panel.value ?: return
        if (panel.fromVault || panel.output.isBlank()) {
            appContext.showShortToastSync(R.string.local_ai__vault_copy)
            return
        }
        clipboardManager.addNewPlaintext(panel.output)
        appContext.showShortToastSync(R.string.local_ai__copied)
    }

    private fun applyClipEditorKey(data: dev.patrickgold.florisboard.ime.keyboard.KeyData): Boolean {
        val session = clipEditor.value ?: return false
        if (!EditorKeyRouter.consumes(data.code, isCharacterKey(data))) return false
        val extend = inputEventDispatcher.isPressed(KeyCode.SHIFT)
        val text = if (isCharacterKey(data) || data.code == KeyCode.SPACE) {
            runCatching { data.asString(isForDisplay = false) }.getOrDefault("")
        } else {
            ""
        }
        val input = EditorKeyInput(
            code = data.code,
            character = isCharacterKey(data),
            text = text,
            shift = extend,
            extend = extend,
            singleLine = session.focus != EditorField.BODY,
            pairBrackets = session.focus == EditorField.BODY && ToolbarRuntime.config.value.bracketPairing,
        )
        val before = session.focused()
        val applied = EditorKeyRouter.apply(before, input)
        clipEditor.value = session.withFocused(EditorKeyRouter.caretAfterPair(before, applied, input))
        return true
    }

    private fun rememberShellLine() {
        ToolbarRuntime.ensure(appContext)
        val record = ShellHistoryPolicy.shouldRecord(
            enabled = ToolbarRuntime.config.value.shellHistoryEnabled,
            incognito = activeState.isIncognitoMode,
            passwordLike = editorInstance.activeInfo.inputAttributes.isPasswordLike(),
            vaultHold = vaultLearningHold,
            editorOpen = clipEditor.value != null,
        )
        if (!record) return
        val line = ShellHistoryPolicy.commandFrom(editorInstance.activeContent.textBeforeSelection) ?: return
        ToolbarRuntime.remember(appContext, line)
    }


    override fun onInputKeyDown(data: KeyData) {
        if (localAi.askConsumes(data.code, isCharacterKey(data))) return
        if (clipEditor.value != null && EditorKeyRouter.consumes(data.code, isCharacterKey(data))) return
        val windowController = FlorisImeService.windowControllerOrNull()
        windowController?.editor?.disableIfNoGestureInProgress()
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> {
                editorInstance.massSelection.begin()
            }
            KeyCode.SHIFT -> handleShiftDown(data)
            KeyCode.CTRL -> terminalModifiers.press(TerminalModifier.CTRL)
            KeyCode.ALT -> terminalModifiers.press(TerminalModifier.ALT)
        }
    }

    override fun onInputKeyUp(data: KeyData) = activeState.batchEdit {
        if (applyAskKey(data)) {
            pendingAutocorrect = null
            if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
                activeState.inputShiftState = InputShiftState.UNSHIFTED
            }
            return@batchEdit
        }
        if (applyClipEditorKey(data)) {
            pendingAutocorrect = null
            if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
                activeState.inputShiftState = InputShiftState.UNSHIFTED
            }
            return@batchEdit
        }
        if (data.code != KeyCode.SPACE && data.code != KeyCode.DELETE) {
            pendingAutocorrect = null
        }
        val windowController = FlorisImeService.windowControllerOrNull() ?: return@batchEdit
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> {
                editorInstance.massSelection.end()
                handleArrow(data.code)
            }
            KeyCode.CTRL -> terminalModifiers.release(TerminalModifier.CTRL, SystemClock.uptimeMillis())
            KeyCode.ALT -> terminalModifiers.release(TerminalModifier.ALT, SystemClock.uptimeMillis())
            KeyCode.CTRL_LOCK -> terminalModifiers.toggleLock(TerminalModifier.CTRL)
            KeyCode.ALT_LOCK -> terminalModifiers.toggleLock(TerminalModifier.ALT)
            KeyCode.ESCAPE -> sendTerminalSpecial(SpecialKey.ESCAPE)
            KeyCode.TAB -> if (!tryExpandSnippet(ExpansionTrigger.TAB)) sendTerminalSpecial(SpecialKey.TAB)
            KeyCode.CAPS_LOCK -> handleCapsLock()
            KeyCode.CHAR_WIDTH_SWITCHER -> handleCharWidthSwitch()
            KeyCode.CHAR_WIDTH_FULL -> handleCharWidthFull()
            KeyCode.CHAR_WIDTH_HALF -> handleCharWidthHalf()
            KeyCode.CLIPBOARD_CUT -> editorInstance.performClipboardCut()
            KeyCode.CLIPBOARD_COPY -> editorInstance.performClipboardCopy()
            KeyCode.CLIPBOARD_PASTE -> editorInstance.performClipboardPaste()
            KeyCode.CLIPBOARD_SELECT -> handleClipboardSelect()
            KeyCode.CLIPBOARD_SELECT_ALL -> editorInstance.performClipboardSelectAll()
            KeyCode.CLIPBOARD_CLEAR_HISTORY -> clipboardManager.clearHistory()
            KeyCode.CLIPBOARD_CLEAR_FULL_HISTORY -> clipboardManager.clearFullHistory()
            KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> {
                if (prefs.clipboard.clearPrimaryClipAffectsHistoryIfUnpinned.get()) {
                    clipboardManager.primaryClip?.let { clipboardManager.deleteClip(it, onlyIfUnpinned = true) }
                }
                clipboardManager.updatePrimaryClip(null)
                appContext.showShortToastSync(R.string.clipboard__cleared_primary_clip)
            }
            KeyCode.TOGGLE_FLOATING_WINDOW -> windowController.actions.toggleFloatingWindow()
            KeyCode.TOGGLE_COMPACT_LAYOUT -> windowController.actions.toggleCompactLayout()
            KeyCode.COMPACT_LAYOUT_TO_LEFT -> windowController.actions.compactLayoutToLeft()
            KeyCode.COMPACT_LAYOUT_TO_RIGHT -> windowController.actions.compactLayoutToRight()
            KeyCode.TOGGLE_RESIZE_MODE -> windowController.editor.toggleEnabled()
            KeyCode.DELETE -> handleBackwardDelete(OperationUnit.CHARACTERS)
            KeyCode.DELETE_WORD -> handleBackwardDelete(OperationUnit.WORDS)
            KeyCode.ENTER -> handleEnter()
            KeyCode.FORWARD_DELETE -> handleForwardDelete(OperationUnit.CHARACTERS)
            KeyCode.FORWARD_DELETE_WORD -> handleForwardDelete(OperationUnit.WORDS)
            KeyCode.IME_SHOW_UI -> FlorisImeService.showUi()
            KeyCode.IME_HIDE_UI -> FlorisImeService.hideUi()
            KeyCode.IME_PREV_SUBTYPE -> subtypeManager.switchToPrevSubtype()
            KeyCode.IME_NEXT_SUBTYPE -> subtypeManager.switchToNextSubtype()
            KeyCode.IME_UI_MODE_TEXT -> activeState.imeUiMode = ImeUiMode.TEXT
            KeyCode.IME_UI_MODE_MEDIA -> activeState.imeUiMode = ImeUiMode.MEDIA
            KeyCode.IME_UI_MODE_CLIPBOARD -> activeState.imeUiMode = ImeUiMode.CLIPBOARD
            KeyCode.IME_UI_MODE_SNIPPETS -> activeState.imeUiMode = ImeUiMode.SNIPPETS
            KeyCode.VOICE_INPUT -> FlorisImeService.switchToVoiceInputMethod()
            KeyCode.KANA_SWITCHER -> handleKanaSwitch()
            KeyCode.KANA_HIRA -> handleKanaHira()
            KeyCode.KANA_KATA -> handleKanaKata()
            KeyCode.KANA_HALF_KATA -> handleKanaHalfKata()
            KeyCode.LANGUAGE_SWITCH -> handleLanguageSwitch()
            KeyCode.REDO -> editorInstance.performRedo()
            KeyCode.SETTINGS -> FlorisImeService.launchSettings()
            KeyCode.SHIFT -> handleShiftUp(data)
            KeyCode.SPACE -> handleSpace(data)
            KeyCode.SYSTEM_INPUT_METHOD_PICKER -> InputMethodUtils.showImePicker(appContext)
            KeyCode.SHOW_SUBTYPE_PICKER -> {
                appContext.keyboardManager.value.activeState.isSubtypeSelectionVisible = true
            }
            KeyCode.SYSTEM_PREV_INPUT_METHOD -> FlorisImeService.switchToPrevInputMethod()
            KeyCode.SYSTEM_NEXT_INPUT_METHOD -> FlorisImeService.switchToNextInputMethod()
            KeyCode.TOGGLE_SMARTBAR_VISIBILITY -> scope.launch {
                prefs.smartbar.enabled.let { it.set(!it.get()) }
            }
            KeyCode.TOGGLE_ACTIONS_OVERFLOW -> {
                activeState.isActionsOverflowVisible = !activeState.isActionsOverflowVisible
            }
            KeyCode.TOGGLE_ACTIONS_EDITOR -> {
                activeState.isActionsEditorVisible = !activeState.isActionsEditorVisible
            }
            KeyCode.TOGGLE_INCOGNITO_MODE -> scope.launch { handleToggleIncognitoMode() }
            KeyCode.TOGGLE_AUTOCORRECT -> handleToggleAutocorrect()
            KeyCode.UNDO -> editorInstance.performUndo()
            KeyCode.VIEW_CHARACTERS -> activeState.keyboardMode = KeyboardMode.CHARACTERS
            KeyCode.VIEW_NUMERIC -> activeState.keyboardMode = KeyboardMode.NUMERIC
            KeyCode.VIEW_NUMERIC_ADVANCED -> activeState.keyboardMode = KeyboardMode.NUMERIC_ADVANCED
            KeyCode.VIEW_PHONE -> activeState.keyboardMode = KeyboardMode.PHONE
            KeyCode.VIEW_PHONE2 -> activeState.keyboardMode = KeyboardMode.PHONE2
            KeyCode.VIEW_SYMBOLS -> activeState.keyboardMode = KeyboardMode.SYMBOLS
            KeyCode.VIEW_SYMBOLS2 -> activeState.keyboardMode = KeyboardMode.SYMBOLS2
            else -> {
                if (activeState.imeUiMode == ImeUiMode.MEDIA) {
                    nlpManager.getAutoCommitCandidate()?.let { commitCandidate(it) }
                    editorInstance.commitText(data.asString(isForDisplay = false))
                    return@batchEdit
                }
                when (activeState.keyboardMode) {
                    KeyboardMode.NUMERIC,
                    KeyboardMode.NUMERIC_ADVANCED,
                    KeyboardMode.PHONE,
                    KeyboardMode.PHONE2 -> when (data.type) {
                        KeyType.CHARACTER,
                        KeyType.NUMERIC -> {
                            val text = data.asString(isForDisplay = false)
                            editorInstance.commitText(text)
                        }
                        else -> when (data.code) {
                            KeyCode.PHONE_PAUSE,
                            KeyCode.PHONE_WAIT -> {
                                val text = data.asString(isForDisplay = false)
                                editorInstance.commitText(text)
                            }
                        }
                    }
                    else -> when (data.type) {
                        KeyType.CHARACTER, KeyType.NUMERIC ->{
                            val text = data.asString(isForDisplay = false)
                            // With Ctrl/Alt active the character is sent as a key event instead of text.
                            if (!(terminalModifiers.anyActive && sendTerminalChar(text))) {
                                if (!UCharacter.isUAlphabetic(UCharacter.codePointAt(text, 0))) {
                                    val candidate = nlpManager.getAutoCommitCandidate()
                                    if (candidate == null) nlpManager.noteTypedOrCommitted(null)
                                    candidate?.let { commitCandidate(it) }
                                }
                                editorInstance.commitChar(text)
                            }
                        }
                        else -> {
                            flogError(LogTopic.KEY_EVENTS) { "Received unknown key: $data" }
                        }
                    }
                }
                if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
                    activeState.inputShiftState = InputShiftState.UNSHIFTED
                }
            }
        }
    }

    override fun onInputKeyCancel(data: KeyData) {
        if (localAi.askConsumes(data.code, isCharacterKey(data))) return
        if (clipEditor.value != null && EditorKeyRouter.consumes(data.code, isCharacterKey(data))) return
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> {
                editorInstance.massSelection.end()
            }
            KeyCode.SHIFT -> handleShiftCancel()
            KeyCode.CTRL -> terminalModifiers.cancel(TerminalModifier.CTRL)
            KeyCode.ALT -> terminalModifiers.cancel(TerminalModifier.ALT)
        }
    }

    override fun onInputKeyRepeat(data: KeyData) {
        if (applyClipEditorKey(data)) {
            FlorisImeService.inputFeedbackController()?.keyRepeatedAction(data)
            return
        }
        FlorisImeService.inputFeedbackController()?.keyRepeatedAction(data)
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> handleArrow(data.code)
            else -> onInputKeyUp(data)
        }
    }

    private fun reevaluateDebugFlags() {
        val devtoolsEnabled = prefs.devtools.enabled.get()
        activeState.batchEdit {
            activeState.debugShowDragAndDropHelpers = devtoolsEnabled && prefs.devtools.showDragAndDropHelpers.get()
        }
    }

    fun onHardwareKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                handleHardwareKeyboardSpace()
                return true
            }
            KeyEvent.KEYCODE_ENTER -> {
                handleEnter()
                return true
            }
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                inputEventDispatcher.sendDown(TextKeyData.SHIFT)
                return true
            }
            else -> return false
        }
    }

    fun onHardwareKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                inputEventDispatcher.sendUp(TextKeyData.SHIFT)
                return true
            }
            else -> return false
        }
    }

    inner class KeyboardManagerResources {
        val composers = MutableStateFlow<Map<ExtensionComponentName, Composer>>(emptyMap())
        val currencySets = MutableStateFlow<Map<ExtensionComponentName, CurrencySet>>(emptyMap())
        val layouts = MutableStateFlow<Map<LayoutType, Map<ExtensionComponentName, LayoutArrangementComponent>>>(emptyMap())
        val popupMappings = MutableStateFlow<Map<ExtensionComponentName, PopupMappingComponent>>(emptyMap())
        val punctuationRules = MutableStateFlow<Map<ExtensionComponentName, PunctuationRule>>(emptyMap())
        val subtypePresets = MutableStateFlow<List<SubtypePreset>>(emptyList())

        val anyChangedVersion = MutableStateFlow(0)

        init {
            extensionManager.keyboardExtensions.collectIn(scope) { keyboardExtensions ->
                parseKeyboardExtensions(keyboardExtensions)
            }
        }

        private fun parseKeyboardExtensions(keyboardExtensions: List<KeyboardExtension>) {
            val localComposers = mutableMapOf<ExtensionComponentName, Composer>()
            val localCurrencySets = mutableMapOf<ExtensionComponentName, CurrencySet>()
            val localLayouts = mutableMapOf<LayoutType, MutableMap<ExtensionComponentName, LayoutArrangementComponent>>()
            val localPopupMappings = mutableMapOf<ExtensionComponentName, PopupMappingComponent>()
            val localPunctuationRules = mutableMapOf<ExtensionComponentName, PunctuationRule>()
            val localSubtypePresets = mutableListOf<SubtypePreset>()
            for (layoutType in LayoutType.entries) {
                localLayouts[layoutType] = mutableMapOf()
            }
            for (keyboardExtension in keyboardExtensions) {
                keyboardExtension.composers.forEach { composer ->
                    localComposers[ExtensionComponentName(keyboardExtension.meta.id, composer.id)] = composer
                }
                keyboardExtension.currencySets.forEach { currencySet ->
                    localCurrencySets[ExtensionComponentName(keyboardExtension.meta.id, currencySet.id)] = currencySet
                }
                keyboardExtension.layouts.forEach { (type, layoutComponents) ->
                    for (layoutComponent in layoutComponents) {
                        localLayouts[LayoutType.entries.first { it.id == type }]!![ExtensionComponentName(keyboardExtension.meta.id, layoutComponent.id)] = layoutComponent
                    }
                }
                keyboardExtension.popupMappings.forEach { popupMapping ->
                    localPopupMappings[ExtensionComponentName(keyboardExtension.meta.id, popupMapping.id)] = popupMapping
                }
                keyboardExtension.punctuationRules.forEach { punctuationRule ->
                    localPunctuationRules[ExtensionComponentName(keyboardExtension.meta.id, punctuationRule.id)] = punctuationRule
                }
                localSubtypePresets.addAll(keyboardExtension.subtypePresets)
            }
            localSubtypePresets.sortBy { it.locale.displayName() }
            for (languageCode in listOf("en-CA", "en-AU", "en-UK", "en-US")) {
                val index: Int = localSubtypePresets.indexOfFirst { it.locale.languageTag() == languageCode }
                if (index > 0) {
                    localSubtypePresets.add(0, localSubtypePresets.removeAt(index))
                }
            }
            subtypePresets.value = localSubtypePresets
            composers.value = localComposers
            currencySets.value = localCurrencySets
            layouts.value = localLayouts
            popupMappings.value = localPopupMappings
            punctuationRules.value = localPunctuationRules
            anyChangedVersion.update { it + 1 }
        }
    }

    private inner class ComputingEvaluatorImpl(
        override val version: Int,
        override val keyboard: Keyboard,
        override val editorInfo: FlorisEditorInfo,
        override val state: KeyboardState,
        override val subtype: Subtype,
    ) : ComputingEvaluator {

        override fun context(): Context = appContext

        val androidKeyguardManager = context().systemService(AndroidKeyguardManager::class)

        override fun displayLanguageNamesIn(): DisplayLanguageNamesIn {
            return prefs.localization.displayLanguageNamesIn.get()
        }

        override fun evaluateEnabled(data: KeyData): Boolean {
            return when (data.code) {
                KeyCode.CLIPBOARD_COPY,
                KeyCode.CLIPBOARD_CUT -> {
                    state.isSelectionMode && editorInfo.isRichInputEditor
                }
                KeyCode.CLIPBOARD_PASTE -> {
                    !androidKeyguardManager.let { it.isDeviceLocked || it.isKeyguardLocked }
                        && clipboardManager.canBePasted(clipboardManager.primaryClip)
                }
                KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> {
                    clipboardManager.canBePasted(clipboardManager.primaryClip)
                }
                KeyCode.CLIPBOARD_SELECT_ALL -> {
                    editorInfo.isRichInputEditor
                }
                KeyCode.TOGGLE_INCOGNITO_MODE -> when (prefs.suggestion.incognitoMode.get()) {
                    IncognitoMode.FORCE_OFF, IncognitoMode.FORCE_ON -> false
                    IncognitoMode.DYNAMIC_ON_OFF -> !editorInfo.imeOptions.flagNoPersonalizedLearning
                }
                KeyCode.LANGUAGE_SWITCH -> {
                    subtypeManager.subtypes.size > 1
                }
                else -> true
            }
        }

        override fun evaluateVisible(data: KeyData): Boolean {
            return when (data.code) {
                KeyCode.IME_UI_MODE_TEXT,
                KeyCode.IME_UI_MODE_MEDIA -> {
                    val tempUtilityKeyAction = when {
                        prefs.keyboard.utilityKeyEnabled.get() -> prefs.keyboard.utilityKeyAction.get()
                        else -> UtilityKeyAction.DISABLED
                    }
                    when (tempUtilityKeyAction) {
                        UtilityKeyAction.DISABLED,
                        UtilityKeyAction.SWITCH_LANGUAGE,
                        UtilityKeyAction.SWITCH_KEYBOARD_APP -> false
                        UtilityKeyAction.SWITCH_TO_EMOJIS -> true
                        UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS -> !shouldShowLanguageSwitch()
                    }
                }
                KeyCode.LANGUAGE_SWITCH -> {
                    val tempUtilityKeyAction = when {
                        prefs.keyboard.utilityKeyEnabled.get() -> prefs.keyboard.utilityKeyAction.get()
                        else -> UtilityKeyAction.DISABLED
                    }
                    when (tempUtilityKeyAction) {
                        UtilityKeyAction.DISABLED,
                        UtilityKeyAction.SWITCH_TO_EMOJIS -> false
                        UtilityKeyAction.SWITCH_LANGUAGE,
                        UtilityKeyAction.SWITCH_KEYBOARD_APP -> true
                        UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS -> shouldShowLanguageSwitch()
                    }
                }
                else -> true
            }
        }

        override fun isSlot(data: KeyData): Boolean {
            return CurrencySet.isCurrencySlot(data.code)
        }

        override fun slotData(data: KeyData): KeyData? {
            return subtypeManager.getCurrencySet(subtype).getSlot(data.code)
        }

        fun asSmartbarQuickActionsEvaluator(): ComputingEvaluatorImpl {
            return ComputingEvaluatorImpl(
                version = version,
                keyboard = SmartbarQuickActionsKeyboard,
                editorInfo = editorInfo,
                state = state,
                subtype = Subtype.DEFAULT,
            )
        }
    }
}
