# Developer tools

See [verification commands](../docs/DEVELOPMENT.md#verification). PowerShell 5.1 and 7 are supported; an absolute `verify.ps1` path works from any directory.

| Option | Checks |
| --- | --- |
| Default | `testDebugUnitTest`, `lintDebug` |
| `-Release` | Also `assembleRelease`, `lintRelease`, APK validation/size report; no hard size cap |
| `-Patterns` | Also verify all packaged videos; needs Python 3, ffmpeg and ffprobe |
| `-PythonExecutable 'C:\path\to\python.exe'` | Use a real Python runtime if PATH resolves to a broken Windows Store alias |

## Tool discovery

JDK 17: `JAVA_HOME`, then `%LOCALAPPDATA%\Programs\jdk-17\jdk-17.0.20.1+1`, then `java.exe` on PATH.

Android Platform 36 / Build-Tools 36.0.0: `local.properties` (`sdk.dir`) takes precedence over `ANDROID_HOME`, `ANDROID_SDK_ROOT` and `%LOCALAPPDATA%\Android\Sdk`. Environment changes last only for the script. Python is checked before Gradle when patterns are requested.

## Results and assets

Logs: `app/build/validation/`; reports: `app/build/reports/`. Success prints a summary; failure prints the last 40 log lines. Native failures retain their exit code; prerequisite/APK validation failures return 1. No software is installed; Gradle may download dependencies. Do not run concurrent Gradle builds in this checkout.

`generate_patterns.py` needs its configured FFmpeg encoders and changes research assets. Regenerate only when requested; then run `verify_patterns.py` and review the asset diff. Verification checks signalling and decoded first/last frames, not every frame or physical playback.
