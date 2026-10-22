package dev.hdrlab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.transformer.*;
import androidx.media3.ui.PlayerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public final class MainActivity extends Activity {
    private static final int BG = 0xff101411, CARD = 0xff1b211c, TEXT = 0xffedf2e9;
    private static final int MUTED = 0xffa7b2a5, ACCENT = 0xffc8f578, PICK = 10, SAVE_VIDEO = 11;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout root, page, workspaceLayout, testingSection, headerSection;
    private LinearLayout deviceSection, sourceSection, conversionSection, controlsSection;
    private ScrollView controlScroll, testScroll;
    private boolean wideLayout, layoutInitialized;
    private int videoWidth = 16, videoHeight = 9;
    private FrameLayout videoFrame;
    private PlayerView playerView;
    private LinearLayout fullscreenHud;
    private Button fsOriginalButton, fsCodecButton, fsGpuButton;
    private Button fsPrevFrameButton, fsNextFrameButton;
    private Display.HdrSdrRatioListener hdrSdrRatioListener;
    private final Runnable hideFullscreenHud = () -> {
        if (fullscreenHud != null && fullscreen) {
            fullscreenHud.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> { if (fullscreenHud != null) fullscreenHud.setVisibility(View.GONE); }).start();
        }
    };
    private TextView status, formatLabel, capabilityLabel, progressLabel, modeLabel, clipLabel;
    private Button originalButton, codecButton, gpuButton, exportButton, shareVideoButton, cancelButton;
    private EditText notes;
    private Spinner duration;
    private ExoPlayer player;
    private Transformer transformer;
    private Uri source;
    private String sourceName = "No clip loaded", decoder = "Not initialized", lastError = "";
    private JSONObject sourceMetadata = new JSONObject(), device = new JSONObject();
    private final JSONArray events = new JSONArray();
    private final Map<String, Variant> variants = new HashMap<>();
    private String mode = "original";
    private long lastPosition, droppedFrames;
    private boolean inspectionPending, hdr, fullscreen, started, resumePlaying;
    private float fixedBrightness = -1f;
    private File pendingExport, savingVideo;
    private ProgressHolder progressHolder;
    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            if (transformer == null) return;
            progressLabel.setVisibility(View.VISIBLE);
            int state = transformer.getProgress(progressHolder);
            progressLabel.setText(state == Transformer.PROGRESS_STATE_AVAILABLE
                ? "Tone mapping · " + progressHolder.progress + "%" : "Preparing tone mapper…");
            handler.postDelayed(this, 500);
        }
    };

    private static final class Variant {
        final Uri uri;
        final File file;
        final long start;
        final JSONObject metadata;
        Variant(Uri uri, File file, long start, JSONObject metadata) {
            this.uri = uri; this.file = file; this.start = start; this.metadata = metadata;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        worker.execute(() -> {
            try {
                JSONObject snapshot = Diagnostics.device(this);
                handler.post(() -> {
                    if (isDestroyed()) return;
                    device = snapshot;
                    JSONObject display = device.optJSONObject("display");
                    capabilityLabel.setText(android.os.Build.MODEL + "  ·  Android " + android.os.Build.VERSION.RELEASE
                        + "\n" + (display == null ? "Display unavailable" : !display.optBoolean("hdrCapabilitiesAvailable", true)
                            ? "HDR capabilities unavailable" : display.optJSONArray("advertisedHdrTypes"))
                        + "\nReported by Android · not measured output");
                });
            } catch (Exception e) { handler.post(() -> capabilityLabel.setText("Capability query: " + e.getMessage())); }
        });
        if (state != null) {
            lastPosition = state.getLong("position");
            notes.setText(state.getString("notes", ""));
        } else notes.setText(getPreferences(0).getString("notes", ""));
        String saved = state != null ? state.getString("source")
            : getPreferences(0).getString("source", null);
        if (saved != null) loadSource(Uri.parse(saved), false);
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            try {
                Display display = getDisplay();
                if (display != null && display.isHdrSdrRatioAvailable()) {
                    hdrSdrRatioListener = (d) -> {
                        float ratio = d.getHdrSdrRatio();
                        record("hdrSdrRatioChanged", "Ratio: " + ratio);
                    };
                    display.registerHdrSdrRatioChangedListener(getMainExecutor(), hdrSdrRatioListener);
                }
            } catch (Throwable ignored) {}
        }
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                | android.view.WindowInsets.Type.displayCutout());
            root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        workspaceLayout = new LinearLayout(this);
        controlScroll = new ScrollView(this);
        controlScroll.setFillViewport(true);
        controlScroll.setClipToPadding(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(20), dp(20), dp(28));
        controlScroll.addView(page);
        workspaceLayout.addView(controlScroll, new LinearLayout.LayoutParams(-1, -1));
        root.addView(workspaceLayout, new LinearLayout.LayoutParams(-1, -1));
        testScroll = new ScrollView(this);
        testScroll.setFillViewport(true);
        setContentView(root);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) root.post(this::applyResponsiveLayout);
        });
        root.post(this::applyResponsiveLayout);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                () -> { if (fullscreen) setFullscreen(false); else finish(); });
        }
        headerSection = new LinearLayout(this);
        headerSection.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = label("DISPLAY DIAGNOSTICS", 10, ACCENT);
        eyebrow.setLetterSpacing(.14f); headerSection.addView(eyebrow);
        headerSection.addView(label("HDR Lab", 34, TEXT));
        headerSection.addView(label("A closer look at your display.", 14, MUTED));
        add(headerSection);
        LinearLayout cap = deviceSection = card();
        capabilityLabel = label(android.os.Build.MODEL + "\nReading HDR capabilities…", 14, TEXT);
        cap.addView(capabilityLabel);
        Button detail = button("Device & decoder details", () -> {
            worker.execute(() -> {
                try {
                    JSONObject snapshot = Diagnostics.device(this);
                    String details = snapshot.toString(2);
                    handler.post(() -> {
                        if (isDestroyed()) return;
                        device = snapshot;
                        showText("Android capability report", details);
                    });
                } catch (Exception e) { handler.post(() -> error("Capability query failed", e)); }
            });
        }); cap.addView(detail);

        LinearLayout sourceCard = sourceSection = card();
        sourceCard.addView(label("01  /  SOURCE", 11, ACCENT));
        clipLabel = label(sourceName, 18, TEXT); sourceCard.addView(clipLabel);
        Button open = button("+  Open video", this::pickVideo);
        primaryStyle(open); sourceCard.addView(open);
        sourceCard.addView(button("Built-in black & near-black tests", this::choosePattern));
        sourceCard.addView(button("Source signal details", () -> {
            try { showText("Source signal metadata", sourceMetadata.toString(2)
                + "\n\nAndroid range: 1 = full, 2 = limited. Transfers: 3 = SDR, 6 = PQ, 7 = HLG. "
                + "Missing metadata is not proof of a particular range or transfer. These describe the file, not measured output."); }
            catch (Exception e) { error("Metadata display failed", e); }
        }));
        sourceCard.addView(label("Local files only. No account, uploads, or storage permission.", 12, MUTED));

        testingSection = new LinearLayout(this);
        testingSection.setOrientation(LinearLayout.VERTICAL);
        testingSection.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) resizeVideo();
        });
        add(testingSection);
        testingSection.addView(label("THE TEST BENCH", 11, MUTED));
        modeLabel = label("Choose a video or a built-in test to begin", 14, ACCENT);
        modeLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        modeLabel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        testingSection.addView(modeLabel);
        videoFrame = new FrameLayout(this);
        videoFrame.setBackgroundColor(Color.BLACK);
        testingSection.addView(videoFrame, new LinearLayout.LayoutParams(-1, dp(240)));
        playerView = (PlayerView) getLayoutInflater().inflate(R.layout.player, videoFrame, false);
        playerView.setKeepContentOnPlayerReset(false);
        videoFrame.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        buildFullscreenHud();
        videoFrame.setOnClickListener(v -> { if (fullscreen) showFullscreenHud(); });
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            if (visibility == View.VISIBLE && fullscreen) showFullscreenHud();
        });
        LinearLayout playControls = new LinearLayout(this);
        playControls.setOrientation(LinearLayout.HORIZONTAL);
        Button fsBtn = button("↗  Fullscreen / dark-room view", () -> setFullscreen(true));
        Button prevFrame = button("◀  Step", () -> stepFrame(-1));
        Button nextFrame = button("Step  ▶", () -> stepFrame(1));
        LinearLayout.LayoutParams pFs = new LinearLayout.LayoutParams(0, -2, 3);
        pFs.setMarginEnd(dp(4));
        LinearLayout.LayoutParams pStep = new LinearLayout.LayoutParams(0, -2, 2);
        pStep.setMarginStart(dp(2)); pStep.setMarginEnd(dp(2));
        playControls.addView(fsBtn, pFs);
        playControls.addView(prevFrame, pStep);
        playControls.addView(nextFrame, pStep);
        testingSection.addView(playControls);

        LinearLayout compare = card();
        page.removeView(compare); testingSection.addView(compare);
        compare.addView(label("COMPARE THE SAME MOMENT", 11, ACCENT));
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        originalButton = button("Original", () -> switchMode("original"));
        codecButton = button("SDR · Codec", () -> switchMode("codec"));
        gpuButton = button("SDR · GPU", () -> switchMode("gpu"));
        for (Button choice : new Button[] {originalButton, codecButton, gpuButton}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(80), 1);
            p.setMarginEnd(dp(4)); p.setMarginStart(dp(4));
            choice.setPadding(dp(4), dp(8), dp(4), dp(8));
            choices.addView(choice, p);
        }
        compare.addView(choices);
        compare.addView(label("The filled button is the active view. Create an SDR copy to unlock its comparison. "
            + "Switching keeps the same moment within the converted section.", 12, MUTED));
        formatLabel = label("Open a clip to inspect its signal and decoder.", 12, MUTED);
        testingSection.addView(formatLabel);
        status = label("Ready when you are", 13, TEXT); testingSection.addView(status);

        LinearLayout conversion = conversionSection = card();
        conversion.addView(label("02  /  SDR COMPARISONS", 11, ACCENT));
        conversion.addView(label("Choose a section to tone map, then select its SDR view on the test bench.", 12, MUTED));
        duration = new Spinner(this);
        ArrayAdapter<String> lengths = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[] { "10 seconds from playhead", "30 seconds from playhead", "Full clip" });
        duration.setAdapter(lengths); duration.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        duration.setContentDescription("Length of the section to convert");
        conversion.addView(duration);
        exportButton = button("Create SDR copy…", this::chooseExport); primaryStyle(exportButton); conversion.addView(exportButton);
        progressLabel = label("", 13, ACCENT); conversion.addView(progressLabel);
        cancelButton = button("Cancel conversion", this::cancelExport); conversion.addView(cancelButton);
        shareVideoButton = button("Save selected SDR video", this::saveVideo); conversion.addView(shareVideoButton);
        conversion.addView(button("Clear generated SDR copies", this::clearExports));

        LinearLayout controls = controlsSection = card();
        controls.addView(label("03  /  CONTROL THE TEST", 11, ACCENT));
        controls.addView(label("Brightness applies to this window. Your device may apply further limits or boosts.", 12, MUTED));
        Switch lock = new Switch(this);
        lock.setText("Use fixed brightness (80%)"); lock.setTextColor(TEXT); controls.addView(lock);
        SeekBar brightness = new SeekBar(this); brightness.setMax(100); brightness.setProgress(80);
        brightness.setEnabled(false); controls.addView(brightness);
        lock.setOnCheckedChangeListener((b, checked) -> {
            brightness.setEnabled(checked);
            fixedBrightness = checked ? Math.max(.01f, brightness.getProgress() / 100f) : -1f;
            applyBrightness();
        });
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean user) {
                lock.setText("Use fixed brightness (" + p + "%)");
                if (lock.isChecked()) { fixedBrightness = Math.max(.01f, p / 100f); applyBrightness(); }
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        controls.addView(button("Black-level test guide", this::showGuide));
        controls.addView(label("OBSERVATIONS", 11, ACCENT));
        notes = new EditText(this);
        notes.setTextColor(TEXT); notes.setHintTextColor(MUTED);
        notes.setHint("Room light, brightness, video enhancements, black lift, visible patch count…");
        notes.setMinLines(3); notes.setGravity(Gravity.TOP); controls.addView(notes);
        controls.addView(button("Share diagnostic report", this::shareReport));
        controls.addView(label("Reports contain device/build details, clip name, metadata, decoder, conversion results, and your notes. "
            + "Playback metadata does not prove that the panel entered HDR mode.", 12, MUTED));
        refreshButtons();
    }

    private void pickVideo() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(pick, PICK);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == PICK) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) { /* Provider may only grant a transient read. */ }
            loadSource(uri, true);
        } else if (request == SAVE_VIDEO && savingVideo != null) {
            File file = savingVideo;
            Uri destination = data.getData();
            worker.execute(() -> {
                try (InputStream input = new FileInputStream(file);
                     OutputStream output = getContentResolver().openOutputStream(destination)) {
                    if (output == null) throw new IOException("Document provider did not open an output stream");
                    copy(input, output);
                    handler.post(() -> status.setText("SDR video saved"));
                } catch (Exception e) { handler.post(() -> error("Save failed", e)); }
            });
        }
    }

    private void choosePattern() {
        String[] labels = { "SDR · reference black", "HDR10 / PQ · reference black", "HLG · reference black",
            "SDR · near-black steps", "HDR10 / PQ · near-black steps", "HLG · near-black steps",
            "SDR · black + white window", "HDR10 / PQ · black + 1000-nit window", "HLG · black + peak-signal window" };
        String[] files = { "sdr_black", "pq_black", "hlg_black", "sdr_steps", "pq_steps", "hlg_steps",
            "sdr_window", "pq_window", "hlg_window" };
        new AlertDialog.Builder(this).setTitle("Built-in black-level tests")
            .setItems(labels, (d, i) -> {
                try {
                    File dir = new File(getFilesDir(), "patterns"); dir.mkdirs();
                    File file = new File(dir, files[i] + ".mp4");
                    if (!file.exists()) try (InputStream input = getAssets().open("patterns/" + file.getName());
                                             OutputStream output = new FileOutputStream(file)) {
                        copy(input, output);
                    }
                    loadSource(Uri.fromFile(file), true);
                    if (i >= 3 && i <= 5) showText("Near-black steps", "Six patches, left to right. The surround is reference black.\n\n"
                        + (i == 3 ? "SDR 10-bit limited Y: 68, 72, 80, 96, 128, 192; black = 64."
                            : "HDR 10-bit limited Y: 65, 66, 68, 72, 80, 96; black = 64.")
                        + "\n\nThese are encoded signal steps, not equal luminance steps. "
                        + "Do not compare patch brightness between SDR, PQ, and HLG. Compare the black surround and use patch visibility to track changes within one format.");
                } catch (Exception e) { error("Could not open pattern", e); }
            }).setNegativeButton("Close", null).show();
    }

    private void loadSource(Uri uri, boolean resetPosition) {
        cancelExport();
        source = uri;
        mode = "original";
        variants.clear(); droppedFrames = 0; decoder = "Not initialized"; hdr = false;
        sourceMetadata = new JSONObject();
        inspectionPending = true;
        if (resetPosition) lastPosition = 0;
        sourceName = uri.getLastPathSegment() == null ? "Video" : uri.getLastPathSegment();
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) sourceName = cursor.getString(0);
            } catch (Exception ignored) {}
        }
        clipLabel.setText(sourceName);
        getPreferences(0).edit().putString("source", uri.toString()).apply();
        status.setText("Reading source metadata…");
        refreshButtons();
        if (started) playAt(lastPosition, true);
        worker.execute(() -> {
            try {
                JSONObject metadata = Diagnostics.inspect(this, uri);
                handler.post(() -> {
                    if (isDestroyed() || !uri.equals(source)) return;
                    inspectionPending = false;
                    sourceMetadata = metadata;
                    hdr = metadata.optBoolean("hdrSignaled");
                    status.setText(Diagnostics.clipSummary(metadata));
                    refreshButtons();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (uri.equals(source)) { inspectionPending = false; error("Source inspection failed", e); refreshButtons(); }
                });
            }
        });
    }

    private void ensurePlayer() {
        if (player != null) return;
        player = new ExoPlayer.Builder(this, new DefaultRenderersFactory(this).setEnableDecoderFallback(true)).build();
        player.setSeekParameters(SeekParameters.EXACT);
        playerView.setPlayer(player);
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.addListener(new Player.Listener() {
            @Override public void onVideoSizeChanged(VideoSize size) {
                if (size.width > 0 && size.height > 0) {
                    videoWidth = Math.max(1, Math.round(size.width * size.pixelWidthHeightRatio));
                    videoHeight = size.height;
                    resizeVideo();
                }
            }
            @Override public void onPlayerError(PlaybackException e) { error("Playback failed (" + e.getErrorCodeName() + ")", e); }
        });
        player.addAnalyticsListener(new AnalyticsListener() {
            @Override public void onVideoDecoderInitialized(EventTime time, String name, long timestamp, long duration) {
                decoder = name; updateFormatLabel();
            }
            @Override public void onVideoInputFormatChanged(EventTime time, Format format,
                    androidx.media3.exoplayer.DecoderReuseEvaluation reuse) { updateFormatLabel(); }
            @Override public void onDroppedVideoFrames(EventTime time, int count, long elapsed) {
                droppedFrames += count; updateFormatLabel();
            }
        });
    }

    private long timelinePosition() {
        long local = player == null ? lastPosition : player.getCurrentPosition();
        Variant variant = variants.get(mode);
        return local + (variant == null ? 0 : variant.start);
    }

    private void switchMode(String selected) {
        if (source == null || (!selected.equals("original") && !variants.containsKey(selected))) return;
        if (mode.equals(selected)) return;
        long absolute = timelinePosition();
        boolean playing = player == null || player.getPlayWhenReady();
        mode = selected;
        Variant variant = variants.get(mode);
        long local = absolute - (variant == null ? 0 : variant.start);
        if (variant != null) {
            long max = durationMs(variant.metadata);
            if (local < 0 || (max > 0 && local >= max)) {
                local = 0;
                status.setText("Outside the converted section · moved to its start");
            }
        }
        playAt(Math.max(0, local), playing);
        refreshButtons();
    }

    private void playAt(long localPosition, boolean playing) {
        if (source == null) return;
        ensurePlayer();
        Variant variant = variants.get(mode);
        decoder = "Initializing…";
        player.setMediaItem(MediaItem.fromUri(variant == null ? source : variant.uri));
        player.seekTo(localPosition);
        player.prepare();
        player.setPlayWhenReady(playing);
        updateModeLabel();
        updateFormatLabel();
    }

    private void updateFormatLabel() {
        Format format = player == null ? null : player.getVideoFormat();
        formatLabel.setText((format == null ? "Reading decoder input…" : format.width + " × " + format.height
            + " · " + (format.colorInfo == null ? "Color metadata unspecified" : format.colorInfo.toString()))
            + "\nDecoder: " + decoder + " · Dropped frames (session): " + droppedFrames
            + "\nSource/decoder metadata · panel HDR state is not verified");
    }

    private void chooseExport() {
        if (source == null || transformer != null || inspectionPending) return;
        if (!hdr) {
            showText("HDR metadata needed", "This source does not signal PQ, HLG, or Dolby Vision. "
                + "Choose an HDR file or a built-in HDR test. Unspecified color metadata is not proof of SDR.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Create SDR with…")
            .setItems(new String[] { "Android MediaCodec tone mapper", "Media3 OpenGL tone mapper" },
                (dialog, which) -> beginExport(which == 0 ? "codec" : "gpu"))
            .setNegativeButton("Cancel", null).show();
    }

    private void beginExport(String method) {
        long start = duration.getSelectedItemPosition() == 2 ? 0 : timelinePosition();
        long length = duration.getSelectedItemPosition() == 1 ? 30_000 : 10_000;
        long end = duration.getSelectedItemPosition() == 2 ? C.TIME_END_OF_SOURCE : start + length;
        long clipLength = durationMs(sourceMetadata);
        if (clipLength > 0 && start >= clipLength - 100) start = 0;
        if (end != C.TIME_END_OF_SOURCE && clipLength > 0) end = Math.min(clipLength, start + length);
        final long startMs = start;
        final long endMs = end;
        if (!mode.equals("original")) switchMode("original");
        File dir = new File(getFilesDir(), "exports"); dir.mkdirs();
        File partial = new File(dir, UUID.randomUUID() + ".partial.mp4");
        pendingExport = partial;
        if (player != null) player.pause();
        MediaItem.ClippingConfiguration.Builder clipping = new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs);
        if (endMs != C.TIME_END_OF_SOURCE) clipping.setEndPositionMs(endMs);
        MediaItem item = new MediaItem.Builder().setUri(source).setClippingConfiguration(clipping.build()).build();
        EditedMediaItem edited = new EditedMediaItem.Builder(item).setRemoveAudio(true).build();
        Composition composition = new Composition.Builder(new EditedMediaItemSequence(edited))
            .setHdrMode(method.equals("codec") ? Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC
                : Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build();
        long began = android.os.SystemClock.elapsedRealtime();
        transformer = new Transformer.Builder(this).setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(new Transformer.Listener() {
                @Override public void onCompleted(Composition c, ExportResult result) {
                    transformer = null; pendingExport = null; handler.removeCallbacks(progressTick);
                    Uri originalSource = source;
                    progressLabel.setText("Checking output metadata…");
                    inspectionPending = true; refreshButtons();
                    worker.execute(() -> {
                        try {
                            JSONObject metadata = Diagnostics.inspect(MainActivity.this, Uri.fromFile(partial));
                            if (metadata.optBoolean("hdrSignaled")) throw new IOException("Output still signals HDR; cannot label this as SDR");
                            if (!signalsSdr(metadata)) throw new IOException("Output does not explicitly signal SDR; comparison rejected");
                            File ready = new File(dir, UUID.randomUUID() + "-" + method + "-sdr.mp4");
                            if (!partial.renameTo(ready)) throw new IOException("Could not finalize output");
                            handler.post(() -> {
                                if (isDestroyed() || !originalSource.equals(source)) return;
                                inspectionPending = false;
                                Variant old = variants.put(method, new Variant(Uri.fromFile(ready), ready, startMs, metadata));
                                if (old != null) old.file.delete();
                                record("conversionComplete", method + ", start=" + startMs + ", end=" + endMs
                                    + ", elapsedMs=" + (android.os.SystemClock.elapsedRealtime() - began));
                                progressLabel.setText("SDR copy ready · select it above to compare");
                                refreshButtons();
                            });
                        } catch (Exception e) {
                            partial.delete();
                            handler.post(() -> { inspectionPending = false; error("Output validation failed", e); refreshButtons(); });
                        }
                    });
                }
                @Override public void onError(Composition c, ExportResult result, ExportException e) {
                    transformer = null; pendingExport = null; partial.delete(); handler.removeCallbacks(progressTick);
                    error("Tone mapping unavailable for this source / pipeline", e);
                    progressLabel.setText("Failed · try the other mapper. Dolby Vision support depends on its profile and device.");
                    refreshButtons();
                }
            }).build();
        progressHolder = new ProgressHolder();
        refreshButtons();
        record("conversionStart", method + ", rangeMs=" + startMs + ".." + endMs);
        try { transformer.start(composition, partial.getAbsolutePath()); handler.post(progressTick); }
        catch (RuntimeException e) { cancelExport(); error("Could not start tone mapping", e); }
    }

    private static boolean signalsSdr(JSONObject metadata) {
        JSONArray tracks = metadata.optJSONArray("tracks");
        if (tracks != null) for (int i = 0; i < tracks.length(); i++) {
            JSONObject track = tracks.optJSONObject(i);
            if (track != null && track.optString("mime").startsWith("video/"))
                return track.optInt("color-transfer", -1) == android.media.MediaFormat.COLOR_TRANSFER_SDR_VIDEO;
        }
        return false;
    }

    private static long durationMs(JSONObject metadata) {
        JSONArray tracks = metadata.optJSONArray("tracks");
        if (tracks != null) for (int i = 0; i < tracks.length(); i++) {
            JSONObject track = tracks.optJSONObject(i);
            if (track != null && track.optString("mime").startsWith("video/")) return track.optLong("durationUs") / 1000;
        }
        return 0;
    }

    private void cancelExport() {
        if (transformer != null) {
            transformer.cancel(); transformer = null;
            record("conversionCanceled", "Canceled or app left foreground");
            if (progressLabel != null) progressLabel.setText("Conversion canceled");
        }
        handler.removeCallbacks(progressTick);
        if (pendingExport != null) { pendingExport.delete(); pendingExport = null; }
        refreshButtons();
    }

    private void clearExports() {
        if (transformer != null || inspectionPending) return;
        if (!mode.equals("original")) switchMode("original");
        variants.clear();
        File[] files = new File(getFilesDir(), "exports").listFiles();
        if (files != null) for (File f : files) f.delete();
        status.setText("Generated SDR copies cleared"); refreshButtons();
    }

    private void saveVideo() {
        Variant variant = variants.get(mode);
        if (variant == null) return;
        savingVideo = variant.file;
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("video/mp4")
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "hdr-lab-" + mode + "-sdr.mp4"), SAVE_VIDEO);
    }

    private void shareReport() {
        try {
            JSONObject report = new JSONObject();
            report.put("schema", "hdr-lab-report-v1");
            report.put("createdUtc", java.time.Instant.now().toString());
            report.put("device", device); report.put("sourceName", sourceName);
            report.put("source", sourceMetadata); report.put("selectedMode", mode);
            report.put("decoder", decoder); report.put("droppedFramesSession", droppedFrames);
            report.put("fixedWindowBrightness", fixedBrightness);
            report.put("notes", notes.getText().toString()); report.put("lastError", lastError);
            report.put("events", events);
            JSONObject outputs = new JSONObject();
            for (Map.Entry<String, Variant> entry : variants.entrySet()) {
                JSONObject output = new JSONObject();
                output.put("startMs", entry.getValue().start); output.put("metadata", entry.getValue().metadata);
                outputs.put(entry.getKey(), output);
            }
            report.put("outputs", outputs);
            report.put("limitation", "Visual diagnostic only. No measurement of panel luminance, black floor, "
                + "or active panel HDR mode. SDR exports include re-encoding and can differ for more reasons than tone mapping.");
            File dir = new File(getFilesDir(), "reports"); dir.mkdirs();
            File file = new File(dir, "hdr-lab-" + System.currentTimeMillis() + ".json");
            try (OutputStream output = new FileOutputStream(file)) {
                output.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            Intent share = new Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(ClipData.newRawUri("HDR Lab report", uri));
            startActivity(Intent.createChooser(share, "Share HDR Lab report"));
        } catch (Exception e) { error("Report failed", e); }
    }

    private void showGuide() {
        showText("Investigating raised blacks", "1. Use a dark room and keep viewing conditions identical. Disable auto brightness and "
            + "eye-comfort/color-temperature changes for the test. Note the chosen brightness.\n\n"
            + "2. In your device settings, search for video enhancement / video booster / HDR enhancement and disable any available options. "
            + "Names and availability vary by firmware. Record changes rather than assuming a setting exists.\n\n"
            + "3. Play SDR reference black, then PQ reference black, then HLG reference black. Also try the black + bright-window tests: "
            + "their center window occupies 10% of the image. PQ Y=723 is approximately a 1000-nit signal; actual panel output is not measured. "
            + "SDR and HLG windows use peak signal Y=940 and are not luminance matched to PQ. Use fullscreen. "
            + "Controls hide after a short delay; tap only when needed. Compare the video region, not system bars or letterboxing. "
            + "These files contain limited-range neutral black: Y=64 with 10-bit chroma=512 in all three formats.\n\n"
            + "4. If the HDR video rectangle glows while SDR is black, that supports an HDR pipeline/processing difference. "
            + "If black tests are dark but real clips look washed out, inspect mastering, transfer/range metadata, and player settings. "
            + "This does not identify the cause by itself.\n\n"
            + "5. Try near-black steps and note which patches you can see, left to right. The steps use different transfer functions "
            + "and are not brightness matched across formats. Start with the all-black clips.\n\n"
            + "6. Convert the same HDR section using each SDR mapper. Compare original and SDR at the same moment, "
            + "and save reports on both devices. A tone-mapped SDR copy is not a reference for intended HDR appearance.\n\n"
            + "Screenshots and screen recordings may convert HDR themselves. A camera with locked exposure can document a difference, "
            + "but a suitable luminance meter is needed to measure black level. Dolby Vision/HDR10+ dynamic metadata is not preserved in SDR copies.");
    }

    private void setFullscreen(boolean enabled) {
        if (fullscreen == enabled) return;
        fullscreen = enabled;
        if (enabled) {
            testingSection.removeView(videoFrame);
            workspaceLayout.setVisibility(View.GONE);
            root.addView(videoFrame, new LinearLayout.LayoutParams(-1, -1));
            getWindow().getInsetsController().hide(android.view.WindowInsets.Type.systemBars());
            getWindow().getInsetsController().setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            playerView.setControllerShowTimeoutMs(1500); playerView.hideController();
            showFullscreenHud();
            Toast.makeText(this, "Back to exit · tap for controls", Toast.LENGTH_SHORT).show();
        } else {
            handler.removeCallbacks(hideFullscreenHud);
            if (fullscreenHud != null) fullscreenHud.setVisibility(View.GONE);
            root.removeView(videoFrame);
            workspaceLayout.setVisibility(View.VISIBLE);
            testingSection.addView(videoFrame, testingSection.indexOfChild(modeLabel) + 1, new LinearLayout.LayoutParams(-1, dp(240)));
            getWindow().getInsetsController().show(android.view.WindowInsets.Type.systemBars());
            root.post(this::applyResponsiveLayout);
        }
    }

    // Only Android 12/12L uses this fallback. API 33+ has the registered
    // OnBackInvokedCallback above, including Android 16's gesture navigation.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (fullscreen) setFullscreen(false); else super.onBackPressed();
    }

    private void applyBrightness() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = fixedBrightness; getWindow().setAttributes(params);
    }

    private void refreshButtons() {
        if (originalButton == null) return;
        boolean busy = transformer != null || inspectionPending;
        originalButton.setEnabled(source != null && !busy);
        codecButton.setEnabled(variants.containsKey("codec") && !busy);
        gpuButton.setEnabled(variants.containsKey("gpu") && !busy);
        modeStyle(originalButton, "Original", "original", source != null);
        modeStyle(codecButton, "SDR · Codec", "codec", variants.containsKey("codec"));
        modeStyle(gpuButton, "SDR · GPU", "gpu", variants.containsKey("gpu"));
        if (fsOriginalButton != null) {
            fsOriginalButton.setEnabled(source != null && !busy);
            fsCodecButton.setEnabled(variants.containsKey("codec") && !busy);
            fsGpuButton.setEnabled(variants.containsKey("gpu") && !busy);
            hudModeStyle(fsOriginalButton, "Original", "original", source != null);
            hudModeStyle(fsCodecButton, "SDR Codec", "codec", variants.containsKey("codec"));
            hudModeStyle(fsGpuButton, "SDR GPU", "gpu", variants.containsKey("gpu"));
        }
        exportButton.setEnabled(source != null && !busy);
        exportButton.setAlpha(exportButton.isEnabled() ? 1f : .45f);
        shareVideoButton.setEnabled(variants.containsKey(mode) && !busy);
        shareVideoButton.setAlpha(shareVideoButton.isEnabled() ? 1f : .4f);
        cancelButton.setVisibility(transformer == null ? View.GONE : View.VISIBLE);
        progressLabel.setVisibility(progressLabel.getText().length() == 0 ? View.GONE : View.VISIBLE);
        updateModeLabel();
    }

    private void updateModeLabel() {
        if (source == null) modeLabel.setText("Choose a video or a built-in test to begin");
        else modeLabel.setText(mode.equals("original") ? "NOW VIEWING  ·  Original / Android display pipeline"
            : "NOW VIEWING  ·  SDR / " + (mode.equals("codec") ? "Codec" : "GPU") + " tone mapper");
    }

    private void modeStyle(Button button, String title, String key, boolean available) {
        boolean selected = source != null && mode.equals(key);
        button.setSelected(selected);
        button.setText((selected ? "✓  " : "") + title + "\n"
            + (selected ? "Selected" : available ? "Ready" : key.equals("original") ? "No source" : "Create first"));
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setTextColor(selected ? BG : available ? TEXT : MUTED);
        button.setAlpha(available || selected ? 1f : .45f);
        button.setBackground(ripple(selected ? ACCENT : BG, selected ? ACCENT : 0xff485546, 12));
        button.setContentDescription(title + ", " + (selected ? "selected" : available ? "ready to select" : "not created"));
        button.setStateDescription(selected ? "Selected" : available ? "Ready" : "Unavailable");
    }

    private void buildFullscreenHud() {
        fullscreenHud = new LinearLayout(this);
        fullscreenHud.setOrientation(LinearLayout.HORIZONTAL);
        fullscreenHud.setGravity(Gravity.CENTER);
        fullscreenHud.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xdd101411);
        pill.setCornerRadius(dp(20));
        pill.setStroke(dp(1), 0x55485546);
        fullscreenHud.setBackground(pill);
        fullscreenHud.setVisibility(View.GONE);

        fsPrevFrameButton = hudButton("◀", () -> { stepFrame(-1); showFullscreenHud(); });
        fsOriginalButton = hudButton("Original", () -> { switchMode("original"); showFullscreenHud(); });
        fsCodecButton = hudButton("SDR Codec", () -> { switchMode("codec"); showFullscreenHud(); });
        fsGpuButton = hudButton("SDR GPU", () -> { switchMode("gpu"); showFullscreenHud(); });
        fsNextFrameButton = hudButton("▶", () -> { stepFrame(1); showFullscreenHud(); });

        fullscreenHud.addView(fsPrevFrameButton);
        fullscreenHud.addView(fsOriginalButton);
        fullscreenHud.addView(fsCodecButton);
        fullscreenHud.addView(fsGpuButton);
        fullscreenHud.addView(fsNextFrameButton);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = dp(24);
        videoFrame.addView(fullscreenHud, params);
    }

    private Button hudButton(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(TEXT);
        b.setBackgroundTintList(null);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(36));
        b.setMinimumWidth(dp(36));
        b.setPadding(dp(8), dp(4), dp(8), dp(4));
        b.setBackground(ripple(0x33252e25, 0x443b4739, 14));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMarginStart(dp(2)); p.setMarginEnd(dp(2));
        b.setLayoutParams(p);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void hudModeStyle(Button button, String title, String key, boolean available) {
        boolean selected = source != null && mode.equals(key);
        button.setSelected(selected);
        button.setText((selected ? "✓ " : "") + title);
        button.setTextColor(selected ? ACCENT : available ? TEXT : MUTED);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setAlpha(available || selected ? 1f : .35f);
        button.setBackground(ripple(selected ? 0x66252e25 : 0x221b211c, selected ? ACCENT : 0x44485546, 14));
    }

    private void showFullscreenHud() {
        if (fullscreenHud == null || !fullscreen) return;
        handler.removeCallbacks(hideFullscreenHud);
        fullscreenHud.animate().cancel();
        fullscreenHud.setAlpha(1f);
        fullscreenHud.setVisibility(View.VISIBLE);
        handler.postDelayed(hideFullscreenHud, 3500);
    }

    private void stepFrame(int direction) {
        if (player == null) return;
        player.pause();
        Format format = player.getVideoFormat();
        float fps = (format != null && format.frameRate > 0) ? format.frameRate : 24f;
        long delta = Math.max(1, Math.round(1000f / fps));
        long current = player.getCurrentPosition();
        long target = Math.max(0, current + direction * delta);
        player.seekTo(target);
    }

    /** Reparent existing views only at a layout breakpoint; retain the player and its decoder. */
    private void applyResponsiveLayout() {
        if (fullscreen || testingSection == null) return;
        int availableWidth = root.getWidth() - root.getPaddingLeft() - root.getPaddingRight();
        float widthDp = availableWidth / getResources().getDisplayMetrics().density;
        boolean wide = widthDp >= 840 || (widthDp >= 720 && getResources().getConfiguration().smallestScreenWidthDp >= 600);
        if (!layoutInitialized || wide != wideLayout) {
            layoutInitialized = true;
            wideLayout = wide;
            for (View view : new View[] {headerSection, deviceSection, sourceSection, testingSection, conversionSection, controlsSection})
                detach(view);
            workspaceLayout.removeAllViews();
            page.removeAllViews();
            workspaceLayout.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
            page.setPadding(dp(wide ? 14 : 20), dp(20), dp(wide ? 14 : 20), dp(28));
            page.addView(headerSection); page.addView(deviceSection); page.addView(sourceSection);
            if (!wide) page.addView(testingSection, new LinearLayout.LayoutParams(-1, -2));
            page.addView(conversionSection); page.addView(controlsSection);
            if (wide) {
                int sidebar = Math.max(dp(224), availableWidth / 4);
                workspaceLayout.addView(controlScroll, new LinearLayout.LayoutParams(sidebar, -1));
                testingSection.setPadding(dp(24), dp(24), dp(24), dp(24));
                testScroll.addView(testingSection, new ScrollView.LayoutParams(-1, -2));
                workspaceLayout.addView(testScroll, new LinearLayout.LayoutParams(0, -1, 1));
            } else {
                testingSection.setPadding(0, dp(18), 0, 0);
                workspaceLayout.addView(controlScroll, new LinearLayout.LayoutParams(-1, -1));
            }
        } else if (wide) {
            android.view.ViewGroup.LayoutParams params = controlScroll.getLayoutParams();
            int sidebar = Math.max(dp(224), availableWidth / 4);
            if (params.width != sidebar) { params.width = sidebar; controlScroll.setLayoutParams(params); }
        }
        testingSection.post(this::resizeVideo);
    }

    private static void detach(View view) {
        if (view.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup) view.getParent()).removeView(view);
    }

    private void resizeVideo() {
        if (fullscreen || testingSection == null) return;
        int width = testingSection.getWidth() - testingSection.getPaddingLeft() - testingSection.getPaddingRight();
        if (width <= 0) return;
        int height = Math.max(dp(180), Math.min(dp(wideLayout ? 640 : 460), width * videoHeight / videoWidth));
        android.view.ViewGroup.LayoutParams params = videoFrame.getLayoutParams();
        if (params.height != height) { params.height = height; videoFrame.setLayoutParams(params); }
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        root.post(this::applyResponsiveLayout);
    }

    private void record(String event, String detail) {
        try {
            JSONObject entry = new JSONObject(); entry.put("utc", java.time.Instant.now().toString());
            entry.put("event", event); entry.put("sourceName", sourceName); entry.put("detail", detail); events.put(entry);
        } catch (Exception ignored) {}
    }

    private void error(String prefix, Exception e) {
        lastError = prefix + ": " + e.getMessage();
        if (e.getCause() != null) lastError += "\n" + e.getCause();
        status.setText(lastError); record("error", lastError);
    }

    private void showText(String title, String text) {
        TextView body = label(text, 14, TEXT); body.setTextIsSelectable(true); body.setPadding(dp(20), dp(12), dp(20), dp(20));
        ScrollView scroll = new ScrollView(this); scroll.addView(body);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Close", null).show();
    }

    private int dp(float value) { return (int) (getResources().getDisplayMetrics().density * value + .5f); }
    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[65536];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }
    private TextView label(String text, int size, int color) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color);
        v.setPadding(0, dp(5), 0, dp(7));
        if (size >= 28) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private Button button(String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextColor(TEXT);
        b.setBackgroundTintList(null);
        b.setTextSize(14); b.setGravity(Gravity.CENTER); b.setMinHeight(dp(48));
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        b.setBackground(ripple(0xff252e25, 0xff3b4739, 12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(6); p.bottomMargin = dp(2); b.setLayoutParams(p);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private RippleDrawable ripple(int fill, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill); shape.setCornerRadius(dp(radius)); shape.setStroke(dp(1), stroke);
        GradientDrawable mask = new GradientDrawable(); mask.setColor(Color.WHITE); mask.setCornerRadius(dp(radius));
        return new RippleDrawable(ColorStateList.valueOf(0x224e713c), shape, mask);
    }
    private void primaryStyle(Button button) {
        button.setTextColor(BG); button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(ripple(ACCENT, ACCENT, 12));
    }
    private void add(View v) { page.addView(v, new LinearLayout.LayoutParams(-1, -2)); }
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(CARD); bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), 0xff2c372c); c.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(18); params.bottomMargin = dp(12);
        page.addView(c, params); return c;
    }

    @Override protected void onStart() {
        super.onStart(); started = true;
        if (source != null) playAt(lastPosition, resumePlaying);
    }
    @Override protected void onStop() {
        started = false;
        handler.removeCallbacks(hideFullscreenHud);
        if (android.os.Build.VERSION.SDK_INT >= 34 && hdrSdrRatioListener != null) {
            try {
                Display display = getDisplay();
                if (display != null) display.unregisterHdrSdrRatioChangedListener(hdrSdrRatioListener);
            } catch (Throwable ignored) {}
            hdrSdrRatioListener = null;
        }
        getPreferences(0).edit().putString("notes", notes.getText().toString()).apply();
        if (player != null) {
            lastPosition = player.getCurrentPosition(); resumePlaying = player.getPlayWhenReady();
            playerView.setPlayer(null); player.release(); player = null;
        }
        cancelExport(); super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (source != null) state.putString("source", source.toString());
        state.putLong("position", timelinePosition()); state.putString("notes", notes.getText().toString());
    }
    @Override protected void onDestroy() {
        cancelExport(); worker.shutdownNow(); super.onDestroy();
    }
}
