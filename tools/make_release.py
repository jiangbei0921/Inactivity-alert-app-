#!/usr/bin/env python3
"""组装一次发布：为最近几个历史版本生成差分补丁，并写出 update.json。

## 为什么需要「历史基线」
补丁是 (旧包 → 新包) 的函数，v100→v101 的补丁对 v102 毫无用处。
想让落后两三个版本的用户也能走增量，发布侧就必须留着那几个旧 APK。
本脚本把上一轮发布的 `app-debug.apk` 与 `base-*.apk` 当作基线池，
为其中最近的 N 个各生成一条补丁，并把该带走的基线复制到本轮产物里，
形成一条自维持的链条——不需要任何外部数据库。

## 补丁匹配靠 SHA-256 而非版本号
同一个 versionCode 可能对应多次构建。只有内容摘要能保证
「设备上装的正是生成补丁时用的那个包」，否则 bspatch 会算出
一个大小正确、内容错乱的文件。这也是 update.json 里带 fromSha256 的原因。

用法：
    python3 make_release.py --new app-release.apk --version-code 42 \
        --version-name 1.1.42 --notes "修复提醒不响铃" \
        --prev-dir prev --out-dir dist

依赖：系统需安装 bsdiff（Debian/Ubuntu: apt-get install -y bsdiff）。
"""

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime, timezone

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_patch import transcode  # noqa: E402

BASE_RE = re.compile(r"^base-(\d+)\.apk$")
FULL_APK_NAME = "app-debug.apk"  # 历史沿用的固定文件名，下载页与二维码都指向它
MANIFEST_NAME = "update.json"


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def build_patch(old_path: str, new_path: str, out_path: str) -> int:
    """生成 SBPATCH1 补丁，返回字节数。"""
    with tempfile.TemporaryDirectory() as tmp:
        raw_path = os.path.join(tmp, "raw.bsdiff")
        subprocess.run(["bsdiff", old_path, new_path, raw_path], check=True)
        with open(raw_path, "rb") as f:
            raw = f.read()
    data = transcode(raw)
    with open(out_path, "wb") as f:
        f.write(data)
    return len(data)


def collect_bases(prev_dir: str, new_version: int) -> dict:
    """从上一轮发布产物里收集可用基线：{versionCode: apk 路径}。"""
    bases = {}
    if not prev_dir or not os.path.isdir(prev_dir):
        return bases

    for name in os.listdir(prev_dir):
        m = BASE_RE.match(name)
        if m:
            bases[int(m.group(1))] = os.path.join(prev_dir, name)

    # 上一轮的全量包版本号记在它自己的清单里
    manifest_path = os.path.join(prev_dir, MANIFEST_NAME)
    full_path = os.path.join(prev_dir, FULL_APK_NAME)
    if os.path.isfile(manifest_path) and os.path.isfile(full_path):
        try:
            with open(manifest_path, "r", encoding="utf-8") as f:
                prev_version = int(json.load(f).get("versionCode", 0))
            if prev_version > 0:
                bases.setdefault(prev_version, full_path)
        except (ValueError, OSError, json.JSONDecodeError) as e:
            print("WARN: cannot read previous manifest: %s" % e, file=sys.stderr)

    return {code: path for code, path in bases.items() if 0 < code < new_version}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--new", required=True, help="本次构建产出的 APK")
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--notes", default="", help="更新说明，展示给用户")
    parser.add_argument("--prev-dir", default="", help="上一轮发布产物目录（可为空）")
    parser.add_argument("--out-dir", required=True)
    parser.add_argument("--keep", type=int, default=3, help="最多为多少个历史版本生成补丁")
    parser.add_argument(
        "--max-ratio", type=float, default=0.85,
        help="补丁体积超过全量包这个比例就丢弃——省不下流量的补丁只会徒增出错概率",
    )
    args = parser.parse_args()

    if not os.path.isfile(args.new):
        print("ERROR: new apk not found: %s" % args.new, file=sys.stderr)
        return 1
    if shutil.which("bsdiff") is None:
        print("ERROR: bsdiff not installed (apt-get install -y bsdiff)", file=sys.stderr)
        return 1

    os.makedirs(args.out_dir, exist_ok=True)
    new_size = os.path.getsize(args.new)
    new_sha = sha256_file(args.new)

    bases = collect_bases(args.prev_dir, args.version_code)
    codes = sorted(bases.keys(), reverse=True)[: max(args.keep, 0)]
    print("base candidates: %s" % (codes or "none"))

    patches = []
    for code in codes:
        base_path = bases[code]
        patch_name = "p-%d-%d.patch" % (code, args.version_code)
        patch_path = os.path.join(args.out_dir, patch_name)
        try:
            size = build_patch(base_path, args.new, patch_path)
        except subprocess.CalledProcessError as e:
            print("WARN: bsdiff failed for base %d: %s" % (code, e), file=sys.stderr)
            continue

        if size >= new_size * args.max_ratio:
            os.remove(patch_path)
            print("skip base %d: patch %d bytes is not worth it" % (code, size))
            continue

        patches.append({
            "fromVersionCode": code,
            "fromSha256": sha256_file(base_path),
            "url": patch_name,
            "size": size,
            "sha256": sha256_file(patch_path),
        })
        print("patch %s: %d bytes (%.2f%% of full)" % (patch_name, size, size * 100.0 / new_size))

    # 把该带走的基线复制进本轮产物，让下一轮还能拿到它们。
    # 新版自己不用复制：它以 app-debug.apk 的身份留在产物里，下一轮按清单版本号识别。
    for code in codes[: max(args.keep - 1, 0)]:
        dest = os.path.join(args.out_dir, "base-%d.apk" % code)
        if os.path.abspath(bases[code]) != os.path.abspath(dest):
            shutil.copy2(bases[code], dest)

    full_dest = os.path.join(args.out_dir, FULL_APK_NAME)
    if os.path.abspath(args.new) != os.path.abspath(full_dest):
        shutil.copy2(args.new, full_dest)

    manifest = {
        "schema": 1,
        "versionCode": args.version_code,
        "versionName": args.version_name,
        "releaseNotes": args.notes,
        "publishedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "full": {"url": FULL_APK_NAME, "size": new_size, "sha256": new_sha},
        "patches": patches,
    }
    with open(os.path.join(args.out_dir, MANIFEST_NAME), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print("manifest written: version %d (%s), %d byte full, %d patch(es)"
          % (args.version_code, args.version_name, new_size, len(patches)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
