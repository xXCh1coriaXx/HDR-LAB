#!/usr/bin/env python3
"""Generate limited-range, neutral black test videos without RGB conversions.

Requires ffmpeg with 10-bit libx265. Source code values are intentional:
All formats use 10-bit black = 64. Patches are not luminance matched
across transfer functions; use the all-black clips to compare the black floor.
"""
from array import array
from pathlib import Path
import subprocess
import sys

DEST = Path(__file__).resolve().parents[1] / "app/src/main/assets/patterns"
DEST.mkdir(parents=True, exist_ok=True)
W, H, FPS, SECONDS = 1280, 720, 24, 4


def frame(ten_bit, kind, transfer):
    sdr = transfer == "sdr"
    black = 64 if ten_bit else 16
    y = array("H" if ten_bit else "B", [black]) * (W * H)
    # Near-black increments in their respective limited-range signal encodings.
    levels = [68, 72, 80, 96, 128, 192] if sdr else [65, 66, 68, 72, 80, 96]
    if kind == "steps":
        for i, level in enumerate(levels):
            x = 80 + i * 190
            for row in range(250, 470):
                y[row * W + x: row * W + x + 170] = array(y.typecode, [level]) * 170
    if kind == "window":
        level = 723 if transfer == "pq" else 940
        for row in range(240, 480):
            y[row * W + 448: row * W + 832] = array(y.typecode, [level]) * 384
    chroma = array(y.typecode, [512 if ten_bit else 128]) * (W * H // 2)
    y.extend(chroma)
    if ten_bit and sys.byteorder != "little":
        y.byteswap()
    return y.tobytes()


for transfer in ("sdr", "pq", "hlg"):
    for kind in ("black", "steps", "window"):
        ten_bit = True
        name = f"{transfer}_{kind}.mp4"
        cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "rawvideo",
               "-pixel_format", "yuv420p10le" if ten_bit else "yuv420p",
               "-video_size", f"{W}x{H}", "-framerate", str(FPS), "-i", "pipe:0", "-an"]
        trc = {"sdr": "bt709", "pq": "smpte2084", "hlg": "arib-std-b67"}[transfer]
        primaries = "bt709" if transfer == "sdr" else "bt2020"
        matrix = "bt709" if transfer == "sdr" else "bt2020nc"
        params = (f"crf=1:vbv-maxrate=20000:vbv-bufsize=20000:level-idc=4.1:"
                  f"pools=2:frame-threads=1:repeat-headers=1:"
                  f"colorprim={primaries}:transfer={trc}:colormatrix={matrix}:range=limited")
        if transfer == "pq":
            params += (":hdr10=1:master-display=G(8500,39850)B(6550,2300)R(35400,14600)"
                       "WP(15635,16450)L(10000000,1):max-cll=1000,400")
        cmd += ["-c:v", "libx265", "-preset", "ultrafast", "-x265-params", params,
                "-tag:v", "hvc1", "-color_primaries", primaries, "-color_trc", trc,
                "-colorspace", matrix]
        cmd += ["-color_range", "tv", "-movflags", "+faststart", str(DEST / name)]
        process = subprocess.Popen(cmd, stdin=subprocess.PIPE)
        try:
            data = frame(ten_bit, kind, transfer)
            for _ in range(FPS * SECONDS):
                process.stdin.write(data)
        finally:
            process.stdin.close()
        if process.wait():
            raise SystemExit(f"Failed: {name}")
        print(name)
