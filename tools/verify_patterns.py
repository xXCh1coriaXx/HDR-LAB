#!/usr/bin/env python3
"""Check file signaling and decoded code values on the first and last frames."""
from array import array
import json
from pathlib import Path
import subprocess
import sys
sys.dont_write_bytecode = True
from generate_oled_hdr_patterns import FPS as OLED_FPS, SECONDS as OLED_SECONDS
from generate_oled_hdr_patterns import frame_and_light, pq_code, specs

DEST = Path(__file__).resolve().parents[1] / "app/src/main/assets/patterns"
W, H = 1280, 720
results = []
paths = sorted(DEST.glob("*.mp4"))
oled_specs = {spec.name: spec for spec in specs()}
assert len(oled_specs) == 44
assert [pq_code(value) for value in (400, 1000, 4000)] == [636, 723, 855]
color_endpoints = {
    "gray": (723, 512, 512), "red": (237, 418, 849),
    "green": (511, 269, 202), "blue": (103, 849, 485),
    "cyan": (550, 606, 175), "magenta": (276, 755, 822),
    "yellow": (684, 175, 539),
}
burnin_endpoints = {**color_endpoints, "white": color_endpoints["gray"],
                    "gray50": (578, 512, 512), "gray20": (416, 512, 512)}
original_names = {f"{t}_{k}" for t in ("sdr", "pq", "hlg") for k in ("black", "steps", "window")}
assert {p.stem for p in paths} == original_names | set(oled_specs)
for path in paths:
    if path.stem in oled_specs:
        spec = oled_specs[path.stem]
        probe = json.loads(subprocess.check_output([
            "ffprobe", "-v", "error", "-show_streams", "-of", "json", str(path)]))
        info = probe["streams"][0]
        assert info["profile"] == "Main 10", path.name
        assert info["width"] == W and info["height"] == H, path.name
        assert info["pix_fmt"] == "yuv420p10le", path.name
        assert info["color_range"] == "tv", path.name
        assert info["color_transfer"] == "smpte2084", path.name
        assert info["color_primaries"] == "bt2020", path.name
        assert info["color_space"] == "bt2020nc", path.name
        assert info["codec_tag_string"] == "hvc1", path.name
        assert info["level"] == 123, path.name  # HEVC Level 4.1.
        assert info["r_frame_rate"] == f"{OLED_FPS}/1", path.name
        assert int(info["nb_frames"]) == OLED_FPS * OLED_SECONDS, path.name

        frames = json.loads(subprocess.check_output([
            "ffprobe", "-v", "error", "-select_streams", "v:0", "-read_intervals", "%+#1",
            "-show_frames", "-show_entries", "frame=side_data_list", "-of", "json", str(path)]))
        side = {part["side_data_type"]: part for part in frames["frames"][0]["side_data_list"]}
        expected, cll, fall = frame_and_light(spec)
        cll, fall = max(1, cll), max(1, fall)
        mastering = side["Mastering display metadata"]
        assert mastering["max_luminance"] == f"{spec.peak * 10000}/10000", path.name
        assert mastering["min_luminance"] == "1/10000", path.name
        light = side["Content light level metadata"]
        assert light["max_content"] == cll, (path.name, light, cll)
        assert light["max_average"] == fall, (path.name, light, fall)

        last = OLED_FPS * OLED_SECONDS - 1
        decoded = subprocess.check_output([
            "ffmpeg", "-v", "error", "-i", str(path),
            "-vf", f"select=eq(n\\,0)+eq(n\\,{last})", "-fps_mode", "passthrough",
            "-f", "rawvideo", "-pix_fmt", "yuv420p10le", "pipe:1"])
        assert len(decoded) == len(expected) * 2, (path.name, len(decoded), len(expected))
        for index in range(2):
            actual = decoded[index * len(expected):(index + 1) * len(expected)]
            assert actual == expected, (path.name, "decoded frame", index,
                                        next((i for i, (a, b) in enumerate(zip(actual, expected)) if a != b), None))
        first = array("H")
        first.frombytes(decoded[:len(expected)])
        if sys.byteorder != "little": first.byteswap()
        y = first[:W * H]
        cb = first[W * H:W * H + W * H // 4]
        cr = first[W * H + W * H // 4:]

        def sample(x, row):
            chroma = (row // 2) * (W // 2) + x // 2
            return y[row * W + x], cb[chroma], cr[chroma]

        if spec.kind == "apl":
            assert y[360 * W + 640] == {400: 636, 1000: 723, 4000: 855}[spec.peak], path.name
            if spec.peak == 1000 and spec.value in (1, 50, 100):
                x0, y0, width, height = {
                    1: (576, 324, 128, 72),
                    50: (188, 106, 906, 510),
                    100: (0, 0, 1280, 720),
                }[spec.value]
                assert y.count(723) == width * height, (path.name, "APL geometry")
                if spec.value < 100:
                    assert y[y0 * W + x0 - 1] == 64, path.name
                    assert y[(y0 + height) * W + x0] == 64, path.name
        elif spec.kind == "dse":
            assert y[360 * W + 640] == {1: 116, 2: 129, 3: 142,
                                          5: 159, 10: 197, 15: 227}[spec.value], path.name
        elif spec.kind == "ladder":
            assert [y[360 * W + x] for x in (80, 240, 1200)] == [96, 116, 197], path.name
        elif spec.kind == "ramp":
            assert sample(640, 10) == (64, 512, 512), path.name
            assert sample(640, 700) == (64, 512, 512), path.name
            assert sample(0, 360) == (64, 512, 512), path.name
            assert sample(W - 1, 360) == color_endpoints[spec.value], path.name
        elif spec.kind == "burnin":
            assert sample(640, 360) == burnin_endpoints[spec.value], path.name
        results.append({"file": path.name, "transfer": "smpte2084", "range": "limited",
                        "profile": info["profile"], "level": info["level"],
                        "maxCLL": cll, "maxFALL": fall,
                        "decodedFirstAndLastFrameChecks": "exact YCbCr match"})
        continue

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
