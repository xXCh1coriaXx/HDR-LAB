package dev.hdrlab;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OledRampTest {
    @Test public void sevenStaticRampsHaveContiguousIntegerBandsAtEveryWidth() {
        OledTestView view = new OledTestView(RuntimeEnvironment.getApplication());
        int[] channels = {7, 1, 2, 4, 6, 5, 3};
        int[] widths = {256, 271, 393, 768, 1081, 3000, 173};

        assertEquals(channels.length, OledTestView.variantCount(OledTestView.MODE_RAMP));
        for (int width : widths) {
            view.layout(0, 0, width, 400);
            Bitmap bitmap = Bitmap.createBitmap(width, 400, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            for (int variant = 0; variant < channels.length; variant++) {
                view.setPattern(OledTestView.MODE_RAMP, variant);
                assertFalse(view.isRunning());
                view.draw(canvas);
                assertEquals(Color.BLACK, bitmap.getPixel(width / 2, 5));
                assertEquals(Color.BLACK, bitmap.getPixel(width / 2, 395));

                int previousCode = -1;
                for (int x = 0; x < width; x++) {
                    int code = width == 1 ? 255
                        : (int) ((x * 255L + (width - 1L) / 2L) / (width - 1L));
                    assertTrue("non-monotonic code at width " + width + ", x " + x,
                        code >= previousCode);
                    int mask = channels[variant];
                    int expected = Color.rgb(
                        (mask & 1) != 0 ? code : 0,
                        (mask & 2) != 0 ? code : 0,
                        (mask & 4) != 0 ? code : 0);
                    assertEquals("variant " + variant + ", width " + width + ", x " + x,
                        expected, bitmap.getPixel(x, 200));
                    previousCode = code;
                }
                assertEquals("black endpoint, variant " + variant + ", width " + width,
                    Color.BLACK, bitmap.getPixel(0, 200));
                int mask = channels[variant];
                int endpoint = Color.rgb(
                    (mask & 1) != 0 ? 255 : 0,
                    (mask & 2) != 0 ? 255 : 0,
                    (mask & 4) != 0 ? 255 : 0);
                assertEquals("white endpoint, variant " + variant + ", width " + width,
                    endpoint, bitmap.getPixel(width - 1, 200));
            }
            bitmap.recycle();
        }
    }

    @Test public void animationStopsDetachedAndRestartsWithFreshFpsWindow() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).create().start().resume().visible().get();
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        OledTestView view = new OledTestView(activity);
        root.addView(view);
        view.setPattern(OledTestView.MODE_SMEAR, 0);
        assertTrue(view.isRunning());

        view.doFrame(1_000_000_000L);
        view.doFrame(1_100_000_000L);
        int dropped = view.getDroppedFrames();
        assertTrue(dropped > 0);
        Field frames = OledTestView.class.getDeclaredField("framesInWindow");
        Field stats = OledTestView.class.getDeclaredField("statsText");
        frames.setAccessible(true);
        stats.setAccessible(true);
        assertTrue(frames.getInt(view) > 0);
        assertFalse(((String) stats.get(view)).isEmpty());

        root.removeView(view);
        assertFalse(view.isRunning());
        view.doFrame(1_200_000_000L);
        view.start();
        assertFalse(view.isRunning());
        assertEquals(dropped, view.getDroppedFrames());

        root.addView(view);
        assertTrue(view.isRunning());
        assertEquals(0, frames.getInt(view));
        assertEquals("", stats.get(view));
        assertEquals(dropped, view.getDroppedFrames());
        view.doFrame(5_000_000_000L);
        assertEquals("First frame after reattach must not count the detached interval", dropped,
            view.getDroppedFrames());

        view.setPattern(OledTestView.MODE_RAMP, 0);
        assertFalse(view.isRunning());
        root.removeView(view);
        root.addView(view);
        assertFalse("Static ramp must remain idle after reattach", view.isRunning());
        view.doFrame(6_000_000_000L);
        assertEquals(0, view.getDroppedFrames());
    }
}
