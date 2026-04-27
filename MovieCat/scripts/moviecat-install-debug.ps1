param(
    [string]$Serial,
    [string]$ApkPath = "$(Join-Path $PSScriptRoot '..\app\build\outputs\apk\debug\app-debug.apk')",
    [switch]$Launch
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
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    throw 'adb.exe not found. Please install Android platform-tools or fix ANDROID_SDK_ROOT.'
}

function Invoke-Adb {
    param([string[]]$AdbArgs)
    $adb = Resolve-Adb
    & $adb @AdbArgs
}

function Resolve-TargetArgs {
    param([string]$PreferredSerial)
    $adb = Resolve-Adb
    $deviceLines = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" }
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

$resolvedApkPath = Resolve-Path $ApkPath
$adbArgsPrefix = Resolve-TargetArgs -PreferredSerial $Serial

Write-Host "Using APK: $resolvedApkPath"
Invoke-Adb -AdbArgs ($adbArgsPrefix + @('devices'))
Invoke-Adb -AdbArgs ($adbArgsPrefix + @('wait-for-device'))
Invoke-Adb -AdbArgs ($adbArgsPrefix + @('install', '-r', '-t', $resolvedApkPath))

if ($Launch) {
    Invoke-Adb -AdbArgs ($adbArgsPrefix + @('shell', 'monkey', '-p', 'com.moviecat.app', '-c', 'android.intent.category.LAUNCHER', '1'))
}
