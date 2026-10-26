# Developer tools

Run from the repository root in Windows PowerShell 5.1 or PowerShell 7:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify.ps1 -Release
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify.ps1 -Release -Patterns
```

The default runs `testDebugUnitTest` and `lintDebug`. `-Release` also runs
`assembleRelease` and `lintRelease`, then verifies and reports the release APK
size (no hard 2 MB cap). `-Patterns` additionally runs `verify_patterns.py`
against all existing video assets; it requires Python 3, `ffmpeg`, and `ffprobe`
on PATH. Checks do not regenerate assets or submit community reports.

If `python` resolves to a Windows Store alias or Python is installed outside
PATH, pass `-PythonExecutable 'C:\path\to\python.exe'` with `-Patterns`. Python 3
is checked before Gradle starts, so a broken alias fails without running a build.

An absolute path to `verify.ps1` works from any working directory. The script
detects JDK 17 from `JAVA_HOME`, the documented local installation, or `java.exe`
on PATH. It detects Android Platform 36 and Build-Tools 36.0.0 from
`local.properties` (`sdk.dir`), SDK environment variables, or
`%LOCALAPPDATA%\Android\Sdk`. A configured `sdk.dir` takes precedence, as it does
in Gradle. Environment changes last only for the script execution.

Logs are saved under `app/build/validation/`; unit-test and lint reports are
under `app/build/reports/`. Successful runs print a compact summary; full native
output stays in the logs. Failures print the last 40 log lines for diagnosis.
A failing native command returns its original exit
code; prerequisite or APK-size failures return 1. Gradle uses its normal caches
and may download missing build dependencies; the script installs no software.
Do not run multiple Gradle builds against this checkout at once.

`generate_patterns.py` changes scientific test assets and is intentionally not
part of validation. Change it only when pattern regeneration is explicitly in
scope, then run `verify_patterns.py` and review the asset diff.
