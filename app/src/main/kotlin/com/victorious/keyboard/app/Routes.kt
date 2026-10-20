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

package com.victorious.keyboard.app

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.victorious.keyboard.app.devtools.AndroidLocalesScreen
import com.victorious.keyboard.app.devtools.AndroidSettingsScreen
import com.victorious.keyboard.app.devtools.DevtoolsScreen
import com.victorious.keyboard.app.devtools.ExportDebugLogScreen
import com.victorious.keyboard.app.ext.CheckUpdatesScreen
import com.victorious.keyboard.app.ext.ExtensionEditScreen
import com.victorious.keyboard.app.ext.ExtensionExportScreen
import com.victorious.keyboard.app.ext.ExtensionHomeScreen
import com.victorious.keyboard.app.ext.ExtensionImportScreen
import com.victorious.keyboard.app.ext.ExtensionImportScreenType
import com.victorious.keyboard.app.ext.ExtensionListScreen
import com.victorious.keyboard.app.ext.ExtensionListScreenType
import com.victorious.keyboard.app.ext.ExtensionViewScreen
import com.victorious.keyboard.app.settings.HomeScreen
import com.victorious.keyboard.app.settings.about.AboutScreen
import com.victorious.keyboard.app.settings.about.ProjectLicenseScreen
import com.victorious.keyboard.app.settings.about.ThirdPartyLicensesScreen
import com.victorious.keyboard.app.settings.advanced.BackupScreen
import com.victorious.keyboard.app.settings.advanced.OtherScreen
import com.victorious.keyboard.app.settings.advanced.PhysicalKeyboardScreen
import com.victorious.keyboard.app.settings.advanced.RestoreScreen
import com.victorious.keyboard.app.settings.clipboard.ClipboardBackupScreen
import com.victorious.keyboard.app.settings.clipboard.ClipboardZipScreen
import com.victorious.keyboard.app.settings.clipboard.ClipboardManagerScreen
import com.victorious.keyboard.app.settings.keyboard.AdvancedDeveloperScreen
import com.victorious.keyboard.app.settings.snippets.SnippetsScreen
import com.victorious.keyboard.app.settings.snippets.SnippetExpansionScreen
import com.victorious.keyboard.app.settings.clipboard.ClipboardScreen
import com.victorious.keyboard.app.settings.dictionary.DictionaryPacksScreen
import com.victorious.keyboard.app.settings.dictionary.DictionaryScreen
import com.victorious.keyboard.app.settings.dictionary.UserDictionaryScreen
import com.victorious.keyboard.app.settings.dictionary.UserDictionaryType
import com.victorious.keyboard.app.settings.gestures.GesturesScreen
import com.victorious.keyboard.app.settings.keyboard.InputFeedbackScreen
import com.victorious.keyboard.app.settings.keyboard.DeveloperScreen
import com.victorious.keyboard.app.settings.keyboard.KeyboardScreen
import com.victorious.keyboard.app.settings.localization.LanguagePackManagerScreen
import com.victorious.keyboard.app.settings.localization.LanguagePackManagerScreenAction
import com.victorious.keyboard.app.settings.localization.LocalizationScreen
import com.victorious.keyboard.app.settings.localization.SelectLocaleScreen
import com.victorious.keyboard.app.settings.localization.SubtypeEditorScreen
import com.victorious.keyboard.app.settings.manual.ManualScreen
import com.victorious.keyboard.app.settings.media.MediaScreen
import com.victorious.keyboard.app.settings.smartbar.SmartbarScreen
import com.victorious.keyboard.app.settings.smartbar.ToolbarSettingsScreen
import com.victorious.keyboard.app.settings.theme.KeyboardColorsScreen
import com.victorious.keyboard.app.settings.theme.ThemeManagerScreen
import com.victorious.keyboard.app.settings.theme.ThemeManagerScreenAction
import com.victorious.keyboard.app.settings.theme.ThemeScreen
import com.victorious.keyboard.app.settings.localai.LocalAiScreen
import com.victorious.keyboard.app.settings.fleet.FleetScreen
import com.victorious.keyboard.app.settings.screenshot.ScreenshotScreen
import com.victorious.keyboard.app.settings.slash.SlashCommandsScreen
import com.victorious.keyboard.app.settings.suggestions.MySuggestionsScreen
import com.victorious.keyboard.app.settings.typing.TypingScreen
import com.victorious.keyboard.app.setup.SetupScreen
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Deeplink(val path: String)

inline fun <reified T : Any> NavGraphBuilder.composableWithDeepLink(
    kClass: KClass<T>,
    noinline content: @Composable (AnimatedContentScope.(NavBackStackEntry) -> Unit),
) {
    val deeplink = requireNotNull(kClass.annotations.firstOrNull { it is Deeplink } as? Deeplink) {
        "faulty class: $kClass with annotations ${kClass.annotations}"
    }
    composable<T>(
        deepLinks = listOf(navDeepLink<T>(basePath = "ui://florisboard/${deeplink.path}")),
        content = content,
    )
}

object Routes {
    object Setup {
        @Serializable
        object Screen
    }

    object Settings {
        @Serializable
        @Deeplink("settings/home")
        object Home

        @Serializable
        @Deeplink("settings/localization")
        object Localization

        @Serializable
        @Deeplink("settings/localization/select-locale")
        object SelectLocale

        @Serializable
        @Deeplink("settings/localization/language-pack-manage")
        data class LanguagePackManager(val action: LanguagePackManagerScreenAction)

        @Serializable
        @Deeplink("settings/localization/subtype/add")
        object SubtypeAdd

        @Serializable
        @Deeplink("settings/localization/subtype/edit")
        data class SubtypeEdit(val id: Long)

        @Serializable
        @Deeplink("settings/theme")
        object Theme

        @Serializable
        @Deeplink("settings/theme/manage")
        data class ThemeManager(val action: ThemeManagerScreenAction)

        @Serializable
        @Deeplink("settings/theme/colors")
        object KeyboardColors

        @Serializable
        @Deeplink("settings/keyboard")
        object Keyboard

        @Serializable
        @Deeplink("settings/keyboard/input-feedback")
        object InputFeedback

        @Serializable
        @Deeplink("settings/smartbar")
        object Smartbar

        @Serializable
        @Deeplink("settings/smartbar/toolbar")
        object Toolbar

        @Serializable
        @Deeplink("settings/typing")
        object Typing

        @Serializable
        @Deeplink("settings/local-ai")
        object LocalAi

        @Serializable
        @Deeplink("settings/dictionary")
        object Dictionary

        @Serializable
        @Deeplink("settings/dictionary/user-dictionary")
        data class UserDictionary(val type: UserDictionaryType)

        @Serializable
        @Deeplink("settings/dictionary/packs")
        object DictionaryPacks

        @Serializable
        @Deeplink("settings/gestures")
        object Gestures

        @Serializable
        @Deeplink("settings/clipboard")
        object Clipboard

        @Serializable
        @Deeplink("settings/clipboard/manager")
        object ClipboardManager

        @Serializable
        @Deeplink("settings/clipboard/backup")
        object ClipboardBackup

        @Serializable
        @Deeplink("settings/clipboard/zip")
        object ClipboardZip

        @Serializable
        @Deeplink("settings/snippets")
        object Snippets

        @Serializable
        @Deeplink("settings/snippets/expansion")
        object SnippetExpansion

        @Serializable
        @Deeplink("settings/developer")
        object Developer

        @Serializable
        @Deeplink("settings/keyboard/developer-advanced")
        object AdvancedDeveloper

        @Serializable
        @Deeplink("settings/media")
        object Media

        @Serializable
        @Deeplink("settings/other")
        object Other

        @Serializable
        @Deeplink("settings/other/physical-keyboard")
        object PhysicalKeyboard

        @Serializable
        @Deeplink("settings/other/backup")
        object Backup

        @Serializable
        @Deeplink("settings/other/restore")
        object Restore

        @Serializable
        @Deeplink("settings/manual")
        object Manual

        @Serializable
        @Deeplink("settings/about")
        object About

        @Serializable
        @Deeplink("settings/about/project-license")
        object ProjectLicense

        @Serializable
        @Deeplink("settings/about/third-party-licenses")
        object ThirdPartyLicenses

        @Serializable
        @Deeplink("settings/suggestions")
        object MySuggestions

        @Serializable
        @Deeplink("settings/slash")
        object SlashCommands

        @Serializable
        @Deeplink("settings/fleet")
        object Fleet

        @Serializable
        @Deeplink("settings/screenshot")
        object Screenshot
    }

    object Devtools {
        @Serializable
        @Deeplink("devtools")
        object Home

        @Serializable
        @Deeplink("devtools/android/locales")
        object AndroidLocales

        @Serializable
        @Deeplink("devtools/android/settings")
        data class AndroidSettings(val name: String)

        @Serializable
        @Deeplink("export-debug-log")
        object ExportDebugLog
    }

    object Ext {
        @Serializable
        @Deeplink("ext")
        object Home

        @Serializable
        @Deeplink("ext/list")
        data class List(val type: ExtensionListScreenType, val showUpdate: Boolean? = null)

        @Serializable
        @Deeplink("ext/edit")
        data class Edit(val id: String, @SerialName("create") val serialType: String? = null)

        @Serializable
        @Deeplink("ext/export")
        data class Export(val id: String)

        @Serializable
        @Deeplink("ext/import")
        data class Import(val type: ExtensionImportScreenType, val uuid: String? = null)

        @Serializable
        @Deeplink("ext/view")
        data class View(val id: String)

        @Serializable
        @Deeplink("ext/check-updates")
        object CheckUpdates
    }

    @Composable
    fun AppNavHost(
        modifier: Modifier,
        navController: NavHostController,
        startDestination: KClass<*>,
    ) {
        NavHost(
            modifier = modifier,
            navController = navController,
            startDestination = startDestination,
            enterTransition = {
                slideIn { IntOffset(it.width, 0) } + fadeIn()
            },
            exitTransition = {
                slideOut { IntOffset(-it.width, 0) } + fadeOut()
            },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = {
                scaleOut(
                    targetScale = 0.85F,
                    transformOrigin = TransformOrigin(pivotFractionX = 0.8f, pivotFractionY = 0.5f)
                ) + fadeOut(spring(stiffness = Spring.StiffnessMedium))
            },
        ) {
            composable<Setup.Screen> { SetupScreen() }

            composableWithDeepLink(Settings.Home::class) { HomeScreen() }

            composableWithDeepLink(Settings.Localization::class) { LocalizationScreen() }
            composableWithDeepLink(Settings.SelectLocale::class) { SelectLocaleScreen() }
            composableWithDeepLink(Settings.LanguagePackManager::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.LanguagePackManager>()
                LanguagePackManagerScreen(payload.action)
            }
            composableWithDeepLink(Settings.SubtypeAdd::class) { SubtypeEditorScreen(null) }
            composableWithDeepLink(Settings.SubtypeEdit::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.SubtypeEdit>()
                SubtypeEditorScreen(payload.id)
            }

            composableWithDeepLink(Settings.Theme::class) { ThemeScreen() }
            composableWithDeepLink(Settings.KeyboardColors::class) { KeyboardColorsScreen() }
            composableWithDeepLink(Settings.ThemeManager::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.ThemeManager>()
                ThemeManagerScreen(payload.action)
            }

            composableWithDeepLink(Settings.Keyboard::class) { KeyboardScreen() }
            composableWithDeepLink(Settings.InputFeedback::class) { InputFeedbackScreen() }

            composableWithDeepLink(Settings.Smartbar::class) { SmartbarScreen() }
            composableWithDeepLink(Settings.Toolbar::class) { ToolbarSettingsScreen() }

            composableWithDeepLink(Settings.Typing::class) { TypingScreen() }
            composableWithDeepLink(Settings.MySuggestions::class) { MySuggestionsScreen() }
            composableWithDeepLink(Settings.SlashCommands::class) { SlashCommandsScreen() }
            composableWithDeepLink(Settings.Fleet::class) { FleetScreen() }
            composableWithDeepLink(Settings.LocalAi::class) { LocalAiScreen() }
            composableWithDeepLink(Settings.Screenshot::class) { ScreenshotScreen() }

            composableWithDeepLink(Settings.Dictionary::class) { DictionaryScreen() }
            composableWithDeepLink(Settings.DictionaryPacks::class) { DictionaryPacksScreen() }
            composableWithDeepLink(Settings.UserDictionary::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.UserDictionary>()
                UserDictionaryScreen(payload.type)
            }

            composableWithDeepLink(Settings.Gestures::class) { GesturesScreen() }

            composableWithDeepLink(Settings.Clipboard::class) { ClipboardScreen() }
            composableWithDeepLink(Settings.ClipboardManager::class) { ClipboardManagerScreen() }
            composableWithDeepLink(Settings.ClipboardBackup::class) { ClipboardBackupScreen() }
            composableWithDeepLink(Settings.ClipboardZip::class) { ClipboardZipScreen() }
            composableWithDeepLink(Settings.Snippets::class) { SnippetsScreen() }
            composableWithDeepLink(Settings.SnippetExpansion::class) { SnippetExpansionScreen() }
            composableWithDeepLink(Settings.Developer::class) { DeveloperScreen() }
            composableWithDeepLink(Settings.AdvancedDeveloper::class) { AdvancedDeveloperScreen() }

            composableWithDeepLink(Settings.Media::class) { MediaScreen() }

            composableWithDeepLink(Settings.Other::class) { OtherScreen() }
            composableWithDeepLink(Settings.PhysicalKeyboard::class) { PhysicalKeyboardScreen() }
            composableWithDeepLink(Settings.Backup::class) { BackupScreen() }
            composableWithDeepLink(Settings.Restore::class) { RestoreScreen() }

            composableWithDeepLink(Settings.Manual::class) { ManualScreen() }
            composableWithDeepLink(Settings.About::class) { AboutScreen() }
            composableWithDeepLink(Settings.ProjectLicense::class) { ProjectLicenseScreen() }
            composableWithDeepLink(Settings.ThirdPartyLicenses::class) { ThirdPartyLicensesScreen() }

            composableWithDeepLink(Devtools.Home::class) { DevtoolsScreen() }
            composableWithDeepLink(Devtools.AndroidLocales::class) { AndroidLocalesScreen() }
            composableWithDeepLink(Devtools.AndroidSettings::class) { navBackStack ->
                val payload = navBackStack.toRoute<Devtools.AndroidSettings>()
                AndroidSettingsScreen(payload.name)
            }
            composableWithDeepLink(Devtools.ExportDebugLog::class) { ExportDebugLogScreen() }

            composableWithDeepLink(Ext.Home::class) { ExtensionHomeScreen() }
            composableWithDeepLink(Ext.List::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.List>()
                val showUpdate = payload.showUpdate != null && payload.showUpdate
                ExtensionListScreen(payload.type, showUpdate)
            }
            composableWithDeepLink(Ext.Edit::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.Edit>()
                val extensionId = payload.id
                val serialType = payload.serialType
                ExtensionEditScreen(
                    id = extensionId,
                    createSerialType = serialType.takeIf { !it.isNullOrBlank() },
                )
            }
            composableWithDeepLink(Ext.Export::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.Export>()
                val extensionId = payload.id
                ExtensionExportScreen(id = extensionId)
            }
            composableWithDeepLink(Ext.Import::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.Import>()
                val uuid = payload.uuid
                ExtensionImportScreen(payload.type, uuid)
            }
            composableWithDeepLink(Ext.View::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.View>()
                val extensionId = payload.id
                ExtensionViewScreen(id = extensionId)
            }
            composableWithDeepLink(Ext.CheckUpdates::class) {
                CheckUpdatesScreen()
            }
        }
    }
}
