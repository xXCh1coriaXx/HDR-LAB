package dev.hdrlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Choreographer;
import android.view.Display;
import android.view.View;

/**
 * Pipeline A: OLED patterns rendered through View/Canvas and the OS compositor.
 * Levels are full-range 8-bit signal percentages, not measured luminance; the
 * OS colour pipeline, panel gamma and vendor processing still apply.
 */
final class OledTestView extends View implements Choreographer.FrameCallback {
    static final int MODE_SMEAR = 0, MODE_LADDER = 1, MODE_DSE = 2, MODE_APL = 3, MODE_BURNIN = 4,
        MODE_RAMP = 5;

    static final int[] SMEAR_CODES = {0x05, 0x08, 0x0F, 0x18, 0x20};
    static final int[] SMEAR_SPEEDS = {100, 250, 500, 1000, 2000};
    static final float[] LADDER_PERCENT = {0.5f, 1f, 2f, 3f, 4f, 5f, 7.5f, 10f};
    static final float[] DSE_PERCENT = {1f, 2f, 3f, 5f, 10f, 15f};
    static final float[] APL_PERCENT = {1f, 2f, 5f, 10f, 25f, 50f, 100f};
    static final int[] BURNIN_COLORS = {
        0xFFFF0000, 0xFF00FF00, 0xFF0000FF,
        0xFFFFFFFF, 0xFF808080, 0xFF333333,
        0xFF00FFFF, 0xFFFF00FF, 0xFFFFFF00
    };
    // Grayscale, red, green, blue, cyan, magenta, yellow.
    private static final int[] RAMP_CHANNELS = {7, 1, 2, 4, 6, 5, 3};
    private static final float DSE_PAN_SPEED = 60f;
    private static final int DSE_STRIPE_PERIOD = 128, DSE_STRIPE_WIDTH = 32;

    private final Paint fill = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mode = MODE_SMEAR, variant;
    private float offset, fps;
    private long lastFrameNanos, windowStartNanos;
    private int framesInWindow, droppedFrames;
    private boolean running;
    private String statsText = "";

    OledTestView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        text.setColor(gray(0x50));
        text.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
    }

    /** Full-range 8-bit code value for a signal percentage. */
    static int code(float percent) {
        return Math.round(percent * 2.55f);
    }

    static int variantCount(int mode) {
        switch (mode) {
            case MODE_SMEAR: return SMEAR_SPEEDS.length * 2;
            case MODE_DSE: return DSE_PERCENT.length * 2;
            case MODE_APL: return APL_PERCENT.length;
            case MODE_BURNIN: return BURNIN_COLORS.length;
            case MODE_RAMP: return RAMP_CHANNELS.length;
            default: return 1;
        }
    }

    int getMode() { return mode; }
    int getVariant() { return variant; }
    int getDroppedFrames() { return droppedFrames; }
    boolean isRunning() { return running; }

    int smearSpeed() { return SMEAR_SPEEDS[variant % SMEAR_SPEEDS.length]; }
    boolean smearVertical() { return variant >= SMEAR_SPEEDS.length; }
    float dsePercent() { return DSE_PERCENT[variant % DSE_PERCENT.length]; }
    boolean dsePan() { return variant >= DSE_PERCENT.length; }
    float aplPercent() { return APL_PERCENT[variant % APL_PERCENT.length]; }
    int burnInColor() { return BURNIN_COLORS[variant % BURNIN_COLORS.length]; }

    void setPattern(int mode, int variant) {
        stop();
        this.mode = mode;
        this.variant = Math.floorMod(variant, variantCount(mode));
        offset = 0f;
        fps = 0f;
        droppedFrames = 0;
        framesInWindow = 0;
        statsText = "";
        invalidate();
        start();
    }

    private boolean isAnimated() {
        return mode == MODE_SMEAR || (mode == MODE_DSE && dsePan());
    }

    void start() {
        if (running || !isAnimated() || getVisibility() != VISIBLE || !isAttachedToWindow()) return;
        running = true;
        lastFrameNanos = 0;
        framesInWindow = 0;
        statsText = "";
        Choreographer.getInstance().postFrameCallback(this);
    }

    void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if (lastFrameNanos == 0) {
            windowStartNanos = frameTimeNanos;
        } else {
            long delta = frameTimeNanos - lastFrameNanos;
            if (delta > 200_000_000L) {
                // If paused or long stall (e.g. notification shade or app backgrounding),
                // resync timing baseline to avoid spurious frame drops or jumping offset.
                windowStartNanos = frameTimeNanos;
                framesInWindow = 0;
            } else {
                float speed = mode == MODE_SMEAR ? smearSpeed() : DSE_PAN_SPEED;
                float period = period();
                offset += speed * delta / 1e9f;
                if (period > 0) offset %= period;
                float interval = 1e9f / refreshRate();
                if (delta > interval * 1.5f) droppedFrames += Math.round(delta / interval) - 1;
                framesInWindow++;
            }
            long window = frameTimeNanos - windowStartNanos;
            if (window >= 500_000_000L || (statsText.isEmpty() && delta > 0)) {
                fps = framesInWindow * 1e9f / Math.max(1L, window);
                statsText = getContext().getString(R.string.oled_stats_format, fps, droppedFrames, refreshRate());
                if (window >= 500_000_000L) {
                    framesInWindow = 0;
                    windowStartNanos = frameTimeNanos;
                }
            }
        }
        lastFrameNanos = frameTimeNanos;
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private float refreshRate() {
        Display display = getDisplay();
        if (display == null) display = Diagnostics.defaultDisplay(getContext());
        float rate = display != null ? display.getRefreshRate() : 60f;
        return rate > 1f ? rate : 60f;
    }

    private float blockSize() {
        return Math.max(1, Math.min(getWidth(), getHeight()) / 5f);
    }

    private float period() {
        if (mode == MODE_DSE) return DSE_STRIPE_PERIOD;
        return Math.max(blockSize() * 3f, (smearVertical() ? getHeight() : getWidth()) / 2f);
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.drawColor(Color.BLACK);
        if (w <= 0 || h <= 0) return;
        switch (mode) {
            case MODE_SMEAR: drawSmear(canvas, w, h); break;
            case MODE_LADDER: drawLadder(canvas, w, h); break;
            case MODE_DSE: drawDse(canvas, w, h); break;
            case MODE_APL: drawApl(canvas, w, h); break;
            case MODE_BURNIN: canvas.drawColor(burnInColor()); break;
            case MODE_RAMP: drawRamp(canvas, w, h); break;
        }
        if (running && !statsText.isEmpty()) {
            canvas.drawText(statsText, text.getTextSize(), h - text.getTextSize() * 2, text);
        }
    }

    private void drawSmear(Canvas canvas, int w, int h) {
        int n = SMEAR_CODES.length;
        float block = blockSize();
        boolean vertical = smearVertical();
        float lane = (vertical ? w : h) / (float) n;
        float thickness = Math.min(lane * 0.6f, block);
        float pPeriod = period();
        float span = vertical ? h : w;
        for (int i = 0; i < n; i++) {
            fill.setColor(gray(SMEAR_CODES[i]));
            float start = lane * i + (lane - thickness) / 2f;
            for (float pos = (offset % pPeriod) - pPeriod; pos < span; pos += pPeriod) {
                if (vertical) canvas.drawRect(start, pos, start + thickness, pos + block, fill);
                else canvas.drawRect(pos, start, pos + block, start + thickness, fill);
            }
        }
    }

    private void drawLadder(Canvas canvas, int w, int h) {
        int n = LADDER_PERCENT.length;
        int cols = w >= h ? n : n / 2;
        int rows = (n + cols - 1) / cols;
        float cellW = w / (float) cols, cellH = h / (float) rows;
        float patch = Math.min(cellW, cellH) * 0.55f;
        for (int i = 0; i < n; i++) {
            float cx = cellW * (i % cols + 0.5f), cy = cellH * (i / cols + 0.5f);
            fill.setColor(gray(code(LADDER_PERCENT[i])));
            canvas.drawRect(cx - patch / 2, cy - patch / 2, cx + patch / 2, cy + patch / 2, fill);
            String label = getContext().getString(R.string.oled_ladder_label, i + 1, LADDER_PERCENT[i]);
            canvas.drawText(label, cx - text.measureText(label) / 2, cy + patch / 2 + text.getTextSize() * 1.6f, text);
        }
    }

    private void drawDse(Canvas canvas, int w, int h) {
        int base = code(dsePercent());
        canvas.drawColor(gray(base));
        if (!dsePan()) return;
        fill.setColor(gray(base + 1));
        for (float x = offset - DSE_STRIPE_PERIOD; x < w; x += DSE_STRIPE_PERIOD) {
            canvas.drawRect(x, 0, x + DSE_STRIPE_WIDTH, h, fill);
        }
    }

    private void drawApl(Canvas canvas, int w, int h) {
        float scale = (float) Math.sqrt(aplPercent() / 100f);
        float bw = w * scale, bh = h * scale;
        fill.setColor(Color.WHITE);
        canvas.drawRect((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f, fill);
    }

    private void drawRamp(Canvas canvas, int w, int h) {
        float padding = 8f * getResources().getDisplayMetrics().density;
        float labelHeight = text.getTextSize();
        float top = Math.max(h * 0.2f, labelHeight + padding * 2f);
        float bottom = Math.min(h * 0.8f, h - labelHeight * 2f - padding * 2f);
        if (bottom <= top) {
            top = h * 0.35f;
            bottom = h * 0.65f;
        }

        int channels = RAMP_CHANNELS[variant];
        int runStart = 0;
        int runCode = rampCodeAt(0, w);
        for (int x = 1; x <= w; x++) {
            int code = x < w ? rampCodeAt(x, w) : -1;
            if (code == runCode) continue;
            fill.setColor(Color.rgb(
                (channels & 1) != 0 ? runCode : 0,
                (channels & 2) != 0 ? runCode : 0,
                (channels & 4) != 0 ? runCode : 0));
            canvas.drawRect(runStart, top, x, bottom, fill);
            runStart = x;
            runCode = code;
        }

        int originalColor = text.getColor();
        float originalSize = text.getTextSize();
        text.setColor(gray(0xB0));
        String scale = getContext().getString(R.string.oled_ramp_scale);
        float scaleWidth = text.measureText(scale);
        if (scaleWidth > w - padding * 2f && w > padding * 2f) {
            text.setTextSize(originalSize * (w - padding * 2f) / scaleWidth);
        }
        canvas.drawText(scale, (w - text.measureText(scale)) / 2f, top - padding, text);
        text.setTextSize(originalSize);
        float baseline = bottom + padding + labelHeight;
        canvas.drawText("0", padding, baseline, text);
        canvas.drawText("128", (w - text.measureText("128")) / 2f, baseline, text);
        canvas.drawText("255", w - padding - text.measureText("255"), baseline, text);
        text.setColor(originalColor);
    }

    /** Maps each physical view column to an exact full-range signal code. */
    private static int rampCodeAt(int x, int width) {
        if (width <= 1) return 255;
        return (int) ((x * 255L + (width - 1L) / 2L) / (width - 1L));
    }

    private static int gray(int c) {
        return Color.rgb(c, c, c);
    }
}
