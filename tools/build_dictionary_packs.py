#!/usr/bin/env python3
"""Builds the bundled dictionary pack zips from dictionary-packs/<id>/{pack.json,words.list,README.txt}.

Output: app/src/main/assets/ime/dictionary-packs/<id>.zip. Deterministic (fixed timestamps, sorted entries), so
rebuilding unchanged sources gives identical bytes. A unit test (DictionaryPackAssetsTest) fails if a committed zip no
longer matches its sources. Run from the repository root: python3 tools/build_dictionary_packs.py
"""
import json, os, re, sys, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "dictionary-packs")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "ime", "dictionary-packs")
FREQ = 128
WORD = re.compile(r"^[^\s;,=\x00-\x1f]{1,64}$")


def words_txt(pack_id: str, words: list) -> bytes:
    lines = [f"dictionary={pack_id};version=1;generated-by=victorious-keyboard-pack-builder"]
    lines += [f" w={w};f={FREQ};l=all" for w in words]
    return ("\n".join(lines) + "\n").encode("utf-8")


def build(pack_id: str) -> bytes:
    d = os.path.join(SRC, pack_id)
    manifest = open(os.path.join(d, "pack.json"), "rb").read()
    assert json.loads(manifest)["id"] == pack_id, f"{pack_id}: id mismatch"
    words = [w.strip() for w in open(os.path.join(d, "words.list"), encoding="utf-8").read().splitlines() if w.strip()]
    for w in words:
        assert WORD.match(w), f"{pack_id}: invalid word {w!r}"
    assert len({w.lower() for w in words}) == len(words), f"{pack_id}: duplicate words"
    readme = open(os.path.join(d, "README.txt"), "rb").read()
    import io
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in (("README.txt", readme), ("pack.json", manifest), ("words.txt", words_txt(pack_id, words))):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, data)
    return buf.getvalue()


def main() -> int:
    os.makedirs(OUT, exist_ok=True)
    ids = sorted(p for p in os.listdir(SRC) if os.path.isdir(os.path.join(SRC, p)))
    for pid in ids:
        data = build(pid)
        open(os.path.join(OUT, pid + ".zip"), "wb").write(data)
        print(f"{pid}.zip {len(data)} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
