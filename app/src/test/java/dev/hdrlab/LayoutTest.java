package dev.hdrlab;

import android.app.AlertDialog;
import android.content.res.Configuration;
import android.content.res.Resources;
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
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
        assertEquals(activity.getString(R.string.btn_detailed_report), btnDetailedReport.getText().toString());
    }

    @Test public void quickTestCardContainsInstructionsAndStartButton() throws Exception {
        LinearLayout testCard = field("quickTestCard");
        assertNotNull("Teste Rápido card must exist", testCard);

        Button btnStartTest = field("btnStartTest");
        assertNotNull(btnStartTest);
        assertEquals(activity.getString(R.string.btn_start_test), btnStartTest.getText().toString());

        // Verify instructions text inside card contains key explanation items
        boolean foundHowExplanation = false;
        boolean foundWhatExplanation = false;
        String expectedHow = activity.getString(R.string.test_how_it_works_text);
        String expectedWhat = activity.getString(R.string.test_what_to_look_text);

        for (int i = 0; i < testCard.getChildCount(); i++) {
            View child = testCard.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout box = (LinearLayout) child;
                for (int j = 0; j < box.getChildCount(); j++) {
                    View inner = box.getChildAt(j);
                    if (inner instanceof TextView) {
                        String text = ((TextView) inner).getText().toString();
                        if (text.equals(expectedHow)) {
                            foundHowExplanation = true;
                        }
                        if (text.equals(expectedWhat)) {
                            foundWhatExplanation = true;
                        }
                    }
                }
            }
        }

        assertTrue("Should contain how test works instruction", foundHowExplanation);
        assertTrue("Should contain what to look for instruction", foundWhatExplanation);
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
        assertEquals(activity.getString(R.string.hud_title_sdr_countdown, 10), hudTitleLabel.getText().toString());

        Runnable ticker = field("testCountdownTicker");
        assertNotNull(ticker);

        // Advance 9 ticks
        for (int i = 0; i < 9; i++) {
            ticker.run();
        }
        String modeAfter9 = field("currentMode");
        assertEquals("sdr", modeAfter9);
        assertEquals(activity.getString(R.string.hud_title_sdr_countdown, 1), hudTitleLabel.getText().toString());

        // Advance 10th tick (triggers transition to HDR)
        ticker.run();
        String modeAfter10 = field("currentMode");
        assertEquals("hdr", modeAfter10);

        boolean isAutomated = field("isAutomatedTest");
        assertFalse("Automated phase ends after 10s transition", isAutomated);
        assertEquals(activity.getString(R.string.hud_title_hdr_active), hudTitleLabel.getText().toString());
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

        Button btnWatchSdr = field("btnWatchSdr");
        Button btnWatchHdr = field("btnWatchHdr");
        Button btnRestart = field("btnRestart");
        Button btnExit = field("btnExit");

        assertNotNull(btnWatchSdr);
        assertNotNull(btnWatchHdr);
        assertNotNull(btnRestart);
        assertNotNull(btnExit);

        int minH = (int) dpMethod.invoke(activity, 36f);
        int minW = (int) dpMethod.invoke(activity, 44f);

        for (Button b : new Button[] {btnWatchSdr, btnWatchHdr, btnRestart, btnExit}) {
            assertTrue("Button must enforce singleLine", b.isSingleLine());
            assertTrue("Button minHeight must be >= 36dp", b.getMinHeight() >= minH);
            assertTrue("Button minimumWidth must be >= 44dp", b.getMinimumWidth() >= minW);
        }
    }

    @Test public void switchingBetweenSdrAndHdrInHud() throws Exception {
        call("startAutomatedTest");

        Button btnWatchSdr = field("btnWatchSdr");
        Button btnWatchHdr = field("btnWatchHdr");
        Button btnRestart = field("btnRestart");

        // Initially SDR
        assertEquals("sdr", field("currentMode"));
        assertTrue(btnWatchSdr.isSelected());
        assertFalse(btnWatchHdr.isSelected());

        // Switch to HDR
        btnWatchHdr.performClick();
        assertEquals("hdr", field("currentMode"));
        assertTrue(btnWatchHdr.isSelected());
        assertFalse(btnWatchSdr.isSelected());

        // Switch back to SDR
        btnWatchSdr.performClick();
        assertEquals("sdr", field("currentMode"));
        assertTrue(btnWatchSdr.isSelected());
        assertFalse(btnWatchHdr.isSelected());

        // Restart test
        btnRestart.performClick();
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

        Button btnExit = field("btnExit");
        btnExit.performClick();

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

    @Test public void responsiveLayoutAdaptsToTabletLandscapeAndNarrowPortrait() throws Exception {
        FrameLayout root = field("root");
        LinearLayout cardsContainer = field("cardsContainer");
        LinearLayout capCard = field("deviceCapabilitiesCard");
        LinearLayout testCard = field("quickTestCard");

        assertNotNull(root);
        assertNotNull(cardsContainer);
        assertNotNull(capCard);
        assertNotNull(testCard);

        float density = activity.getResources().getDisplayMetrics().density;

        // 1. Wide landscape (e.g. 1280x800, sw720dp)
        activity.getResources().getConfiguration().orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int wideWidthPx = (int) (1280 * density);
        int wideHeightPx = (int) (800 * density);
        root.layout(0, 0, wideWidthPx, wideHeightPx);
        call("applyResponsiveLayout");

        assertEquals("cardsContainer must be HORIZONTAL in wide landscape",
            LinearLayout.HORIZONTAL, cardsContainer.getOrientation());

        LinearLayout.LayoutParams capParamsWide = (LinearLayout.LayoutParams) capCard.getLayoutParams();
        LinearLayout.LayoutParams testParamsWide = (LinearLayout.LayoutParams) testCard.getLayoutParams();
        assertNotNull(capParamsWide);
        assertNotNull(testParamsWide);
        assertEquals("deviceCapabilitiesCard weight must be 1.0f in wide landscape", 1.0f, capParamsWide.weight, 0.001f);
        assertEquals("quickTestCard weight must be 1.0f in wide landscape", 1.0f, testParamsWide.weight, 0.001f);

        // 2. Narrow portrait (e.g. 400x800)
        activity.getResources().getConfiguration().orientation = android.content.res.Configuration.ORIENTATION_PORTRAIT;
        int narrowWidthPx = (int) (400 * density);
        int narrowHeightPx = (int) (800 * density);
        root.layout(0, 0, narrowWidthPx, narrowHeightPx);
        call("applyResponsiveLayout");

        assertEquals("cardsContainer must be VERTICAL in narrow portrait",
            LinearLayout.VERTICAL, cardsContainer.getOrientation());
    }

    @Test public void detectOsInfoIdentifiesAllVendorCustomRomsAndFallback() {
        // 1. Honor MagicOS
        Map<String, String> honorProps = new HashMap<>();
        honorProps.put("ro.build.version.magic", "8.0");
        assertEquals("MagicOS 8.0 (Android 16)",
            Diagnostics.detectOsInfo(honorProps::get, "HONOR", "16"));

        // 2. Xiaomi HyperOS
        Map<String, String> hyperProps = new HashMap<>();
        hyperProps.put("ro.mi.os.version.name", "1.0.2");
        assertEquals("HyperOS 1.0.2 (Android 14)",
            Diagnostics.detectOsInfo(hyperProps::get, "Xiaomi", "14"));

        // 3. Xiaomi MIUI
        Map<String, String> miuiProps = new HashMap<>();
        miuiProps.put("ro.miui.ui.version.name", "14.0");
        assertEquals("MIUI 14.0 (Android 13)",
            Diagnostics.detectOsInfo(miuiProps::get, "POCO", "13"));

        // 4. Samsung One UI (numeric ro.build.version.oneui)
        Map<String, String> oneUiProps = new HashMap<>();
        oneUiProps.put("ro.build.version.oneui", "60100");
        assertEquals("One UI 6.1 (Android 14)",
            Diagnostics.detectOsInfo(oneUiProps::get, "Samsung", "14"));

        // 5. Samsung One UI (SEM_PLATFORM_INT)
        Map<String, String> semProps = new HashMap<>();
        semProps.put("SEM_PLATFORM_INT", "150100");
        assertEquals("One UI 6.1 (Android 14)",
            Diagnostics.detectOsInfo(semProps::get, "samsung", "14"));

        // 6. OnePlus OxygenOS
        Map<String, String> oxygenProps = new HashMap<>();
        oxygenProps.put("ro.oxygen.version", "14.0");
        assertEquals("OxygenOS 14.0 (Android 14)",
            Diagnostics.detectOsInfo(oxygenProps::get, "OnePlus", "14"));

        // 7. OPPO ColorOS
        Map<String, String> oppoProps = new HashMap<>();
        oppoProps.put("ro.build.version.opporom", "14.0");
        assertEquals("ColorOS 14.0 (Android 14)",
            Diagnostics.detectOsInfo(oppoProps::get, "OPPO", "14"));

        // 8. Realme UI
        Map<String, String> realmeProps = new HashMap<>();
        realmeProps.put("ro.build.version.realmeui", "5.0");
        assertEquals("Realme UI 5.0 (Android 14)",
            Diagnostics.detectOsInfo(realmeProps::get, "Realme", "14"));

        // 9. Vivo OriginOS / Funtouch
        Map<String, String> vivoProps = new HashMap<>();
        vivoProps.put("ro.vivo.os.version", "4.0");
        assertEquals("OriginOS / Funtouch OS 4.0 (Android 14)",
            Diagnostics.detectOsInfo(vivoProps::get, "vivo", "14"));

        // 10. Google Pixel UI
        assertEquals("Pixel UI (Android 15)",
            Diagnostics.detectOsInfo(k -> null, "Google", "15"));

        // 11. Nothing OS
        Map<String, String> nothingProps = new HashMap<>();
        nothingProps.put("ro.nothing.version", "2.5");
        assertEquals("Nothing OS 2.5 (Android 14)",
            Diagnostics.detectOsInfo(nothingProps::get, "Nothing", "14"));

        // 12. Fallback generic Android
        assertEquals("Android 14",
            Diagnostics.detectOsInfo(k -> null, "Motorola", "14"));

        // 13. Production no-arg detectOsInfo runs without throwing
        String osInfo = Diagnostics.detectOsInfo();
        assertNotNull(osInfo);
        assertTrue(osInfo.length() > 0);
    }

    @Test public void communityReportButtonExistsAndOpensSurveyDialog() throws Exception {
        LinearLayout testCard = field("quickTestCard");
        assertNotNull("quickTestCard must exist", testCard);

        Button btnCommunityReport = field("btnCommunityReport");
        assertNotNull("btnCommunityReport must exist", btnCommunityReport);
        assertEquals(activity.getString(R.string.btn_community_report), btnCommunityReport.getText().toString());
        assertTrue("Button should be single line", btnCommunityReport.isSingleLine());

        Method dpMethod = MainActivity.class.getDeclaredMethod("dp", float.class);
        dpMethod.setAccessible(true);
        int minH = (int) dpMethod.invoke(activity, 48f);
        assertTrue("Button minHeight should be >= 48dp", btnCommunityReport.getMinHeight() >= minH);

        // Click button and verify AlertDialog is displayed
        btnCommunityReport.performClick();
        AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
        assertNotNull("Survey dialog should be shown after clicking report button", dialog);
        assertTrue("Dialog must be showing", dialog.isShowing());

        // Cancel dialog
        dialog.dismiss();
    }

    @Test public void reportSubmitterHandlesMockSubmissionSuccessfully() throws Exception {
        ReportSubmitter.CommunityReport report = new ReportSubmitter.CommunityReport(
            "Test Model", "MagicOS 8.0 (Android 16)", "HDR10", "1000",
            "Pure absolute black (OLED / Pixels off)", "All 6 steps visible (Excellent shadow detail)",
            "Remained pure black (Flawless HDR presentation)", "All 6 steps visible in HDR (Accurate tone mapping)",
            "Test comments", System.currentTimeMillis()
        );

        assertEquals("Test Model", report.model);
        assertEquals("MagicOS 8.0 (Android 16)", report.os);
        assertEquals("HDR10", report.hdr);
        assertEquals("1000", report.nits);
        assertEquals("Pure absolute black (OLED / Pixels off)", report.sdrBlackLevel);
        assertEquals("All 6 steps visible (Excellent shadow detail)", report.sdrSteps);
        assertEquals("Remained pure black (Flawless HDR presentation)", report.hdrBlackLevel);
        assertEquals("All 6 steps visible in HDR (Accurate tone mapping)", report.hdrSteps);
        assertEquals("Test comments", report.comments);
        assertTrue(report.timestamp > 0);

        AtomicBoolean success = new AtomicBoolean(false);

        Future<?> f = ReportSubmitter.submit(report, "mock://test", new ReportSubmitter.Callback() {
            @Override public void onSuccess() {
                success.set(true);
            }
            @Override public void onError(String message) {}
        });

        assertNotNull(f);
        f.get(3, TimeUnit.SECONDS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertTrue("Mock/Default endpoint should succeed", success.get());
    }

    @Test public void localeResolvesEnglishDefaultAndPortuguese() {
        Configuration configEn = new Configuration(activity.getResources().getConfiguration());
        configEn.setLocale(Locale.ENGLISH);
        Resources enRes = activity.createConfigurationContext(configEn).getResources();

        assertEquals("HDR Lab Light", enRes.getString(R.string.app_name));
        assertEquals("DISPLAY DIAGNOSTICS", enRes.getString(R.string.header_eyebrow));
        assertEquals("Screen Capabilities", enRes.getString(R.string.card_capabilities_title));
        assertEquals("Quick Test: SDR vs HDR", enRes.getString(R.string.card_quick_test_title));
        assertEquals("Device & Android:", enRes.getString(R.string.prop_device_model));
        assertEquals("HDR Support:", enRes.getString(R.string.prop_hdr_support));
        assertEquals("Color Gamut:", enRes.getString(R.string.prop_color_gamut));
        assertEquals("Estimated Peak Brightness:", enRes.getString(R.string.prop_peak_brightness));
        assertEquals("View Detailed Technical Report", enRes.getString(R.string.btn_detailed_report));
        assertEquals("▶ Start Fullscreen SDR vs HDR Test", enRes.getString(R.string.btn_start_test));
        assertEquals("📋 Submit Community Report", enRes.getString(R.string.btn_community_report));
        assertEquals("SDR MODE · %1$ds remaining", enRes.getString(R.string.hud_title_sdr_countdown));
        assertEquals("HDR MODE (HDR10 / PQ) ACTIVE", enRes.getString(R.string.hud_title_hdr_active));
        assertEquals("Watch SDR", enRes.getString(R.string.hud_btn_watch_sdr));
        assertEquals("Watch HDR", enRes.getString(R.string.hud_btn_watch_hdr));
        assertEquals("⟲ Restart", enRes.getString(R.string.hud_btn_restart));
        assertEquals("✕ Exit", enRes.getString(R.string.hud_btn_exit));
        assertEquals("Technical Report", enRes.getString(R.string.dialog_technical_report_title));
        assertEquals("Share Report", enRes.getString(R.string.share_report_title));
        assertEquals("Reading system data…", enRes.getString(R.string.status_reading_system_data));
        assertEquals("Checking…", enRes.getString(R.string.status_checking));
        assertEquals("Not reported by system", enRes.getString(R.string.brightness_not_reported));

        assertEquals("Community Display Report", enRes.getString(R.string.dialog_report_title));
        assertEquals("DETECTED DISPLAY SPECIFICATIONS", enRes.getString(R.string.dialog_report_specs_header));
        assertEquals("1. SDR Black Background Level (First 10s):", enRes.getString(R.string.dialog_report_q1_title));
        assertEquals("Pure absolute black (OLED / Pixels off)", enRes.getString(R.string.dialog_report_q1_opt_a));
        assertEquals("Subtle normal grey glow (Standard LCD)", enRes.getString(R.string.dialog_report_q1_opt_b));
        assertEquals("2. SDR Near-Black Steps (1% to 6%):", enRes.getString(R.string.dialog_report_q2_title));
        assertEquals("All 6 steps visible (Excellent shadow detail)", enRes.getString(R.string.dialog_report_q2_opt_a));
        assertEquals("4 to 5 steps visible", enRes.getString(R.string.dialog_report_q2_opt_b));
        assertEquals("Less than 4 steps (Black crush / crushed shadows)", enRes.getString(R.string.dialog_report_q2_opt_c));
        assertEquals("3. HDR Black Background (After transition at 10s):", enRes.getString(R.string.dialog_report_q3_title));
        assertEquals("Remained pure black (Flawless HDR presentation)", enRes.getString(R.string.dialog_report_q3_opt_a));
        assertEquals("Elevated / Glowed grey (Raised blacks bug in HDR)", enRes.getString(R.string.dialog_report_q3_opt_b));
        assertEquals("Subtle normal grey glow (Unchanged LCD)", enRes.getString(R.string.dialog_report_q3_opt_c));
        assertEquals("4. HDR Near-Black Steps (1% to 6%):", enRes.getString(R.string.dialog_report_q4_title));
        assertEquals("All 6 steps visible in HDR (Accurate tone mapping)", enRes.getString(R.string.dialog_report_q4_opt_a));
        assertEquals("4 to 5 steps visible in HDR", enRes.getString(R.string.dialog_report_q4_opt_b));
        assertEquals("Less than 4 steps in HDR (HDR crushed shadows)", enRes.getString(R.string.dialog_report_q4_opt_c));
        assertEquals("5. Observations / Notes (optional):", enRes.getString(R.string.dialog_report_q5_title));
        assertEquals("Cancel", enRes.getString(R.string.dialog_btn_cancel));
        assertEquals("Submit", enRes.getString(R.string.dialog_btn_submit));
        assertEquals("✅ Report submitted successfully! Thank you.", enRes.getString(R.string.report_submit_success));

        // Portuguese pt-PT
        Configuration configPt = new Configuration(activity.getResources().getConfiguration());
        configPt.setLocale(new Locale("pt", "PT"));
        Resources ptRes = activity.createConfigurationContext(configPt).getResources();

        assertEquals("HDR Lab Light", ptRes.getString(R.string.app_name));
        assertEquals("DIAGNÓSTICO VISUAL DE ECRÃ", ptRes.getString(R.string.header_eyebrow));
        assertEquals("Capacidades do Teu Ecrã", ptRes.getString(R.string.card_capabilities_title));
        assertEquals("Teste Rápido: SDR vs HDR", ptRes.getString(R.string.card_quick_test_title));
        assertEquals("Modelo e Versão Android:", ptRes.getString(R.string.prop_device_model));
        assertEquals("Suporte HDR:", ptRes.getString(R.string.prop_hdr_support));
        assertEquals("Gama de Cores:", ptRes.getString(R.string.prop_color_gamut));
        assertEquals("Brilho de Pico Estimado:", ptRes.getString(R.string.prop_peak_brightness));
        assertEquals("Ver Relatório Técnico Detalhado", ptRes.getString(R.string.btn_detailed_report));
        assertEquals("▶ Iniciar Teste SDR vs HDR em Ecrã Cheio", ptRes.getString(R.string.btn_start_test));
        assertEquals("📋 Submeter Relatório Comunitário", ptRes.getString(R.string.btn_community_report));
        assertEquals("MODO SDR · %1$ds restantes", ptRes.getString(R.string.hud_title_sdr_countdown));
        assertEquals("MODO HDR (HDR10 / PQ) ATIVADO", ptRes.getString(R.string.hud_title_hdr_active));
        assertEquals("Ver em SDR", ptRes.getString(R.string.hud_btn_watch_sdr));
        assertEquals("Ver em HDR", ptRes.getString(R.string.hud_btn_watch_hdr));
        assertEquals("⟲ Reiniciar", ptRes.getString(R.string.hud_btn_restart));
        assertEquals("✕ Sair", ptRes.getString(R.string.hud_btn_exit));
        assertEquals("Relatório Técnico do Dispositivo", ptRes.getString(R.string.dialog_technical_report_title));
        assertEquals("Partilhar Relatório HDR Lab Light", ptRes.getString(R.string.share_report_title));
        assertEquals("A ler dados do sistema…", ptRes.getString(R.string.status_reading_system_data));
        assertEquals("A verificar…", ptRes.getString(R.string.status_checking));
        assertEquals("Não declarado pelo sistema", ptRes.getString(R.string.brightness_not_reported));

        assertEquals("Relatório Comunitário de Ecrã", ptRes.getString(R.string.dialog_report_title));
        assertEquals("ESPECIFICAÇÕES DETETADAS DO ECRÃ", ptRes.getString(R.string.dialog_report_specs_header));
        assertEquals("1. Nível de Fundo Preto SDR (Primeiros 10s):", ptRes.getString(R.string.dialog_report_q1_title));
        assertEquals("Preto puro absoluto (OLED / Píxeis desligados)", ptRes.getString(R.string.dialog_report_q1_opt_a));
        assertEquals("Ligeiro brilho cinzento normal (LCD padrão)", ptRes.getString(R.string.dialog_report_q1_opt_b));
        assertEquals("2. Degraus Quase-Preto SDR (1% a 6%):", ptRes.getString(R.string.dialog_report_q2_title));
        assertEquals("Todos os 6 degraus visíveis (Excelente detalhe)", ptRes.getString(R.string.dialog_report_q2_opt_a));
        assertEquals("4 a 5 degraus visíveis", ptRes.getString(R.string.dialog_report_q2_opt_b));
        assertEquals("Menos de 4 degraus (Black crush / sombras esmagadas)", ptRes.getString(R.string.dialog_report_q2_opt_c));
        assertEquals("3. Fundo Preto HDR (Após transição aos 10s):", ptRes.getString(R.string.dialog_report_q3_title));
        assertEquals("Manteve-se preto puro (Calibração HDR perfeita)", ptRes.getString(R.string.dialog_report_q3_opt_a));
        assertEquals("Acendeu a cinzento / brilhante (Bug de pretos elevados em HDR)", ptRes.getString(R.string.dialog_report_q3_opt_b));
        assertEquals("Ligeiro brilho cinzento normal (LCD inalterado)", ptRes.getString(R.string.dialog_report_q3_opt_c));
        assertEquals("4. Degraus Quase-Preto HDR (1% a 6%):", ptRes.getString(R.string.dialog_report_q4_title));
        assertEquals("Todos os 6 degraus visíveis em HDR (Tone mapping preciso)", ptRes.getString(R.string.dialog_report_q4_opt_a));
        assertEquals("4 a 5 degraus visíveis em HDR", ptRes.getString(R.string.dialog_report_q4_opt_b));
        assertEquals("Menos de 4 degraus em HDR (Sombras esmagadas em HDR)", ptRes.getString(R.string.dialog_report_q4_opt_c));
        assertEquals("5. Observações / Notas (opcional):", ptRes.getString(R.string.dialog_report_q5_title));
        assertEquals("Cancelar", ptRes.getString(R.string.dialog_btn_cancel));
        assertEquals("Submeter", ptRes.getString(R.string.dialog_btn_submit));
        assertEquals("✅ Relatório enviado com sucesso! Obrigado pela contribuição.", ptRes.getString(R.string.report_submit_success));
    }
}
