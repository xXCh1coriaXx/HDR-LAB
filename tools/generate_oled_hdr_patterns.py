#!/usr/bin/env python3
"""Generate static OLED HDR10 references as exact limited-range 10-bit YCbCr.

Each output is a two-second, 24 fps HEVC Main10 clip. Spatial edges are even
pixel aligned so 4:2:0 chroma never mixes adjacent reference colors. Values
describe encoded PQ signals, not measured display luminance.
"""

from array import array
from dataclasses import dataclass
from pathlib import Path
import math
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "app/src/main/assets/patterns"
BUILD = ROOT / "app/build/pattern-generation"
W, H, FPS, SECONDS = 1280, 720, 24, 2
KR, KG, KB = 0.2627, 0.6780, 0.0593  # BT.2020 non-constant luminance.
CHANNELS = {
    "gray": 7, "red": 1, "green": 2, "blue": 4,
    "cyan": 6, "magenta": 5, "yellow": 3,
}
APL = (1, 2, 5, 10, 25, 50, 100)
DSE_PERCENT = (1, 2, 3, 5, 10, 15)
LADDER_PERCENT = (0.5, 1, 2, 3, 4, 5, 7.5, 10)
BURNIN = {
    "red": (255, 0, 0), "green": (0, 255, 0), "blue": (0, 0, 255),
    "white": (255, 255, 255), "gray50": (128, 128, 128),
    "gray20": (51, 51, 51), "cyan": (0, 255, 255),
    "magenta": (255, 0, 255), "yellow": (255, 255, 0),
}


def nearest(value):
    return math.floor(value + 0.5)


def srgb_linear(code):
    value = code / 255
    return value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4


def pq_signal(nits):
    value = (nits / 10000) ** (2610 / 16384)
    return ((3424 / 4096 + 2413 / 128 * value)
            / (1 + 2392 / 128 * value)) ** (2523 / 32)


def pq_code(nits):
    return max(64, min(940, nearest(64 + 876 * pq_signal(nits))))


def pq_nits(code):
    signal = max(0, (code - 64) / 876) ** (32 / 2523)
    numerator = max(signal - 3424 / 4096, 0)
    denominator = 2413 / 128 - 2392 / 128 * signal
    return 10000 * (numerator / denominator) ** (16384 / 2610)


def rgb_codes(source, peak=1000):
    return tuple(pq_code(srgb_linear(code) * peak) for code in source)


def neutral(code):
    return (code, code, code)


def ycbcr(rgb):
    red, green, blue = ((code - 64) / 876 for code in rgb)
    luma = KR * red + KG * green + KB * blue
    cb = (blue - luma) / (2 * (1 - KB))
    cr = (red - luma) / (2 * (1 - KR))
    return (nearest(64 + 876 * luma), nearest(512 + 896 * cb),
            nearest(512 + 896 * cr))


def reconstructed_luminance(yuv):
    """Estimate content luminance after 10-bit BT.2020 YCbCr quantization."""
    yy, cb, cr = yuv
    yy = (yy - 64) / 876
    cb = (cb - 512) / 896
    cr = (cr - 512) / 896
    red = yy + 2 * (1 - KR) * cr
    blue = yy + 2 * (1 - KB) * cb
    green = (yy - KR * red - KB * blue) / KG
    signals = (red, green, blue)
    return sum(weight * pq_nits(64 + 876 * max(0, min(1, signal)))
               for weight, signal in zip((KR, KG, KB), signals))


def even(value):
    return 2 * nearest(value / 2)


@dataclass(frozen=True)
class Spec:
    name: str
    kind: str
    peak: int = 1000
    value: object = None


def specs():
    for peak in (400, 1000, 4000):
        for area in APL:
            yield Spec(f"pq_oled_apl_{peak}_{area:02d}", "apl", peak, area)
    for percent in DSE_PERCENT:
        yield Spec(f"pq_oled_dse_{percent:02d}", "dse", 1000, percent)
    yield Spec("pq_oled_ladder", "ladder")
    for color in CHANNELS:
        yield Spec(f"pq_oled_ramp_{color}", "ramp", 1000, color)
    for color in BURNIN:
        yield Spec(f"pq_oled_burnin_{color}", "burnin", 1000, color)


def frame_and_light(spec):
    """Return raw frame and conservative nominal light metadata after quantization."""
    y = array("H", [64]) * (W * H)
    cb = array("H", [512]) * (W * H // 4)
    cr = array("H", [512]) * (W * H // 4)
    maximum = total = 0.0

    def paint(x0, y0, x1, y1, rgb):
        nonlocal maximum, total
        assert 0 <= x0 < x1 <= W and 0 <= y0 < y1 <= H
        assert not (x0 | y0 | x1 | y1) & 1, "4:2:0 edges must be even"
        yy, uu, vv = ycbcr(rgb)
        width = x1 - x0
        half = width // 2
        y_span = array("H", [yy]) * width
        u_span = array("H", [uu]) * half
        v_span = array("H", [vv]) * half
        for row in range(y0, y1):
            y[row * W + x0:row * W + x1] = y_span
        for row in range(y0 // 2, y1 // 2):
            start = row * (W // 2) + x0 // 2
            cb[start:start + half] = u_span
            cr[start:start + half] = v_span
        level = reconstructed_luminance((yy, uu, vv))
        maximum = max(maximum, level)
        total += level * width * (y1 - y0)

    if spec.kind == "apl":
        scale = math.sqrt(spec.value / 100)
        width, height = even(W * scale), even(H * scale)
        x0, y0 = even((W - width) / 2), even((H - height) / 2)
        paint(x0, y0, x0 + width, y0 + height, neutral(pq_code(spec.peak)))
    elif spec.kind == "dse":
        source = nearest(spec.value * 2.55)  # Same source code as OledTestView.code().
        paint(0, 0, W, H, neutral(pq_code(srgb_linear(source) * 100)))
    elif spec.kind == "ladder":
        # Landscape Canvas ladder: eight 88 x 88 patches at cell centers.
        for index, percent in enumerate(LADDER_PERCENT):
            source = nearest(percent * 2.55)
            x0 = 80 + index * 160 - 44
            paint(x0, 316, x0 + 88, 404,
                  neutral(pq_code(srgb_linear(source) * 100)))
    elif spec.kind == "ramp":
        mask = CHANNELS[spec.value]
        for source in range(256):
            # 1280/256 = 5px; round both boundaries to even pixels for 4:2:0.
            x0 = even(source * W / 256)
            x1 = even((source + 1) * W / 256)
            code = nearest(64 + (pq_code(1000) - 64) * source / 255)
            paint(x0, 144, x1, 576, tuple(code if mask & (1 << channel) else 64
                                          for channel in range(3)))
    elif spec.kind == "burnin":
        paint(0, 0, W, H, rgb_codes(BURNIN[spec.value]))
    else:
        raise ValueError(spec.kind)

    uv = cb + cr
    raw = y + uv
    if sys.byteorder != "little":
        raw.byteswap()
    return raw.tobytes(), math.ceil(maximum - 1e-6), math.ceil(total / (W * H) - 1e-6)


def generate():
    DEST.mkdir(parents=True, exist_ok=True)
    BUILD.mkdir(parents=True, exist_ok=True)
    log_path = BUILD / "ffmpeg-oled-hdr.log"
    all_specs = list(specs())
    assert len(all_specs) == 44
    with tempfile.TemporaryDirectory(dir=BUILD) as folder, log_path.open("w", encoding="utf-8") as log:
        raw_path = Path(folder) / "frame.yuv"
        for index, spec in enumerate(all_specs, 1):
            output = DEST / (spec.name + ".mp4")
            encoded = Path(folder) / (spec.name + ".mp4")
            raw, cll, fall = frame_and_light(spec)
            raw_path.write_bytes(raw)
            params = (
                "lossless=1:sao=0:deblock=0:pools=2:frame-threads=1:repeat-headers=1:"
                "colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:"
                "range=limited:hdr10=1:"
                "master-display=G(8500,39850)B(6550,2300)R(35400,14600)"
                f"WP(15635,16450)L({spec.peak * 10000},1):max-cll={cll},{fall}"
            )
            command = [
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-stream_loop", "-1", "-f", "rawvideo", "-pixel_format", "yuv420p10le",
                "-video_size", f"{W}x{H}", "-framerate", str(FPS), "-i", str(raw_path),
                "-frames:v", str(FPS * SECONDS), "-an", "-c:v", "libx265",
                "-preset", "ultrafast", "-profile:v", "main10", "-x265-params", params,
                "-tag:v", "hvc1", "-color_range", "tv",
                "-bsf:v", "hevc_metadata=level=4.1",
                "-movflags", "+faststart", str(encoded),
            ]
            log.write(f"\n[{index}/{len(all_specs)}] {spec.name}\n")
            log.flush()
            result = subprocess.run(command, stdout=log, stderr=log, check=False)
            if result.returncode:
                raise SystemExit(f"FFmpeg failed for {spec.name}; see {log_path}")
            encoded.replace(output)
            print(f"[{index}/{len(all_specs)}] {spec.name}: {output.stat().st_size} bytes, "
                  f"MaxCLL={cll}, MaxFALL={fall}", flush=True)


if __name__ == "__main__":
    generate()
