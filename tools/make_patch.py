#!/usr/bin/env python3
"""把标准 bsdiff 补丁转码成客户端可直接消费的 SBPATCH1 格式。

背景：Android/JDK 运行时不自带 bzip2 解压器，而标准 BSDIFF40 的三个数据块正是 bzip2 压缩的。
与其给 APK 塞进 1MB 的 commons-compress，不如在发布侧做一次无损转码——
块内容一个字节都不改，只把压缩算法从 bzip2 换成 deflate，客户端用 JDK 内置的
InflaterInputStream 即可还原（见 app/src/main/java/com/sitbreak/app/update/BsPatch.kt）。

用法：
    python3 make_patch.py <old.apk> <new.apk> <out.patch>

依赖：系统需安装 bsdiff 命令（Debian/Ubuntu: apt-get install -y bsdiff）。
"""

import os
import shutil
import struct
import subprocess
import sys
import tempfile
import zlib
import bz2

MAGIC = b"SBPATCH1"
HEADER_SIZE = 32


def offtin(buf: bytes) -> int:
    """bsdiff 自有整数编码：小端绝对值 + 最高位符号位（不是补码）。"""
    y = buf[7] & 0x7F
    for i in range(6, -1, -1):
        y = (y << 8) + buf[i]
    return -y if (buf[7] & 0x80) else y


def transcode(raw: bytes) -> bytes:
    if raw[:8] != b"BSDIFF40":
        raise ValueError("not a BSDIFF40 patch: %r" % raw[:8])

    ctrl_len = offtin(raw[8:16])
    diff_len = offtin(raw[16:24])
    new_size = offtin(raw[24:32])
    if ctrl_len < 0 or diff_len < 0 or new_size < 0:
        raise ValueError("corrupt BSDIFF40 header")

    p = HEADER_SIZE
    ctrl = bz2.decompress(raw[p:p + ctrl_len]); p += ctrl_len
    diff = bz2.decompress(raw[p:p + diff_len]); p += diff_len
    extra = bz2.decompress(raw[p:])

    # level=9 换来的体积收益对补丁这种量级很划算，耗时在 CI 上可忽略
    zc = zlib.compress(ctrl, 9)
    zd = zlib.compress(diff, 9)
    ze = zlib.compress(extra, 9)

    return MAGIC + struct.pack("<qqq", len(zc), len(zd), new_size) + zc + zd + ze


def main() -> int:
    if len(sys.argv) != 4:
        print(__doc__)
        return 2

    old_path, new_path, out_path = sys.argv[1:4]
    for p in (old_path, new_path):
        if not os.path.isfile(p):
            print("ERROR: file not found: %s" % p, file=sys.stderr)
            return 1

    if shutil.which("bsdiff") is None:
        print("ERROR: bsdiff not installed (apt-get install -y bsdiff)", file=sys.stderr)
        return 1

    with tempfile.TemporaryDirectory() as tmp:
        raw_patch = os.path.join(tmp, "raw.bsdiff")
        # bsdiff 峰值内存约 17x 旧文件大小；12MB 的 APK 约需 200MB，CI runner 完全够用
        subprocess.run(["bsdiff", old_path, new_path, raw_patch], check=True)
        with open(raw_patch, "rb") as f:
            raw = f.read()

    out = transcode(raw)
    with open(out_path, "wb") as f:
        f.write(out)

    old_size = os.path.getsize(old_path)
    new_size = os.path.getsize(new_path)
    ratio = len(out) * 100.0 / new_size if new_size else 0
    print(
        "patch written: %s\n  old=%d bytes  new=%d bytes  patch=%d bytes (%.1f%% of full)"
        % (out_path, old_size, new_size, len(out), ratio)
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
