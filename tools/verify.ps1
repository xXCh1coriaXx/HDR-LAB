#Requires -Version 5.1
<#
.SYNOPSIS
Runs local checks from any working directory. See tools/README.md.
#>
[CmdletBinding()]
param(
    [switch]$Release,
    [switch]$Patterns,
    [string]$PythonExecutable = 'python'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$logDirectory = Join-Path $projectRoot 'app/build/validation'
$originalJavaHome = $env:JAVA_HOME
$originalAndroidHome = $env:ANDROID_HOME
$originalAndroidSdkRoot = $env:ANDROID_SDK_ROOT
$exitCode = 1

function Find-Jdk17 {
    $candidates = @($env:JAVA_HOME)
    if ($env:LOCALAPPDATA) {
        $candidates += Join-Path $env:LOCALAPPDATA 'Programs/jdk-17/jdk-17.0.20.1+1'
    }
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $candidates += Split-Path -Parent (Split-Path -Parent $javaCommand.Source)
    }
    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        $releaseFile = Join-Path $candidate 'release'
        if ((Test-Path (Join-Path $candidate 'bin/javac.exe')) -and
            (Test-Path $releaseFile) -and
            (Select-String -Path $releaseFile -Pattern '^JAVA_VERSION="17[."]' -Quiet)) {
            return $candidate
        }
    }
    throw 'JDK 17 was not found. Install a JDK 17 and set JAVA_HOME to its directory (containing bin/javac.exe).'
}

function Find-AndroidSdk {
    $candidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    $propertiesFile = Join-Path $projectRoot 'local.properties'
    if (Test-Path $propertiesFile) {
        $sdkLine = Get-Content $propertiesFile | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -Last 1
        if ($sdkLine) {
            $sdkPath = ($sdkLine -replace '^\s*sdk\.dir\s*=\s*', '').Replace('\:', ':').Replace('\\', '\')
            if (-not [IO.Path]::IsPathRooted($sdkPath)) { $sdkPath = Join-Path $projectRoot $sdkPath }
            # Gradle gives local.properties precedence over environment variables.
            $candidates = @($sdkPath) + $candidates
        }
    }
    if ($env:LOCALAPPDATA) { $candidates += Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path (Join-Path $candidate 'platforms/android-36/android.jar')) -and
            (Test-Path (Join-Path $candidate 'build-tools/36.0.0/aapt2.exe'))) {
            return $candidate
        }
        if ($candidate -and $sdkLine -and $candidate -eq $sdkPath) {
            throw 'The sdk.dir in local.properties is missing Android Platform 36 or Build-Tools 36.0.0. Fix sdk.dir or install those packages with Android SDK Manager.'
        }
    }
    throw 'Android SDK was not found with Platform 36 and Build-Tools 36.0.0. Install those packages and set ANDROID_HOME, or configure sdk.dir in local.properties.'
}

try {
    $env:JAVA_HOME = Find-Jdk17
    $env:ANDROID_HOME = Find-AndroidSdk
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
    if ($Patterns) {
        foreach ($command in @($PythonExecutable, 'ffmpeg', 'ffprobe')) {
            if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
                throw "Pattern checks require $command on PATH. Install Python 3 and FFmpeg, then retry -Patterns."
            }
        }
        $ErrorActionPreference = 'Continue'
        $pythonVersion = & $PythonExecutable --version 2>&1
        $pythonExitCode = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($pythonExitCode -ne 0 -or ($pythonVersion -join ' ') -notmatch '^Python 3\.') {
            throw 'Pattern checks require a working Python 3. Disable the Windows Store python alias or pass -PythonExecutable with the full path to python.exe.'
        }
    }
    New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
    $logPrefix = Join-Path $logDirectory (Get-Date -Format 'yyyyMMdd-HHmmss-fff')
    Push-Location $projectRoot
    try {
        Write-Host "JDK: $env:JAVA_HOME"
        Write-Host "SDK: $env:ANDROID_HOME"
        $tasks = @('testDebugUnitTest', 'lintDebug')
        if ($Release) { $tasks += @('assembleRelease', 'lintRelease') }
        Write-Host "Gradle log: $logPrefix-gradle.log"
        # Windows PowerShell 5.1 wraps native stderr in ErrorRecord objects.
        # Preserve native status rather than treating stderr alone as failure.
        $ErrorActionPreference = 'Continue'
        & (Join-Path $projectRoot 'gradlew.bat') --console=plain @tasks 2>&1 |
            Out-File -FilePath "$logPrefix-gradle.log" -Encoding utf8
        $exitCode = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($exitCode -ne 0) {
            Get-Content -LiteralPath "$logPrefix-gradle.log" -Tail 40 | ForEach-Object { Write-Host $_ }
            throw "Gradle failed (exit $exitCode). See $logPrefix-gradle.log"
        }
        Write-Host "Gradle passed: $($tasks -join ', ')."
        $exitCode = 1
        if ($Release) {
            $apkPath = Join-Path $projectRoot 'app/build/outputs/apk/release/app-release.apk'
            $apk = Get-Item -LiteralPath $apkPath
            if (-not $apk -or $apk.Length -le 0) { throw "Release APK is missing or empty: $apkPath" }
            Write-Host "Release APK size: $($apk.Length) bytes."
        }
        if ($Patterns) {
            Write-Host "Pattern log: $logPrefix-patterns.log"
            $ErrorActionPreference = 'Continue'
            & $PythonExecutable (Join-Path $PSScriptRoot 'verify_patterns.py') 2>&1 |
                Out-File -FilePath "$logPrefix-patterns.log" -Encoding utf8
            $exitCode = $LASTEXITCODE
            $ErrorActionPreference = 'Stop'
            if ($exitCode -ne 0) {
                Get-Content -LiteralPath "$logPrefix-patterns.log" -Tail 40 | ForEach-Object { Write-Host $_ }
                throw "Pattern verification failed (exit $exitCode). See $logPrefix-patterns.log; ensure Python 3 and FFmpeg are available."
            }
            Write-Host 'Pattern verification passed.'
        }
        $exitCode = 0
        Write-Host 'Validation passed. Reports: app/build/reports/'
    }
    finally { Pop-Location }
}
catch {
    Write-Host "Validation failed: $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    $env:JAVA_HOME = $originalJavaHome
    $env:ANDROID_HOME = $originalAndroidHome
    $env:ANDROID_SDK_ROOT = $originalAndroidSdkRoot
}
exit $exitCode
