/* Copyright (C) 2026 Victorious Keyboard Contributors
 * Licensed under the Apache License, Version 2.0. See LICENSE.
 */
package com.victorious.keyboard.app.settings.usb

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import com.victorious.keyboard.ime.usb.UsbHidMouse
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.victorious.keyboard.R
import com.victorious.keyboard.ime.usb.UsbHidKeyboard
import com.victorious.keyboard.ime.usb.UsbKey
import com.victorious.keyboard.ime.usb.UsbKeyboardController
import com.victorious.keyboard.ime.usb.UsbKeyboardController.Phase
import com.victorious.keyboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UsbKeyboardScreen() = FlorisScreen {
    title = stringRes(R.string.usb_keyboard_title)
    previewFieldVisible = false
    val context = LocalContext.current
    val scrollStep = with(LocalDensity.current) { 24.dp.toPx() }
    // Reading a small packaged script is the only startup work; root starts solely after confirmation.
    val controller = remember { UsbKeyboardController(context.assets.open("usb/keyboard.sh").bufferedReader().use { it.readText() }) }
    val state by controller.state.collectAsState()
    val owner = LocalLifecycleOwner.current
    var draft by remember { mutableStateOf("") } // Never saved or backed up.
    var modifiers by remember { mutableStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    var invalidText by remember { mutableStateOf(false) }
    var shortcutKey by remember { mutableStateOf("") }
    val active = state.phase == Phase.ACTIVE
    val running = state.phase in listOf(Phase.STARTING, Phase.ACTIVE, Phase.SENDING, Phase.STOPPING)

    DisposableEffect(owner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                draft = ""
                shortcutKey = ""
                modifiers = 0
                controller.stop()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            controller.stop(dispose = true)
        }
    }

    content {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringRes(R.string.usb_keyboard_intro))
            Text(stringRes(when (state.phase) {
                Phase.IDLE -> R.string.usb_keyboard_idle
                Phase.STARTING -> R.string.usb_keyboard_starting
                Phase.ACTIVE -> R.string.usb_keyboard_active
                Phase.SENDING -> R.string.usb_keyboard_sending
                Phase.STOPPING -> R.string.usb_keyboard_stopping
                Phase.ERROR -> when (state.error) {
                    "CONFIGFS_MISSING", "UDC_MISSING", "HID_UNSUPPORTED", "HID_NODE_MISSING" -> R.string.usb_keyboard_unsupported
                    "HOST_NOT_CONFIGURED" -> R.string.usb_keyboard_no_host
                    "RESTORE_FAILED", "STALE_GADGET" -> R.string.usb_keyboard_restore_failed
                    "ROOT_REQUIRED", "HELPER_EXITED", "TIMEOUT" -> R.string.usb_keyboard_root_error
                    "NO_ACTIVE_GADGET", "MULTIPLE_GADGETS", "USB_BUSY", "USB_BIND", "AMBIGUOUS_NODE" -> R.string.usb_keyboard_usb_busy
                    else -> R.string.usb_keyboard_error
                }
            }))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { confirm = true }, enabled = !running) { Text(stringRes(R.string.usb_keyboard_start)) }
                OutlinedButton(onClick = { modifiers = 0; controller.stop() }, enabled = running && state.phase != Phase.STOPPING) {
                    Text(stringRes(R.string.usb_keyboard_stop))
                }
            }
            Text(stringRes(R.string.usb_keyboard_touchpad_help))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Box(Modifier.fillMaxWidth().height(180.dp)
                        .pointerInput(active) {
                            if (active) detectTapGestures(
                                onTap = { controller.click(UsbHidMouse.LEFT) },
                                onLongPress = { controller.click(UsbHidMouse.RIGHT) },
                            )
                        }
                        .pointerInput(active) {
                            var remainderX = 0f
                            var remainderY = 0f
                            if (active) detectDragGestures(onDrag = { change, amount ->
                                change.consume()
                                remainderX += amount.x * 1.5f
                                remainderY += amount.y * 1.5f
                                val x = remainderX.roundToInt()
                                val y = remainderY.roundToInt()
                                remainderX -= x
                                remainderY -= y
                                controller.move(x, y)
                            })
                        }, contentAlignment = Alignment.Center) {
                        Text(stringRes(R.string.usb_keyboard_touchpad))
                    }
                }
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Box(Modifier.width(64.dp).height(180.dp).pointerInput(active, scrollStep) {
                        var remainder = 0f
                        if (active) detectVerticalDragGestures(onVerticalDrag = { change, amount ->
                            change.consume()
                            remainder -= amount / scrollStep
                            val steps = remainder.toInt()
                            if (steps != 0) { remainder -= steps; controller.move(wheel = steps) }
                        })
                    }, contentAlignment = Alignment.Center) { Text(stringRes(R.string.usb_keyboard_scroll)) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, button) in listOf(R.string.usb_keyboard_left_click to UsbHidMouse.LEFT,
                    R.string.usb_keyboard_right_click to UsbHidMouse.RIGHT, R.string.usb_keyboard_middle_click to UsbHidMouse.MIDDLE)) {
                    OutlinedButton(onClick = { controller.click(button) }, enabled = active) { Text(stringRes(label)) }
                }
                OutlinedButton(onClick = { controller.move(wheel = 3) }, enabled = active) { Text(stringRes(R.string.usb_keyboard_scroll_up)) }
                OutlinedButton(onClick = { controller.move(wheel = -3) }, enabled = active) { Text(stringRes(R.string.usb_keyboard_scroll_down)) }
            }
            Text(stringRes(R.string.usb_keyboard_text_help))
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= UsbHidKeyboard.MAX_TEXT) { draft = it; invalidText = false } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringRes(R.string.usb_keyboard_draft)) },
                enabled = active,
                isError = invalidText,
                supportingText = { Text(stringRes(if (invalidText) R.string.usb_keyboard_ascii_only else R.string.usb_keyboard_text_limit)) },
                maxLines = 4,
            )
            Button(onClick = {
                // Validate everything first; the draft is cleared only once it was actually handed over,
                // and a failed transmission is never retried automatically.
                if (!UsbHidKeyboard.isSupported(draft)) invalidText = true
                else if (controller.sendText(draft)) draft = ""
            }, enabled = active && draft.isNotEmpty()) { Text(stringRes(R.string.usb_keyboard_send)) }
            Text(stringRes(R.string.usb_keyboard_keys_help))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, bit) in listOf("Ctrl" to UsbHidKeyboard.CTRL, "Shift" to UsbHidKeyboard.SHIFT,
                    "Alt" to UsbHidKeyboard.ALT, "Super" to UsbHidKeyboard.SUPER)) {
                    FilterChip(selected = modifiers and bit != 0, onClick = { modifiers = modifiers xor bit },
                        enabled = active, label = { Text(label) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (key in UsbKey.entries) {
                    OutlinedButton(onClick = { controller.key(key, modifiers); modifiers = 0 }, enabled = active) {
                        Text(when (key) {
                            UsbKey.PAGE_UP -> "PgUp"
                            UsbKey.PAGE_DOWN -> "PgDn"
                            else -> key.name.lowercase().replaceFirstChar { it.uppercase() }
                        })
                    }
                }
            }
            Text(stringRes(R.string.usb_keyboard_shortcut_help))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = shortcutKey,
                    onValueChange = { shortcutKey = it.takeLast(1) },
                    modifier = Modifier.width(96.dp),
                    label = { Text(stringRes(R.string.usb_keyboard_shortcut_key)) },
                    enabled = active,
                    singleLine = true,
                )
                Button(onClick = {
                    val key = shortcutKey.firstOrNull() ?: return@Button
                    if (runCatching { UsbHidKeyboard.chord(key, modifiers) }.isSuccess) {
                        controller.chord(key, modifiers)
                        modifiers = 0
                    } else invalidText = true
                }, enabled = active && shortcutKey.isNotEmpty()) { Text(stringRes(R.string.usb_keyboard_shortcut_send)) }
                OutlinedButton(onClick = { controller.modifierTap(modifiers); modifiers = 0 }, enabled = active && modifiers != 0) {
                    Text(stringRes(R.string.usb_keyboard_modifier_tap))
                }
            }
            OutlinedButton(onClick = { modifiers = 0; controller.release() }, enabled = active) {
                Text(stringRes(R.string.usb_keyboard_release))
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringRes(R.string.usb_keyboard_start)) },
        text = { Text(stringRes(R.string.usb_keyboard_confirm)) },
        confirmButton = { TextButton(onClick = { confirm = false; controller.start() }) { Text(stringRes(R.string.usb_keyboard_start)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringRes(R.string.action__cancel)) } },
    )
}
