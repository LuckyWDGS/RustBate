param(
    [string]$Serial,
    [string]$ApkPath = "$(Join-Path $PSScriptRoot '..\app\build\outputs\apk\debug\app-debug.apk')",
    [int]$LogDurationSec = 25,
    [string]$PackageName = 'com.moviecat.app',
    [string]$ActivityName = 'com.moviecat.app/.MainActivity',
    [string]$OutputRoot = "$(Join-Path $PSScriptRoot '..\debug-artifacts\device')"
)

$ErrorActionPreference = 'Stop'

function Resolve-Adb {
    $candidates = @(
        (Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue),
        'E:\AndriodSDK\platform-tools\adb.exe',
        "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe",
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    ) | Where-Object { $_ } | Select-Object -Unique

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return $candidate
        }
    }

    throw 'adb.exe not found. Please install Android platform-tools or fix ANDROID_SDK_ROOT.'
}

function Invoke-Adb {
    param(
        [string]$Adb,
        [string[]]$PrefixArgs,
        [string[]]$AdbArgs,
        [switch]$IgnoreExitCode
    )

    $output = & $Adb @PrefixArgs @AdbArgs 2>&1
    if (-not $IgnoreExitCode -and $LASTEXITCODE -ne 0) {
        throw "adb $($AdbArgs -join ' ') failed: $($output | Out-String)"
    }
    return ($output | Out-String).Trim()
}

function Resolve-TargetArgs {
    param(
        [string]$Adb,
        [string]$PreferredSerial
    )

    $deviceLines = (& $Adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" }
    $serials = $deviceLines | ForEach-Object { ($_ -split '\s+')[0] } | Where-Object { $_ }

    if ($PreferredSerial) {
        return @('-s', $PreferredSerial)
    }
    if ($serials.Count -eq 1) {
        return @('-s', $serials[0])
    }
    if ($serials.Count -gt 1) {
        throw "More than one device/emulator is connected. Please pass -Serial. Devices: $($serials -join ', ')"
    }
    throw 'No connected device found. Please connect the TV/box with USB debugging enabled.'
}

function Save-Text {
    param(
        [string]$Path,
        [string]$Text
    )

    $directory = Split-Path -Parent $Path
    if ($directory -and !(Test-Path $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    [System.IO.File]::WriteAllText($Path, $Text, [System.Text.Encoding]::UTF8)
}

$adb = Resolve-Adb
$adbPrefix = Resolve-TargetArgs -Adb $adb -PreferredSerial $Serial
$resolvedApkPath = (Resolve-Path $ApkPath).Path
$resolvedSerial = $adbPrefix[1]

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outputDir = Join-Path $OutputRoot "$($resolvedSerial)-$timestamp"
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null

$summary = [System.Collections.Generic.List[string]]::new()
$summary.Add("serial=$resolvedSerial")
$summary.Add("apk=$resolvedApkPath")
$summary.Add("logDurationSec=$LogDurationSec")

Write-Host "== MovieCat device debug =="
Write-Host "serial: $resolvedSerial"
Write-Host "apk: $resolvedApkPath"
Write-Host "out: $outputDir"

Save-Text (Join-Path $outputDir 'adb-devices.txt') ((& $adb devices -l | Out-String).Trim())

Invoke-Adb -Adb $adb -PrefixArgs $adbPrefix -AdbArgs @('wait-for-device') | Out-Null
$installOutput = ''
try {
    $installOutput = Invoke-Adb -Adb $adb -PrefixArgs $adbPrefix -AdbArgs @('install', '-r', '-t', $resolvedApkPath)
    Save-Text (Join-Path $outputDir 'install.txt') $installOutput
} catch {
    $installError = $_.Exception.Message
    $installedPath = Invoke-Adb -Adb $adb -PrefixArgs $adbPrefix -AdbArgs @('shell', 'pm', 'path', $PackageName) -IgnoreExitCode
    $installOutput = "INSTALL_FAILED`r`n$installError`r`ninstalledPath=$installedPath"
    Save-Text (Join-Path $outputDir 'install.txt') $installOutput
    if ($installedPath -notmatch '^package:') {
        throw
    }
}

Invoke-Adb -Adb $adb -PrefixArgs $adbPrefix -AdbArgs @('shell', 'am', 'force-stop', $PackageName) -IgnoreExitCode | Out-Null
Invoke-Adb -Adb $adb -PrefixArgs $adbPrefix -AdbArgs @('logcat', '-c') | Out-Null

$startOutput = Invoke-Adb -Adb $adb -PrefixArgs $adbPrefix -AdbArgs @('shell', 'am', 'start', '-W', '-n', $ActivityName)
Save-Text (Join-Path $outputDir 'start.txt') $startOutput
Start-Sleep -Seconds 5

$appPid = Invoke-Adb -Adb $adb -PrefixArgs $adbPrefix -AdbArgs @('shell', 'pidof', $PackageName) -IgnoreExitCode
Save-Text (Join-Path $outputDir 'pid.txt') $appPid
$summary.Add("pid=$appPid")

$topActivity = Invoke-Adb -Adb $adb -PrefixArgs $adbPrefix -AdbArgs @('shell', 'dumpsys', 'activity', 'activities') -IgnoreExitCode
Save-Text (Join-Path $outputDir 'dumpsys-activity-activities.txt') $topActivity

$windowDump = Invoke-Adb -Adb $adb -PrefixArgs $adbPrefix -AdbArgs @('shell', 'dumpsys', 'window', 'windows') -IgnoreExitCode
Save-Text (Join-Path $outputDir 'dumpsys-window-windows.txt') $windowDump

$packageDump = Invoke-Adb -Adb $adb -PrefixArgs $adbPrefix -AdbArgs @('shell', 'dumpsys', 'package', $PackageName) -IgnoreExitCode
Save-Text (Join-Path $outputDir 'dumpsys-package.txt') $packageDump

Invoke-Adb -Adb $adb -PrefixArgs $adbPrefix -AdbArgs @('shell', 'uiautomator', 'dump', '/sdcard/moviecat-ui.xml') -IgnoreExitCode | Out-Null
$uiXml = Invoke-Adb -Adb $adb -PrefixArgs $adbPrefix -AdbArgs @('shell', 'cat', '/sdcard/moviecat-ui.xml') -IgnoreExitCode
Save-Text (Join-Path $outputDir 'ui.xml') $uiXml

$screenshotPath = Join-Path $outputDir 'screen.png'
Invoke-Adb -Adb $adb -PrefixArgs $adbPrefix -AdbArgs @('shell', 'screencap', '-p', '/sdcard/moviecat-screen.png') -IgnoreExitCode | Out-Null
& $adb @adbPrefix pull /sdcard/moviecat-screen.png $screenshotPath | Out-Null

$logFile = Join-Path $outputDir 'logcat.txt'
$logArgs = @(
    @($adbPrefix)
    'logcat'
    '-v'
    'time'
    '-s'
    'MovieCatNet'
    'MovieCatCatVod'
    'MovieCatQuickJS'
    'MovieCatParser'
    'MovieCatVM'
    'MovieCatPlayer'
    'MovieCatSpider'
    'AndroidRuntime'
    'System.err'
) | ForEach-Object { $_ }

$logProcess = Start-Process -FilePath $adb -ArgumentList $logArgs -RedirectStandardOutput $logFile -RedirectStandardError (Join-Path $outputDir 'logcat-stderr.txt') -PassThru -WindowStyle Hidden
Start-Sleep -Seconds $LogDurationSec
if (!$logProcess.HasExited) {
    Stop-Process -Id $logProcess.Id -Force
}

$recentCrash = Select-String -Path $logFile -Pattern 'FATAL EXCEPTION|AndroidRuntime|Exception|Error|failed|denied' -SimpleMatch:$false | Select-Object -First 80 | ForEach-Object { $_.Line }
Save-Text (Join-Path $outputDir 'logcat-highlights.txt') (($recentCrash | Out-String).Trim())

$summaryText = @(
    "outputDir=$outputDir"
    $summary
    "installResult=$installOutput"
    "startResult=$startOutput"
) -join [Environment]::NewLine
Save-Text (Join-Path $outputDir 'summary.txt') $summaryText

Write-Host "== Done =="
Write-Host "Artifacts: $outputDir"
Write-Host "Log: $logFile"
