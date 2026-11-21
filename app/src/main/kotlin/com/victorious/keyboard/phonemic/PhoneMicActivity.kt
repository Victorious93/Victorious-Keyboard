/*
 * Copyright (C) 2026 Victorious Keyboard Contributors
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

package com.victorious.keyboard.phonemic

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * PC microphone: a normal visible screen (never the keyboard window) where the owner pastes the offer line from the
 * PC, grants microphone access and starts, mutes or stops sharing. It is separate from the keyboard's ordinary voice
 * typing and from USB keyboard/mouse mode: starting it does not change the default keyboard or touch ConfigFS.
 * COMPILES only after a build; NEVER RUN on a device. Typed text, surrounding text and the clipboard are not read here.
 */
class PhoneMicActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { PhoneMicPanel(Modifier.padding(16.dp)) } } }
    }

    companion object {
        fun launch(ctx: Context) = ctx.startActivity(Intent(ctx, PhoneMicActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun PhoneMicPanel(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val status by PhoneMicBus.status.collectAsState()
    var offerText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun begin() {
        val o = MicOffer.parse(offerText)
        if (o == null) { error = "That is not a valid offer. Copy the whole line from the PC."; return }
        if (o.expired(System.currentTimeMillis() / 1000)) { error = "That offer has expired. Create a new one on the PC."; return }
        error = null
        PhoneMicService.start(ctx, offerText.trim())
        offerText = ""
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) begin() else error = "Microphone access is off. Allow it in Android Settings → Apps → Victorious Keyboard → Permissions."
    }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { perm.launch(Manifest.permission.RECORD_AUDIO) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PC microphone", style = MaterialTheme.typography.titleLarge)
        val active = status.state in listOf(ShareState.CONNECTING, ShareState.SHARING, ShareState.MUTED, ShareState.SILENCED)
        if (active) {
            Text(when (status.state) {
                ShareState.CONNECTING -> "Connecting to ${status.pcName}…"
                ShareState.MUTED -> "Muted. ${status.pcName} hears nothing."
                ShareState.SILENCED -> "Android is silencing this microphone (a call, another app, or the privacy switch). ${status.pcName} hears nothing."
                else -> "Your microphone is being sent to ${status.pcName}."
            })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { PhoneMicService.mute(ctx) }) { Text(if (status.state == ShareState.MUTED) "Unmute" else "Mute") }
                Button(onClick = { PhoneMicService.stop(ctx) }) { Text("Stop sharing") }
            }
        } else {
            Text("On the PC run `victor-mic serve --offer NAME:ID:keyboard` and paste the line it prints. Audio goes only to that PC, only while sharing is shown here and in the notification. Nothing you type is sent.")
            OutlinedTextField(offerText, { offerText = it }, label = { Text("Offer from the PC") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                if (android.os.Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) else perm.launch(Manifest.permission.RECORD_AUDIO)
            }, modifier = Modifier.fillMaxWidth()) { Text("Start PC microphone") }
        }
        (error ?: status.note.takeIf { it.isNotBlank() && !active })?.let { Text(it) }
    }
}
