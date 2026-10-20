# Victorious Keyboard

![Neon ring](manual/header.svg)

This is the user manual. It is stored inside the app, so you can read it with the internet turned off. Open it from Settings → Help.

The pictures below are empty slots. Each one is marked TODO. No phone was available to photograph the screens, so those files are not pictures.

## What Victorious Keyboard is

Victorious Keyboard is the keyboard you type with on this phone. It is made for everyday writing, and it also has tools for people who use a terminal, snippets, and a clipboard.

It belongs to the Droidcommand fleet as the phone's keyboard. The fleet is the set of apps on this phone that can offer extra commands. The keyboard still works on its own if you never link any of them. It does not need the internet to type, to suggest words, or to talk to a fleet app that is already installed.

The look you get at first is a squared black keyboard: a near-black background, dark keys, light letters, and only slightly rounded corners.

## Install and first run

![Enable the keyboard](manual/img/enable-keyboard.txt)

TODO: photograph the Android screen where Victorious Keyboard is switched on. `docs/manual/img/enable-keyboard.txt` is a placeholder, not a picture.

1. Install the app and open it. The first screen is a short setup.
2. Tap the button that opens Android's keyboard list. Turn Victorious Keyboard on. Android asks for this because a keyboard can see what you type.
3. Come back to the app. Tap the button that lets you pick the keyboard you are using, and choose Victorious Keyboard.

![Choose the keyboard](manual/img/choose-keyboard.txt)

TODO: photograph the input-method picker with Victorious Keyboard selected. `docs/manual/img/choose-keyboard.txt` is a placeholder, not a picture.

4. On Android 13 and newer, setup can ask to show notifications. That permission is only for notifications from this app. The rest of the keyboard works if you leave it off.
5. When setup finishes, you land on Settings. The same screens are there later: open the app from the launcher any time.

If the keyboard does not appear in other apps, open Settings again. A message at the top tells you whether the keyboard is turned off in Android, or turned on but not chosen. Tap that message to fix it.

### Linking the Droidcommand hub

This step is optional. Skip it if you only want a keyboard.

![Fleet link](manual/img/fleet-link.txt)

TODO: photograph Settings, Fleet link, with Hub status off. `docs/manual/img/fleet-link.txt` is a placeholder, not a picture.

1. Open Settings → Tools → Fleet link.
2. Leave Hub status off unless you want the Droidcommand hub to talk to this keyboard. It starts off.
3. To turn the bridge on, switch Hub status on. The hub can then ask whether the keyboard is running. With that same switch it can also type into the box you are using and press keys such as Enter. It cannot read that box. What you type is not sent to the hub.
4. Switch Hub status off again any time. The keyboard keeps working.

## Features

Each part below is something the app actually has. Steps use the names you see on screen.

### Suggestion strip

![Suggestion strip](manual/img/suggestion-strip.txt)

TODO: photograph the suggestion strip and the tools button. `docs/manual/img/suggestion-strip.txt` is a placeholder, not a picture.

The row above the keys shows word suggestions while you type.

1. Start typing a word. Suggestions appear in that row. Swipe the row sideways when there are more than fit.
2. The first suggestion is underlined. Tap a suggestion to use it.
3. Word prediction can offer the word you are typing, a guess for the next word, and a close spelling. A word that is already in the dictionary, in your own dictionary, or in words the keyboard has learned is left as you typed it.
4. Settings → Typing → Word prediction turns this on or off. It starts on. Autocorrect can be Off, Mild, or Normal. Backspace right after a replacement puts your typing back.
5. A period typed between letters, with no space, stays inside the word. `word.word`, a file name, a domain, an email, a URL, a version such as `1.2.3`, and code are not given an automatic space, a capital letter, or a spelling replacement. A period followed by a space still starts a new sentence, and the next word can be capitalized.
6. Learn from my typing starts off. While it is off, new words are not saved. While it is on, new words stay on this phone only. Passwords, Incognito, and boxes that ask for no personalised learning are skipped.
7. Settings → Typing → My suggestions stores your own phrases. Long-press a suggestion, or text you have selected, to add it. An optional shortcut shows the phrase when you type that shortcut. Long-press a saved phrase to delete it.
8. When you are not in the middle of a word, a recent clipboard chip can still show in the strip. Tap it to paste that clip.
9. The button on the left of the strip opens the tools panel. Long-press that button to open the tools settings. Up to four pinned tools can sit on the strip as small icons next to that button. Pinning nothing leaves the strip as suggestions and the tools button.

Suggestions stay off in password boxes and in boxes that ask for no suggestions.

### Tools panel

![Tools panel](manual/img/tools-panel.txt)

TODO: photograph the tools panel tabs. `docs/manual/img/tools-panel.txt` is a placeholder, not a picture.

1. Tap the tools button on the left of the suggestion strip. The panel replaces the keys. The app behind the keyboard does not jump, because the panel uses the same height as the keys plus the terminal row.
2. Tap a tab: Clipboard, AI, Terminal, Code, or Quick. A Fleet tab appears beside them when a fleet app you trust is connected.
3. Each tile is an icon with a name. Switches such as Incognito show On or Off.
4. Tap the same tools button, or the back control, to return to the keys.
5. Settings → Smartbar → Tools panel, also opened by a long-press on the tools button, is where the panel is arranged.
   - Show or hide each tile.
   - Reorder tiles by dragging the handle, or with Earlier and Later.
   - Pin up to four tools. They appear as small icons on the suggestion strip, next to the tools button. A pin does what its tile does. Pinning nothing keeps the strip as it is.
   - Choose the tile size (fit the panel, small, medium, or large) and the number of columns (fit the width, or 2 to 5).
   - Reset layout puts the tiles, order, pins, size, and columns back. Your saved commands and shell history stay.
   - A slash command you created, and a fleet action the keyboard has seen, can be a tile on the Quick tab or a pin. A slash pin inserts the command. A fleet pin asks that app. Neither is run as a shell command.
   - Your own commands on the Terminal tab are inserted as text. They are not run.

Clipboard tab:

- History opens the clipboard list.
- Paste, Copy, Cut, Select all, Undo, and Redo act on the box you are typing in.
- Snippets opens the snippet picker.
- Clip editor opens the latest clip in the editor.
- Paste pinned inserts pinned clips.
- Vault opens the sensitive list.
- ZIP backup opens the clipboard ZIP screen in Settings.

AI tab: Ask, Rewrite, Fix grammar, Continue, Summarize, Translate, and Shell. These need a model you imported. Shell inserts one command and does not run it. See Local AI below.

Terminal tab: Ctrl, Alt, Esc, Tab, the arrow keys, Home, End, Page up, Page down, and Ctrl+C, Ctrl+D, Ctrl+Z, Ctrl+L, Ctrl+R. Those keys are real key presses. Key row cycles the terminal row between Auto, Always, and Never. Your own commands, if you added any, are listed here and inserted as text.

Code tab, used on text you have selected: Camel, Snake, Kebab, Pascal, Indent, Outdent, Comment, JSON, Base64 encode, Base64 decode, UUID, and Time.

Quick tab: Incognito, Learn words, Emoji, Voice input, One-handed, Bottom gap, Next colors, Settings, Screenshot, and Media.

- Voice input switches to a voice keyboard Android already has, if one is installed. This app does not record the microphone itself.
- Next colors steps through the keyboard color presets.
- Bottom gap cycles the extra space under the keys.

### Clipboard

![Clipboard search](manual/img/clipboard.txt)

TODO: photograph clipboard search with a pinned clip. `docs/manual/img/clipboard.txt` is a placeholder, not a picture.

Clipboard history starts off.

1. Open Settings → Tools → Clipboard and turn on Enable clipboard history. You can also turn it on from the clipboard panel when it offers that button.
2. Copy text in any app. It is kept on this phone, encrypted, up to 500 items. Pinned items are kept when the list fills up.
3. On the keyboard, open the tools panel → Clipboard → History. The panel has a Clipboard tab and a Vault tab.
4. Tap Search. While search is on, the keys type into the search line. They are not sent to the app behind the keyboard.
5. Tap Pinned to show only pinned clips. Tap Favorites to show only favorites. Tap Clear to reset the search and those two filters.
6. Long-press a clip for Pin, Favorite, Edit, and Delete. Pin keeps it. Favorite marks it so the Favorites filter can find it.
7. Text that looks like a password, an API key, or a card number is not saved to the regular list unless you turn on Save passwords and keys. That detection can miss some secrets and can flag ordinary text.
8. Clear history is on the clipboard panel. It removes the regular history you are looking at.

The search text is not written to a log.

### Clip editor

![Clip editor](manual/img/clip-editor.txt)

TODO: photograph the clip editor. `docs/manual/img/clip-editor.txt` is a placeholder, not a picture.

1. Open a clip from the clipboard list and choose Edit, or tap Clip editor on the Clipboard tab. Clip editor opens the latest clip.
2. Change the text. Find and Replace are separate lines in the editor.
3. Save updates that clip. Save as new adds another clip.
4. A clip that came from the Vault is saved back to the Vault. It is not copied into the regular history.
5. You can also open the editor with the slash command `/edit`.

### Vault

![Vault](manual/img/vault.txt)

TODO: photograph the Vault tab with items masked. `docs/manual/img/vault.txt` is a placeholder, not a picture.

The Vault is a second list for sensitive clips. It is separate from the regular clipboard.

1. Open the clipboard panel and choose the Vault tab, or tap Vault on the Clipboard tab of the tools panel.
2. A copy you make in a password box, and a clip Android marks as sensitive, goes to the Vault when clipboard history is on.
3. Settings → Tools → Clipboard → Detect secrets into the vault starts off. When you turn it on, text that looks like a card number, an IBAN, an API key, a private key, or a one-time code is saved in the Vault instead of the regular list. The match is not perfect.
4. Vault text is masked until you reveal it. Viewing it asks for the phone's screen lock. That lock is on by default, and the Vault locks again after a timeout.
5. Unpinned vault items expire after 24 hours unless you pick another period, or Never, in the clipboard settings. Pinned vault items stay.
6. Vault text is kept out of the suggestion strip.
7. You can move an item between the regular list and the Vault.
8. `/vault` unlocks the Vault from the keyboard.

### Snippets

![Snippets](manual/img/snippets.txt)

TODO: photograph the snippet editor with a fill-in. `docs/manual/img/snippets.txt` is a placeholder, not a picture.

A snippet is a piece of text you reuse. You can keep up to 500.

1. Open Settings → Tools → Snippets.
2. Tap Add. Give it a title, an optional abbreviation, the text, and optional tags. Leave Enabled on.
3. Insert one from the keyboard: tools panel → Clipboard → Snippets, then tap the snippet. You can also type `/snip` and the snippet's name.
4. Expansion while you type starts off. Settings → Tools → Snippets → Expansion turns it on. The usual trigger is a semicolon, then the abbreviation, then Space. Enter and Tab can each be turned on separately. If nothing matches, that key still does its normal job.
5. Per keyboard mode (on the Expansion screen) can follow those switches, use Space only, or stay off. Termux mode starts off, because Enter runs the line in a terminal.
6. Expansion never runs in a password box, and not while Shift, Ctrl, or Alt is held.

Fill-ins, expanded when the snippet is inserted:

- `{date}` becomes today's date, like 2026-10-20.
- `{time}` becomes the time, like 14:05.
- `{clipboard}` becomes the current clipboard text. It is empty when the clipboard is empty, and it is empty in a password box.
- `{cursor}` is removed, and the caret is placed there. If you write it more than once, the first one wins.
- The same four names also work with double braces: `{{date}}`, `{{time}}`, `{{clipboard}}`, `{{cursor}}`.
- `{{datetime}}` and `{{selection}}` are the double-brace forms. `{{selection}}` is filled only when you insert the snippet from the keyboard's snippet panel.
- `{{date:yyyy}}` and the same idea for time and datetime let you choose the pattern.
- Write `\{date}` when you want the characters `{date}` left as they are.
- `{date:yyyy}`, `{ return; }`, and any other braces are left as you wrote them. Nothing in a snippet is run as a command.

### Export and import

![Password-protected export](manual/img/export.txt)

TODO: photograph the password field on export. `docs/manual/img/export.txt` is a placeholder, not a picture.

You choose the file with Android's file picker. The app does not upload it.

Clipboard ZIP:

1. Open Settings → Tools → Clipboard → Export to ZIP, or tap ZIP backup on the keyboard's Clipboard tab.
2. Choose Regular, Vault, or both.
3. A Regular ZIP can be plain. A password is optional. Turn on Encrypt this export if you want one.
4. A ZIP that includes the Vault requires a password. There is no way to recover a lost password. The item text is encrypted inside the file.
5. Images and other clips that are not text are skipped.
6. Import from ZIP checks the file, asks for the password when the file has one, and lets you merge with what you have or replace it. Vault rows go back to the Vault.
7. When you have changed the tools layout, the same ZIP includes it as `toolbar.json`. Shell history is not in that file. Importing a damaged layout changes nothing. Replace takes the layout from the file and keeps the shell history already on this phone. Merge adds hidden tiles and extra tiles, and keeps a size, column count, pin list, or order you already set.

Snippets:

1. Settings → Tools → Snippets → Export.
2. Protect with a password is offered, and it starts on. The protected file is encrypted. A short password is refused.
3. A plain export is ordinary text and contains everything you wrote in your snippets. The screen says so.
4. Import asks for the password when the file is encrypted. You can overwrite snippets that share an id, or keep yours. Entries the file cannot validate are skipped.

Older clipboard backup formats are still on Settings → Tools → Clipboard → the backup entry. Vault items are left out of those unless you opt into an encrypted export.

### Slash commands

![Slash commands](manual/img/slash-commands.txt)

TODO: photograph the slash list in the suggestion strip. `docs/manual/img/slash-commands.txt` is a placeholder, not a picture.

1. Type `/` at the start of a word. The suggestion strip lists matching commands.
2. Tap one to expand it. The words you typed after the name are the argument, when that command uses one.
3. Settings → Tools → Slash commands is the master switch. It starts on. Slash commands stay off in password boxes even when the switch is on.

Built in:

- `/date` inserts today's date.
- `/time` inserts the time.
- `/clip` inserts the latest clip that is not sensitive.
- `/snip` inserts a snippet by name.
- `/ai`, `/fix`, `/rewrite`, `/sum`, and `/translate` use Local AI and insert the reply.
- `/shell` inserts one command. It is not run.
- `/edit` opens the clip editor.
- `/vault` unlocks the Vault.

Your own commands:

1. Settings → Tools → Slash commands → Add a command.
2. The name is letters, and it cannot copy a built-in name.
3. Output text can use `{date}`, `{time}`, `{clipboard}`, `{selection}`, and `{cursor}`.
4. You can edit, delete, reorder, and disable a single command. Disabled commands stay in the list and do not appear when you type `/`.

Commands from fleet apps show up in the same list after you approve that app. When two apps use the same name, you see a prefix, such as `/edl:status`. See Fleet below.

### Terminal key row

![Terminal key row](manual/img/terminal-row.txt)

TODO: photograph the terminal key row and a macro key. `docs/manual/img/terminal-row.txt` is a placeholder, not a picture.

The row sits between the suggestion strip and the keys. It starts hidden in ordinary apps.

1. Open Settings → Developer & terminal.
2. Show terminal key row is Auto, Always, or Never. Auto shows it in terminal apps (Termux, ConnectBot, JuiceSSH, NetHunter Terminal, and similar), in boxes that are not normal text, and while Developer or Termux mode is on.
3. The Key row tile on the tools panel's Terminal tab cycles Auto, Always, and Never.
4. Keys on the row send real key presses. Ctrl+C in a terminal is an interrupt. It is not the letters C-t-r-l.
5. Ctrl and Alt: tap once for the next key, double-tap to lock, or hold.

Choose and reorder the keys on that same screen:

1. The list starts as Esc, Tab, Ctrl, Alt, arrows, Home, End, Page up, and Page down. A blank list keeps that usual row.
2. Tap Up or Down to move a key. Tap Remove to take it off the row.
3. Add puts back a key you removed, and can add `|`, `~`, `/`, `\`, `&`, `$`, `;`, `:`, `#`, `!`, `-`, `_`, a backtick, and the media keys (Play, Prev, Next, Rew, FF, Vol-, Vol+). Those media keys control the player. They do not type into the app.
4. Reset row restores the usual keys.

Macro keys send a saved sequence. They are added at the end of the row.

1. Type a command and tap Add command + Enter. The command is inserted, then Enter is pressed. The command is not run by the keyboard, and it is not written to a log.
2. Type one letter and tap Add Ctrl key. That key sends Ctrl plus that letter, such as Ctrl+C.
3. You can keep a handful of macros. Remove a macro the same way you remove any other key on the list.

Operating mode on the same screen is Standard, Developer, or Termux. Auto-detect can switch for Termux and ConnectBot when you turn that on. It starts off.

### Screenshot

![Screenshot tile](manual/img/screenshot.txt)

TODO: photograph the screenshot helper setup. `docs/manual/img/screenshot.txt` is a placeholder, not a picture.

1. Open the tools panel → Quick → Screenshot. The tile can be hidden in Settings → Tools → Screenshot.
2. The keyboard hides, then one picture is taken and saved on this phone in Pictures/Victorious Keyboard. A dialog offers Copy image.
3. Android only allows that through a small accessibility helper. The helper is used only when you tap Screenshot. It does not read the screen.
4. The first time, if the helper is off, the tile opens a setup screen. It explains why and opens Accessibility settings. Turn on Victorious Keyboard screenshot, then come back.
5. On Android 10 and older, the tile tells you that screenshots from the keyboard need Android 11.
6. The picture stays on the phone. It is not sent anywhere, and it is not written to a log.

### Media

The Media tile is on the tools panel's Quick tab. It opens player buttons in place of the keys. Those buttons control the player. They do not type into the app you are focused on, and they do not read what you type.

1. Play/Pause, Previous, Next, Rewind, and Fast-forward send the usual media keys. Any player that listens for those keys can use them. Nothing has to be paired.
2. Volume down and Volume up change the music volume and show the system volume bar.
3. Close returns to the keys.

The same buttons can be added to the terminal key row under Settings → Developer & terminal. They are not on that row until you add them. A blank list keeps the usual keys.

Exact position, through Victorious File Manager, starts off.

1. Install Victorious File Manager when you have it. The package rename may not be on this phone yet. If it is not installed, skip the rest of this list: the Media tile still sends media keys.
2. Turn its media bridge on in that app. It starts off there too.
3. Open Settings → Tools → Fleet link. The keyboard looks for the installed package. It does not require the app to be there.
4. A debug install signed with the published fingerprint is already pinned. A release install has no published fingerprint yet, so tap Approve File Manager. That pins the certificate of the copy on this phone, and a different signature asks again. Release is never trusted on its own.
5. Turn on File Manager seek.
6. While that is on and a track is playing, the Media panel can show the track title. The title stays on the phone and is not written to a log.
7. Rewind and fast-forward then jump about ten seconds. If nothing is playing, the keyboard falls back to the media keys, and a short message says nothing is playing. Volume buttons stay volume buttons either way.
8. Turn File Manager seek off to use media keys only.

### Theme

![Squared black keyboard](manual/img/theme.txt)

TODO: photograph the default squared black keyboard. `docs/manual/img/theme.txt` is a placeholder, not a picture.

1. The default day theme and night theme are Victorious Black: near-black background, dark keys, light letters, and slightly rounded corners.
2. Settings → Look → Theme picks the day theme, the night theme, or a schedule. Victorious Neon, Neon Light, and the older themes stay in the list.
3. Settings → Look → Keyboard colors recolors Victorious Black, Victorious Neon, and Neon Light: background, key, key text, accent, highlight, and suggestion pills. Presets include Neon, Cyber blue, Matrix, Purple, Red, and Mono. Reset to default restores the preset colors. Another theme is left alone.
4. The Quick tab tile Next colors steps through those presets.
5. Settings → Look → Keyboard → Bottom gap lifts the keyboard above the navigation bar. It starts with a small gap. The slider adds more, up to 48 dp.
6. Settings → Keyboard extensions, Language pack extensions, and Theme extensions list the built-in packs under Victorious names. Their ids start with `com.victorious.keyboard.` (for example `com.victorious.keyboard.themes`). A theme, layout, or language saved under the older `org.florisboard.` id still loads. The Chinese shape pack points at https://github.com/Victorious93/Victorious-Keyboard for more input methods. Settings → About still credits FlorisBoard under the Apache 2.0 licence.

### Local AI

![Local AI](manual/img/local-ai.txt)

TODO: photograph Local AI after a model import. `docs/manual/img/local-ai.txt` is a placeholder, not a picture.

This is optional. The app does not include a model, and it does not download one.

1. Open Settings → Tools → Local AI.
2. Tap Import .gguf and pick a model file on the phone. It is copied into the app's private storage. Files of 512 MB or more ask before the copy. The limit is 4 GB and 20 models.
3. Select the model you want. You can set context size, threads, temperature, max tokens, the language used for translate, and a system prompt. Delete model removes that copy.
4. The model loads the first time you use it, in the background, and unloads after it has been idle or when the phone is low on memory.
5. On the keyboard, open the tools panel → AI and tap Ask, Rewrite, Fix grammar, Continue, Summarize, Translate, or Shell. You can also type `/ai`, `/fix`, `/rewrite`, `/sum`, `/translate`, or `/shell`.
6. The reply is inserted as text. Shell does not run the command. Cancel stops a reply that is still being written.
7. Clipboard text and Vault text are used only after you choose that source. A Vault result can be inserted. It is not copied to the regular clipboard.
8. AI suggestions in the strip start off. They also need Learn from my typing. They stay off for passwords, Incognito, boxes that disallow personalised learning, and a Vault paste.
9. With no model, or on a phone where the on-device engine is missing, the keyboard still types. The AI action explains how to import a model.

### Privacy mode

![Incognito](manual/img/privacy.txt)

TODO: photograph Incognito switched on. `docs/manual/img/privacy.txt` is a placeholder, not a picture.

The privacy mode in this keyboard is Incognito.

1. Open the tools panel → Quick → Incognito. The tile shows On or Off.
2. While Incognito is on, the keyboard does not learn words from what you type.
3. Settings → Typing → Incognito mode can force it on, force it off, or follow the app you are typing in plus the Incognito tile. The usual choice is the dynamic one.
4. Settings → Keyboard → Incognito indicator chooses how the keyboard shows that it is on.
5. Password boxes are a separate protection. Suggestions, snippet expansion, slash commands, fleet actions, and learning stay off there whether or not Incognito is on.

## Permissions

The app asks you for two permissions. Both are named in the install manifest.

- Vibration. Used when you have turned on vibration for key presses. Settings → Keyboard → Input feedback is where that lives. The keyboard does not vibrate unless that is on.
- Notifications. Used on Android 13 and newer so the app can post a notification. Setup asks for it. The keyboard types without it.

Three system permissions appear on the services, and you are not asked to grant them. They limit who may connect:

- Bind input method. Android uses this so only the system can attach the keyboard.
- Bind accessibility service. Android uses this so only the system can attach the screenshot helper.
- Bind text service. Android uses this so only the system can attach the spell checker.

The app does not ask for Internet access. It does not ask for your contacts, location, microphone, camera, or storage. The screenshot helper is not a permission in that list. You turn that helper on yourself in Accessibility settings, and it cannot read the screen.

Android also lets the app see three kinds of installed services, without a list of package names: other keyboards, spell checkers, and apps that offer fleet commands. That is how Fleet link can find a command app after you install it.

## Fleet

The keyboard can use commands from other apps on this phone. This is the command provider link. It uses messages inside the phone. It does not use the internet.

1. A fleet app offers commands by advertising a command-provider service. This keyboard does not ship a list of those apps. It looks for any installed app that offers that service.
2. Open Settings → Tools → Fleet link. Each app is listed with its name and the fingerprint of its signing certificate.
3. The Droidcommand-AI fleet switch is the master switch. Turn it off to hide every fleet command. It starts on, but that does not approve apps by itself.
4. An app signed with this keyboard's release key can start on. You can still switch it off. A debug build of this keyboard does not treat other debug apps as that release key, so those apps wait for you.
5. Every other app starts off. Tap Approve this certificate once. That approval is pinned to the certificate you saw. If the app is later signed with a different certificate, the old approval does not carry over, and you are asked again.
6. After approval, a switch on that row turns the app off and on. Off means its commands and snippets disappear. The keyboard keeps working.
7. Approved apps can add slash commands and snippets. Tap a slash command to run it. The app receives the words you typed after the command, because you tapped it. The text that comes back is inserted. It is not run.
8. When a trusted app is connected, the tools panel shows a Fleet tab: the app's name, an Ask tile, the actions it advertised, and its snippets. Ask sends the current selection only because you tapped it.
9. Password boxes are never sent. Nothing is sent until you run a command. A plain list of commands sends no typed text, no clipboard, and no Vault contents.
10. Removing the other app drops the link. The keyboard stays.

Hub status, on the same screen, is the bridge to the Droidcommand hub. It starts off. Only the Droidcommand hub app (and its debug build) is allowed to call it, and only after you turn the switch on. With the switch on, the hub can ask whether the keyboard is running, type into the focused box, and press Enter, Tab, arrows, or Ctrl plus a letter. It cannot read the box. Typed text is never shared. Password boxes are refused. Turn Hub status off to stop all of that.

File Manager seek, on the same screen, is separate from Hub status. It starts off. The debug build of Victorious File Manager is pinned to its published certificate. The release build has no published certificate yet, so it waits until you tap Approve, and it is never trusted automatically. The keyboard looks for the app when you open this screen. If it is not installed, media keys still work. The link is only for playback. It does not read what you type, the clipboard, or the Vault.

## Troubleshooting

The keyboard is missing from other apps. Open this app. Tap the message at the top of Settings. First enable Victorious Keyboard in Android's keyboard list, then pick it as the input method.

A new debug install says the app is not installed, or that there is a conflict with the existing package. This happens once when the phone already has a debug build signed with a different key. Uninstall Victorious Keyboard once, then install the new debug app. After that, newer debug builds that use the shared debug key install over each other. Uninstalling removes the app's private data. Export anything you want to keep first. Release installs are a different signing key from debug, so a debug build does not update a release build in place.

Suggestions never appear. Settings → Typing → Word prediction should be on. Password boxes and boxes that ask for no suggestions do not show them. Incognito does not by itself hide the dictionary suggestions. It stops learning.

A snippet does not expand when I press Space. Expansion starts off. Turn it on under Settings → Tools → Snippets → Expansion, and use the abbreviation with the prefix (a semicolon unless you changed it). In Termux mode, expansion starts off on purpose.

`{date}` stayed as the word `{date}`. The single-brace fill-in has to be exact. `{date:yyyy}` and `{ return; }` are kept as written. Use `{{date:yyyy}}` for a custom date pattern.

Clipboard history is empty. Enable clipboard history under Settings → Tools → Clipboard. Copies that look like secrets are skipped unless you allow them, and sensitive copies go to the Vault instead.

Search is typing into the app behind the keyboard. Tap Search on the clipboard panel first. While that line is active, keys stay in the search. Tap it again, or Clear, when you want to type in the app.

The Vault asks me to unlock the phone. That is the device lock. It is on by default before vault text is shown.

Screenshot does nothing useful. Turn on the accessibility helper from Settings → Tools → Screenshot. Android 10 and older cannot take this kind of screenshot. The picture, when it works, is in Pictures/Victorious Keyboard.

Local AI says there is no model. Import a `.gguf` file under Settings → Tools → Local AI. The app does not download one.

A fleet app is missing from the list. Install that app, open Settings → Tools → Fleet link, and leave the master switch on. Approve its certificate once. Switch that row off if you want its commands hidden.

Hub status does not respond. It starts off. Turn it on only for the Droidcommand hub app. Other apps are refused even with the switch on.

Media buttons do nothing. The player has to accept media keys. File Manager seek stays off unless you turn it on. A release install also needs Approve. "Nothing is playing" means File Manager has no track. If File Manager is not installed, the buttons stay media keys.

The app crashed. The crash screen stays on the phone. Nothing is sent unless you copy it into a report yourself. Read it before you share it, in case it contains private text.

I want the old colors back. Settings → Look → Keyboard colors → Reset to default, or pick Victorious Black again under Theme.

## Privacy and your data

The app does not ask for Internet access. Typing, suggestions, the clipboard, snippets, fleet messages, and Local AI stay on this phone. Local AI does not download a model. A fleet command talks only to an app you installed and approved.

Opening About → Repository, Changelog, or Privacy policy, or the project links on the setup screen, hands that address to your browser. That happens because you tapped it. The Help screen does not do this. It reads the manual from inside the app.

What is stored on the phone:

- Settings, including theme and keyboard colors. Android backup may include settings.
- Your personal dictionary. Android backup may include it.
- Keyboard layouts and themes you imported, under the app's files. Android backup may include those.
- Clipboard history, encrypted, and only after you turn it on. It is excluded from Android backup.
- Vault items, encrypted with their own key. They are excluded from backup unless you make an encrypted export yourself.
- Snippets, slash commands, words learned from typing, your own suggestion phrases, and the Local AI prompt. These are excluded from Android backup.
- `.gguf` models you imported, in private storage.
- Screenshots, in Pictures/Victorious Keyboard. Those are ordinary pictures in your gallery.

Nothing you type is written to a log. Clipboard text, snippet text, and prompts are not written to a log either.

How to wipe it:

1. Settings → Typing → Clear learned words deletes words learned from your typing. The bundled dictionaries stay. Confirm the dialog.
2. On the clipboard panel, clear history removes the regular clips you are viewing. Delete Vault items one by one, or import a replace that you intend to be empty only after you understand that replace removes what is there.
3. Settings → Tools → Snippets, delete the snippets you do not want. Deleting the app's storage removes the file.
4. Settings → Tools → Local AI → Delete model removes an imported model.
5. Settings → Smartbar → Tools panel → Clear shell history removes saved shell history. That history starts off, and it skips Incognito, passwords, and the Vault.
6. Delete screenshots from the gallery. Clearing the app does not remove pictures already saved under Pictures.
7. Android Settings → Apps → Victorious Keyboard → Storage → Clear data removes the app's private files, including settings, clipboard, Vault, snippets, and models. This cannot be undone from inside the app.
8. Uninstalling the app removes that private data as well. Pictures in the gallery remain until you delete them.

There is no account to delete, because the app does not sign you in anywhere.
