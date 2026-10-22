package dev.hdrlab;

import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
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
        assertFalse(fsCodec.isSelected());
        assertFalse(fsGpu.isSelected());

        fsCodec.performClick();
        assertEquals("codec", field("mode"));
        assertTrue(fsCodec.isSelected());
        assertFalse(fsOriginal.isSelected());

        fullscreenMethod.invoke(activity, false);
        assertEquals(View.GONE, hud.getVisibility());
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
