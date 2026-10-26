# Community reports

Testing works offline. Submission is optional and user-triggered: the app posts JSON asynchronously to the Apps Script endpoint in `ReportSubmitter.DEFAULT_ENDPOINT`. The UI currently allows reporting before test completion.

## Current payload

`ReportSubmitter.CommunityReport.toJson()` is the source of truth. Its object is **flat**, not nested:

| Group | Keys |
| --- | --- |
| Metadata | `appVersion`, `timestamp` (Unix milliseconds) |
| Device | `deviceName`, `model`, `manufacturer`, `brand`, `product`, `hardware`, `socModel` |
| OS | `os`, `androidRelease`, `sdkInt` |
| Display | `hdrTypes`, `peakNits`, `minNits`, `wideColorGamut`, `activeMode`, `refreshRateHz`, `supportedModes` |
| Survey | `sdrBlackLevel`, `sdrSteps`, `hdrBlackLevel`, `hdrSteps`, `comments` |
| OLED observations | `oledBlackCrush`, `oledSmear`, `oledUniformity`, `oledFloatingBlack`, `oledBurnIn` |
| Compatibility aliases | `hdr`, `nits`, `sdr_black`, `sdr_steps`, `hdr_black`, `hdr_steps` |

HDR types/supported modes are arrays; gamut is boolean. Invalid/unavailable luminance or refresh values become JSON null; refresh is rounded to two decimals. Missing optional OLED responses are empty strings. Survey values are localised text, not stable research categories. Report version comes from BuildConfig; there is no current schema-version field.

Display values are declarations, not measurements. ROM detection is covered in [architecture](ARCHITECTURE.md#device-and-rom-discovery).

## Proposed backend

The script below saves each submission as a separate JSON file in Drive folder `HDR lab reports` (`1VEHauaaZFqlIzDBNgHXb0RLlgMnkwoeb`), named `report_YYYY-MM-DD_HH-mm-ss_<model>.json` using server UTC. Reports can later be aggregated into CSV/Excel/Sheets; no aggregation tool is supplied here.

This is a backend example, not confirmation of the deployed configuration. Separate files avoid shared spreadsheet row updates but do not enforce immutability. The client accepts HTTP 2xx/3xx without checking the script's response body, so displayed success does not prove storage.

### Apps Script (`Code.gs`)

```javascript
/**
 * HDR Lab Light — Community Display Database Ingestion Webhook
 * Receives JSON telemetry and saves individual report files into Google Drive.
 */

// Target Google Drive Folder: "HDR lab reports"
const TARGET_FOLDER_ID = "1VEHauaaZFqlIzDBNgHXb0RLlgMnkwoeb";

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return ContentService.createTextOutput("ERROR: Empty request body")
        .setMimeType(ContentService.MimeType.TEXT);
    }

    var folder = DriveApp.getFolderById(TARGET_FOLDER_ID);
    var raw = e.postData.contents;
    var data = null;

    // 1. Parse JSON body
    try {
      data = JSON.parse(raw);
    } catch (parseErr) {
      // Graceful fallback if sent as URL-encoded form parameters
      data = e.parameter || {};
    }

    // 2. Extract automatically discovered device name for clean filename
    var model = "device";
    if (data.deviceName && String(data.deviceName).trim().length > 0) {
      model = data.deviceName;
    } else {
      var mfg = data.manufacturer || (data.device && data.device.manufacturer) || "";
      var mdl = data.model || (data.device && data.device.model) || "";
      if (mfg && mdl) {
        model = mfg + "_" + mdl;
      } else if (mdl) {
        model = mdl;
      } else if (mfg) {
        model = mfg;
      }
    }
    var cleanModel = String(model).replace(/[^a-zA-Z0-9_\-\.]/g, "_").replace(/_+/g, "_");

    // 3. Generate timestamp string: YYYY-MM-DD_HH-mm-ss
    var now = new Date();
    var pad = function(n) { return (n < 10 ? "0" : "") + n; };
    var timeStamp = now.getUTCFullYear() + "-" +
                    pad(now.getUTCMonth() + 1) + "-" +
                    pad(now.getUTCDate()) + "_" +
                    pad(now.getUTCHours()) + "-" +
                    pad(now.getUTCMinutes()) + "-" +
                    pad(now.getUTCSeconds());

    var fileName = "report_" + timeStamp + "_" + cleanModel + ".json";

    // 4. Save file to Google Drive folder
    folder.createFile(fileName, JSON.stringify(data, null, 2), MimeType.PLAIN_TEXT);

    return ContentService.createTextOutput("SUCCESS: " + fileName)
      .setMimeType(ContentService.MimeType.TEXT);
  } catch (err) {
    return ContentService.createTextOutput("ERROR: " + err.message)
      .setMimeType(ContentService.MimeType.TEXT);
  }
}

function doGet(e) {
  return ContentService.createTextOutput("HDR Lab Light Drive Ingestion Webhook: READY")
    .setMimeType(ContentService.MimeType.TEXT);
}
```

### Deploy

1. Open [Google Apps Script](https://script.google.com) (or a Sheet's **Extensions → Apps Script**) and save the code as `Code.gs`.
2. Select **Deploy → New deployment → Web app**, or edit an existing web-app deployment under **Manage deployments**.
3. For an update, select **New version**. Set **Who has access** to **Anyone**, then deploy.
4. Check the resulting endpoint against `ReportSubmitter.DEFAULT_ENDPOINT`; a new deployment may have a different URL. Changing the app endpoint is separate code work.

This cleanup does not deploy the example or send reports.

## Privacy and limitations

The app adds no accounts, names, email addresses, hardware/tracking IDs, location, user files, cookies or credentials to the report. There is no background analytics or automatic reporting. Optional notes can contain personal information entered by the user; avoid including it. Cancel closes the survey without submission.

Keep networking fail-safe and non-blocking. Real delivery, redirect handling, backend rejection and scientific validity need separate verification; see [known gaps](ARCHITECTURE.md#known-gaps).
