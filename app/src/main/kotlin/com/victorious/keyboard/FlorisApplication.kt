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

package com.victorious.keyboard

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import androidx.core.os.UserManagerCompat
import com.victorious.keyboard.app.FlorisPreferenceModel
import com.victorious.keyboard.app.FlorisPreferenceStore
import com.victorious.keyboard.ime.clipboard.ClipboardManager
import com.victorious.keyboard.ime.snippets.SnippetManager
import com.victorious.keyboard.ime.core.SubtypeManager
import com.victorious.keyboard.ime.dictionary.DictionaryManager
import com.victorious.keyboard.ime.editor.EditorInstance
import com.victorious.keyboard.ime.keyboard.KeyboardManager
import com.victorious.keyboard.ime.media.emoji.FlorisEmojiCompat
import com.victorious.keyboard.ime.nlp.NlpManager
import com.victorious.keyboard.ime.text.gestures.GlideTypingManager
import com.victorious.keyboard.ime.theme.ThemeManager
import com.victorious.keyboard.lib.cache.CacheManager
import com.victorious.keyboard.lib.crashutility.CrashUtility
import com.victorious.keyboard.lib.devtools.Flog
import com.victorious.keyboard.lib.devtools.LogTopic
import com.victorious.keyboard.lib.devtools.flogError
import com.victorious.keyboard.lib.ext.ExtensionManager
import dev.patrickgold.jetpref.datastore.runtime.initAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.florisboard.lib.kotlin.io.deleteContentsRecursively
import org.florisboard.lib.kotlin.tryOrNull
import java.lang.ref.WeakReference

/**
 * Global weak reference for the [FlorisApplication] class. This is needed as in certain scenarios an application
 * reference is needed, but the Android framework hasn't finished setting up
 */
private var FlorisApplicationReference = WeakReference<FlorisApplication?>(null)

@Suppress("unused")
class FlorisApplication : Application() {
    private val mainHandler by lazy { Handler(mainLooper) }
    private val scope = CoroutineScope(Dispatchers.Default)
    val preferenceStoreLoaded = MutableStateFlow(false)

    val cacheManager = lazy { CacheManager(this) }
    val clipboardManager = lazy { ClipboardManager(this) }
    val editorInstance = lazy { EditorInstance(this) }
    val extensionManager = lazy { ExtensionManager(this) }
    val glideTypingManager = lazy { GlideTypingManager(this) }
    val snippetManager = lazy { SnippetManager(this) }
    val customSuggestions = lazy { com.victorious.keyboard.ime.nlp.custom.CustomSuggestionManager(this) }
    val slashCommands = lazy { com.victorious.keyboard.ime.slash.SlashCommandManager(this) }
    val fleetHub = lazy { com.victorious.keyboard.ime.fleet.FleetHubClient(this) }
    val keyboardManager = lazy { KeyboardManager(this) }
    val localAi = lazy { com.victorious.keyboard.ime.ai.LocalAiManager(this) }
    val nlpManager = lazy { NlpManager(this) }
    val subtypeManager = lazy { SubtypeManager(this) }
    val themeManager = lazy { ThemeManager(this) }

    override fun onCreate() {
        super.onCreate()
        FlorisApplicationReference = WeakReference(this)
        try {
            Flog.install(
                context = this,
                isFloggingEnabled = BuildConfig.DEBUG,
                flogTopics = LogTopic.ALL,
                flogLevels = Flog.LEVEL_ALL,
                flogOutputs = Flog.OUTPUT_CONSOLE,
            )
            CrashUtility.install(this)
            FlorisEmojiCompat.init(this)

            if (!UserManagerCompat.isUserUnlocked(this)) {
                cacheDir?.deleteContentsRecursively()
                extensionManager.value.init()
                registerReceiver(BootComplete(), IntentFilter(Intent.ACTION_USER_UNLOCKED))
                return
            }

            init()
        } catch (e: Exception) {
            CrashUtility.stageException(e)
            return
        }
    }

    fun init() {
        cacheDir?.deleteContentsRecursively()
        scope.launch {
            val result = FlorisPreferenceStore.initAndroid(
                context = this@FlorisApplication,
                datastoreName = FlorisPreferenceModel.NAME,
            )
            Log.i("PREFS", result.toString())
            preferenceStoreLoaded.value = true
        }
        extensionManager.value.init()
        clipboardManager.value.initializeForContext(this)
        snippetManager.value.ensureLoaded() // loads the snippet file off the main thread (also after a Direct Boot start)
        customSuggestions.value
        slashCommands.value
        fleetHub.value.start()
        localAi.value
        DictionaryManager.init(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (localAi.isInitialized()) localAi.value.onTrimMemory(level)
    }

    private inner class BootComplete : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action == Intent.ACTION_USER_UNLOCKED) {
                try {
                    unregisterReceiver(this)
                } catch (e: Exception) {
                    flogError { e.toString() }
                }
                mainHandler.post { init() }
            }
        }
    }
}

private tailrec fun Context.florisApplication(): FlorisApplication {
    return when (this) {
        is FlorisApplication -> this
        is ContextWrapper -> when {
            this.baseContext != null -> this.baseContext.florisApplication()
            else -> FlorisApplicationReference.get()!!
        }
        else -> tryOrNull { this.applicationContext as FlorisApplication } ?: FlorisApplicationReference.get()!!
    }
}

fun Context.appContext() = lazyOf(this.florisApplication())

fun Context.cacheManager() = this.florisApplication().cacheManager

fun Context.clipboardManager() = this.florisApplication().clipboardManager

fun Context.editorInstance() = this.florisApplication().editorInstance

fun Context.extensionManager() = this.florisApplication().extensionManager

fun Context.glideTypingManager() = this.florisApplication().glideTypingManager

fun Context.keyboardManager() = this.florisApplication().keyboardManager

fun Context.localAiManager() = this.florisApplication().localAi

fun Context.nlpManager() = this.florisApplication().nlpManager

fun Context.snippetManager() = this.florisApplication().snippetManager

fun Context.customSuggestions() = this.florisApplication().customSuggestions

fun Context.slashCommands() = this.florisApplication().slashCommands

fun Context.fleetHub() = this.florisApplication().fleetHub

fun Context.subtypeManager() = this.florisApplication().subtypeManager

fun Context.themeManager() = this.florisApplication().themeManager
