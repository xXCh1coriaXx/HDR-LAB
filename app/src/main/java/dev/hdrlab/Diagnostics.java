package dev.hdrlab;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.view.Display;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reports declarations from Android APIs; these are not panel measurements. */
final class Diagnostics {

    static final class DeviceSummary {
        final String modelAndVersion;
        final String hdrSupport;
        final boolean hasHdr;
        final String wideGamut;
        final boolean isWideGamut;
        final String peakBrightness;
        final float peakBrightnessNits;

        DeviceSummary(String modelAndVersion, String hdrSupport, boolean hasHdr,
                      String wideGamut, boolean isWideGamut,
                      String peakBrightness, float peakBrightnessNits) {
            this.modelAndVersion = modelAndVersion;
            this.hdrSupport = hdrSupport;
            this.hasHdr = hasHdr;
            this.wideGamut = wideGamut;
            this.isWideGamut = isWideGamut;
            this.peakBrightness = peakBrightness;
            this.peakBrightnessNits = peakBrightnessNits;
        }
    }

    static DeviceSummary summarize(Context context, JSONObject deviceSnapshot) {
        String modelAndVersion = Build.MANUFACTURER + " " + Build.MODEL + "  ·  Android " + Build.VERSION.RELEASE;
        Display display = null;
        try { display = context.getDisplay(); }
        catch (Throwable ignored) { /* Fall back for contexts without a display */ }
        if (display == null) {
            try {
                display = context.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            } catch (Throwable ignored) {}
        }

        boolean hasHdr = false;
        String hdrSupport = "Não (Apenas SDR)";
        boolean isWideGamut = false;
        String wideGamut = "Normal (sRGB)";
        float maxLum = -1f;
        String peakBrightness = "N/D (Não declarado pelo sistema)";

        if (display != null) {
            isWideGamut = display.isWideColorGamut();
            wideGamut = isWideGamut ? "Sim (DCI-P3 / BT.2020)" : "Normal (sRGB)";

            Display.HdrCapabilities caps = display.getHdrCapabilities();
            if (caps != null && caps.getSupportedHdrTypes() != null && caps.getSupportedHdrTypes().length > 0) {
                hasHdr = true;
                List<String> types = new ArrayList<>();
                for (int t : caps.getSupportedHdrTypes()) types.add(hdrName(t));
                hdrSupport = "Sim (" + String.join(", ", types) + ")";
                maxLum = caps.getDesiredMaxLuminance();
                if (maxLum > 0 && Float.isFinite(maxLum)) {
                    int nits = Math.round(maxLum);
                    String tag = nits >= 1000 ? " (Brilho Muito Alto)" : nits >= 700 ? " (Brilho Alto)" : " (Brilho Médio)";
                    peakBrightness = "~" + nits + " nits" + tag;
                }
            }
        } else if (deviceSnapshot != null && deviceSnapshot.has("display")) {
            JSONObject screen = deviceSnapshot.optJSONObject("display");
            if (screen != null) {
                JSONArray advertised = screen.optJSONArray("advertisedHdrTypes");
                if (advertised != null && advertised.length() > 0) {
                    hasHdr = true;
                    List<String> types = new ArrayList<>();
                    for (int i = 0; i < advertised.length(); i++) types.add(advertised.optString(i));
                    hdrSupport = "Sim (" + String.join(", ", types) + ")";
                }
                isWideGamut = screen.optBoolean("wideColorGamut", false);
                wideGamut = isWideGamut ? "Sim (DCI-P3 / BT.2020)" : "Normal (sRGB)";
                double lum = screen.optDouble("desiredMaxLuminanceNits", -1);
                if (lum > 0 && Double.isFinite(lum)) {
                    int nits = (int) Math.round(lum);
                    String tag = nits >= 1000 ? " (Brilho Muito Alto)" : nits >= 700 ? " (Brilho Alto)" : " (Brilho Médio)";
                    peakBrightness = "~" + nits + " nits" + tag;
                    maxLum = (float) lum;
                }
            }
        }

        return new DeviceSummary(modelAndVersion, hdrSupport, hasHdr, wideGamut, isWideGamut, peakBrightness, maxLum);
    }

    static JSONObject device(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("manufacturer", Build.MANUFACTURER);
        root.put("model", Build.MODEL);
        root.put("android", Build.VERSION.RELEASE);
        root.put("sdk", Build.VERSION.SDK_INT);
        root.put("build", Build.DISPLAY);
        Display display = null;
        try { display = context.getDisplay(); }
        catch (UnsupportedOperationException ignored) { /* Fall back for contexts without a display. */ }
        if (display == null) display = context.getSystemService(DisplayManager.class)
            .getDisplay(Display.DEFAULT_DISPLAY);
        if (display != null) {
            JSONObject screen = new JSONObject();
            Display.HdrCapabilities caps = display.getHdrCapabilities();
            JSONArray types = new JSONArray();
            for (int t : caps == null ? new int[0] : caps.getSupportedHdrTypes()) types.put(hdrName(t));
            screen.put("advertisedHdrTypes", types);
            screen.put("hdrCapabilitiesAvailable", caps != null);
            screen.put("wideColorGamut", display.isWideColorGamut());
            putLuminance(screen, "desiredMaxLuminanceNits", caps == null ? -1 : caps.getDesiredMaxLuminance());
            putLuminance(screen, "desiredMaxAverageLuminanceNits", caps == null ? -1 : caps.getDesiredMaxAverageLuminance());
            putLuminance(screen, "desiredMinLuminanceNits", caps == null ? -1 : caps.getDesiredMinLuminance());
            Display.Mode active = display.getMode();
            screen.put("activeMode", active.getPhysicalWidth() + " × " + active.getPhysicalHeight()
                + " @ " + String.format(Locale.US, "%.2f Hz", active.getRefreshRate()));
            if (Build.VERSION.SDK_INT >= 34 && display.isHdrSdrRatioAvailable()) {
                screen.put("reportedHdrSdrRatio", display.getHdrSdrRatio());
            }
            screen.put("caveat", "OS declarations, not measured luminance or confirmation of HDR output. "
                + "HDR/SDR ratio is a display snapshot, not a per-video measurement.");
            root.put("display", screen);
        }
        JSONArray decoders = new JSONArray();
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
            if (info.isEncoder() || info.isAlias()) continue;
            for (String type : info.getSupportedTypes()) {
                if (!(type.equals("video/hevc") || type.equals("video/av01")
                        || type.equals("video/dolby-vision") || type.equals("video/x-vnd.on2.vp9"))) continue;
                JSONObject codec = new JSONObject();
                codec.put("name", info.getName());
                codec.put("mime", type);
                codec.put("hardwareAccelerated", info.isHardwareAccelerated());
                try {
                    MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(type);
                    JSONArray profiles = new JSONArray();
                    for (MediaCodecInfo.CodecProfileLevel p : capabilities.profileLevels) {
                        JSONObject profile = new JSONObject();
                        profile.put("profile", p.profile);
                        profile.put("level", p.level);
                        profiles.put(profile);
                    }
                    codec.put("profileLevels", profiles);
                } catch (RuntimeException e) { codec.put("queryError", e.toString()); }
                decoders.put(codec);
            }
        }
        root.put("decoders", decoders);
        return root;
    }

    private static void putLuminance(JSONObject out, String name, float value) throws Exception {
        out.put(name, value >= 0 && Float.isFinite(value) ? value : JSONObject.NULL);
    }

    static String hdrName(int type) {
        switch (type) {
            case Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION: return "Dolby Vision";
            case Display.HdrCapabilities.HDR_TYPE_HDR10: return "HDR10";
            case Display.HdrCapabilities.HDR_TYPE_HLG: return "HLG";
            case Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS: return "HDR10+";
            default: return "Type " + type;
        }
    }

    static JSONObject inspect(Context context, Uri uri) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, uri, null);
            JSONObject result = new JSONObject();
            JSONArray tracks = new JSONArray();
            boolean hdr = false;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                JSONObject track = new JSONObject();
                String mime = format.getString(MediaFormat.KEY_MIME);
                track.put("mime", mime);
                for (String key : new String[] { "width", "height", "frame-rate", "profile", "level",
                        "bitrate", "color-standard", "color-transfer", "color-range", "rotation-degrees" }) {
                    if (format.containsKey(key)) {
                        // Frame rate may be encoded as an integer or float.
                        try { track.put(key, format.getInteger(key)); }
                        catch (ClassCastException e) { track.put(key, format.getFloat(key)); }
                    }
                }
                if (format.containsKey(MediaFormat.KEY_DURATION))
                    track.put("durationUs", format.getLong(MediaFormat.KEY_DURATION));
                if (format.containsKey(MediaFormat.KEY_HDR_STATIC_INFO)) {
                    ByteBuffer bytes = format.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO);
                    if (bytes != null) {
                        ByteBuffer copy = bytes.duplicate();
                        StringBuilder hex = new StringBuilder();
                        while (copy.hasRemaining()) hex.append(String.format(Locale.US, "%02x", copy.get() & 255));
                        track.put("hdrStaticInfoHex", hex.toString());
                        JSONObject parsed = parseHdrStaticInfo(bytes);
                        if (parsed != null) track.put("hdrStaticMetadata", parsed);
                    }
                }
                if (mime != null && mime.startsWith("video/")) {
                    int transfer = format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)
                        ? format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) : -1;
                    hdr |= transfer == MediaFormat.COLOR_TRANSFER_ST2084
                        || transfer == MediaFormat.COLOR_TRANSFER_HLG || mime.equals("video/dolby-vision");
                    track.put("transferLabel", transferName(transfer));
                }
                tracks.put(track);
            }
            result.put("tracks", tracks);
            result.put("hdrSignaled", hdr);
            return result;
        } finally { extractor.release(); }
    }

    static String transferName(int transfer) {
        switch (transfer) {
            case MediaFormat.COLOR_TRANSFER_ST2084: return "PQ / ST 2084";
            case MediaFormat.COLOR_TRANSFER_HLG: return "HLG";
            case MediaFormat.COLOR_TRANSFER_SDR_VIDEO: return "SDR";
            case MediaFormat.COLOR_TRANSFER_LINEAR: return "Linear";
            default: return "Unspecified (" + transfer + ")";
        }
    }

    static String clipSummary(JSONObject metadata) {
        JSONArray tracks = metadata.optJSONArray("tracks");
        if (tracks != null) for (int i = 0; i < tracks.length(); i++) {
            JSONObject t = tracks.optJSONObject(i);
            if (t != null && t.optString("mime").startsWith("video/"))
                return t.optInt("width") + " × " + t.optInt("height") + "  ·  "
                    + t.optString("mime").replace("video/", "") + "  ·  " + t.optString("transferLabel")
                    + (t.has("frame-rate") ? "  ·  " + t.opt("frame-rate") + " fps" : "");
        }
        return "No video track found";
    }

    static JSONObject parseHdrStaticInfo(ByteBuffer buffer) {
        if (buffer == null || buffer.remaining() < 24) return null;
        ByteBuffer copy = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN);
        try {
            JSONObject obj = new JSONObject();
            if (copy.remaining() >= 25) {
                obj.put("descriptorType", copy.get() & 0xff);
            }
            int gx = copy.getShort() & 0xffff;
            int gy = copy.getShort() & 0xffff;
            int bx = copy.getShort() & 0xffff;
            int by = copy.getShort() & 0xffff;
            int rx = copy.getShort() & 0xffff;
            int ry = copy.getShort() & 0xffff;
            int wx = copy.getShort() & 0xffff;
            int wy = copy.getShort() & 0xffff;
            int maxLum = copy.getShort() & 0xffff;
            int minLum = copy.getShort() & 0xffff;
            int maxCll = copy.getShort() & 0xffff;
            int maxFall = copy.getShort() & 0xffff;

            JSONObject primaries = new JSONObject();
            primaries.put("red", new JSONObject().put("x", round4(rx * 0.00002)).put("y", round4(ry * 0.00002)));
            primaries.put("green", new JSONObject().put("x", round4(gx * 0.00002)).put("y", round4(gy * 0.00002)));
            primaries.put("blue", new JSONObject().put("x", round4(bx * 0.00002)).put("y", round4(by * 0.00002)));
            obj.put("displayPrimaries", primaries);

            JSONObject wp = new JSONObject();
            wp.put("x", round4(wx * 0.00002));
            wp.put("y", round4(wy * 0.00002));
            obj.put("whitePoint", wp);

            obj.put("maxMasteringLuminanceNits", maxLum);
            obj.put("minMasteringLuminanceNits", round4(minLum * 0.0001));
            obj.put("maxContentLightLevelNits", maxCll);
            obj.put("maxFrameAverageLightLevelNits", maxFall);
            return obj;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static double round4(double val) {
        return Math.round(val * 10000.0) / 10000.0;
    }
}
