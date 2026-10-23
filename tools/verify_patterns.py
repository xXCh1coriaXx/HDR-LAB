#!/usr/bin/env python3
"""Check file signaling and decoded code values on the first and last frames."""
from array import array
import json
from pathlib import Path
import subprocess
import sys

DEST = Path(__file__).resolve().parents[1] / "app/src/main/assets/patterns"
W, H = 1280, 720
results = []
paths = sorted(DEST.glob("*.mp4"))
assert {p.stem for p in paths} == {f"{t}_{k}" for t in ("sdr", "pq", "hlg") for k in ("black", "steps", "window")}
for path in paths:
    hdr = not path.name.startswith("sdr")
    ten_bit = True
    probe = json.loads(subprocess.check_output([
        "ffprobe", "-v", "error", "-show_streams", "-of", "json", str(path)]))
    info = probe["streams"][0]
    expected_transfer = "smpte2084" if path.name.startswith("pq") else "arib-std-b67" if hdr else "bt709"
    assert info["color_transfer"] == expected_transfer, (path.name, info)
    assert info["color_range"] == "tv", (path.name, info)
    assert info["color_primaries"] == ("bt2020" if hdr else "bt709"), path.name
    assert info["profile"] == "Main 10"
    assert info["level"] != 255, "Unspecified HEVC level is unsuitable for hardware tests"
    if path.name.startswith("pq"):
        frame_info = json.loads(subprocess.check_output([
            "ffprobe", "-v", "error", "-select_streams", "v:0", "-read_intervals", "%+#1",
            "-show_frames", "-show_entries", "frame=side_data_list", "-of", "json", str(path)]))
        side = {d["side_data_type"]: d for d in frame_info["frames"][0]["side_data_list"]}
        assert side["Mastering display metadata"]["max_luminance"] == "10000000/10000"
        assert side["Content light level metadata"]["max_content"] == 1000
    raw = subprocess.check_output([
        "ffmpeg", "-v", "error", "-i", str(path), "-vf", "select=eq(n\\,0)+eq(n\\,95)",
        "-fps_mode", "passthrough", "-f", "rawvideo", "-pix_fmt",
        "yuv420p10le" if ten_bit else "yuv420p", "pipe:1"])
    frame_bytes = W * H * 3 // 2 * (2 if ten_bit else 1)
    assert len(raw) == frame_bytes * 2, (path.name, len(raw))
    for i in range(2):
        values = array("H" if ten_bit else "B")
        values.frombytes(raw[i * frame_bytes:(i + 1) * frame_bytes])
        if ten_bit and sys.byteorder != "little": values.byteswap()
        y = values[:W * H]
        uv = values[W * H:]
        black = 64
        assert set(uv) == {512}, (path.name, "chroma")
        assert y[0] == black and y[H * W - 1] == black, (path.name, "surround")
        if "black" in path.name:
            assert set(y) == {black}, (path.name, "reference black", min(y), max(y))
        elif "window" in path.name:
            assert y[360 * W + 640] == (723 if path.name.startswith("pq") else 940), path.name
        else:
            levels = [83, 104, 137, 167, 214, 281] if path.name.startswith("pq") else [68, 72, 80, 96, 128, 192]
            centers = [y[360 * W + 80 + j * 190 + 85] for j in range(6)]
            assert centers == levels, (path.name, centers, levels)
    results.append({"file": path.name, "transfer": expected_transfer, "range": "limited",
                    "profile": info["profile"], "level": info["level"],
                    "decodedFirstAndLastFrameChecks": "passed"})
print(json.dumps(results, indent=2))
