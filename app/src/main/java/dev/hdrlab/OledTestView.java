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
    static final int MODE_SMEAR = 0, MODE_LADDER = 1, MODE_DSE = 2, MODE_APL = 3;

    static final int[] SMEAR_CODES = {0x05, 0x08, 0x0F, 0x18, 0x20};
    static final int[] SMEAR_SPEEDS = {100, 250, 500, 1000, 2000};
    static final float[] LADDER_PERCENT = {0.5f, 1f, 2f, 3f, 4f, 5f, 7.5f, 10f};
    static final float[] DSE_PERCENT = {1f, 2f, 3f, 5f, 10f, 15f};
    static final float[] APL_PERCENT = {1f, 2f, 5f, 10f, 25f, 50f, 100f};
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

    void setPattern(int mode, int variant) {
        stop();
        this.mode = mode;
        this.variant = Math.floorMod(variant, variantCount(mode));
        offset = 0f;
        fps = 0f;
        droppedFrames = 0;
        statsText = "";
        invalidate();
        start();
    }

    private boolean isAnimated() {
        return mode == MODE_SMEAR || (mode == MODE_DSE && dsePan());
    }

    void start() {
        if (running || !isAnimated() || getVisibility() != VISIBLE) return;
        running = true;
        lastFrameNanos = 0;
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

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if (lastFrameNanos == 0) {
            windowStartNanos = frameTimeNanos;
        } else {
            long delta = frameTimeNanos - lastFrameNanos;
            float speed = mode == MODE_SMEAR ? smearSpeed() : DSE_PAN_SPEED;
            float period = period();
            offset += speed * delta / 1e9f;
            if (period > 0) offset %= period;
            float interval = 1e9f / refreshRate();
            if (delta > interval * 1.5f) droppedFrames += Math.round(delta / interval) - 1;
            framesInWindow++;
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

    private static int gray(int c) {
        return Color.rgb(c, c, c);
    }
}
