package dev.hdrlab;

import android.os.Handler;
import android.os.Looper;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ReportSubmitter {

    public static final String DEFAULT_ENDPOINT =
        "https://script.google.com/macros/s/AKfycbwIv6MKctOKiJI2Cw9gqnhYvwI5Q9d__8-GD9M8rny2XiFjPwTJUgVEnK_Xc6TV7BMcNg/exec";

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    public static final class CommunityReport {
        public final String model;
        public final String os;
        public final String hdr;
        public final String nits;
        public final String sdrBlackLevel;
        public final String sdrSteps;
        public final String hdrBlackLevel;
        public final String hdrSteps;
        public final String comments;
        public final long timestamp;

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
        }
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static Future<?> submit(CommunityReport report, String endpointUrl, Callback callback) {
        return executor.submit(() -> {
            try {
                String targetUrl = (endpointUrl != null && !endpointUrl.trim().isEmpty())
                    ? endpointUrl.trim() : DEFAULT_ENDPOINT;

                // Handled mock/fallback submission when template ID or mock protocol is used
                if (targetUrl.contains("DEFAULT_FORM_ID") || targetUrl.startsWith("mock://")) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess();
                    });
                    return;
                }

                Map<String, String> params = new LinkedHashMap<>();
                params.put("model", report.model);
                params.put("os", report.os);
                params.put("hdr", report.hdr);
                params.put("nits", report.nits);
                params.put("sdr_black", report.sdrBlackLevel);
                params.put("sdr_steps", report.sdrSteps);
                params.put("hdr_black", report.hdrBlackLevel);
                params.put("hdr_steps", report.hdrSteps);
                params.put("black_level", report.hdrBlackLevel);
                params.put("steps_visible", report.hdrSteps);
                params.put("comments", report.comments);
                params.put("timestamp", String.valueOf(report.timestamp));

                StringBuilder postBody = new StringBuilder();
                boolean first = true;
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    if (!first) postBody.append('&');
                    first = false;
                    postBody.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                    postBody.append('=');
                    postBody.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
                }

                byte[] postBytes = postBody.toString().getBytes(StandardCharsets.UTF_8);

                URL url = new URL(targetUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("User-Agent", "HDR-Lab-Light/0.1.6 (Android)");
                conn.setFixedLengthStreamingMode(postBytes.length);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postBytes);
                }

                int code = conn.getResponseCode();
                // 200 OK, 302/303 Redirect (standard Google Forms success), or any 2xx
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
