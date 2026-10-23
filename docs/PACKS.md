# Packs

How add-ons for Victorious Keyboard are described, published and checked. This page replaces the old `LANGUAGEPACKS.md` stub.

## Status (read first)

* **Nothing is published yet.** `packs/index.json` is a valid, empty catalog. The pages under `packs/` say the same.
* **The keyboard does not read the catalog.** The Get more buttons only open this project's pages in your browser. An optional update check is queued work (`ROADMAP.md`, item 10) and needs the `INTERNET` permission, which the app does not have.
* **Importing is by file.** You download a pack yourself, then import it on the matching screen. Nothing here has been run on a device.
* What is implemented and tested: the catalog reader (`PackCatalogReader`, pure Kotlin, JVM-tested) and the hash check (`PackHash`). They are not wired into any screen.

## Pack types

| Type (`type`) | What it is | File | Format | Import today |
|---|---|---|---|---|
| `dictionary-pack` | A word list for the internal user dictionary | `.zip` | [`DICTIONARY_PACKS.md`](../DICTIONARY_PACKS.md): `pack.json` + `words.txt`, flat zip, at most 1 MiB | Settings → Typing → Dictionary → Dictionary packs → open a pack file |
| `theme`, `layout`, `language-pack`, `extension` | An extension archive | `.flex` | A zip with `extension.json` at its root. Its `meta` block is `ExtensionMeta` (`id`, `version`, `title`, `description`, `maintainers`, `license`, optional `homepage`, `issueTracker`, `keywords`, `aliases`) and its `items` are the themes, layouts or languages. The built-in ones under `app/src/main/assets/ime/` are the working examples | The Import button on the extension lists (Settings → Theme / Keyboard / Language pack extensions) |
| `command-provider` | An installed app that offers fleet commands | none | A service that answers the messages in [`docs/FLEET.md`](FLEET.md) | Install the app, then approve its certificate on the Fleet link screen |

The catalog says which type an entry is. The keyboard must still read the file with that type's own strict reader; a catalog entry never replaces it. Language packs have no separate format specification yet. That, and the import path, is queued work (`ROADMAP.md`, item 12), and until then a language pack is an ordinary extension archive.

## Ids

* An extension-style pack (`theme`, `layout`, `language-pack`, `extension`) uses the extension id pattern: lowercase letters, digits and `_`, in dot-separated groups, starting with a letter (`com.victorious.neonplus`).
* A `dictionary-pack` uses the slug pattern from its `pack.json`: lowercase letters, digits and `-`, 1 to 40 characters, starting with a letter or digit (`kali-extra`).
* Packs this project publishes use `publisher: "victorious"` and, if extension-style, an id under `com.victorious.`. Nobody else may use that namespace, and the project may not publish outside it.
* The ids of things that ship in the app are reserved: the prefixes `org.florisboard.` and `com.victorious.keyboard.`, every built-in extension id and alias, and the five bundled dictionary pack slugs. A catalog entry may not reuse one, because it could never replace it. Built-in extensions use `com.victorious.keyboard.*` ids; the older `org.florisboard.*` ids are kept as aliases so saved themes, layouts and languages still resolve.
* A command provider is identified by its Android package, not by a prefix.

## The catalog: `packs/index.json`

```json
{
  "format": "victorious-pack-catalog",
  "version": 1,
  "updated": "2026-10-23",
  "packs": []
}
```

Each item of `packs` has these fields. Unknown fields are refused, and every value must have exactly the type shown.

| Field | Type | Applies to | Rule |
|---|---|---|---|
| `id` | string | all | see Ids; at most 80 characters; unique in the catalog |
| `type` | string | all | one of the types above |
| `version` | string | all | three numbers, `1.2.3` |
| `title` | string | all | 1 to 80 characters, no control characters |
| `description` | string | all, optional | at most 400 characters; line breaks allowed |
| `publisher` | string | all | lowercase letters, digits, space, `.`, `_`, `-`; at most 60 characters |
| `download` | string | downloadable types | a release asset of this repository: `https://github.com/Victorious93/Victorious-Keyboard/releases/download/<tag>/<file>.zip` or `.flex`. No query, fragment, credentials, `..`, `%`, spaces or empty parts; at most 300 characters |
| `sha256` | string | downloadable types | 64 lowercase hex digits, the SHA-256 of the downloaded file |
| `size_bytes` | number | downloadable types | 1 to 67,108,864 |
| `package` | string | `command-provider` | an Android package name |
| `cert_sha256` | string | `command-provider` | the signing certificate's SHA-256 as 32 colon-separated uppercase hex pairs, the same form the Fleet link screen shows |

Limits on the whole document: at most 256 KiB and 500 entries. A downloadable entry may not carry `package` or `cert_sha256`, and a command provider may not carry `download`, `sha256` or `size_bytes`.

An entry that would look like this (placeholder values, not a real pack):

```json
{
  "id": "com.victorious.neonplus",
  "type": "theme",
  "version": "1.0.0",
  "title": "Neon Plus",
  "description": "An example theme.",
  "publisher": "victorious",
  "download": "https://github.com/Victorious93/Victorious-Keyboard/releases/download/packs-1/neonplus.flex",
  "sha256": "<64 lowercase hex digits>",
  "size_bytes": 2048
}
```

## Publishing a pack (for the maintainer)

1. Build the file: a `.zip` for a dictionary pack (`tools/build_dictionary_packs.py` builds the bundled ones) or a `.flex` for an extension.
2. Check it by importing it on a phone, or with the matching reader in the unit tests.
3. Compute its hash: `sha256sum neonplus.flex`.
4. Create a GitHub release on this repository and attach the file as an asset. The download link must be that asset's link.
5. Add the entry to `packs/index.json` with the hash and the file size in bytes, and set `updated`.
6. Open a pull request. The unit test `PackCatalogTest` validates the repository's own `index.json` and fails on any of the rules above.

## Trust

* The catalog is untrusted input even though this repository serves it. It is bounded, strictly typed, and refuses unknown fields.
* Downloads may only come from this repository's release assets.
* A download must match its `sha256` before it is read at all, and must then pass its own type's strict reader (size limits, safe names, nothing extracted to disk). `PackHash.matches` does the hash comparison.
* The hash protects against a corrupted or swapped file. It does not protect against a changed catalog, because the hash comes from the same place as the link. Signing the catalog is a possible later step (`ROADMAP.md`, item 14).
* A catalog entry never makes anything trusted. A command-provider app still has to be approved on the Fleet link screen, pinned to the certificate you see there. The certificate in the catalog is information for you, not an approval.
* Where the catalog will be fetched from by the update check (item 10) is not decided here. The page you can read in a browser is <https://github.com/Victorious93/Victorious-Keyboard/blob/main/packs/index.json>.
