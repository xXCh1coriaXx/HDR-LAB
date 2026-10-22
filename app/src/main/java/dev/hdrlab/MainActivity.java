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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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

    private PlayerView playerView;
    private ExoPlayer player;

    private FrameLayout fullscreenHud;
    private LinearLayout fullscreenTopHud;
    private LinearLayout fullscreenBottomHud;
    private TextView hudTitleLabel;
    private TextView hudTipLabel;
    private Button btnVerSdr;
    private Button btnVerHdr;
    private Button btnReiniciar;
    private Button btnSair;

    private boolean fullscreen;
    private String currentMode = "sdr";
    private boolean isAutomatedTest;
    private int testSecondsElapsed;
    private float originalBrightness = -1f;
    private JSONObject deviceSnapshot;

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
        TextView eyebrow = label("DIAGNÓSTICO VISUAL DE ECRÃ", 10, ACCENT);
        eyebrow.setLetterSpacing(.14f);
        headerSection.addView(eyebrow);
        headerSection.addView(label("HDR Lab Light", 32, TEXT));
        headerSection.addView(label("Verifica a calibração de pretos e capacidade HDR do teu ecrã.", 14, MUTED));
        cardsLayout.addView(headerSection);

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
        TextView capTitle = label("Capacidades do Teu Ecrã", 18, TEXT);
        capTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        deviceCapabilitiesCard.addView(capTitle);
        deviceCapabilitiesCard.addView(label("Informações reportadas pelo sistema Android", 12, MUTED));

        capModelLabel = label("A ler dados do sistema…", 14, TEXT);
        capHdrLabel = label("A verificar…", 14, TEXT);
        capGamutLabel = label("A verificar…", 14, TEXT);
        capBrightnessLabel = label("A verificar…", 14, TEXT);

        deviceCapabilitiesCard.addView(cardProperty("Modelo e Versão Android:", capModelLabel));
        deviceCapabilitiesCard.addView(cardProperty("Suporte HDR:", capHdrLabel));
        deviceCapabilitiesCard.addView(cardProperty("Gama de Cores:", capGamutLabel));
        deviceCapabilitiesCard.addView(cardProperty("Brilho de Pico Estimado:", capBrightnessLabel));

        btnDetailedReport = button("Ver Relatório Técnico Detalhado", this::showDetailedReport);
        deviceCapabilitiesCard.addView(btnDetailedReport);
        cardsLayout.addView(deviceCapabilitiesCard);
    }

    private void buildQuickTestCard() {
        quickTestCard = card();
        TextView testTitle = label("Teste Rápido: SDR vs HDR", 18, TEXT);
        testTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        quickTestCard.addView(testTitle);
        quickTestCard.addView(label("Comparação automatizada de 10 segundos em ecrã inteiro", 12, MUTED));

        LinearLayout infoBox = new LinearLayout(this);
        infoBox.setOrientation(LinearLayout.VERTICAL);
        infoBox.setPadding(0, dp(10), 0, dp(8));

        TextView howTitle = label("Como funciona o teste:", 13, ACCENT);
        howTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        infoBox.addView(howTitle);

        TextView howText = label(
            "• 10 segundos em SDR, com transição automática para HDR em ecrã cheio.\n"
            + "• O ecrã é colocado com brilho no máximo (100%) durante o teste.", 13, TEXT);
        infoBox.addView(howText);

        TextView whatTitle = label("O que deves observar no ecrã:", 13, ACCENT);
        whatTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        infoBox.addView(whatTitle);

        TextView whatText = label(
            "• Fundo Preto Puro: Em ecrãs OLED, o fundo é preto absoluto (píxeis desligados). "
            + "Em ecrãs LCD, poderás notar um ligeiro brilho cinzento (backlight glow).\n"
            + "• 6 Degraus Quase-Preto: Observa as 6 barras de cinzento suave (1%, 2%...). "
            + "Devem ser percetíveis sem clarear o fundo preto ao redor.", 13, TEXT);
        infoBox.addView(whatText);

        quickTestCard.addView(infoBox);

        btnStartTest = button("▶ Iniciar Teste SDR vs HDR em Ecrã Cheio", this::startAutomatedTest);
        primaryStyle(btnStartTest);
        quickTestCard.addView(btnStartTest);

        cardsLayout.addView(quickTestCard);
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

        hudTitleLabel = label("MODO SDR (10s)", 15, ACCENT);
        hudTitleLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hudTitleLabel.setGravity(Gravity.CENTER);

        hudTipLabel = label("Observe o fundo preto e a visibilidade das 6 barras de cinzento.", 12, TEXT);
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

        btnVerSdr = hudButton("Ver em SDR", this::switchToSdr);
        btnVerHdr = hudButton("Ver em HDR", () -> switchToHdr(false));
        btnReiniciar = hudButton("⟲ Reiniciar", this::restartTest);
        btnSair = hudButton("✕ Sair", this::exitFullscreen);

        fullscreenBottomHud.addView(btnVerSdr);
        fullscreenBottomHud.addView(btnVerHdr);
        fullscreenBottomHud.addView(btnReiniciar);
        fullscreenBottomHud.addView(btnSair);

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
                hudTitleLabel.setText("MODO SDR (" + countdown + "s)");
                hudTipLabel.setText("Observe o fundo preto e a visibilidade das 6 barras de cinzento.");
            } else {
                hudTitleLabel.setText("MODO SDR ATIVADO");
                hudTipLabel.setText("Modo SDR manual. Observe o fundo preto e as barras.");
            }
        } else {
            hudTitleLabel.setText("MODO HDR (HDR10 / PQ) ATIVADO");
            hudTipLabel.setText("O ecrã ativa o modo HDR. O fundo deve manter-se preto puro.");
        }
        updateHudButtons();
    }

    private void updateHudButtons() {
        boolean isSdr = "sdr".equals(currentMode);
        btnVerSdr.setSelected(isSdr);
        btnVerSdr.setText((isSdr ? "✓ " : "") + "Ver em SDR");
        btnVerSdr.setTextColor(isSdr ? ACCENT : TEXT);
        btnVerSdr.setTypeface(Typeface.DEFAULT, isSdr ? Typeface.BOLD : Typeface.NORMAL);
        btnVerSdr.setBackground(ripple(isSdr ? 0x66252e25 : 0x221b211c, isSdr ? ACCENT : 0x44485546, 14));

        boolean isHdr = "hdr".equals(currentMode);
        btnVerHdr.setSelected(isHdr);
        btnVerHdr.setText((isHdr ? "✓ " : "") + "Ver em HDR");
        btnVerHdr.setTextColor(isHdr ? ACCENT : TEXT);
        btnVerHdr.setTypeface(Typeface.DEFAULT, isHdr ? Typeface.BOLD : Typeface.NORMAL);
        btnVerHdr.setBackground(ripple(isHdr ? 0x66252e25 : 0x221b211c, isHdr ? ACCENT : 0x44485546, 14));
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
                handler.post(() -> Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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
            .setTitle("Relatório Técnico do Dispositivo")
            .setView(scroll)
            .setPositiveButton("Partilhar", (d, w) -> shareReportJson(report))
            .setNegativeButton("Fechar", null)
            .show();
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
            share.setClipData(ClipData.newRawUri("HDR Lab Light report", uri));
            startActivity(Intent.createChooser(share, "Partilhar Relatório HDR Lab Light"));
        } catch (Exception e) {
            Toast.makeText(this, "Falha ao partilhar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        if (fullscreen || cardsLayout == null) return;
        int availableWidth = root.getWidth() - root.getPaddingLeft() - root.getPaddingRight();
        float widthDp = availableWidth / getResources().getDisplayMetrics().density;
        boolean wide = widthDp >= 720;
        if (wide) {
            int contentWidth = Math.min(dp(720), availableWidth - dp(48));
            ScrollView.LayoutParams params = (ScrollView.LayoutParams) cardsLayout.getLayoutParams();
            if (params.width != contentWidth) {
                params.width = contentWidth;
                params.gravity = Gravity.CENTER_HORIZONTAL;
                cardsLayout.setLayoutParams(params);
            }
        } else {
            ScrollView.LayoutParams params = (ScrollView.LayoutParams) cardsLayout.getLayoutParams();
            if (params.width != -1) {
                params.width = -1;
                params.gravity = Gravity.NO_GRAVITY;
                cardsLayout.setLayoutParams(params);
            }
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
