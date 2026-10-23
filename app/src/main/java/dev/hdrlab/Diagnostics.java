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

    @FunctionalInterface
    interface PropertyGetter {
        String get(String key);
    }

    static String getSystemProperty(String key) {
        if ("SEM_PLATFORM_INT".equals(key)) {
            try {
                java.lang.reflect.Field f = Build.VERSION.class.getField("SEM_PLATFORM_INT");
                int sem = f.getInt(null);
                return sem > 0 ? String.valueOf(sem) : null;
            } catch (Throwable ignored) {
                return null;
            }
        }
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = sp.getMethod("get", String.class);
            String val = (String) get.invoke(null, key);
            return (val != null && !val.trim().isEmpty()) ? val.trim() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        str = str.trim();
        if (str.isEmpty()) return "";
        char first = str.charAt(0);
        if (Character.isUpperCase(first)) return str;
        return Character.toUpperCase(first) + str.substring(1);
    }

    static String getCleanDeviceName() {
        return getCleanDeviceName(Build.MANUFACTURER, Build.MODEL);
    }

    static String getCleanDeviceName(String manufacturer, String model) {
        if (manufacturer == null) manufacturer = "";
        if (model == null) model = "";
        manufacturer = manufacturer.trim();
        model = model.trim();

        if (manufacturer.isEmpty()) return capitalize(model);
        if (model.isEmpty()) return capitalize(manufacturer);

        String capManufacturer = capitalize(manufacturer);
        // If model already begins with the manufacturer name, avoid redundant prefix
        if (model.toLowerCase(Locale.US).startsWith(manufacturer.toLowerCase(Locale.US))) {
            return capitalize(model);
        }
        return capManufacturer + " " + model;
    }

    static String detectOsInfo() {
        return detectOsInfo(Diagnostics::getSystemProperty, Build.MANUFACTURER, Build.VERSION.RELEASE);
    }

    static String detectOsInfo(PropertyGetter getter, String manufacturer, String androidRelease) {
        if (getter == null) getter = k -> null;
        if (manufacturer == null) manufacturer = "";
        if (androidRelease == null) androidRelease = "";

        // 1. Honor: ro.build.version.magic -> "MagicOS X.X"
        String magic = getter.get("ro.build.version.magic");
        if (magic != null && !magic.isEmpty()) {
            String os = magic.toLowerCase(Locale.US).contains("magicos") ? magic : "MagicOS " + magic;
            return formatOsWithAndroid(os, androidRelease);
        }

        // 2. Xiaomi/Redmi/POCO: ro.mi.os.version.name -> "HyperOS X.X", or ro.miui.ui.version.name -> "MIUI X.X"
        String hyperOs = getter.get("ro.mi.os.version.name");
        if (hyperOs != null && !hyperOs.isEmpty()) {
            String os = hyperOs.toLowerCase(Locale.US).contains("hyperos") ? hyperOs : "HyperOS " + hyperOs;
            return formatOsWithAndroid(os, androidRelease);
        }
        String miui = getter.get("ro.miui.ui.version.name");
        if (miui != null && !miui.isEmpty()) {
            String os = miui.toLowerCase(Locale.US).contains("miui") ? miui : "MIUI " + miui;
            return formatOsWithAndroid(os, androidRelease);
        }

        // 3. Samsung: ro.build.version.oneui or SEM_PLATFORM_INT -> "One UI X.X"
        String oneUi = getter.get("ro.build.version.oneui");
        if (oneUi == null || oneUi.isEmpty()) {
            oneUi = getter.get("ro.build.version.sep");
        }
        String semStr = getter.get("SEM_PLATFORM_INT");
        if ((oneUi != null && !oneUi.isEmpty()) || (semStr != null && !semStr.isEmpty()) || "samsung".equalsIgnoreCase(manufacturer)) {
            String parsedVersion = null;
            if (oneUi != null && !oneUi.isEmpty()) {
                if (oneUi.matches("\\d{4,6}")) {
                    try {
                        int v = Integer.parseInt(oneUi);
                        int major = v / 10000;
                        int minor = (v % 10000) / 100;
                        parsedVersion = major + "." + minor;
                    } catch (NumberFormatException ignored) {}
                } else if (oneUi.toLowerCase(Locale.US).contains("one ui")) {
                    return formatOsWithAndroid(oneUi, androidRelease);
                } else {
                    parsedVersion = oneUi;
                }
            }
            if (parsedVersion == null && semStr != null && !semStr.isEmpty()) {
                try {
                    int sem = Integer.parseInt(semStr);
                    int oneUiVal = sem - 90000;
                    if (oneUiVal > 0) {
                        int major = oneUiVal / 10000;
                        int minor = (oneUiVal % 10000) / 100;
                        parsedVersion = major + "." + minor;
                    }
                } catch (NumberFormatException ignored) {}
            }
            if (parsedVersion != null) {
                return formatOsWithAndroid("One UI " + parsedVersion, androidRelease);
            } else if ("samsung".equalsIgnoreCase(manufacturer)) {
                return formatOsWithAndroid("One UI", androidRelease);
            }
        }

        // 4. OnePlus: ro.oxygen.version -> "OxygenOS X.X"
        String oxygen = getter.get("ro.oxygen.version");
        if (oxygen != null && !oxygen.isEmpty()) {
            String os = oxygen.toLowerCase(Locale.US).contains("oxygenos") ? oxygen : "OxygenOS " + oxygen;
            return formatOsWithAndroid(os, androidRelease);
        }

        // 5. OPPO: ro.build.version.opporom -> "ColorOS X.X"
        String oppo = getter.get("ro.build.version.opporom");
        if (oppo != null && !oppo.isEmpty()) {
            String os = oppo.toLowerCase(Locale.US).contains("coloros") ? oppo : "ColorOS " + oppo;
            return formatOsWithAndroid(os, androidRelease);
        }

        // 6. Realme: ro.build.version.realmeui -> "Realme UI X.X"
        String realme = getter.get("ro.build.version.realmeui");
        if (realme != null && !realme.isEmpty()) {
            String os = realme.toLowerCase(Locale.US).contains("realme ui") ? realme : "Realme UI " + realme;
            return formatOsWithAndroid(os, androidRelease);
        }

        // 7. Vivo: ro.vivo.os.version -> "OriginOS / Funtouch OS X.X"
        String vivo = getter.get("ro.vivo.os.version");
        if (vivo != null && !vivo.isEmpty()) {
            String lower = vivo.toLowerCase(Locale.US);
            String os = (lower.contains("origin") || lower.contains("funtouch"))
                ? vivo : "OriginOS / Funtouch OS " + vivo;
            return formatOsWithAndroid(os, androidRelease);
        }

        // 8. Google: Build.MANUFACTURER equals "Google" -> "Pixel UI"
        if ("Google".equalsIgnoreCase(manufacturer)) {
            return formatOsWithAndroid("Pixel UI", androidRelease);
        }

        // 9. Nothing: ro.nothing.version -> "Nothing OS X.X"
        String nothing = getter.get("ro.nothing.version");
        if (nothing != null && !nothing.isEmpty()) {
            String os = nothing.toLowerCase(Locale.US).contains("nothing os") ? nothing : "Nothing OS " + nothing;
            return formatOsWithAndroid(os, androidRelease);
        }

        // 10. Generic Custom ROM / OS Properties (e.g. LineageOS, GrapheneOS, etc.)
        String modVersion = getter.get("ro.modversion");
        if (modVersion != null && !modVersion.isEmpty()) {
            return formatOsWithAndroid(modVersion, androidRelease);
        }
        String romVersion = getter.get("ro.rom.version");
        if (romVersion != null && !romVersion.isEmpty()) {
            return formatOsWithAndroid(romVersion, androidRelease);
        }

        // 11. Fallback: "Android " + Build.VERSION.RELEASE
        return "Android " + androidRelease;
    }

    private static String formatOsWithAndroid(String customOs, String androidRelease) {
        if (androidRelease != null && !androidRelease.isEmpty()) {
            return customOs + " (Android " + androidRelease + ")";
        }
        return customOs;
    }

    static final class DeviceSummary {
        final String modelAndVersion;
        final String osInfo;
        final String hdrSupport;
        final boolean hasHdr;
        final String wideGamut;
        final boolean isWideGamut;
        final String peakBrightness;
        final float peakBrightnessNits;
        final float minLuminanceNits;
        final List<String> hdrTypes;
        final String activeMode;

        DeviceSummary(String modelAndVersion, String hdrSupport, boolean hasHdr,
                      String wideGamut, boolean isWideGamut,
                      String peakBrightness, float peakBrightnessNits) {
            this(modelAndVersion, detectOsInfo(), hdrSupport, hasHdr, wideGamut, isWideGamut,
                 peakBrightness, peakBrightnessNits, -1f, java.util.Collections.emptyList(), "");
        }

        DeviceSummary(String modelAndVersion, String osInfo, String hdrSupport, boolean hasHdr,
                      String wideGamut, boolean isWideGamut,
                      String peakBrightness, float peakBrightnessNits) {
            this(modelAndVersion, osInfo, hdrSupport, hasHdr, wideGamut, isWideGamut,
                 peakBrightness, peakBrightnessNits, -1f, java.util.Collections.emptyList(), "");
        }

        DeviceSummary(String modelAndVersion, String osInfo, String hdrSupport, boolean hasHdr,
                      String wideGamut, boolean isWideGamut,
                      String peakBrightness, float peakBrightnessNits,
                      float minLuminanceNits, List<String> hdrTypes, String activeMode) {
            this.modelAndVersion = modelAndVersion;
            this.osInfo = osInfo;
            this.hdrSupport = hdrSupport;
            this.hasHdr = hasHdr;
            this.wideGamut = wideGamut;
            this.isWideGamut = isWideGamut;
            this.peakBrightness = peakBrightness;
            this.peakBrightnessNits = peakBrightnessNits;
            this.minLuminanceNits = minLuminanceNits;
            this.hdrTypes = hdrTypes != null ? java.util.Collections.unmodifiableList(new ArrayList<>(hdrTypes)) : java.util.Collections.emptyList();
            this.activeMode = activeMode != null ? activeMode : "";
        }
    }

    static DeviceSummary summarize(Context context, JSONObject deviceSnapshot) {
        String osInfo = detectOsInfo();
        String modelAndVersion = getCleanDeviceName(Build.MANUFACTURER, Build.MODEL) + "  ·  " + osInfo;
        Display display = null;
        try { display = context != null ? context.getDisplay() : null; }
        catch (Throwable ignored) { /* Fall back for contexts without a display */ }
        if (display == null && context != null) {
            try {
                display = context.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            } catch (Throwable ignored) {}
        }

        boolean hasHdr = false;
        String hdrSupport = context != null ? context.getString(R.string.hdr_support_no) : "No (SDR only)";
        boolean isWideGamut = false;
        String wideGamut = formatGamut(context, false);
        float maxLum = -1f;
        String peakBrightness = context != null ? context.getString(R.string.brightness_not_reported) : "Not reported by system";
        float minLum = -1f;
        String activeMode = "";
        List<String> hdrTypes = new ArrayList<>();

        if (display != null) {
            isWideGamut = display.isWideColorGamut();
            wideGamut = formatGamut(context, isWideGamut);

            Display.HdrCapabilities caps = display.getHdrCapabilities();
            if (caps != null && caps.getSupportedHdrTypes() != null && caps.getSupportedHdrTypes().length > 0) {
                hasHdr = true;
                for (int t : caps.getSupportedHdrTypes()) hdrTypes.add(hdrName(t));
                hdrSupport = formatHdrSupport(context, hdrTypes);
                maxLum = caps.getDesiredMaxLuminance();
                if (maxLum > 0 && Float.isFinite(maxLum)) {
                    peakBrightness = formatPeakBrightness(context, Math.round(maxLum));
                }
                minLum = caps.getDesiredMinLuminance();
            }
            Display.Mode active = display.getMode();
            if (active != null) {
                activeMode = active.getPhysicalWidth() + " × " + active.getPhysicalHeight()
                    + " @ " + String.format(Locale.US, "%.2f Hz", active.getRefreshRate());
            }
        } else if (deviceSnapshot != null && deviceSnapshot.has("display")) {
            JSONObject screen = deviceSnapshot.optJSONObject("display");
            if (screen != null) {
                JSONArray advertised = screen.optJSONArray("advertisedHdrTypes");
                if (advertised != null && advertised.length() > 0) {
                    hasHdr = true;
                    for (int i = 0; i < advertised.length(); i++) hdrTypes.add(advertised.optString(i));
                    hdrSupport = formatHdrSupport(context, hdrTypes);
                }
                isWideGamut = screen.optBoolean("wideColorGamut", false);
                wideGamut = formatGamut(context, isWideGamut);
                double lum = screen.optDouble("desiredMaxLuminanceNits", -1);
                if (lum > 0 && Double.isFinite(lum)) {
                    peakBrightness = formatPeakBrightness(context, (int) Math.round(lum));
                    maxLum = (float) lum;
                }
                double min = screen.optDouble("desiredMinLuminanceNits", -1);
                if (min >= 0 && Double.isFinite(min)) {
                    minLum = (float) min;
                }
                activeMode = screen.optString("activeMode", "");
            }
        }

        return new DeviceSummary(modelAndVersion, osInfo, hdrSupport, hasHdr, wideGamut, isWideGamut, peakBrightness, maxLum, minLum, hdrTypes, activeMode);
    }

    private static String formatGamut(Context context, boolean isWideGamut) {
        return isWideGamut
            ? (context != null ? context.getString(R.string.gamut_wide) : "Yes (DCI-P3 / BT.2020)")
            : (context != null ? context.getString(R.string.gamut_normal) : "Normal (sRGB)");
    }

    private static String formatHdrSupport(Context context, List<String> types) {
        String joined = String.join(", ", types);
        return context != null
            ? context.getString(R.string.hdr_support_yes, joined)
            : "Yes (" + joined + ")";
    }

    private static String formatPeakBrightness(Context context, int nits) {
        String tag = nits >= 1000
            ? (context != null ? context.getString(R.string.brightness_tag_very_high) : " (Very High Brightness)")
            : nits >= 700
            ? (context != null ? context.getString(R.string.brightness_tag_high) : " (High Brightness)")
            : (context != null ? context.getString(R.string.brightness_tag_medium) : " (Medium Brightness)");
        return context != null
            ? context.getString(R.string.brightness_nits_format, nits, tag)
            : "~" + nits + " nits" + tag;
    }

    static JSONObject device(Context context) throws Exception {
        JSONObject root = new JSONObject();
        String cleanDevice = getCleanDeviceName(Build.MANUFACTURER, Build.MODEL);
        root.put("deviceName", cleanDevice);
        root.put("manufacturer", Build.MANUFACTURER != null ? Build.MANUFACTURER : "");
        root.put("brand", Build.BRAND != null ? Build.BRAND : "");
        root.put("model", Build.MODEL != null ? Build.MODEL : "");
        root.put("product", Build.PRODUCT != null ? Build.PRODUCT : "");
        root.put("device", Build.DEVICE != null ? Build.DEVICE : "");
        root.put("board", Build.BOARD != null ? Build.BOARD : "");
        root.put("hardware", Build.HARDWARE != null ? Build.HARDWARE : "");
        if (Build.VERSION.SDK_INT >= 31) {
            root.put("socModel", Build.SOC_MODEL != null ? Build.SOC_MODEL : "");
        }
        root.put("os", detectOsInfo());
        root.put("android", Build.VERSION.RELEASE != null ? Build.VERSION.RELEASE : "");
        root.put("sdk", Build.VERSION.SDK_INT);
        root.put("build", Build.DISPLAY != null ? Build.DISPLAY : "");
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
