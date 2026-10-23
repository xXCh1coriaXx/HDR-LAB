package dev.hdrlab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.ui.PlayerView;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public final class MainActivity extends Activity {
    private static final int BG = 0xff101411, CARD = 0xff1b211c, TEXT = 0xffedf2e9;
    private static final int MUTED = 0xffa7b2a5, ACCENT = 0xffc8f578, CARD_STROKE = 0xff2c372c;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

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

    private FrameLayout fullscreenHud;
    private LinearLayout fullscreenTopHud;
    private LinearLayout fullscreenBottomHud;
    private TextView hudTitleLabel;
    private TextView hudTipLabel;
    private Button btnWatchSdr;
    private Button btnWatchHdr;
    private Button btnRestart;
    private Button btnExit;

    private boolean fullscreen;
    private String currentMode = "sdr";
    private boolean isAutomatedTest;
    private int testSecondsElapsed;
    private float originalBrightness = -1f;
    private JSONObject deviceSnapshot;
    private Diagnostics.DeviceSummary currentSummary;

    private final Runnable hideFullscreenHud = () -> {
        if (fullscreenHud != null && fullscreen) {
            fullscreenHud.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> { if (fullscreenHud != null) fullscreenHud.setVisibility(View.GONE); }).start();
        }
    };

    private final Runnable testCountdownTicker = new Runnable() {
        @Override public void run() {
            if (!fullscreen || !isAutomatedTest) return;
            testSecondsElapsed++;
            int remaining = 10 - testSecondsElapsed;
            if (remaining > 0) {
                updateHudMode("sdr", remaining);
                handler.postDelayed(this, 1000);
            } else {
                isAutomatedTest = false;
                switchToHdr(true);
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

        buildFullscreenContainer();

        setContentView(root);

        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) root.post(this::applyResponsiveLayout);
        });
        root.post(this::applyResponsiveLayout);
    }

    private void buildDeviceCapabilitiesCard() {
        deviceCapabilitiesCard = card();
        TextView capTitle = label(getString(R.string.card_capabilities_title), 18, TEXT);
        capTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
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
        quickTestCard = card();
        TextView testTitle = label(getString(R.string.card_quick_test_title), 18, TEXT);
        testTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
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

    private void buildFullscreenContainer() {
        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(Color.BLACK);
        fullscreenContainer.setVisibility(View.GONE);

        playerView = (PlayerView) getLayoutInflater().inflate(R.layout.player, fullscreenContainer, false);
        playerView.setKeepContentOnPlayerReset(false);
        playerView.setUseController(false);
        fullscreenContainer.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        buildFullscreenHud();
        fullscreenContainer.setOnClickListener(v -> showFullscreenHud());
        playerView.setOnClickListener(v -> showFullscreenHud());

        root.addView(fullscreenContainer, new FrameLayout.LayoutParams(-1, -1));
    }

    private void buildFullscreenHud() {
        fullscreenHud = new FrameLayout(this);
        fullscreenHud.setVisibility(View.GONE);

        fullscreenTopHud = new LinearLayout(this);
        fullscreenTopHud.setOrientation(LinearLayout.VERTICAL);
        fullscreenTopHud.setGravity(Gravity.CENTER);
        GradientDrawable topPill = new GradientDrawable();
        topPill.setColor(0xdd101411);
        topPill.setCornerRadius(dp(16));
        topPill.setStroke(dp(1), 0x55485546);
        fullscreenTopHud.setBackground(topPill);
        fullscreenTopHud.setPadding(dp(16), dp(10), dp(16), dp(10));

        hudTitleLabel = label(getString(R.string.hud_title_sdr_countdown, 10), 15, ACCENT);
        hudTitleLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hudTitleLabel.setGravity(Gravity.CENTER);

        hudTipLabel = label(getString(R.string.hud_tip_sdr_countdown), 12, TEXT);
        hudTipLabel.setGravity(Gravity.CENTER);

        fullscreenTopHud.addView(hudTitleLabel);
        fullscreenTopHud.addView(hudTipLabel);

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        topParams.topMargin = dp(44);
        topParams.leftMargin = dp(16);
        topParams.rightMargin = dp(16);
        fullscreenHud.addView(fullscreenTopHud, topParams);

        fullscreenBottomHud = new LinearLayout(this);
        fullscreenBottomHud.setOrientation(LinearLayout.HORIZONTAL);
        fullscreenBottomHud.setGravity(Gravity.CENTER);
        GradientDrawable bottomPill = new GradientDrawable();
        bottomPill.setColor(0xdd101411);
        bottomPill.setCornerRadius(dp(20));
        bottomPill.setStroke(dp(1), 0x55485546);
        fullscreenBottomHud.setBackground(bottomPill);
        fullscreenBottomHud.setPadding(dp(6), dp(4), dp(6), dp(4));

        btnWatchSdr = hudButton(getString(R.string.hud_btn_watch_sdr), this::switchToSdr);
        btnWatchHdr = hudButton(getString(R.string.hud_btn_watch_hdr), () -> switchToHdr(false));
        btnRestart = hudButton(getString(R.string.hud_btn_restart), this::restartTest);
        btnExit = hudButton(getString(R.string.hud_btn_exit), this::exitFullscreen);

        fullscreenBottomHud.addView(btnWatchSdr);
        fullscreenBottomHud.addView(btnWatchHdr);
        fullscreenBottomHud.addView(btnRestart);
        fullscreenBottomHud.addView(btnExit);

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bottomParams.bottomMargin = dp(76);
        bottomParams.leftMargin = dp(8);
        bottomParams.rightMargin = dp(8);
        fullscreenHud.addView(fullscreenBottomHud, bottomParams);

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
        b.setMinHeight(dp(36));
        b.setMinimumWidth(dp(44));
        b.setBackground(ripple(0x33252e25, 0x443b4739, 14));
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
        setFullscreen(true);
        isAutomatedTest = true;
        testSecondsElapsed = 0;
        currentMode = "sdr";
        playPattern("sdr_steps", 0);
        updateHudMode("sdr", 10);
        showFullscreenHud();
        handler.removeCallbacks(testCountdownTicker);
        handler.postDelayed(testCountdownTicker, 1000);
    }

    private void switchToHdr(boolean autoTransition) {
        currentMode = "hdr";
        long pos = player != null ? player.getCurrentPosition() : 0;
        playPattern("pq_steps", pos);
        updateHudMode("hdr", 0);
        showFullscreenHud();
    }

    private void switchToSdr() {
        isAutomatedTest = false;
        handler.removeCallbacks(testCountdownTicker);
        currentMode = "sdr";
        long pos = player != null ? player.getCurrentPosition() : 0;
        playPattern("sdr_steps", pos);
        updateHudMode("sdr", -1);
        showFullscreenHud();
    }

    private void restartTest() {
        startAutomatedTest();
    }

    private void setFullscreen(boolean enabled) {
        if (fullscreen == enabled) return;
        if (enabled) {
            fullscreen = true;
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            originalBrightness = lp.screenBrightness;
            lp.screenBrightness = 1.0f;
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
        } else {
            exitFullscreen();
        }
    }

    private void exitFullscreen() {
        if (!fullscreen) return;
        fullscreen = false;
        isAutomatedTest = false;
        handler.removeCallbacks(testCountdownTicker);
        handler.removeCallbacks(hideFullscreenHud);
        if (fullscreenHud != null) fullscreenHud.setVisibility(View.GONE);
        if (player != null) player.pause();

        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = originalBrightness;
        getWindow().setAttributes(lp);

        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) controller.show(WindowInsets.Type.systemBars());
        }

        fullscreenContainer.setVisibility(View.GONE);
        workspaceLayout.setVisibility(View.VISIBLE);
    }

    private void ensurePlayer() {
        if (player != null) return;
        player = new ExoPlayer.Builder(this, new DefaultRenderersFactory(this).setEnableDecoderFallback(true)).build();
        player.setSeekParameters(SeekParameters.EXACT);
        playerView.setPlayer(player);
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
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
        if (!file.exists()) {
            try (InputStream in = getAssets().open("patterns/" + name + ".mp4");
                 OutputStream out = new FileOutputStream(file)) {
                copy(in, out);
            } catch (IOException e) {
                return null;
            }
        }
        return file;
    }

    private void updateHudMode(String mode, int countdown) {
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
    }

    private void updateHudButtons() {
        boolean isSdr = "sdr".equals(currentMode);
        btnWatchSdr.setSelected(isSdr);
        btnWatchSdr.setText((isSdr ? "✓ " : "") + getString(R.string.hud_btn_watch_sdr));
        btnWatchSdr.setTextColor(isSdr ? ACCENT : TEXT);
        btnWatchSdr.setTypeface(Typeface.DEFAULT, isSdr ? Typeface.BOLD : Typeface.NORMAL);
        btnWatchSdr.setBackground(ripple(isSdr ? 0x66252e25 : 0x221b211c, isSdr ? ACCENT : 0x44485546, 14));

        boolean isHdr = "hdr".equals(currentMode);
        btnWatchHdr.setSelected(isHdr);
        btnWatchHdr.setText((isHdr ? "✓ " : "") + getString(R.string.hud_btn_watch_hdr));
        btnWatchHdr.setTextColor(isHdr ? ACCENT : TEXT);
        btnWatchHdr.setTypeface(Typeface.DEFAULT, isHdr ? Typeface.BOLD : Typeface.NORMAL);
        btnWatchHdr.setBackground(ripple(isHdr ? 0x66252e25 : 0x221b211c, isHdr ? ACCENT : 0x44485546, 14));
    }

    private void showFullscreenHud() {
        if (fullscreenHud == null || !fullscreen) return;
        handler.removeCallbacks(hideFullscreenHud);
        fullscreenHud.animate().cancel();
        fullscreenHud.setAlpha(1f);
        fullscreenHud.setVisibility(View.VISIBLE);
        handler.postDelayed(hideFullscreenHud, 3000);
    }

    private void showDetailedReport() {
        worker.execute(() -> {
            try {
                JSONObject report = Diagnostics.device(this);
                String text = report.toString(2);
                handler.post(() -> {
                    if (isDestroyed()) return;
                    showReportDialog(text, report);
                });
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this, getString(R.string.error_dialog, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showReportDialog(String text, JSONObject report) {
        TextView body = label(text, 12, TEXT);
        body.setTextIsSelectable(true);
        body.setTypeface(Typeface.MONOSPACE);
        body.setPadding(dp(20), dp(12), dp(20), dp(20));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_technical_report_title))
            .setView(scroll)
            .setPositiveButton(getString(R.string.dialog_btn_share), (d, w) -> shareReportJson(report))
            .setNegativeButton(getString(R.string.dialog_btn_close), null)
            .show();
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
        boxBg.setColor(0xdd101411);
        boxBg.setCornerRadius(dp(8));
        boxBg.setStroke(dp(1), 0x55485546);
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

        // Question 5: Observations / Notes (optional)
        TextView q5Title = label(getString(R.string.dialog_report_q5_title), 13, ACCENT);
        q5Title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams q5Params = new LinearLayout.LayoutParams(-1, -2);
        q5Params.topMargin = dp(12);
        q5Title.setLayoutParams(q5Params);
        content.addView(q5Title);

        EditText etComments = new EditText(this);
        etComments.setHint(getString(R.string.dialog_report_q5_hint));
        etComments.setHintTextColor(0x88a7b2a5);
        etComments.setTextColor(TEXT);
        etComments.setTextSize(13);
        etComments.setMinLines(2);
        etComments.setMaxLines(4);
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0x33101411);
        etBg.setCornerRadius(dp(8));
        etBg.setStroke(dp(1), 0x55485546);
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
                String nits = (currentSummary != null && currentSummary.peakBrightnessNits > 0)
                    ? String.valueOf(Math.round(currentSummary.peakBrightnessNits)) : "Not reported";

                ReportSubmitter.CommunityReport report = new ReportSubmitter.CommunityReport(
                    model, os, hdr, nits, sdrBlackLevel, sdrSteps, hdrBlackLevel, hdrSteps, comments, System.currentTimeMillis()
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
            File dir = new File(getFilesDir(), "reports");
            dir.mkdirs();
            File file = new File(dir, "hdr-lab-light-report-" + System.currentTimeMillis() + ".json");
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

    @Override protected void onStop() {
        if (fullscreen) exitFullscreen();
        handler.removeCallbacks(testCountdownTicker);
        handler.removeCallbacks(hideFullscreenHud);
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
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
        b.setSingleLine(true);
        b.setMinHeight(dp(48));
        b.setMinimumWidth(dp(48));
        b.setBackground(ripple(0xff252e25, 0xff3b4739, 12));
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

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), CARD_STROKE);
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
        TextView title = label(labelText, 12, ACCENT);
        row.addView(title);
        row.addView(valueView);
        return row;
    }
}
