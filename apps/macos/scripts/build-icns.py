#!/usr/bin/env python3
"""由已生成的 PNG 图标组装 ICNS；不依赖 iconutil 的系统图像服务。"""
from pathlib import Path
import struct
import sys

directory = Path(sys.argv[1])
chunks = []
for kind, name in [(b"icp4", "icon_16x16.png"), (b"icp5", "icon_32x32.png"), (b"ic07", "icon_128x128.png"), (b"ic08", "icon_256x256.png"), (b"ic09", "icon_512x512.png"), (b"ic10", "icon_512x512@2x.png")]:
    data = (directory / name).read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError(f"Invalid PNG: {name}")
    chunks.append(kind + struct.pack(">I", len(data) + 8) + data)
body = b"".join(chunks)
Path(sys.argv[2]).write_bytes(b"icns" + struct.pack(">I", len(body) + 8) + body)
