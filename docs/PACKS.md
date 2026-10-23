# Packs: the catalog and the file formats

Status: **the catalog format and its reader are JVM-tested. Nothing downloads or installs from the catalog yet: the keyboard has no `INTERNET` permission (the opt-in update check is ROADMAP item 10). No pack is published. Nothing here has been run on a device.**

This page is the one place that describes how add-ons for Victorious Keyboard are listed and packaged. It replaces the old language-pack stub (`LANGUAGEPACKS.md`).

## What a pack is

| Type (`type` in the catalog) | What it is | File format | Installed by |
|---|---|---|---|
| `theme` | a stylesheet theme | an extension archive (`.flex`, a zip with `extension.json` at its root) | Addons & Extensions → Import |
| `layout` | a keyboard layout set | an extension archive | Addons & Extensions → Import |
| `language-pack` | a language pack extension | an extension archive; the pack format itself is specified with ROADMAP item 12 | Addons & Extensions → Import |
| `dictionary-pack` | a word list for the internal user dictionary | a flat zip described in `DICTIONARY_PACKS.md` (`pack.json` and `words.txt`) | Settings → Typing → Dictionary → Dictionary packs |
| `command-provider` | an app that offers slash commands and actions | not a file: an installed app, see `docs/FLEET.md` | Settings → Fleet link (one approval pinned to the app's signing certificate) |

## Ids

* Packs published by this project use ids that start with `com.victorious.` followed by one to four lower-case segments (`com.victorious.kali-tools`).
* The built-in packs keep their `org.florisboard.*` ids. Renaming them would orphan saved theme and layout selections, so they are never listed in the catalog.
* An id names one pack. Two catalog entries with the same id are not both accepted, even if their types differ.
* A dictionary pack's own `pack.json` id is a short id (`kali-tools`). The catalog id for it is `com.victorious.` plus that short id.

## The catalog: `packs/index.json`

One JSON file in this repository. It lists packs and says where their files are; it never contains the files and reading it installs nothing.

```json
{
  "format": "victorious-pack-catalog",
  "version": 1,
  "packs": [
    {
      "type": "dictionary-pack",
      "id": "com.victorious.kali-tools",
      "version": "1.0.0",
      "title": "Kali tools",
      "description": "Names of common Kali Linux tools.",
      "publisher": "Victorious",
      "url": "https://github.com/Victorious93/Victorious-Keyboard/releases/download/packs-kali-tools-1.0.0/kali-tools-1.0.0.zip",
      "sha256": "<64 lower-case hex characters>",
      "sizeBytes": 4096
    },
    {
      "type": "command-provider",
      "id": "com.victorious.example-provider",
      "version": "1.0.0",
      "title": "Example provider",
      "description": "An app that offers slash commands.",
      "publisher": "Victorious",
      "packageName": "ai.example.app",
      "certSha256": "<64 lower-case hex characters>"
    }
  ]
}
```

Fields for every entry: `type`, `id`, `version` (three plain numbers, `1.0.0`), `title` (up to 80 characters), `description` (up to 400), `publisher` (up to 80). Text may not contain control characters.

Fields for a downloadable type (`theme`, `layout`, `language-pack`, `dictionary-pack`): `url`, `sha256`, `sizeBytes`, and nothing about a package.

* `url` must be a release asset of this repository: `https://github.com/Victorious93/Victorious-Keyboard/releases/download/<tag>/<file>`, no query, no `..`.
* `sha256` is the SHA-256 of the file, 64 lower-case hex characters.
* `sizeBytes` is between 1 and 16 MiB.

Fields for `command-provider`: `packageName`, `certSha256` (the SHA-256 of the app's signing certificate), and an optional `url` that must be a page in this repository. A provider entry carries no hash or size of a file.

### Limits and what happens to a bad entry

The file may be at most 256 KiB and list at most 500 packs; format and version must match exactly (a different `version` means a breaking change, and an older keyboard refuses the file). Unknown extra fields are ignored so a newer catalog can add information. **An entry that fails any check is dropped and counted, never repaired**; the other entries are kept. A file that is not valid JSON, is too large, or has the wrong format or version is refused whole. The reader is `PackCatalogReader` (`ime/packs/PackCatalog.kt`, pure Kotlin, no logging).

## Publishing a pack (the maintainer's checklist)

1. Build the file. For a dictionary pack, a flat zip as in `DICTIONARY_PACKS.md`; for an extension, a `.flex` archive.
2. Compute its SHA-256 and size: `sha256sum file.zip` and `wc -c file.zip`.
3. Create a release tagged `packs-<short-id>-<version>` and attach the file, named `<short-id>-<version>.zip` (or `.flex`).
4. Add the entry to `packs/index.json` in a pull request. `PackCatalogTest` fails if any entry in the file is malformed, uses a link outside this repository, or repeats an id.
5. Update the matching page in `packs/` (`packs/themes.md` and so on) with a human description.

## Trust model, honestly

* **The catalog is not signed.** Anyone who can change `packs/index.json` on `main` can change what it lists. Catalog signing is ROADMAP item 14; it is wanted before the keyboard fetches the catalog on its own.
* **A hash only proves the file matches the listing.** A client that downloads a file must check `sha256` and `sizeBytes` before it opens the file; item 10 implements the download and must do that. Nothing in this item downloads anything.
* **A listing never grants trust.** A `command-provider` entry only tells the keyboard which package and certificate to expect. Using a provider still needs the one-time, certificate-pinned approval in Settings → Fleet link.
* **Imported files are untrusted input** whatever their source: the existing size and name limits for each format apply.

## What is not done

* No pack is published; `packs/index.json` lists none.
* No download, no `INTERNET` permission, no update check (item 10).
* The language-pack file format is not specified yet (item 12). Dictionary-pack import from a catalog link and plugin handling are items 11 and 13.
