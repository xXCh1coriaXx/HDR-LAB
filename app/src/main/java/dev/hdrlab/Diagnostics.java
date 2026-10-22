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
import java.util.Locale;

/** Reports declarations from Android APIs; these are not panel measurements. */
final class Diagnostics {
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
}
