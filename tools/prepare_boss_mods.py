"""-PwithBossMods の Cracker's Wither Storm Mod を開発環境で読めるように用意する（2026-10-20）。

Wither Storm は前提の CrackersLib を jar の中（META-INF/jarjar）に入れて配っていて、CrackersLib は単体では配られていない。
入れ子の jar はデオブフされない（SRG 名のまま）ので、開発環境では呼び出しが合わずに落ちる。そこで:

- Modrinth から Wither Storm 4.2.1（版 nY68hRY1）を落とし、入れ子の CrackersLib を取り出して libs/bossmods/crackerslib-forge-1.20.1-0.4.1.jar に
- Wither Storm から入れ子（META-INF/jarjar）を外したものを libs/bossmods/witherstormmod-1.20.1-4.2.1.jar に

置く。build.gradle はこの 2 つを flatDir（libs/bossmods）からデオブフして読む。libs/bossmods は git に入れない。

    python tools/prepare_boss_mods.py
"""
import io
import json
import os
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "libs", "bossmods")
WITHER_STORM_VERSION = "nY68hRY1"
NESTED = "META-INF/jarjar/crackerslib-forge-1.20.1-0.4.1.jar"


def download(version_id):
    with urllib.request.urlopen(f"https://api.modrinth.com/v2/version/{version_id}") as r:
        meta = json.load(r)
    url = next(f["url"] for f in meta["files"] if f["primary"])
    with urllib.request.urlopen(url) as r:
        return r.read()


def main():
    os.makedirs(OUT, exist_ok=True)
    src = zipfile.ZipFile(io.BytesIO(download(WITHER_STORM_VERSION)))
    with open(os.path.join(OUT, "crackerslib-forge-1.20.1-0.4.1.jar"), "wb") as f:
        f.write(src.read(NESTED))
    with zipfile.ZipFile(os.path.join(OUT, "witherstormmod-1.20.1-4.2.1.jar"), "w", zipfile.ZIP_DEFLATED) as dst:
        for info in src.infolist():
            if info.filename.startswith("META-INF/jarjar/"):
                continue
            dst.writestr(info, src.read(info.filename))
    print("wrote", OUT)


if __name__ == "__main__":
    main()
