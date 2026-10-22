package dev.hdrlab;

import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.*;
import java.util.Map;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "sw720dp-w1280dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LayoutTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;

    @Before public void setup() {
        controller = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        activity = controller.get();
    }

    @After public void teardown() { controller.pause().stop().destroy(); }

    @SuppressWarnings("unchecked")
    private <T> T field(String name) throws Exception {
        Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true);
        return (T) f.get(activity);
    }
    private void set(String name, Object value) throws Exception {
        Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(activity, value);
    }
    private void call(String name) throws Exception {
        Method m = MainActivity.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(activity);
    }
    private void viewport(int width, int height) throws Exception {
        LinearLayout root = field("root");
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        call("applyResponsiveLayout");
        root.forceLayout();
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private void capture(String name) throws Exception {
        String destination = System.getProperty("hdrlab.screenshots");
        if (destination == null) return;
        View root = field("root");
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(root.getWidth(), root.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(bitmap));
        File dir = new File(destination); dir.mkdirs();
        try (FileOutputStream output = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
        } finally { bitmap.recycle(); }
    }

    @Test public void tabletUsesQuarterSidebarAndRestoresPhoneLayoutWithoutReplacingVideo() throws Exception {
        View video = field("videoFrame");
        call("ensurePlayer");
        Object playback = field("player");
        viewport(1280, 800);
        LinearLayout workspace = field("workspaceLayout");
        assertEquals(LinearLayout.HORIZONTAL, workspace.getOrientation());
        assertEquals(2, workspace.getChildCount());
        assertEquals(320, workspace.getChildAt(0).getLayoutParams().width);
        assertSame(field("testScroll"), ((View) field("testingSection")).getParent());
        capture("tablet");
        viewport(411, 891);
        assertEquals(LinearLayout.VERTICAL, workspace.getOrientation());
        assertEquals(1, workspace.getChildCount());
        assertSame(field("page"), ((View) field("testingSection")).getParent());
        assertEquals(411, ((View) field("controlScroll")).getWidth());
        assertEquals(371, ((View) field("testingSection")).getWidth());
        assertTrue(((View) field("testingSection")).getTop() >= ((View) field("sourceSection")).getBottom());
        assertSame(video, field("videoFrame"));
        assertSame(playback, field("player"));
        capture("phone");
        viewport(1280, 800);
        assertEquals(2, workspace.getChildCount());
        assertSame(video, field("videoFrame"));
        assertSame(playback, field("player"));
    }

    @Test public void everyComparisonHasAnExplicitAndExclusiveSelectedState() throws Exception {
        set("source", Uri.parse("file:///test.mp4"));
        Class<?> variantClass = Class.forName("dev.hdrlab.MainActivity$Variant");
        Constructor<?> constructor = variantClass.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        Map<String, Object> variants = field("variants");
        for (String key : new String[] {"codec", "gpu"})
            variants.put(key, constructor.newInstance(Uri.parse("file:///" + key + ".mp4"), new File(key), 0L, new JSONObject()));
        String[] keys = {"original", "codec", "gpu"};
        String[] fields = {"originalButton", "codecButton", "gpuButton"};
        for (int i = 0; i < keys.length; i++) {
            set("mode", keys[i]); call("refreshButtons");
            for (int j = 0; j < fields.length; j++) {
                Button button = field(fields[j]);
                assertEquals(i == j, button.isSelected());
                assertEquals(i == j, button.getText().toString().contains("Selected"));
                if (i == j) {
                    assertTrue(button.getContentDescription().toString().contains("selected"));
                    assertEquals("Selected", button.getStateDescription());
                }
            }
        }
        assertTrue(((TextView) field("modeLabel")).getText().toString().contains("GPU"));
        viewport(1280, 800); capture("tablet-selection");
    }

    @Test public void uncreatedCopiesAreDisabledAndBusyStatePreservesSelection() throws Exception {
        set("source", Uri.parse("file:///test.mp4")); call("refreshButtons");
        assertFalse(((Button) field("codecButton")).isEnabled());
        assertTrue(((Button) field("codecButton")).getText().toString().contains("Create first"));
        set("inspectionPending", true); call("refreshButtons");
        Button original = field("originalButton");
        assertFalse(original.isEnabled()); assertTrue(original.isSelected());
        assertEquals(1f, original.getAlpha(), 0);
    }

    @Test public void tappingSelectedModeDoesNotStartANewPlaybackPipeline() throws Exception {
        set("source", Uri.parse("file:///test.mp4")); call("refreshButtons");
        ((Button) field("originalButton")).performClick();
        assertNull(field("player"));
    }

    @Test public void fullscreenReturnsVideoToCorrectParentAcrossBreakpoints() throws Exception {
        viewport(1280, 800);
        Method fullscreen = MainActivity.class.getDeclaredMethod("setFullscreen", boolean.class);
        fullscreen.setAccessible(true);
        View video = field("videoFrame");
        fullscreen.invoke(activity, true);
        assertSame(field("root"), video.getParent());
        assertEquals(View.GONE, ((View) field("workspaceLayout")).getVisibility());
        fullscreen.invoke(activity, false);
        assertSame(field("testingSection"), video.getParent());
        assertEquals(View.VISIBLE, ((View) field("workspaceLayout")).getVisibility());
        viewport(411, 891);
        assertSame(field("page"), ((View) field("testingSection")).getParent());
    }

    @Test public void fullscreenHudSyncsWithModeSelectionAndTogglesVariants() throws Exception {
        set("source", Uri.parse("file:///test.mp4"));
        Class<?> variantClass = Class.forName("dev.hdrlab.MainActivity$Variant");
        Constructor<?> constructor = variantClass.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        Map<String, Object> variants = field("variants");
        for (String key : new String[] {"codec", "gpu"})
            variants.put(key, constructor.newInstance(Uri.parse("file:///" + key + ".mp4"), new File(key), 0L, new JSONObject()));
        call("refreshButtons");

        Method fullscreenMethod = MainActivity.class.getDeclaredMethod("setFullscreen", boolean.class);
        fullscreenMethod.setAccessible(true);
        fullscreenMethod.invoke(activity, true);

        View hud = field("fullscreenHud");
        assertNotNull(hud);
        assertEquals(View.VISIBLE, hud.getVisibility());

        Button fsOriginal = field("fsOriginalButton");
        Button fsCodec = field("fsCodecButton");
        Button fsGpu = field("fsGpuButton");
        assertTrue(fsOriginal.isSelected());
        assertEquals("✓ Original", fsOriginal.getText().toString());
        assertFalse(fsCodec.isSelected());
        assertEquals("Codec", fsCodec.getText().toString());
        assertFalse(fsGpu.isSelected());
        assertEquals("GPU", fsGpu.getText().toString());

        fsCodec.performClick();
        assertEquals("codec", field("mode"));
        assertTrue(fsCodec.isSelected());
        assertEquals("✓ Codec", fsCodec.getText().toString());
        assertFalse(fsOriginal.isSelected());
        assertEquals("Original", fsOriginal.getText().toString());

        fullscreenMethod.invoke(activity, false);
        assertEquals(View.GONE, hud.getVisibility());
    }

    @Test public void fullscreenHudLayoutMarginsAndContentDescriptions() throws Exception {
        View hud = field("fullscreenHud");
        assertNotNull(hud);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) hud.getLayoutParams();
        assertNotNull(params);
        Method dp = MainActivity.class.getDeclaredMethod("dp", float.class);
        dp.setAccessible(true);
        int expectedBottom = (int) dp.invoke(activity, 76f);
        int expectedHorizontal = (int) dp.invoke(activity, 8f);
        assertEquals(expectedBottom, params.bottomMargin);
        assertEquals(expectedHorizontal, params.leftMargin);
        assertEquals(expectedHorizontal, params.rightMargin);

        int expectedPadH = (int) dp.invoke(activity, 6f);
        int expectedPadV = (int) dp.invoke(activity, 3f);
        assertEquals(expectedPadH, hud.getPaddingLeft());
        assertEquals(expectedPadV, hud.getPaddingTop());
        assertEquals(expectedPadH, hud.getPaddingRight());
        assertEquals(expectedPadV, hud.getPaddingBottom());

        Button fsOriginal = field("fsOriginalButton");
        Button fsCodec = field("fsCodecButton");
        Button fsGpu = field("fsGpuButton");
        Button fsPrev = field("fsPrevFrameButton");
        Button fsNext = field("fsNextFrameButton");

        assertEquals("Original HDR view", fsOriginal.getContentDescription().toString());
        assertEquals("SDR MediaCodec view", fsCodec.getContentDescription().toString());
        assertEquals("SDR OpenGL view", fsGpu.getContentDescription().toString());
        assertEquals("Step backward one frame", fsPrev.getContentDescription().toString());
        assertEquals("Step forward one frame", fsNext.getContentDescription().toString());

        int expectedBtnMinH = (int) dp.invoke(activity, 34f);
        int expectedBtnMinW = (int) dp.invoke(activity, 32f);
        int expectedBtnPadH = (int) dp.invoke(activity, 6f);
        int expectedBtnPadV = (int) dp.invoke(activity, 2f);

        for (Button b : new Button[] {fsPrev, fsOriginal, fsCodec, fsGpu, fsNext}) {
            assertEquals(expectedBtnMinH, b.getMinHeight());
            assertEquals(expectedBtnMinW, b.getMinimumWidth());
            assertTrue(b.isSingleLine());
            assertEquals(expectedBtnPadH, b.getPaddingLeft());
            assertEquals(expectedBtnPadV, b.getPaddingTop());
            assertEquals(expectedBtnPadH, b.getPaddingRight());
            assertEquals(expectedBtnPadV, b.getPaddingBottom());
        }
    }

    @Test public void modeButtonsInHudDisplayOriginalCodecGpuWithCheckmarkWhenSelected() throws Exception {
        set("source", Uri.parse("file:///test.mp4"));
        Class<?> variantClass = Class.forName("dev.hdrlab.MainActivity$Variant");
        Constructor<?> constructor = variantClass.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        Map<String, Object> variants = field("variants");
        for (String key : new String[] {"codec", "gpu"})
            variants.put(key, constructor.newInstance(Uri.parse("file:///" + key + ".mp4"), new File(key), 0L, new JSONObject()));

        Button fsOriginal = field("fsOriginalButton");
        Button fsCodec = field("fsCodecButton");
        Button fsGpu = field("fsGpuButton");

        set("mode", "original");
        call("refreshButtons");
        assertEquals("✓ Original", fsOriginal.getText().toString());
        assertEquals("Codec", fsCodec.getText().toString());
        assertEquals("GPU", fsGpu.getText().toString());

        set("mode", "codec");
        call("refreshButtons");
        assertEquals("Original", fsOriginal.getText().toString());
        assertEquals("✓ Codec", fsCodec.getText().toString());
        assertEquals("GPU", fsGpu.getText().toString());

        set("mode", "gpu");
        call("refreshButtons");
        assertEquals("Original", fsOriginal.getText().toString());
        assertEquals("Codec", fsCodec.getText().toString());
        assertEquals("✓ GPU", fsGpu.getText().toString());
    }

    @Test public void testBenchFullscreenButtonTextAndSingleLineControls() throws Exception {
        LinearLayout testingSection = field("testingSection");
        assertNotNull(testingSection);
        Button fsBtn = null;
        Button prevFrame = null;
        Button nextFrame = null;
        for (int i = 0; i < testingSection.getChildCount(); i++) {
            View child = testingSection.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) child;
                for (int j = 0; j < layout.getChildCount(); j++) {
                    View inner = layout.getChildAt(j);
                    if (inner instanceof Button) {
                        Button b = (Button) inner;
                        String text = b.getText().toString();
                        if (text.contains("Fullscreen")) fsBtn = b;
                        else if (text.contains("◀  Step")) prevFrame = b;
                        else if (text.contains("Step  ▶")) nextFrame = b;
                    }
                }
            }
        }
        assertNotNull("Fullscreen button not found", fsBtn);
        assertNotNull("Prev frame button not found", prevFrame);
        assertNotNull("Next frame button not found", nextFrame);

        assertEquals("↗  Fullscreen", fsBtn.getText().toString());
        assertTrue(fsBtn.isSingleLine());
        assertEquals(android.text.TextUtils.TruncateAt.END, fsBtn.getEllipsize());
        assertTrue(prevFrame.isSingleLine());
        assertTrue(nextFrame.isSingleLine());
    }

    @Test public void formatColorInfoFormatsAllRequiredColorMetadata() {
        assertEquals("Color metadata unspecified", MainActivity.formatColorInfo(null));
        assertEquals("Color metadata unspecified", MainActivity.formatColorInfo(new ColorInfo.Builder().build()));

        // ST2084 (PQ / HDR10)
        ColorInfo st2084 = new ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_ST2084).build();
        assertEquals("PQ / HDR10", MainActivity.formatColorInfo(st2084));

        // HLG
        ColorInfo hlg = new ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_HLG).build();
        assertEquals("HLG", MainActivity.formatColorInfo(hlg));

        // SDR
        ColorInfo sdr = new ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_SDR).build();
        assertEquals("SDR", MainActivity.formatColorInfo(sdr));

        // sRGB
        ColorInfo srgb = new ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_SRGB).build();
        assertEquals("sRGB", MainActivity.formatColorInfo(srgb));

        // BT2020
        ColorInfo bt2020 = new ColorInfo.Builder().setColorSpace(C.COLOR_SPACE_BT2020).build();
        assertEquals("BT.2020", MainActivity.formatColorInfo(bt2020));

        // BT709
        ColorInfo bt709 = new ColorInfo.Builder().setColorSpace(C.COLOR_SPACE_BT709).build();
        assertEquals("BT.709", MainActivity.formatColorInfo(bt709));

        // BT601
        ColorInfo bt601 = new ColorInfo.Builder().setColorSpace(C.COLOR_SPACE_BT601).build();
        assertEquals("BT.601", MainActivity.formatColorInfo(bt601));

        // Limited
        ColorInfo limited = new ColorInfo.Builder().setColorRange(C.COLOR_RANGE_LIMITED).build();
        assertEquals("Limited", MainActivity.formatColorInfo(limited));

        // Full
        ColorInfo full = new ColorInfo.Builder().setColorRange(C.COLOR_RANGE_FULL).build();
        assertEquals("Full", MainActivity.formatColorInfo(full));

        // Combined: BT.2020 · Limited · PQ / HDR10
        ColorInfo hdr10 = new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_ST2084)
            .build();
        assertEquals("BT.2020 · Limited · PQ / HDR10", MainActivity.formatColorInfo(hdr10));

        // Combined: BT.709 · Full · SDR
        ColorInfo bt709Sdr = new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT709)
            .setColorRange(C.COLOR_RANGE_FULL)
            .setColorTransfer(C.COLOR_TRANSFER_SDR)
            .build();
        assertEquals("BT.709 · Full · SDR", MainActivity.formatColorInfo(bt709Sdr));
    }

    @Test public void updateFormatLabelDisplaysHumanizedColorInfoAndDroppedFrames() throws Exception {
        call("updateFormatLabel");
        TextView formatLabel = field("formatLabel");
        String text = formatLabel.getText().toString();
        assertTrue(text.contains("Reading decoder input…"));
        assertTrue(text.contains("Decoder: "));
        assertTrue(text.contains("Dropped frames: "));
        assertFalse(text.contains("Dropped frames (session): "));
        assertTrue(text.contains("Source/decoder metadata · panel HDR state is not verified"));
    }

    @Test public void hdrStaticInfoParsesCta861DescriptorCorrectly() throws Exception {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(25).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0);
        buf.putShort((short) 8500); buf.putShort((short) 39850);
        buf.putShort((short) 6550); buf.putShort((short) 2300);
        buf.putShort((short) 35400); buf.putShort((short) 14600);
        buf.putShort((short) 15635); buf.putShort((short) 16450);
        buf.putShort((short) 1000); buf.putShort((short) 1);
        buf.putShort((short) 1000); buf.putShort((short) 400);
        buf.flip();

        JSONObject parsed = Diagnostics.parseHdrStaticInfo(buf);
        assertNotNull(parsed);
        assertEquals(0, parsed.getInt("descriptorType"));
        assertEquals(1000, parsed.getInt("maxMasteringLuminanceNits"));
        assertEquals(0.0001, parsed.getDouble("minMasteringLuminanceNits"), 0.00001);
        assertEquals(1000, parsed.getInt("maxContentLightLevelNits"));
        assertEquals(400, parsed.getInt("maxFrameAverageLightLevelNits"));

        JSONObject red = parsed.getJSONObject("displayPrimaries").getJSONObject("red");
        assertEquals(0.708, red.getDouble("x"), 0.001);
        assertEquals(0.292, red.getDouble("y"), 0.001);

        JSONObject wp = parsed.getJSONObject("whitePoint");
        assertEquals(0.3127, wp.getDouble("x"), 0.001);
        assertEquals(0.329, wp.getDouble("y"), 0.001);
    }
}
