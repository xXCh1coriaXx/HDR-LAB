package dev.hdrlab;

import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.ui.PlayerView;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
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
        Field f = MainActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(activity);
    }

    private void call(String name) throws Exception {
        Method m = MainActivity.class.getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(activity);
    }

    @Test public void deviceCapabilitiesCardDisplaysModelHdrGamutBrightness() throws Exception {
        LinearLayout capCard = field("deviceCapabilitiesCard");
        assertNotNull("Capacidades do Teu Ecrã card must exist", capCard);

        TextView capModelLabel = field("capModelLabel");
        TextView capHdrLabel = field("capHdrLabel");
        TextView capGamutLabel = field("capGamutLabel");
        TextView capBrightnessLabel = field("capBrightnessLabel");
        Button btnDetailedReport = field("btnDetailedReport");

        assertNotNull(capModelLabel);
        assertNotNull(capHdrLabel);
        assertNotNull(capGamutLabel);
        assertNotNull(capBrightnessLabel);
        assertNotNull(btnDetailedReport);

        assertTrue("Model label should contain Android version or model",
            capModelLabel.getText().toString().contains(android.os.Build.MODEL)
                || capModelLabel.getText().toString().contains("Android"));
        assertTrue("HDR label should be non-empty", capHdrLabel.getText().length() > 0);
        assertTrue("Gamut label should be non-empty", capGamutLabel.getText().length() > 0);
        assertTrue("Brightness label should be non-empty", capBrightnessLabel.getText().length() > 0);
        assertEquals("Ver Relatório Técnico Detalhado", btnDetailedReport.getText().toString());
    }

    @Test public void quickTestCardContainsInstructionsAndStartButton() throws Exception {
        LinearLayout testCard = field("quickTestCard");
        assertNotNull("Teste Rápido card must exist", testCard);

        Button btnStartTest = field("btnStartTest");
        assertNotNull(btnStartTest);
        assertEquals("▶ Iniciar Teste SDR vs HDR em Ecrã Cheio", btnStartTest.getText().toString());

        // Verify instructions text inside card contains key explanation items
        boolean foundSdrExplanation = false;
        boolean foundBlackExplanation = false;
        boolean foundStepsExplanation = false;

        for (int i = 0; i < testCard.getChildCount(); i++) {
            View child = testCard.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout box = (LinearLayout) child;
                for (int j = 0; j < box.getChildCount(); j++) {
                    View inner = box.getChildAt(j);
                    if (inner instanceof TextView) {
                        String text = ((TextView) inner).getText().toString();
                        if (text.contains("10 segundos em SDR") || text.contains("transição automática para HDR")) {
                            foundSdrExplanation = true;
                        }
                        if (text.contains("Fundo Preto Puro") || text.contains("OLED") || text.contains("LCD")) {
                            foundBlackExplanation = true;
                        }
                        if (text.contains("Degraus Quase-Preto") || text.contains("1%, 2%")) {
                            foundStepsExplanation = true;
                        }
                    }
                }
            }
        }

        assertTrue("Should contain 10-second SDR to HDR transition instruction", foundSdrExplanation);
        assertTrue("Should contain OLED black surround vs LCD glow instruction", foundBlackExplanation);
        assertTrue("Should contain 6 near-black grey steps instruction", foundStepsExplanation);
    }

    @Test public void startTestEntersFullscreenSetsMaxBrightnessAndPlaysSdr() throws Exception {
        Button btnStartTest = field("btnStartTest");
        btnStartTest.performClick();

        boolean fullscreen = field("fullscreen");
        assertTrue("Fullscreen mode must be active after clicking start test", fullscreen);

        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        assertEquals(1.0f, lp.screenBrightness, 0.001f);

        String currentMode = field("currentMode");
        assertEquals("sdr", currentMode);

        boolean isAutomated = field("isAutomatedTest");
        assertTrue("Automated test should be in progress", isAutomated);

        PlayerView playerView = field("playerView");
        assertNotNull(playerView);
        FrameLayout fullscreenContainer = field("fullscreenContainer");
        assertEquals(View.VISIBLE, fullscreenContainer.getVisibility());
    }

    @Test public void countdownTransitionsFromSdrToHdrAtTenSeconds() throws Exception {
        call("startAutomatedTest");

        TextView hudTitleLabel = field("hudTitleLabel");
        assertTrue(hudTitleLabel.getText().toString().contains("MODO SDR"));

        Runnable ticker = field("testCountdownTicker");
        assertNotNull(ticker);

        // Advance 9 ticks
        for (int i = 0; i < 9; i++) {
            ticker.run();
        }
        String modeAfter9 = field("currentMode");
        assertEquals("sdr", modeAfter9);

        // Advance 10th tick (triggers transition to HDR)
        ticker.run();
        String modeAfter10 = field("currentMode");
        assertEquals("hdr", modeAfter10);

        boolean isAutomated = field("isAutomatedTest");
        assertFalse("Automated phase ends after 10s transition", isAutomated);
        assertTrue(hudTitleLabel.getText().toString().contains("MODO HDR (HDR10 / PQ) ATIVADO"));
    }

    @Test public void fullscreenHudHasRequiredButtonsWithProperClearanceAndConstraints() throws Exception {
        call("startAutomatedTest");

        LinearLayout bottomHud = field("fullscreenBottomHud");
        assertNotNull(bottomHud);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) bottomHud.getLayoutParams();
        assertNotNull(params);

        Method dpMethod = MainActivity.class.getDeclaredMethod("dp", float.class);
        dpMethod.setAccessible(true);
        int expectedClearance = (int) dpMethod.invoke(activity, 76f);
        assertTrue("HUD bottom margin must be >= 76dp for PlayerView clearance",
            params.bottomMargin >= expectedClearance);

        Button btnVerSdr = field("btnVerSdr");
        Button btnVerHdr = field("btnVerHdr");
        Button btnReiniciar = field("btnReiniciar");
        Button btnSair = field("btnSair");

        assertNotNull(btnVerSdr);
        assertNotNull(btnVerHdr);
        assertNotNull(btnReiniciar);
        assertNotNull(btnSair);

        int minH = (int) dpMethod.invoke(activity, 36f);
        int minW = (int) dpMethod.invoke(activity, 44f);

        for (Button b : new Button[] {btnVerSdr, btnVerHdr, btnReiniciar, btnSair}) {
            assertTrue("Button must enforce singleLine", b.isSingleLine());
            assertTrue("Button minHeight must be >= 36dp", b.getMinHeight() >= minH);
            assertTrue("Button minimumWidth must be >= 44dp", b.getMinimumWidth() >= minW);
        }
    }

    @Test public void switchingBetweenSdrAndHdrInHud() throws Exception {
        call("startAutomatedTest");

        Button btnVerSdr = field("btnVerSdr");
        Button btnVerHdr = field("btnVerHdr");
        Button btnReiniciar = field("btnReiniciar");

        // Initially SDR
        assertEquals("sdr", field("currentMode"));
        assertTrue(btnVerSdr.isSelected());
        assertFalse(btnVerHdr.isSelected());

        // Switch to HDR
        btnVerHdr.performClick();
        assertEquals("hdr", field("currentMode"));
        assertTrue(btnVerHdr.isSelected());
        assertFalse(btnVerSdr.isSelected());

        // Switch back to SDR
        btnVerSdr.performClick();
        assertEquals("sdr", field("currentMode"));
        assertTrue(btnVerSdr.isSelected());
        assertFalse(btnVerHdr.isSelected());

        // Restart test
        btnReiniciar.performClick();
        assertEquals("sdr", field("currentMode"));
        boolean isAutomated = field("isAutomatedTest");
        assertTrue(isAutomated);
    }

    @Test public void exitingFullscreenRestoresOriginalBrightnessAndUi() throws Exception {
        // Set an arbitrary initial brightness
        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        lp.screenBrightness = 0.5f;
        activity.getWindow().setAttributes(lp);

        call("startAutomatedTest");
        assertEquals(1.0f, activity.getWindow().getAttributes().screenBrightness, 0.001f);
        assertTrue((Boolean) field("fullscreen"));

        Button btnSair = field("btnSair");
        btnSair.performClick();

        assertFalse((Boolean) field("fullscreen"));
        assertEquals(0.5f, activity.getWindow().getAttributes().screenBrightness, 0.001f);

        FrameLayout fullscreenContainer = field("fullscreenContainer");
        assertEquals(View.GONE, fullscreenContainer.getVisibility());
        View workspace = field("workspaceLayout");
        assertEquals(View.VISIBLE, workspace.getVisibility());
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
