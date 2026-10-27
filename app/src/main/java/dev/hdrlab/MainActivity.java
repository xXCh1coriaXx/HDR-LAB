package dev.hdrlab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.AspectRatioFrameLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public final class MainActivity extends Activity {
    private static final int BG = Color.BLACK, CARD = 0xff161616, TEXT = 0xfff5f5f5;
    private static final int MUTED = 0xffb8b8b8, ACCENT = 0xff69f0ae, CARD_STROKE = 0xff343434;
    private static final int RED = 0xffff6b6b, BLUE = 0xff82b1ff;
    private static final int[] APL_PEAKS = {400, 1000, 4000};
    private static final String[] HDR_RAMP_NAMES = {"gray", "red", "green", "blue", "cyan", "magenta", "yellow"};
    private static final String[] HDR_BURNIN_NAMES = {"red", "green", "blue", "white", "gray50", "gray20", "cyan", "magenta", "yellow"};
    /** Pipeline B ladder variants reuse the packaged reference step videos. */
    private static final String[] LADDER_VIDEOS = {"sdr_steps", "pq_steps", "hlg_steps"};
    private static final int[] LADDER_VIDEO_LABELS = {R.string.oled_video_sdr, R.string.oled_video_pq, R.string.oled_video_hlg};
    private static final int[] BURNIN_LABELS = {
        R.string.oled_burnin_red, R.string.oled_burnin_green, R.string.oled_burnin_blue,
        R.string.oled_burnin_white, R.string.oled_burnin_gray50, R.string.oled_burnin_gray20,
        R.string.oled_burnin_cyan, R.string.oled_burnin_magenta, R.string.oled_burnin_yellow
    };
    private static final int[] RAMP_LABELS = {
        R.string.oled_ramp_gray, R.string.oled_ramp_red, R.string.oled_ramp_green,
        R.string.oled_ramp_blue, R.string.oled_ramp_cyan, R.string.oled_ramp_magenta,
        R.string.oled_ramp_yellow
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final java.util.Set<String> extractedPatterns = new java.util.HashSet<>();

    private FrameLayout root;
    private ScrollView workspaceLayout;
    private LinearLayout cardsLayout;
    private LinearLayout cardsContainer;
    private FrameLayout fullscreenContainer;

    private LinearLayout headerSection;
    private LinearLayout deviceCapabilitiesCard;
    private LinearLayout quickTestCard;

    private TextView capModelLabel;
    private TextView capHdrLabel;
    private TextView capGamutLabel;
    private TextView capBrightnessLabel;
    private Button btnDetailedReport;
    private Button btnStartTest;
    private Button btnCommunityReport;

    private PlayerView playerView;
    private ExoPlayer player;
    private Player.Listener playbackListener;

    private FrameLayout fullscreenHud;
    private LinearLayout fullscreenTopHud;
    private LinearLayout fullscreenBottomHud;
    private TextView hudTitleLabel;
    private TextView hudTipLabel;
    private Button btnWatchSdr;
    private Button btnWatchHdr;
    private Button btnRestart;
    private Button btnExit;

    private LinearLayout oledCard;
    private OledTestView oledView;
    private Button btnPrev;
    private Button btnNext;
    private Button btnOledHdr;
    private Button btnAplPeak;
    private LinearLayout oledBrightnessRow;
    private SeekBar brightnessSeek;
    private TextView brightnessLabel;
    private int oledVariant;
    private boolean oledHdrMode;
    private boolean oledHdrPatternPlaying;
    private int activeOledMode = -1;
    private int aplPeakIndex = 1;
    // -1 uses platform capabilities; tests can choose an explicit availability result.
    private int hdrAvailabilityOverride = -1;
    private int originalDisplayModeId;
    private int originalColorMode;
    private float originalHdrHeadroom;

    private boolean fullscreen;
    private String currentMode = "sdr";
    private boolean isAutomatedTest;
    private long testPlaybackMs;
    private long testPlayingSinceMs = -1;
    private float originalBrightness = -1f;
    private JSONObject deviceSnapshot;
    private Diagnostics.DeviceSummary currentSummary;

    private final Runnable hideFullscreenHud = () -> {
        AccessibilityManager accessibility = getSystemService(AccessibilityManager.class);
        if (fullscreenHud != null && fullscreen && !fullscreenHud.hasFocus()
            && (accessibility == null || !accessibility.isTouchExplorationEnabled())) {
            fullscreenHud.setVisibility(View.GONE);
        }
    };

    private final Runnable testCountdownTicker = new Runnable() {
        @Override public void run() {
            if (!fullscreen || !isAutomatedTest) return;
            long playedMs = testPlaybackMs + (testPlayingSinceMs < 0 ? 0
                : SystemClock.elapsedRealtime() - testPlayingSinceMs);
            int remaining = (int) Math.max(0, (10_000 - playedMs + 999) / 1000);
            if (remaining > 0) {
                updateHudMode("sdr", remaining);
                handler.removeCallbacks(this);
                handler.postDelayed(this, 250);
            } else {
                switchToHdr();
            }
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                () -> { if (fullscreen) exitFullscreen(); else finish(); });
        }

        updateCapabilitiesUi(Diagnostics.summarize(this, null));
        worker.execute(() -> {
            try {
                JSONObject snapshot = Diagnostics.device(this);
                Diagnostics.DeviceSummary summary = Diagnostics.summarize(this, snapshot);
                handler.post(() -> {
                    if (isDestroyed()) return;
                    deviceSnapshot = snapshot;
                    updateCapabilitiesUi(summary);
                });
            } catch (Exception ignored) {}
        });
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (!fullscreen) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
                root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                root.setPadding(0, 0, 0, 0);
                android.graphics.Insets cutout = insets.getInsets(WindowInsets.Type.displayCutout());
                fullscreenHud.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom);
            }
            return insets;
        });

        workspaceLayout = new ScrollView(this);
        workspaceLayout.setFillViewport(true);

        cardsLayout = new LinearLayout(this);
        cardsLayout.setOrientation(LinearLayout.VERTICAL);
        cardsLayout.setPadding(dp(20), dp(20), dp(20), dp(28));
        workspaceLayout.addView(cardsLayout, new ScrollView.LayoutParams(-1, -2));
        root.addView(workspaceLayout, new FrameLayout.LayoutParams(-1, -1));

        headerSection = new LinearLayout(this);
        headerSection.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = label(getString(R.string.header_eyebrow), 10, ACCENT);
        eyebrow.setLetterSpacing(.14f);
        headerSection.addView(eyebrow);
        headerSection.addView(label(getString(R.string.header_title), 32, TEXT));
        headerSection.addView(label(getString(R.string.header_subtitle), 14, MUTED));
        cardsLayout.addView(headerSection);

        cardsContainer = new LinearLayout(this);
        cardsContainer.setOrientation(LinearLayout.VERTICAL);
        cardsLayout.addView(cardsContainer, new LinearLayout.LayoutParams(-1, -2));

        buildDeviceCapabilitiesCard();
        buildQuickTestCard();
        buildOledCard();

        buildFullscreenContainer();

        setContentView(root);

        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) root.post(this::applyResponsiveLayout);
        });
        root.post(this::applyResponsiveLayout);
    }

    private void buildDeviceCapabilitiesCard() {
        deviceCapabilitiesCard = card(RED);
        TextView capTitle = label(getString(R.string.card_capabilities_title), 18, RED);
        capTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        capTitle.setAccessibilityHeading(true);
        deviceCapabilitiesCard.addView(capTitle);
        deviceCapabilitiesCard.addView(label(getString(R.string.card_capabilities_subtitle), 12, MUTED));

        capModelLabel = label(getString(R.string.status_reading_system_data), 14, TEXT);
        capHdrLabel = label(getString(R.string.status_checking), 14, TEXT);
        capGamutLabel = label(getString(R.string.status_checking), 14, TEXT);
        capBrightnessLabel = label(getString(R.string.status_checking), 14, TEXT);

        deviceCapabilitiesCard.addView(cardProperty(getString(R.string.prop_device_model), capModelLabel));
        deviceCapabilitiesCard.addView(cardProperty(getString(R.string.prop_hdr_support), capHdrLabel));
        deviceCapabilitiesCard.addView(cardProperty(getString(R.string.prop_color_gamut), capGamutLabel));
        deviceCapabilitiesCard.addView(cardProperty(getString(R.string.prop_peak_brightness), capBrightnessLabel));

        View spacer = new View(this);
        deviceCapabilitiesCard.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));
        btnDetailedReport = button(getString(R.string.btn_detailed_report), this::showDetailedReport);
        deviceCapabilitiesCard.addView(btnDetailedReport);
        cardsContainer.addView(deviceCapabilitiesCard);
    }

    private void buildQuickTestCard() {
        quickTestCard = card(ACCENT);
        TextView testTitle = label(getString(R.string.card_quick_test_title), 18, ACCENT);
        testTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        testTitle.setAccessibilityHeading(true);
        quickTestCard.addView(testTitle);
        quickTestCard.addView(label(getString(R.string.card_quick_test_subtitle), 12, MUTED));

        LinearLayout infoBox = new LinearLayout(this);
        infoBox.setOrientation(LinearLayout.VERTICAL);
        infoBox.setPadding(0, dp(10), 0, dp(8));

        TextView howTitle = label(getString(R.string.test_how_it_works_title), 13, ACCENT);
        howTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        infoBox.addView(howTitle);

        TextView howText = label(getString(R.string.test_how_it_works_text), 13, TEXT);
        infoBox.addView(howText);

        TextView whatTitle = label(getString(R.string.test_what_to_look_title), 13, ACCENT);
        whatTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        infoBox.addView(whatTitle);

        TextView whatText = label(getString(R.string.test_what_to_look_text), 13, TEXT);
        infoBox.addView(whatText);

        quickTestCard.addView(infoBox);

        btnStartTest = button(getString(R.string.btn_start_test), this::startAutomatedTest);
        primaryStyle(btnStartTest);
        quickTestCard.addView(btnStartTest);

        btnCommunityReport = button(getString(R.string.btn_community_report), this::showCommunityReportSurvey);
        quickTestCard.addView(btnCommunityReport);

        cardsContainer.addView(quickTestCard);
    }

    /** Full-width card below the two responsive cards; it does not take part in their sizing. */
    private void buildOledCard() {
        oledCard = card(BLUE);
        TextView title = label(getString(R.string.card_oled_title), 18, BLUE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setAccessibilityHeading(true);
        oledCard.addView(title);
        oledCard.addView(label(getString(R.string.card_oled_subtitle), 12, MUTED));
        oledCard.addView(label(getString(R.string.oled_caveat), 12, TEXT));
        oledCard.addView(button(getString(R.string.btn_oled_smear), () -> startOledSuite(OledTestView.MODE_SMEAR)));
        oledCard.addView(button(getString(R.string.btn_oled_ladder), () -> startOledSuite(OledTestView.MODE_LADDER)));
        oledCard.addView(button(getString(R.string.btn_oled_dse), () -> startOledSuite(OledTestView.MODE_DSE)));
        oledCard.addView(button(getString(R.string.btn_oled_apl), () -> startOledSuite(OledTestView.MODE_APL)));
        oledCard.addView(button(getString(R.string.btn_oled_burnin), () -> startOledSuite(OledTestView.MODE_BURNIN)));
        oledCard.addView(button(getString(R.string.btn_oled_ramp), () -> startOledSuite(OledTestView.MODE_RAMP)));
        cardsLayout.addView(oledCard);
    }

    private void buildFullscreenContainer() {
        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(Color.BLACK);
        fullscreenContainer.setVisibility(View.GONE);

        playerView = (PlayerView) getLayoutInflater().inflate(R.layout.player, fullscreenContainer, false);
        playerView.setKeepContentOnPlayerReset(false);
        playerView.setUseController(false);
        fullscreenContainer.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        oledView = new OledTestView(this);
        oledView.setVisibility(View.GONE);
        oledView.setOnClickListener(v -> showFullscreenHud());
        fullscreenContainer.addView(oledView, new FrameLayout.LayoutParams(-1, -1));

        buildFullscreenHud();
        fullscreenContainer.setOnClickListener(v -> showFullscreenHud());
        playerView.setOnClickListener(v -> showFullscreenHud());

        root.addView(fullscreenContainer, new FrameLayout.LayoutParams(-1, -1));
    }

    private void buildFullscreenHud() {
        fullscreenHud = new FrameLayout(this);
        fullscreenHud.setVisibility(View.GONE);
        ScrollView hudScroll = new ScrollView(this) {
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                handler.removeCallbacks(hideFullscreenHud);
                boolean handled = super.dispatchTouchEvent(event);
                if ((event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                    && fullscreenHud.getVisibility() == View.VISIBLE) showFullscreenHud();
                return handled;
            }
        };
        hudScroll.setFillViewport(true);
        LinearLayout hudContent = new LinearLayout(this);
        hudContent.setOrientation(LinearLayout.VERTICAL);
        hudScroll.addView(hudContent, new ScrollView.LayoutParams(-1, -2));
        fullscreenHud.addView(hudScroll, new FrameLayout.LayoutParams(-1, -1));

        fullscreenTopHud = new LinearLayout(this);
        fullscreenTopHud.setOrientation(LinearLayout.VERTICAL);
        fullscreenTopHud.setGravity(Gravity.CENTER);
        GradientDrawable topPill = new GradientDrawable();
        topPill.setColor(0xee101010);
        topPill.setCornerRadius(dp(16));
        topPill.setStroke(dp(1), CARD_STROKE);
        fullscreenTopHud.setBackground(topPill);
        fullscreenTopHud.setPadding(dp(16), dp(10), dp(16), dp(10));

        hudTitleLabel = label(getString(R.string.hud_title_sdr_countdown, 10), 15, ACCENT);
        hudTitleLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hudTitleLabel.setGravity(Gravity.CENTER);

        hudTipLabel = label(getString(R.string.hud_tip_sdr_countdown), 12, TEXT);
        hudTipLabel.setGravity(Gravity.CENTER);

        fullscreenTopHud.addView(hudTitleLabel);
        fullscreenTopHud.addView(hudTipLabel);

        oledBrightnessRow = new LinearLayout(this);
        oledBrightnessRow.setGravity(Gravity.CENTER_VERTICAL);
        oledBrightnessRow.setVisibility(View.GONE);
        brightnessLabel = label(getString(R.string.oled_brightness_value, 100), 12, MUTED);
        oledBrightnessRow.addView(brightnessLabel);
        brightnessSeek = new SeekBar(this);
        brightnessSeek.setMin(1);
        brightnessSeek.setMax(100);
        brightnessSeek.setContentDescription(getString(R.string.oled_brightness));
        brightnessSeek.setProgressTintList(ColorStateList.valueOf(ACCENT));
        brightnessSeek.setThumbTintList(ColorStateList.valueOf(ACCENT));
        brightnessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                brightnessLabel.setText(getString(R.string.oled_brightness_value, progress));
                if (!fromUser || !fullscreen) return;
                // Window override only; exitFullscreen() restores the pre-fullscreen value.
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = progress / 100f;
                getWindow().setAttributes(lp);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { handler.removeCallbacks(hideFullscreenHud); }
            @Override public void onStopTrackingTouch(SeekBar bar) { showFullscreenHud(); }
        });
        brightnessSeek.setMinimumHeight(dp(48));
        oledBrightnessRow.addView(brightnessSeek, new LinearLayout.LayoutParams(0, dp(48), 1f));
        fullscreenTopHud.addView(oledBrightnessRow);

        LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(-1, -2);
        topParams.topMargin = dp(44);
        topParams.leftMargin = dp(16);
        topParams.rightMargin = dp(16);
        hudContent.addView(fullscreenTopHud, topParams);
        hudContent.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        fullscreenBottomHud = new LinearLayout(this);
        fullscreenBottomHud.setOrientation(LinearLayout.HORIZONTAL);
        fullscreenBottomHud.setGravity(Gravity.CENTER);
        GradientDrawable bottomPill = new GradientDrawable();
        bottomPill.setColor(0xee101010);
        bottomPill.setCornerRadius(dp(20));
        bottomPill.setStroke(dp(1), CARD_STROKE);
        fullscreenBottomHud.setBackground(bottomPill);
        fullscreenBottomHud.setPadding(dp(6), dp(4), dp(6), dp(4));

        btnWatchSdr = hudButton(getString(R.string.hud_btn_watch_sdr), this::switchToSdr);
        btnWatchHdr = hudButton(getString(R.string.hud_btn_watch_hdr), this::switchToHdr);
        btnRestart = hudButton(getString(R.string.hud_btn_restart), this::restartTest);
        btnExit = hudButton(getString(R.string.hud_btn_exit), this::exitFullscreen);
        btnPrev = hudButton(getString(R.string.hud_btn_prev), () -> showOledVariant(activeOledMode, oledVariant - 1));
        btnNext = hudButton(getString(R.string.hud_btn_next), () -> showOledVariant(activeOledMode, oledVariant + 1));
        btnOledHdr = hudButton(getString(R.string.hud_btn_oled_sdr), this::toggleOledHdr);
        btnAplPeak = hudButton(getString(R.string.oled_apl_peak_button, APL_PEAKS[aplPeakIndex]), () -> {
            aplPeakIndex = (aplPeakIndex + 1) % APL_PEAKS.length;
            updateAplPeakButton();
            showOledVariant(activeOledMode, oledVariant);
        });
        btnPrev.setContentDescription(getString(R.string.a11y_previous_pattern));
        btnNext.setContentDescription(getString(R.string.a11y_next_pattern));
        btnRestart.setContentDescription(getString(R.string.a11y_restart_test));
        btnExit.setContentDescription(getString(R.string.a11y_exit_test));
        btnPrev.setVisibility(View.GONE);
        btnNext.setVisibility(View.GONE);
        btnOledHdr.setVisibility(View.GONE);
        btnAplPeak.setVisibility(View.GONE);

        fullscreenBottomHud.addView(btnWatchSdr);
        fullscreenBottomHud.addView(btnWatchHdr);
        fullscreenBottomHud.addView(btnRestart);
        fullscreenBottomHud.addView(btnPrev);
        fullscreenBottomHud.addView(btnNext);
        fullscreenBottomHud.addView(btnOledHdr);
        fullscreenBottomHud.addView(btnAplPeak);
        fullscreenBottomHud.addView(btnExit);
        Button btnHide = hudButton(getString(R.string.hud_btn_hide), () -> {});
        btnHide.setOnClickListener(v -> {
            handler.removeCallbacks(hideFullscreenHud);
            fullscreenHud.setVisibility(View.GONE);
            (oledView.getVisibility() == View.VISIBLE ? oledView : playerView).requestFocus();
        });
        fullscreenBottomHud.addView(btnHide);

        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(-1, -2);
        bottomParams.topMargin = dp(12);
        bottomParams.bottomMargin = dp(76);
        bottomParams.leftMargin = dp(8);
        bottomParams.rightMargin = dp(8);
        hudContent.addView(fullscreenBottomHud, bottomParams);

        fullscreenContainer.addView(fullscreenHud, new FrameLayout.LayoutParams(-1, -1));
    }

    private Button hudButton(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(TEXT);
        b.setBackgroundTintList(null);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setMinHeight(dp(48));
        b.setMinimumWidth(dp(48));
        b.setBackground(ripple(0xff222222, CARD_STROKE, 14));
        b.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMarginStart(dp(2));
        p.setMarginEnd(dp(2));
        b.setLayoutParams(p);
        b.setOnClickListener(v -> {
            action.run();
            showFullscreenHud();
        });
        return b;
    }

    private void updateCapabilitiesUi(Diagnostics.DeviceSummary summary) {
        if (summary == null) return;
        currentSummary = summary;
        capModelLabel.setText(summary.modelAndVersion);
        capHdrLabel.setText(summary.hdrSupport);
        capGamutLabel.setText(summary.wideGamut);
        capBrightnessLabel.setText(summary.peakBrightness);
    }

    private void startAutomatedTest() {
        cancelAutomatedTest();
        if (player != null) player.pause();
        setFullscreen(true);
        oledHdrMode = false;
        activeOledMode = -1;
        oledHdrPatternPlaying = false;
        applyDisplayColorMode(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        configureHudForOled(false);
        oledBrightnessRow.setVisibility(View.VISIBLE);
        brightnessSeek.setProgress(100);
        oledView.stop();
        oledView.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
        isAutomatedTest = true;
        currentMode = "sdr";
        playPattern("sdr_steps", 0);
        updateHudMode("sdr", 10);
        showFullscreenHud();
        handler.removeCallbacks(testCountdownTicker);
        handler.postDelayed(testCountdownTicker, 1000);
    }

    private void startOledSuite(int mode) {
        cancelAutomatedTest();
        setFullscreen(true);
        activeOledMode = mode;
        oledHdrMode = false;
        oledHdrPatternPlaying = false;
        aplPeakIndex = 1;
        configureHudForOled(true);
        btnAplPeak.setVisibility(mode == OledTestView.MODE_APL ? View.VISIBLE : View.GONE);
        updateAplPeakButton();
        oledBrightnessRow.setVisibility(View.VISIBLE);
        brightnessSeek.setProgress(100);
        applyOledColorMode();
        showOledVariant(mode, 0);
        showFullscreenHud();
    }

    private void configureHudForOled(boolean oled) {
        int quickVis = oled ? View.GONE : View.VISIBLE;
        int oledVis = oled ? View.VISIBLE : View.GONE;
        btnWatchSdr.setVisibility(quickVis);
        btnWatchHdr.setVisibility(quickVis);
        btnRestart.setVisibility(quickVis);
        btnPrev.setVisibility(oledVis);
        btnNext.setVisibility(oledVis);
        btnOledHdr.setVisibility(oledVis);
        btnAplPeak.setVisibility(oled && activeOledMode == OledTestView.MODE_APL ? View.VISIBLE : View.GONE);
    }

    private void toggleOledHdr() {
        if (!fullscreen || activeOledMode < 0 || !hdrVariantAvailable(activeOledMode, oledVariant)) return;
        oledHdrMode = !oledHdrMode;
        if (activeOledMode == OledTestView.MODE_LADDER) {
            if (oledHdrMode && oledVariant == 1) {
                showOledVariant(OledTestView.MODE_LADDER, 2);
                return;
            } else if (!oledHdrMode && oledVariant >= 2) {
                showOledVariant(OledTestView.MODE_LADDER, 1);
                return;
            }
        }
        showOledVariant(activeOledMode, oledVariant);
    }

    private void applyOledColorMode() {
        applyDisplayColorMode(oledHdrMode);
        btnOledHdr.setSelected(oledHdrMode);
        btnOledHdr.setText((oledHdrMode ? "✓ " : "") + getString(oledHdrMode ? R.string.hud_btn_oled_hdr : R.string.hud_btn_oled_sdr));
        btnOledHdr.setTextColor(oledHdrMode ? BLUE : TEXT);
        btnOledHdr.setTypeface(Typeface.DEFAULT, oledHdrMode ? Typeface.BOLD : Typeface.NORMAL);
        btnOledHdr.setBackground(ripple(oledHdrMode ? 0x332f64ae : 0x33222222, oledHdrMode ? BLUE : CARD_STROKE, 14));
        btnOledHdr.setContentDescription(btnOledHdr.getText());
    }

    private void updateAplPeakButton() {
        btnAplPeak.setText(getString(R.string.oled_apl_peak_button, APL_PEAKS[aplPeakIndex]));
        btnAplPeak.setContentDescription(btnAplPeak.getText());
    }

    private void applyDisplayColorMode(boolean hdr) {
        getWindow().setColorMode(hdr ? ActivityInfo.COLOR_MODE_HDR : ActivityInfo.COLOR_MODE_DEFAULT);
        if (Build.VERSION.SDK_INT >= 35) getWindow().setDesiredHdrHeadroom(hdr ? 0f : 1f);
    }

    private void showOledVariant(int mode, int variant) {
        if (!fullscreen) return;
        activeOledMode = mode;
        int count = mode == OledTestView.MODE_LADDER ? 1 + LADDER_VIDEOS.length : OledTestView.variantCount(mode);
        oledVariant = Math.floorMod(variant, count);
        oledBrightnessRow.setVisibility(View.VISIBLE);
        boolean motion = !hdrVariantAvailable(mode, oledVariant);
        btnOledHdr.setEnabled(!motion);
        btnOledHdr.setAlpha(motion ? .55f : 1f);
        btnAplPeak.setVisibility(mode == OledTestView.MODE_APL ? View.VISIBLE : View.GONE);
        if (motion) oledHdrMode = false;
        if (mode == OledTestView.MODE_LADDER && oledVariant > 0) {
            int videoIdx = oledVariant - 1;
            int unavailable = videoIdx == 0 ? 0 : hdrUnavailableReason(videoIdx == 2);
            if (unavailable != 0) {
                showOledVariant(mode, 1);
                hudTipLabel.append(" " + getString(unavailable));
                updatePatternDescription();
                return;
            }
            oledHdrMode = videoIdx > 0;
            oledHdrPatternPlaying = false;
            applyOledColorMode();
            oledView.stop();
            oledView.setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            playPattern(LADDER_VIDEOS[videoIdx], 0);
            hudTitleLabel.setText(getString(R.string.oled_title_ladder_video, getString(LADDER_VIDEO_LABELS[videoIdx])));
            hudTipLabel.setText(getString(R.string.oled_tip_ladder_video));
            styleOledHud();
            updatePatternDescription();
            return;
        }
        String hdrPattern = oledHdrMode ? hdrPatternName(mode, oledVariant) : null;
        int unavailable = hdrPattern == null ? 0 : hdrUnavailableReason();
        if (unavailable != 0) oledHdrMode = false;
        if (oledHdrMode) {
            oledHdrPatternPlaying = true;
            applyOledColorMode();
            oledView.stop();
            oledView.setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
            setOledHudText(mode, oledVariant, true);
            styleOledHud();
            updatePatternDescription();
            try {
                playPattern(hdrPattern, 0);
            } catch (RuntimeException error) {
                fallbackHdrPattern(R.string.oled_hdr_playback_failed);
            }
            return;
        }
        oledHdrPatternPlaying = false;
        applyOledColorMode();
        if (player != null) player.pause();
        playerView.setVisibility(View.GONE);
        oledView.setVisibility(View.VISIBLE);
        oledView.setPattern(mode, oledVariant);
        setOledHudText(mode, oledVariant, false);
        if (unavailable != 0) hudTipLabel.append(" " + getString(unavailable));
        else if (motion) hudTipLabel.append(" " + getString(R.string.oled_hdr_motion_unavailable));
        styleOledHud();
        updatePatternDescription();
    }

    private void setOledHudText(int mode, int variant, boolean hdr) {
        switch (mode) {
            case OledTestView.MODE_SMEAR:
                hudTitleLabel.setText(getString(R.string.oled_title_smear, oledView.smearSpeed(),
                    getString(oledView.smearVertical() ? R.string.oled_dir_vertical : R.string.oled_dir_horizontal)));
                hudTipLabel.setText(getString(R.string.oled_tip_smear));
                break;
            case OledTestView.MODE_LADDER:
                hudTitleLabel.setText(hdr
                    ? getString(R.string.oled_title_ladder_video, getString(R.string.oled_video_pq))
                    : getString(R.string.oled_title_ladder_canvas));
                hudTipLabel.setText(getString(R.string.oled_tip_ladder_canvas));
                break;
            case OledTestView.MODE_DSE:
                hudTitleLabel.setText(getString(R.string.oled_title_dse,
                    OledTestView.DSE_PERCENT[variant % OledTestView.DSE_PERCENT.length],
                    getString(variant >= OledTestView.DSE_PERCENT.length ? R.string.oled_dse_pan : R.string.oled_dse_static)));
                hudTipLabel.setText(getString(R.string.oled_tip_dse));
                break;
            case OledTestView.MODE_APL:
                hudTitleLabel.setText(getString(R.string.oled_title_apl, OledTestView.APL_PERCENT[variant]));
                hudTipLabel.setText(getString(R.string.oled_tip_apl));
                break;
            case OledTestView.MODE_BURNIN:
                hudTitleLabel.setText(getString(R.string.oled_title_burnin, getString(BURNIN_LABELS[oledVariant])));
                hudTipLabel.setText(getString(R.string.oled_tip_burnin));
                break;
            case OledTestView.MODE_RAMP:
                hudTitleLabel.setText(getString(hdr ? R.string.oled_title_hdr_ramp : R.string.oled_title_ramp,
                    getString(RAMP_LABELS[variant])));
                hudTipLabel.setText(getString(R.string.oled_tip_ramp));
                break;
        }
        if (hdr) {
            hudTitleLabel.setText(getString(R.string.oled_hdr_title_format,
                getString(R.string.oled_hdr_requested), hudTitleLabel.getText()));
            if (mode == OledTestView.MODE_APL) {
                hudTipLabel.append(" " + getString(R.string.oled_hdr_apl_reference,
                    (int) OledTestView.APL_PERCENT[variant], APL_PEAKS[aplPeakIndex]));
            } else if (mode == OledTestView.MODE_DSE) {
                int code = OledTestView.code(OledTestView.DSE_PERCENT[variant]);
                hudTipLabel.append(" " + getString(R.string.oled_hdr_levels, code, nominalNits(code)));
            } else if (mode == OledTestView.MODE_LADDER) {
                StringBuilder levels = new StringBuilder();
                for (int i = 0; i < OledTestView.LADDER_PERCENT.length; i++) {
                    if (i > 0) levels.append(", ");
                    levels.append(String.format(getResources().getConfiguration().getLocales().get(0), "%.3f",
                        nominalNits(OledTestView.code(OledTestView.LADDER_PERCENT[i]))));
                }
                hudTipLabel.append(" " + getString(R.string.oled_hdr_ladder_levels, levels.toString()));
            } else {
                hudTipLabel.append(" " + getString(R.string.oled_hdr_reference_peak, 1000));
            }
        }
    }

    private void styleOledHud() {
        hudTitleLabel.setTextColor(BLUE);
        brightnessSeek.setProgressTintList(ColorStateList.valueOf(BLUE));
        brightnessSeek.setThumbTintList(ColorStateList.valueOf(BLUE));
    }

    private static boolean hdrVariantAvailable(int mode, int variant) {
        return mode != OledTestView.MODE_SMEAR && (mode != OledTestView.MODE_DSE
            || variant < OledTestView.DSE_PERCENT.length);
    }

    private String hdrPatternName(int mode, int variant) {
        switch (mode) {
            case OledTestView.MODE_LADDER: return variant == 0 ? "pq_oled_ladder" : null;
            case OledTestView.MODE_DSE: return variant < OledTestView.DSE_PERCENT.length
                ? String.format(Locale.ROOT, "pq_oled_dse_%02d", (int) OledTestView.DSE_PERCENT[variant]) : null;
            case OledTestView.MODE_APL: return String.format(Locale.ROOT, "pq_oled_apl_%d_%02d",
                APL_PEAKS[aplPeakIndex], (int) OledTestView.APL_PERCENT[variant]);
            case OledTestView.MODE_BURNIN: return "pq_oled_burnin_" + HDR_BURNIN_NAMES[variant];
            case OledTestView.MODE_RAMP: return "pq_oled_ramp_" + HDR_RAMP_NAMES[variant];
            default: return null;
        }
    }

    private static double nominalNits(int code) {
        double signal = code / 255d;
        double linear = signal <= .04045d ? signal / 12.92d : Math.pow((signal + .055d) / 1.055d, 2.4d);
        // Match the limited-range 10-bit PQ quantization used by the packaged reference clips.
        double m1 = 2610d / 16384d, m2 = 2523d / 32d;
        double c1 = 3424d / 4096d, c2 = 2413d / 128d, c3 = 2392d / 128d;
        double l = Math.pow(linear * 100d / 10000d, m1);
        double pq = Math.pow((c1 + c2 * l) / (1d + c3 * l), m2);
        int encoded = (int) Math.round(64d + 876d * pq);
        double decoded = Math.pow((encoded - 64d) / 876d, 1d / m2);
        return Math.pow(Math.max(0d, decoded - c1) / (c2 - c3 * decoded), 1d / m1) * 10000d;
    }

    private int hdrUnavailableReason() { return hdrUnavailableReason(false); }

    private int hdrUnavailableReason(boolean hlg) {
        if (hdrAvailabilityOverride != -1) return hdrAvailabilityOverride;
        try {
            Display display = Diagnostics.defaultDisplay(this);
            if (display != null && display.getHdrCapabilities() != null) {
                boolean transferSupported = false;
                for (int type : display.getHdrCapabilities().getSupportedHdrTypes()) {
                    if (type == (hlg ? Display.HdrCapabilities.HDR_TYPE_HLG
                        : Display.HdrCapabilities.HDR_TYPE_HDR10)) transferSupported = true;
                }
                if (!transferSupported) return hlg ? R.string.oled_hlg_unavailable_display
                    : R.string.oled_hdr_unavailable_display;
            }
        } catch (RuntimeException ignored) { /* Unknown capability: try the video. */ }
        try {
            MediaCodecInfo[] codecs = new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
            boolean profileKnown = false, main10Seen = false, profileUnknown = false;
            for (MediaCodecInfo codec : codecs) {
                if (codec.isEncoder()) continue;
                for (String type : codec.getSupportedTypes()) {
                    if (!"video/hevc".equalsIgnoreCase(type)) continue;
                    MediaCodecInfo.CodecProfileLevel[] profiles = codec.getCapabilitiesForType(type).profileLevels;
                    if (profiles == null || profiles.length == 0) {
                        profileUnknown = true;
                        continue;
                    }
                    profileKnown = true;
                    for (MediaCodecInfo.CodecProfileLevel profile : profiles) {
                        if (profile.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                            || profile.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
                            || profile.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus) main10Seen = true;
                    }
                }
            }
            if (profileKnown && !profileUnknown && !main10Seen) return R.string.oled_hdr_unavailable_decoder;
        } catch (RuntimeException ignored) { /* Unknown capability: let Media3 report failure. */ }
        return 0;
    }

    private void fallbackHdrPattern(int reason) {
        if (!fullscreen || !oledHdrPatternPlaying || activeOledMode < 0) return;
        oledHdrPatternPlaying = false;
        oledHdrMode = false;
        if (player != null) player.pause();
        showOledVariant(activeOledMode, oledVariant);
        hudTipLabel.append(" " + getString(reason));
        updatePatternDescription();
        showFullscreenHud();
    }

    private void switchToHdr() {
        if (!fullscreen) return;
        cancelAutomatedTest();
        applyDisplayColorMode(true);
        currentMode = "hdr";
        long pos = player != null ? player.getCurrentPosition() : 0;
        playPattern("pq_steps", pos);
        updateHudMode("hdr", 0);
        showFullscreenHud();
    }

    private void switchToSdr() {
        if (!fullscreen) return;
        cancelAutomatedTest();
        applyDisplayColorMode(false);
        currentMode = "sdr";
        long pos = player != null ? player.getCurrentPosition() : 0;
        playPattern("sdr_steps", pos);
        updateHudMode("sdr", -1);
        showFullscreenHud();
    }

    private void restartTest() {
        startAutomatedTest();
    }

    private void cancelAutomatedTest() {
        isAutomatedTest = false;
        testPlaybackMs = 0;
        testPlayingSinceMs = -1;
        handler.removeCallbacks(testCountdownTicker);
    }

    private void setFullscreen(boolean enabled) {
        if (!enabled) {
            exitFullscreen();
            return;
        }
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (!fullscreen) {
            fullscreen = true;
            originalBrightness = lp.screenBrightness;
            originalDisplayModeId = lp.preferredDisplayModeId;
            originalColorMode = getWindow().getColorMode();
            if (Build.VERSION.SDK_INT >= 35) originalHdrHeadroom = getWindow().getDesiredHdrHeadroom();
        }
        lp.screenBrightness = 1.0f;
        Display display = Diagnostics.defaultDisplay(this);
        if (display != null) {
            Display.Mode best = Diagnostics.highestRefreshMode(display);
            if (best != null) lp.preferredDisplayModeId = best.getModeId();
        }
        getWindow().setAttributes(lp);

        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }

        workspaceLayout.setVisibility(View.GONE);
        fullscreenContainer.setVisibility(View.VISIBLE);
        root.setPadding(0, 0, 0, 0);
        root.requestApplyInsets();
    }

    private void exitFullscreen() {
        if (!fullscreen) return;
        fullscreen = false;
        cancelAutomatedTest();
        handler.removeCallbacks(hideFullscreenHud);
        if (fullscreenHud != null) fullscreenHud.setVisibility(View.GONE);
        if (oledView != null) {
            oledView.stop();
            oledView.setVisibility(View.GONE);
        }
        if (player != null) player.pause();
        oledHdrPatternPlaying = false;
        activeOledMode = -1;

        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = originalBrightness;
        lp.preferredDisplayModeId = originalDisplayModeId;
        getWindow().setAttributes(lp);
        oledHdrMode = false;
        getWindow().setColorMode(originalColorMode);
        if (Build.VERSION.SDK_INT >= 35) {
            getWindow().setDesiredHdrHeadroom(originalHdrHeadroom);
        }

        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) controller.show(WindowInsets.Type.systemBars());
        }

        fullscreenContainer.setVisibility(View.GONE);
        workspaceLayout.setVisibility(View.VISIBLE);
        fullscreenHud.setPadding(0, 0, 0, 0);
        root.requestApplyInsets();
        root.post(this::applyResponsiveLayout);
    }

    private void ensurePlayer() {
        if (player != null) return;
        player = new ExoPlayer.Builder(this, new DefaultRenderersFactory(this).setEnableDecoderFallback(true)).build();
        player.setSeekParameters(SeekParameters.EXACT);
        playerView.setPlayer(player);
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        playbackListener = new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                if (!isAutomatedTest) return;
                if (isPlaying && testPlayingSinceMs < 0) {
                    testPlayingSinceMs = SystemClock.elapsedRealtime();
                } else if (!isPlaying && testPlayingSinceMs >= 0) {
                    testPlaybackMs += SystemClock.elapsedRealtime() - testPlayingSinceMs;
                    testPlayingSinceMs = -1;
                }
            }

            @Override public void onPlayerError(PlaybackException error) {
                if (!fullscreen || playerView.getVisibility() != View.VISIBLE) return;
                if (oledHdrPatternPlaying) {
                    fallbackHdrPattern(R.string.oled_hdr_playback_failed);
                    return;
                }
                if (activeOledMode == OledTestView.MODE_LADDER && oledVariant >= 2) {
                    int failedVariant = oledVariant;
                    oledHdrMode = false;
                    showOledVariant(OledTestView.MODE_LADDER, 1);
                    hudTipLabel.append(" " + getString(failedVariant == 3
                        ? R.string.oled_hlg_playback_failed : R.string.oled_hdr_playback_failed));
                    updatePatternDescription();
                    showFullscreenHud();
                    return;
                }
                exitFullscreen();
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(getString(R.string.playback_error, error.getErrorCodeName()))
                    .setPositiveButton(R.string.dialog_btn_close, null).show();
            }
        };
        player.addListener(playbackListener);
    }

    private void playPattern(String patternName, long position) {
        ensurePlayer();
        File file = getPatternFile(patternName);
        Uri uri = file != null ? Uri.fromFile(file) : Uri.parse("asset:///patterns/" + patternName + ".mp4");
        player.setMediaItem(MediaItem.fromUri(uri));
        player.seekTo(position);
        player.prepare();
        player.play();
    }

    private File getPatternFile(String name) {
        File dir = new File(getFilesDir(), "patterns");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, name + ".mp4");
        if (!file.exists() || !extractedPatterns.contains(name)) {
            try (InputStream in = getAssets().open("patterns/" + name + ".mp4");
                 OutputStream out = new FileOutputStream(file, false)) {
                copy(in, out);
                extractedPatterns.add(name);
            } catch (IOException e) {
                return file.exists() ? file : null;
            }
        }
        return file;
    }

    private void updateHudMode(String mode, int countdown) {
        hudTitleLabel.setTextColor(ACCENT);
        brightnessSeek.setProgressTintList(ColorStateList.valueOf(ACCENT));
        brightnessSeek.setThumbTintList(ColorStateList.valueOf(ACCENT));
        if ("sdr".equals(mode)) {
            if (countdown > 0) {
                hudTitleLabel.setText(getString(R.string.hud_title_sdr_countdown, countdown));
                hudTipLabel.setText(getString(R.string.hud_tip_sdr_countdown));
            } else {
                hudTitleLabel.setText(getString(R.string.hud_title_sdr_active));
                hudTipLabel.setText(getString(R.string.hud_tip_sdr_active));
            }
        } else {
            hudTitleLabel.setText(getString(R.string.hud_title_hdr_active));
            hudTipLabel.setText(getString(R.string.hud_tip_hdr_active));
        }
        updateHudButtons();
        updatePatternDescription();
    }

    private void updatePatternDescription() {
        String description = getString(R.string.oled_pattern_description,
            hudTitleLabel.getText() + ". " + hudTipLabel.getText());
        oledView.setContentDescription(description);
        playerView.setContentDescription(description);
        oledView.setFocusable(true);
        playerView.setFocusable(true);
    }

    private void updateHudButtons() {
        boolean isSdr = "sdr".equals(currentMode);
        btnWatchSdr.setSelected(isSdr);
        btnWatchSdr.setText((isSdr ? "✓ " : "") + getString(R.string.hud_btn_watch_sdr));
        btnWatchSdr.setTextColor(isSdr ? ACCENT : TEXT);
        btnWatchSdr.setTypeface(Typeface.DEFAULT, isSdr ? Typeface.BOLD : Typeface.NORMAL);
        btnWatchSdr.setBackground(ripple(isSdr ? 0x33416955 : 0xff222222, isSdr ? ACCENT : CARD_STROKE, 14));

        boolean isHdr = "hdr".equals(currentMode);
        btnWatchHdr.setSelected(isHdr);
        btnWatchHdr.setText((isHdr ? "✓ " : "") + getString(R.string.hud_btn_watch_hdr));
        btnWatchHdr.setTextColor(isHdr ? ACCENT : TEXT);
        btnWatchHdr.setTypeface(Typeface.DEFAULT, isHdr ? Typeface.BOLD : Typeface.NORMAL);
        btnWatchHdr.setBackground(ripple(isHdr ? 0x33416955 : 0xff222222, isHdr ? ACCENT : CARD_STROKE, 14));
    }

    private void showFullscreenHud() {
        if (fullscreenHud == null || !fullscreen) return;
        handler.removeCallbacks(hideFullscreenHud);
        fullscreenHud.setVisibility(View.VISIBLE);
        applyFullscreenLayout();
        AccessibilityManager accessibility = getSystemService(AccessibilityManager.class);
        if (accessibility == null || !accessibility.isTouchExplorationEnabled()) {
            int timeout = accessibility == null ? 3000 : accessibility.getRecommendedTimeoutMillis(3000,
                AccessibilityManager.FLAG_CONTENT_TEXT | AccessibilityManager.FLAG_CONTENT_CONTROLS);
            handler.postDelayed(hideFullscreenHud, timeout);
        }
    }

    private void showDetailedReport() {
        worker.execute(() -> {
            try {
                JSONObject report = Diagnostics.device(this);
                String text = report.toString(2);
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    showReportDialog(text, report);
                });
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this, getString(R.string.error_dialog, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showReportDialog(String text, JSONObject report) {
        TextView title = label(getString(R.string.dialog_technical_report_title), 18, RED);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(dp(24), dp(20), dp(24), dp(8));
        title.setAccessibilityHeading(true);
        title.setContentDescription(title.getText());
        TextView body = label(text, 12, TEXT);
        body.setTextIsSelectable(true);
        body.setTypeface(Typeface.MONOSPACE);
        body.setPadding(dp(20), dp(12), dp(20), dp(20));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setCustomTitle(title)
            .setView(scroll)
            .setPositiveButton(getString(R.string.dialog_btn_share), (d, w) -> shareReportJson(report))
            .setNegativeButton(getString(R.string.dialog_btn_close), null)
            .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(RED);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(RED);
    }

    private void showCommunityReportSurvey() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(16));
        scroll.addView(content);

        // Specs Summary Header
        TextView specsHeader = label(getString(R.string.dialog_report_specs_header), 11, ACCENT);
        specsHeader.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        specsHeader.setLetterSpacing(0.08f);
        content.addView(specsHeader);

        LinearLayout specsBox = new LinearLayout(this);
        specsBox.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(0xee101010);
        boxBg.setCornerRadius(dp(8));
        boxBg.setStroke(dp(1), CARD_STROKE);
        specsBox.setBackground(boxBg);
        specsBox.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(-1, -2);
        boxParams.topMargin = dp(4);
        boxParams.bottomMargin = dp(14);
        specsBox.setLayoutParams(boxParams);

        String model = Build.MANUFACTURER + " " + Build.MODEL;
        String os = (currentSummary != null && currentSummary.osInfo != null)
            ? currentSummary.osInfo : Diagnostics.detectOsInfo();
        String hdr = (currentSummary != null && currentSummary.hdrSupport != null)
            ? currentSummary.hdrSupport : getString(R.string.status_checking);
        String peak = (currentSummary != null && currentSummary.peakBrightness != null)
            ? currentSummary.peakBrightness : getString(R.string.status_checking);

        specsBox.addView(label(getString(R.string.dialog_report_spec_device, model), 12, TEXT));
        specsBox.addView(label(getString(R.string.dialog_report_spec_os, os), 12, TEXT));
        specsBox.addView(label(getString(R.string.dialog_report_spec_hdr, hdr), 12, TEXT));
        specsBox.addView(label(getString(R.string.dialog_report_spec_peak, peak), 12, TEXT));
        content.addView(specsBox);

        // Question 1: SDR Black background level
        TextView q1Title = label(getString(R.string.dialog_report_q1_title), 13, ACCENT);
        q1Title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(q1Title);

        RadioGroup rgSdrBlack = new RadioGroup(this);
        rgSdrBlack.setOrientation(RadioGroup.VERTICAL);
        RadioButton rb1A = createRadioButton(getString(R.string.dialog_report_q1_opt_a));
        RadioButton rb1B = createRadioButton(getString(R.string.dialog_report_q1_opt_b));
        rgSdrBlack.addView(rb1A);
        rgSdrBlack.addView(rb1B);
        rgSdrBlack.check(rb1A.getId());
        content.addView(rgSdrBlack);

        // Question 2: SDR Near-black steps
        TextView q2Title = label(getString(R.string.dialog_report_q2_title), 13, ACCENT);
        q2Title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams q2Params = new LinearLayout.LayoutParams(-1, -2);
        q2Params.topMargin = dp(12);
        q2Title.setLayoutParams(q2Params);
        content.addView(q2Title);

        RadioGroup rgSdrSteps = new RadioGroup(this);
        rgSdrSteps.setOrientation(RadioGroup.VERTICAL);
        RadioButton rb2A = createRadioButton(getString(R.string.dialog_report_q2_opt_a));
        RadioButton rb2B = createRadioButton(getString(R.string.dialog_report_q2_opt_b));
        RadioButton rb2C = createRadioButton(getString(R.string.dialog_report_q2_opt_c));
        rgSdrSteps.addView(rb2A);
        rgSdrSteps.addView(rb2B);
        rgSdrSteps.addView(rb2C);
        rgSdrSteps.check(rb2A.getId());
        content.addView(rgSdrSteps);

        // Question 3: HDR Black background
        TextView q3Title = label(getString(R.string.dialog_report_q3_title), 13, ACCENT);
        q3Title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams q3Params = new LinearLayout.LayoutParams(-1, -2);
        q3Params.topMargin = dp(12);
        q3Title.setLayoutParams(q3Params);
        content.addView(q3Title);

        RadioGroup rgHdrBlack = new RadioGroup(this);
        rgHdrBlack.setOrientation(RadioGroup.VERTICAL);
        RadioButton rb3A = createRadioButton(getString(R.string.dialog_report_q3_opt_a));
        RadioButton rb3B = createRadioButton(getString(R.string.dialog_report_q3_opt_b));
        RadioButton rb3C = createRadioButton(getString(R.string.dialog_report_q3_opt_c));
        rgHdrBlack.addView(rb3A);
        rgHdrBlack.addView(rb3B);
        rgHdrBlack.addView(rb3C);
        rgHdrBlack.check(rb3A.getId());
        content.addView(rgHdrBlack);

        // Question 4: HDR Near-black steps
        TextView q4Title = label(getString(R.string.dialog_report_q4_title), 13, ACCENT);
        q4Title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams q4Params = new LinearLayout.LayoutParams(-1, -2);
        q4Params.topMargin = dp(12);
        q4Title.setLayoutParams(q4Params);
        content.addView(q4Title);

        RadioGroup rgHdrSteps = new RadioGroup(this);
        rgHdrSteps.setOrientation(RadioGroup.VERTICAL);
        RadioButton rb4A = createRadioButton(getString(R.string.dialog_report_q4_opt_a));
        RadioButton rb4B = createRadioButton(getString(R.string.dialog_report_q4_opt_b));
        RadioButton rb4C = createRadioButton(getString(R.string.dialog_report_q4_opt_c));
        rgHdrSteps.addView(rb4A);
        rgHdrSteps.addView(rb4B);
        rgHdrSteps.addView(rb4C);
        rgHdrSteps.check(rb4A.getId());
        content.addView(rgHdrSteps);

        // Optional OLED Test Suite observations
        TextView oledHeader = label(getString(R.string.dialog_report_oled_header), 11, ACCENT);
        oledHeader.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        oledHeader.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams oledHeaderParams = new LinearLayout.LayoutParams(-1, -2);
        oledHeaderParams.topMargin = dp(16);
        oledHeader.setLayoutParams(oledHeaderParams);
        content.addView(oledHeader);

        String notTested = getString(R.string.oled_not_tested);
        RadioGroup rgCrush = addSurveyGroup(content, getString(R.string.dialog_report_crush_title),
            notTested,
            getString(R.string.dialog_report_crush_all),
            getString(R.string.dialog_report_crush_below1),
            getString(R.string.dialog_report_crush_below2),
            getString(R.string.dialog_report_crush_below3),
            getString(R.string.dialog_report_crush_above3));
        RadioGroup rgSmear = addSurveyGroup(content, getString(R.string.dialog_report_smear_title),
            notTested,
            getString(R.string.dialog_report_smear_none),
            getString(R.string.dialog_report_smear_mild),
            getString(R.string.dialog_report_smear_severe));
        RadioGroup rgDse = addSurveyGroup(content, getString(R.string.dialog_report_dse_title),
            notTested,
            getString(R.string.dialog_report_dse_clean),
            getString(R.string.dialog_report_dse_banding),
            getString(R.string.dialog_report_dse_mottling));
        RadioGroup rgFloat = addSurveyGroup(content, getString(R.string.dialog_report_float_title),
            notTested,
            getString(R.string.dialog_report_float_true),
            getString(R.string.dialog_report_float_glow));
        RadioGroup rgBurnIn = addSurveyGroup(content, getString(R.string.dialog_report_burnin_title),
            notTested,
            getString(R.string.dialog_report_burnin_clean),
            getString(R.string.dialog_report_burnin_retention),
            getString(R.string.dialog_report_burnin_burnin));

        // Question 10: Observations / Notes (optional)
        TextView q5Title = label(getString(R.string.dialog_report_q5_title), 13, ACCENT);
        q5Title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams q5Params = new LinearLayout.LayoutParams(-1, -2);
        q5Params.topMargin = dp(12);
        q5Title.setLayoutParams(q5Params);
        content.addView(q5Title);

        EditText etComments = new EditText(this);
        etComments.setFilters(new android.text.InputFilter[] {
            new android.text.InputFilter.LengthFilter(500)
        });
        etComments.setHint(getString(R.string.dialog_report_q5_hint));
        etComments.setHintTextColor(0x88a7b2a5);
        etComments.setTextColor(TEXT);
        etComments.setTextSize(13);
        etComments.setMinLines(2);
        etComments.setMaxLines(4);
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0xff242424);
        etBg.setCornerRadius(dp(8));
        etBg.setStroke(dp(1), CARD_STROKE);
        etComments.setBackground(etBg);
        etComments.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(-1, -2);
        etParams.topMargin = dp(4);
        etComments.setLayoutParams(etParams);
        content.addView(etComments);

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_report_title))
            .setView(scroll)
            .setPositiveButton(getString(R.string.dialog_btn_submit), (dialog, which) -> {
                String sdrBlackLevel = getCheckedText(rgSdrBlack);
                String sdrSteps = getCheckedText(rgSdrSteps);
                String hdrBlackLevel = getCheckedText(rgHdrBlack);
                String hdrSteps = getCheckedText(rgHdrSteps);

                String comments = etComments.getText().toString().trim();

                List<String> hdrTypes = (currentSummary != null && currentSummary.hdrTypes != null && !currentSummary.hdrTypes.isEmpty())
                    ? currentSummary.hdrTypes
                    : Collections.emptyList();
                float peakNits = (currentSummary != null) ? currentSummary.peakBrightnessNits : -1f;
                float minNits = (currentSummary != null) ? currentSummary.minLuminanceNits : -1f;
                boolean wideGamut = (currentSummary != null) && currentSummary.isWideGamut;
                String activeMode = (currentSummary != null && currentSummary.activeMode != null)
                    ? currentSummary.activeMode : "";
                String socModel = Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL != null ? Build.SOC_MODEL : "";

                // If background device snapshot has additional display mode or luminance info, fallback to it
                if (deviceSnapshot != null && deviceSnapshot.has("display")) {
                    JSONObject screen = deviceSnapshot.optJSONObject("display");
                    if (screen != null) {
                        if (hdrTypes.isEmpty()) {
                            JSONArray arr = screen.optJSONArray("advertisedHdrTypes");
                            if (arr != null && arr.length() > 0) {
                                List<String> list = new ArrayList<>();
                                for (int i = 0; i < arr.length(); i++) list.add(arr.optString(i));
                                hdrTypes = list;
                            }
                        }
                        if (minNits < 0) {
                            double min = screen.optDouble("desiredMinLuminanceNits", -1);
                            if (min >= 0 && Double.isFinite(min)) minNits = (float) min;
                        }
                        if (peakNits <= 0) {
                            double lum = screen.optDouble("desiredMaxLuminanceNits", -1);
                            if (lum > 0 && Double.isFinite(lum)) peakNits = (float) lum;
                        }
                        if (activeMode.isEmpty()) {
                            activeMode = screen.optString("activeMode", "");
                        }
                    }
                }

                String cleanDevice = Diagnostics.getCleanDeviceName(Build.MANUFACTURER, Build.MODEL);
                String brand = Build.BRAND != null ? Build.BRAND : "";
                String product = Build.PRODUCT != null ? Build.PRODUCT : "";
                String hardware = Build.HARDWARE != null ? Build.HARDWARE : "";
                Display display = Diagnostics.defaultDisplay(this);
                float refreshRateHz = display != null ? display.getRefreshRate() : -1f;
                List<String> supportedModes = Diagnostics.supportedModeLabels(display);

                ReportSubmitter.CommunityReport report = new ReportSubmitter.CommunityReport(
                    cleanDevice,
                    Build.MODEL != null ? Build.MODEL : "",
                    Build.MANUFACTURER != null ? Build.MANUFACTURER : "",
                    brand,
                    product,
                    hardware,
                    socModel,
                    os,
                    Build.VERSION.RELEASE != null ? Build.VERSION.RELEASE : "",
                    Build.VERSION.SDK_INT,
                    hdrTypes,
                    peakNits,
                    minNits,
                    wideGamut,
                    activeMode,
                    ReportSubmitter.APP_VERSION,
                    sdrBlackLevel,
                    sdrSteps,
                    hdrBlackLevel,
                    hdrSteps,
                    comments,
                    System.currentTimeMillis(),
                    refreshRateHz,
                    supportedModes,
                    optionalObservation(rgCrush, notTested),
                    optionalObservation(rgSmear, notTested),
                    optionalObservation(rgDse, notTested),
                    optionalObservation(rgFloat, notTested),
                    optionalObservation(rgBurnIn, notTested)
                );

                ReportSubmitter.submit(report, ReportSubmitter.DEFAULT_ENDPOINT, new ReportSubmitter.Callback() {
                    @Override public void onSuccess() {
                        Toast.makeText(MainActivity.this, getString(R.string.report_submit_success), Toast.LENGTH_LONG).show();
                    }
                    @Override public void onError(String message) {
                        Toast.makeText(MainActivity.this, getString(R.string.report_submit_error, message), Toast.LENGTH_LONG).show();
                    }
                });
            })
            .setNegativeButton(getString(R.string.dialog_btn_cancel), null)
            .show();
    }

    private RadioGroup addSurveyGroup(LinearLayout parent, String titleText, String... options) {
        TextView title = label(titleText, 13, ACCENT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(12);
        title.setLayoutParams(p);
        parent.addView(title);
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < options.length; i++) {
            RadioButton rb = createRadioButton(options[i]);
            rg.addView(rb);
            if (i == 0) rg.check(rb.getId());
        }
        parent.addView(rg);
        return rg;
    }

    private String optionalObservation(RadioGroup group, String notTested) {
        String value = getCheckedText(group);
        return value.equals(notTested) ? "" : value;
    }

    private String getCheckedText(RadioGroup group) {
        if (group == null) return "";
        int checkedId = group.getCheckedRadioButtonId();
        if (checkedId != View.NO_ID && checkedId != -1) {
            RadioButton rb = group.findViewById(checkedId);
            if (rb != null && rb.getText() != null) return rb.getText().toString();
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof RadioButton && ((RadioButton) child).isChecked()) {
                CharSequence text = ((RadioButton) child).getText();
                return text != null ? text.toString() : "";
            }
        }
        return "";
    }

    private RadioButton createRadioButton(String text) {
        RadioButton rb = new RadioButton(this);
        rb.setId(View.generateViewId());
        rb.setText(text);
        rb.setTextColor(TEXT);
        rb.setTextSize(13);
        rb.setPadding(dp(6), dp(6), dp(6), dp(6));
        if (Build.VERSION.SDK_INT >= 21) {
            rb.setButtonTintList(ColorStateList.valueOf(ACCENT));
        }
        return rb;
    }

    private void shareReportJson(JSONObject report) {
        try {
            File oldDir = new File(getFilesDir(), "reports");
            if (oldDir.exists()) {
                File[] oldFiles = oldDir.listFiles();
                if (oldFiles != null) for (File f : oldFiles) f.delete();
                oldDir.delete();
            }
            File dir = new File(getCacheDir(), "reports");
            dir.mkdirs();
            File file = new File(dir, "hdr-lab-light-report.json");
            try (OutputStream output = new FileOutputStream(file)) {
                output.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            Intent share = new Intent(Intent.ACTION_SEND)
                .setType("application/json")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(ClipData.newRawUri(getString(R.string.share_clip_label), uri));
            startActivity(Intent.createChooser(share, getString(R.string.share_report_title)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_share, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    public static String formatColorInfo(ColorInfo colorInfo) {
        if (colorInfo == null) return "Color metadata unspecified";
        String space = null;
        switch (colorInfo.colorSpace) {
            case C.COLOR_SPACE_BT2020: space = "BT.2020"; break;
            case C.COLOR_SPACE_BT709: space = "BT.709"; break;
            case C.COLOR_SPACE_BT601: space = "BT.601"; break;
        }
        String range = null;
        switch (colorInfo.colorRange) {
            case C.COLOR_RANGE_LIMITED: range = "Limited"; break;
            case C.COLOR_RANGE_FULL: range = "Full"; break;
        }
        String transfer = null;
        switch (colorInfo.colorTransfer) {
            case C.COLOR_TRANSFER_ST2084: transfer = "PQ / HDR10"; break;
            case C.COLOR_TRANSFER_HLG: transfer = "HLG"; break;
            case C.COLOR_TRANSFER_SDR: transfer = "SDR"; break;
            case C.COLOR_TRANSFER_SRGB: transfer = "sRGB"; break;
        }
        List<String> parts = new ArrayList<>();
        if (space != null) parts.add(space);
        if (range != null) parts.add(range);
        if (transfer != null) parts.add(transfer);
        if (parts.isEmpty()) return "Color metadata unspecified";
        return String.join(" · ", parts);
    }

    private void applyResponsiveLayout() {
        applyFullscreenLayout();
        if (fullscreen || cardsLayout == null || cardsContainer == null) return;
        int availableWidth = root.getWidth() - root.getPaddingLeft() - root.getPaddingRight();
        float widthDp = availableWidth / getResources().getDisplayMetrics().density;
        boolean isLandscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        boolean wide = widthDp >= 720 || (widthDp >= 580 && isLandscape);

        if (wide) {
            int contentWidth = Math.min(dp(1100), availableWidth - dp(32));
            ScrollView.LayoutParams params = (ScrollView.LayoutParams) cardsLayout.getLayoutParams();
            if (params.width != contentWidth || params.gravity != Gravity.CENTER_HORIZONTAL) {
                params.width = contentWidth;
                params.gravity = Gravity.CENTER_HORIZONTAL;
                cardsLayout.setLayoutParams(params);
            }
            if (cardsContainer.getOrientation() != LinearLayout.HORIZONTAL) {
                cardsContainer.setOrientation(LinearLayout.HORIZONTAL);
                cardsContainer.setBaselineAligned(false);
            }
            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            p1.setMargins(0, dp(16), dp(10), dp(12));
            deviceCapabilitiesCard.setLayoutParams(p1);

            LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            p2.setMargins(dp(10), dp(16), 0, dp(12));
            quickTestCard.setLayoutParams(p2);
        } else {
            ScrollView.LayoutParams params = (ScrollView.LayoutParams) cardsLayout.getLayoutParams();
            if (params.width != -1 || params.gravity != Gravity.NO_GRAVITY) {
                params.width = -1;
                params.gravity = Gravity.NO_GRAVITY;
                cardsLayout.setLayoutParams(params);
            }
            if (cardsContainer.getOrientation() != LinearLayout.VERTICAL) {
                cardsContainer.setOrientation(LinearLayout.VERTICAL);
            }
            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(-1, -2);
            p1.setMargins(0, dp(16), 0, dp(6));
            deviceCapabilitiesCard.setLayoutParams(p1);

            LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(-1, -2);
            p2.setMargins(0, dp(6), 0, dp(12));
            quickTestCard.setLayoutParams(p2);
        }
    }

    private void applyFullscreenLayout() {
        if (fullscreenBottomHud == null || root.getWidth() == 0) return;
        int available = root.getWidth() - fullscreenHud.getPaddingLeft() - fullscreenHud.getPaddingRight() - dp(40);
        int needed = fullscreenBottomHud.getPaddingLeft() + fullscreenBottomHud.getPaddingRight();
        for (int i = 0; i < fullscreenBottomHud.getChildCount(); i++) {
            View child = fullscreenBottomHud.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            Button button = (Button) child;
            needed += Math.max(button.getMinimumWidth(), (int) Math.ceil(button.getPaint().measureText(
                button.getText().toString())) + button.getPaddingLeft() + button.getPaddingRight()) + dp(4);
        }
        boolean vertical = needed > available;
        fullscreenBottomHud.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        for (int i = 0; i < fullscreenBottomHud.getChildCount(); i++) {
            View child = fullscreenBottomHud.getChildAt(i);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
            int width = vertical ? -1 : -2;
            if (params.width != width) {
                params.width = width;
                child.setLayoutParams(params);
            }
        }
        boolean stackBrightness = brightnessLabel.getPaint().measureText(brightnessLabel.getText().toString())
            + dp(128) > available - dp(32);
        oledBrightnessRow.setOrientation(stackBrightness ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams seekParams = (LinearLayout.LayoutParams) brightnessSeek.getLayoutParams();
        int seekWidth = stackBrightness ? -1 : 0;
        if (seekParams.width != seekWidth) {
            seekParams.width = seekWidth;
            seekParams.weight = stackBrightness ? 0f : 1f;
            brightnessSeek.setLayoutParams(seekParams);
        }
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        root.post(this::applyResponsiveLayout);
    }

    @android.annotation.SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (fullscreen) exitFullscreen();
        else super.onBackPressed();
    }

    @Override protected void onPause() {
        if (fullscreen) exitFullscreen();
        super.onPause();
    }

    @Override protected void onStop() {
        if (fullscreen) exitFullscreen();
        handler.removeCallbacks(testCountdownTicker);
        handler.removeCallbacks(hideFullscreenHud);
        if (player != null) {
            playerView.setPlayer(null);
            player.removeListener(playbackListener);
            player.release();
            player = null;
            playbackListener = null;
        }
        super.onStop();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private int dp(float value) {
        return (int) (getResources().getDisplayMetrics().density * value + .5f);
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[65536];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }

    private TextView label(String text, int size, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setPadding(0, dp(4), 0, dp(4));
        return v;
    }

    private Button button(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setBackgroundTintList(null);
        b.setTextSize(14);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(false);
        b.setMinHeight(dp(48));
        b.setMinimumWidth(dp(48));
        b.setBackground(ripple(0xff242424, CARD_STROKE, 12));
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(8);
        p.bottomMargin = dp(4);
        b.setLayoutParams(p);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private RippleDrawable ripple(int fill, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), stroke);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(radius));
        return new RippleDrawable(ColorStateList.valueOf(0x224e713c), shape, mask);
    }

    private void primaryStyle(Button button) {
        button.setTextColor(BG);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(ripple(ACCENT, ACCENT, 12));
    }

    private LinearLayout card(int accent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), accent);
        c.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(16);
        params.bottomMargin = dp(8);
        c.setLayoutParams(params);
        return c;
    }

    private LinearLayout cardProperty(String labelText, TextView valueView) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView title = label(labelText, 12, RED);
        row.addView(title);
        row.addView(valueView);
        return row;
    }
}
