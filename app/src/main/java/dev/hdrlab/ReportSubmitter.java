package dev.hdrlab;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ReportSubmitter {

    public static final String APP_VERSION = "0.1.8";

    public static final String DEFAULT_ENDPOINT =
        "https://script.google.com/macros/s/AKfycbzkObCvBOclBat5ZM1np2FuY0BCy0zaXDOP9ueWwaIqH_bUUI8c5_PbqKLHJiMhXTdwuA/exec";

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    public static final class CommunityReport {
        // User perceptual survey results
        public final String sdrBlackLevel;
        public final String sdrSteps;
        public final String hdrBlackLevel;
        public final String hdrSteps;
        public final String comments;
        public final long timestamp;

        // Device & display metadata
        public final String deviceName;
        public final String model;
        public final String manufacturer;
        public final String brand;
        public final String product;
        public final String os;
        public final String androidRelease;
        public final int sdkInt;
        public final String socModel;
        public final String hardware;
        public final List<String> hdrTypes;
        public final float peakNits;
        public final float minNits;
        public final boolean wideColorGamut;
        public final String activeMode;
        public final String appVersion;

        // Legacy compatibility fields
        public final String hdr;
        public final String nits;

        public CommunityReport(String deviceName, String model, String manufacturer, String brand,
                               String product, String hardware, String socModel,
                               String os, String androidRelease, int sdkInt,
                               List<String> hdrTypes, float peakNits, float minNits,
                               boolean wideColorGamut, String activeMode, String appVersion,
                               String sdrBlackLevel, String sdrSteps,
                               String hdrBlackLevel, String hdrSteps,
                               String comments, long timestamp) {
            this.deviceName = deviceName != null && !deviceName.isEmpty()
                ? deviceName : Diagnostics.getCleanDeviceName(manufacturer, model);
            this.model = model != null ? model : "";
            this.manufacturer = manufacturer != null ? manufacturer : "";
            this.brand = brand != null ? brand : "";
            this.product = product != null ? product : "";
            this.hardware = hardware != null ? hardware : "";
            this.socModel = socModel != null ? socModel : "";
            this.os = os != null ? os : "";
            this.androidRelease = androidRelease != null ? androidRelease : "";
            this.sdkInt = sdkInt;
            this.hdrTypes = hdrTypes != null ? Collections.unmodifiableList(new ArrayList<>(hdrTypes)) : Collections.emptyList();
            this.peakNits = peakNits;
            this.minNits = minNits;
            this.wideColorGamut = wideColorGamut;
            this.activeMode = activeMode != null ? activeMode : "";
            this.appVersion = appVersion != null ? appVersion : APP_VERSION;

            this.sdrBlackLevel = sdrBlackLevel != null ? sdrBlackLevel : "";
            this.sdrSteps = sdrSteps != null ? sdrSteps : "";
            this.hdrBlackLevel = hdrBlackLevel != null ? hdrBlackLevel : "";
            this.hdrSteps = hdrSteps != null ? hdrSteps : "";
            this.comments = comments != null ? comments : "";
            this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();

            this.hdr = !this.hdrTypes.isEmpty() ? String.join(", ", this.hdrTypes) : "None";
            this.nits = this.peakNits > 0 ? String.valueOf(Math.round(this.peakNits)) : "Not reported";
        }

        public CommunityReport(String model, String manufacturer, String os, String androidRelease,
                               int sdkInt, String socModel, String hardware, List<String> hdrTypes,
                               float peakNits, float minNits, boolean wideColorGamut, String activeMode,
                               String appVersion,
                               String sdrBlackLevel, String sdrSteps,
                               String hdrBlackLevel, String hdrSteps,
                               String comments, long timestamp) {
            this(Diagnostics.getCleanDeviceName(manufacturer, model),
                 model, manufacturer,
                 Build.BRAND != null ? Build.BRAND : "",
                 Build.PRODUCT != null ? Build.PRODUCT : "",
                 hardware, socModel, os, androidRelease, sdkInt,
                 hdrTypes, peakNits, minNits, wideColorGamut, activeMode, appVersion,
                 sdrBlackLevel, sdrSteps, hdrBlackLevel, hdrSteps, comments, timestamp);
        }

        /** Legacy constructor preserved for backwards compatibility with tests and callers. */
        public CommunityReport(String model, String os, String hdr, String nits,
                               String sdrBlackLevel, String sdrSteps,
                               String hdrBlackLevel, String hdrSteps,
                               String comments, long timestamp) {
            this.model = model != null ? model : "";
            this.os = os != null ? os : "";
            this.hdr = hdr != null ? hdr : "";
            this.nits = nits != null ? nits : "";
            this.sdrBlackLevel = sdrBlackLevel != null ? sdrBlackLevel : "";
            this.sdrSteps = sdrSteps != null ? sdrSteps : "";
            this.hdrBlackLevel = hdrBlackLevel != null ? hdrBlackLevel : "";
            this.hdrSteps = hdrSteps != null ? hdrSteps : "";
            this.comments = comments != null ? comments : "";
            this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();

            this.manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER : "";
            this.brand = Build.BRAND != null ? Build.BRAND : "";
            this.product = Build.PRODUCT != null ? Build.PRODUCT : "";
            this.deviceName = Diagnostics.getCleanDeviceName(this.manufacturer, this.model);
            this.androidRelease = Build.VERSION.RELEASE != null ? Build.VERSION.RELEASE : "";
            this.sdkInt = Build.VERSION.SDK_INT;
            this.socModel = Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL != null ? Build.SOC_MODEL : "";
            this.hardware = Build.HARDWARE != null ? Build.HARDWARE : "";

            List<String> types = new ArrayList<>();
            if (hdr != null && !hdr.isEmpty() && !hdr.equalsIgnoreCase("none")) {
                for (String t : hdr.split(",\\s*")) {
                    if (!t.isEmpty()) types.add(t.trim());
                }
            }
            this.hdrTypes = Collections.unmodifiableList(types);

            float parsedPeak = -1f;
            if (nits != null) {
                try {
                    parsedPeak = Float.parseFloat(nits.replaceAll("[^0-9.]", ""));
                } catch (Exception ignored) {}
            }
            this.peakNits = parsedPeak;
            this.minNits = -1f;
            this.wideColorGamut = false;
            this.activeMode = "";
            this.appVersion = APP_VERSION;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("appVersion", appVersion);
                json.put("timestamp", timestamp);

                // Hardware identity automatically discovered from device
                json.put("deviceName", deviceName);
                json.put("model", model);
                json.put("manufacturer", manufacturer);
                json.put("brand", brand);
                json.put("product", product);
                json.put("hardware", hardware);
                json.put("socModel", socModel);

                // OS & runtime
                json.put("os", os);
                json.put("androidRelease", androidRelease);
                json.put("sdkInt", sdkInt);

                JSONArray typesArray = new JSONArray();
                for (String t : hdrTypes) {
                    typesArray.put(t);
                }
                json.put("hdrTypes", typesArray);

                if (peakNits > 0 && Float.isFinite(peakNits)) {
                    json.put("peakNits", (double) peakNits);
                } else {
                    json.put("peakNits", JSONObject.NULL);
                }

                if (minNits >= 0 && Float.isFinite(minNits)) {
                    json.put("minNits", (double) minNits);
                } else {
                    json.put("minNits", JSONObject.NULL);
                }

                json.put("wideColorGamut", wideColorGamut);
                json.put("activeMode", activeMode);

                // User survey results
                json.put("sdrBlackLevel", sdrBlackLevel);
                json.put("sdrSteps", sdrSteps);
                json.put("hdrBlackLevel", hdrBlackLevel);
                json.put("hdrSteps", hdrSteps);
                json.put("comments", comments);

                // Compatibility aliases
                json.put("hdr", hdr);
                json.put("nits", nits);
                json.put("sdr_black", sdrBlackLevel);
                json.put("sdr_steps", sdrSteps);
                json.put("hdr_black", hdrBlackLevel);
                json.put("hdr_steps", hdrSteps);
            } catch (Exception ignored) {}
            return json;
        }
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static Future<?> submit(CommunityReport report, String endpointUrl, Callback callback) {
        return executor.submit(() -> {
            try {
                String targetUrl = (endpointUrl != null && !endpointUrl.trim().isEmpty())
                    ? endpointUrl.trim() : DEFAULT_ENDPOINT;

                JSONObject payload = (report != null) ? report.toJson() : new JSONObject();
                byte[] postBytes = payload.toString().getBytes(StandardCharsets.UTF_8);

                // Handled mock/fallback submission when template ID or mock protocol is used
                if (targetUrl.contains("DEFAULT_FORM_ID") || targetUrl.startsWith("mock://")) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess();
                    });
                    return;
                }

                URL url = new URL(targetUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("User-Agent", "HDR-Lab-Light/" + APP_VERSION + " (Android)");
                conn.setFixedLengthStreamingMode(postBytes.length);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postBytes);
                }

                int code = conn.getResponseCode();
                // 200 OK, 302/303 Redirect (standard Google script redirect), or any 2xx/3xx
                if (code >= 200 && code < 400) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess();
                    });
                } else {
                    String msg = "HTTP " + code + " " + conn.getResponseMessage();
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError(msg);
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(errorMsg);
                });
            }
        });
    }
}
