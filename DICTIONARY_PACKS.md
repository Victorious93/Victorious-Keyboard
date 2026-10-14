# Dictionary packs

Status: **pure-Kotlin core JVM-tested; install screen and database wiring compiled, never run or rendered on a device.**

## What this is, and what it is not (read first)

* Five ready-made word lists shipped inside the app as small zips: **Kali Linux tools**, **Kali NetHunter**, **AI and LLM
  vocabulary**, **Slash commands**, **Termux and shell commands** (about 50-190 words each).
* Installing a pack adds its words to the keyboard's **internal user dictionary** for all languages
  (Settings → Typing → Dictionary → *Dictionary packs*; the internal user dictionary must be switched on).
* The Android spell checker service (`FlorisSpellCheckerService`) reads the internal user dictionary: installed words
  stop being flagged as typos in apps that use the system spell checker.
* **Word prediction reads the bundled pack zips directly**, not the user-dictionary rows. With *Word prediction* on
  (the default), the suggestion bar offers current-word completions, a next-word guess, and a close correction.
  A correction is auto-committed on space only when it is one edit away, the typed token is at least 4 characters, and
  the token is not already in the lexicon. *Learn from my typing* is off by default. When it is on, new words and the
  word that came before them are stored in `noBackupFilesDir/prediction/learned-words.json` (not backed up, never
  logged). Passwords, incognito, and fields with `IME_FLAG_NO_PERSONALIZED_LEARNING` or no-suggestions are not stored,
  and prediction itself does not run in password or no-suggestions fields. The static packs have no sentence pairs, so
  next-word with learning off is the most frequent dictionary word, which is a weak guess. `spell()` is still the
  upstream test stub. None of this has been run on a device.
* The spell checker looks at words, so entries that start with `/` or contain `-` are stored as typed but may not be
  matched by it. Prediction's token pattern does keep `_./+-`, so those pack words can still be suggested.

## Honest provenance

The lists were curated by the contributors **from memory**. They were **not checked against the current official lists or
documentation** (the environment used to write them had no network access: fetching kali.org failed), and a name may be
missing, renamed or removed in a newer release. Treat them as a starting point. They contain **names only**: no options,
no command lines, no payloads, no URLs or addresses, no text from manuals. A test enforces the last three.

## Format

A pack is a flat zip (at most 8 files, 1 MiB) with:

* `pack.json`: `{"format":"victorious-dictionary-pack","version":1,"id":"…","title":"…","description":"…"}`
* `words.txt`: the user dictionary's own text format, one ` w=word;f=128;l=all` line per word (a header line starting with
  `dictionary=` is ignored). Because it is the same format, an unzipped `words.txt` also works with the existing
  *User dictionary → Import*.
* `README.txt` (and any other text file) is ignored by the app.

Limits (untrusted input, CLAUDE.md invariant 5): sizes are enforced on the bytes actually decompressed, never on the sizes
the zip claims; names must be plain file names (no directories, no `..`); at most 5 000 words; a word is one token of at
most 64 characters without whitespace, `;`, `,` or `=`; frequency 1-255; a line naming a language or a shortcut is
rejected; invalid lines are counted and never repaired; nothing is extracted to disk.

## Install and remove

* Install adds only words you do not already have for all languages. **An existing word is never changed** (your
  frequency and shortcut win). Running it twice adds nothing the second time. One database transaction, off the main thread.
* Remove deletes only entries that are **identical** to the pack's (same word and frequency, all languages, no shortcut),
  after a confirmation. A word you edited or added yourself stays. There is no pack-origin column in the database (that
  would need a migration), so a word you added yourself with exactly the pack's values would also go; the dialog says so.
* "Open a pack file" lets you load your own pack zip from the device; it goes through the same strict reader.

## Rebuilding the zips

Sources are in `dictionary-packs/<id>/` (`words.list`, `pack.json`, `README.txt`). Edit the plain word list, then run
`python3 tools/build_dictionary_packs.py` from the repository root; it writes `app/src/main/assets/ime/dictionary-packs/<id>.zip`
deterministically. `DictionaryPackAssetsTest` fails if a committed zip no longer matches its sources or breaks the rules.

## Not done

* Gesture typing still has no dictionary behind it. Per-locale packs, a pack origin marker, translations of the pack
  texts, verification of the lists against official sources, and device validation of prediction are not done.
  `spell()` remains a stub. Next-word without learning is a frequency ranking, not a language model.
