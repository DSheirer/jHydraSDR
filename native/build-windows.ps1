# Builds hydrasdr.dll for the host architecture with MSVC and copies it, with
# the libusb DLL, to dist\native\windows-<arch>.
#
# Usage: native\build-windows.ps1 -Arch x64|ARM64 [-Ref <hydrasdr-git-ref>]
#
# Windows uses the prebuilt libusb binaries published by the libusb project.
# pthreads4w and the C runtime are linked statically by the upstream CMake build.
param(
    [Parameter(Mandatory = $true)][ValidateSet('x64', 'ARM64')][string]$Arch,
    [string]$Ref = ''
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true

$Root = Split-Path -Parent $PSScriptRoot
$Versions = @{}
Get-Content "$Root\native\versions.env" | Where-Object { $_ -match '^[A-Z0-9_]+=' } | ForEach-Object {
    $Key, $Value = $_ -split '=', 2
    $Versions[$Key] = $Value
}
if (-not $Ref) { $Ref = $Versions.HYDRASDR_REF }
$LibusbVersion = $Versions.LIBUSB_VERSION

if ($Arch -eq 'x64') {
    $Platform = 'windows-x86_64'
    $UsbDir = 'VS2022\MS64\dll'
} else {
    $Platform = 'windows-aarch64'
    $UsbDir = 'VS2025\ARM64\dll'
}
$Out = "$Root\dist\native\$Platform"
$Licenses = "$Root\dist\native\licenses"
$Work = "$Root\build\native-work"
Remove-Item -Recurse -Force $Work, $Out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Work, $Out, $Licenses | Out-Null
Set-Location $Work

Write-Host "=== libusb $LibusbVersion ==="
$Archive = "libusb-$LibusbVersion.7z"
Invoke-WebRequest "https://github.com/libusb/libusb/releases/download/v$LibusbVersion/$Archive" -OutFile $Archive
$Hash = (Get-FileHash $Archive -Algorithm SHA256).Hash
if ($Hash -ine $Versions.LIBUSB_WINDOWS_7Z_SHA256) {
    throw "libusb archive checksum mismatch: $Hash"
}
7z x $Archive -ousb | Out-Null

Write-Host "=== libhydrasdr $Ref ==="
git clone --quiet --depth 1 --branch $Ref $Versions.HYDRASDR_REPOSITORY hydrasdr
$Commit = (git -C hydrasdr rev-parse HEAD).Trim()

cmake -S hydrasdr -B build -A $Arch `
    "-DLIBUSB_INCLUDE_DIR=$Work\usb\include" `
    "-DLIBUSB_LIBRARIES=$Work\usb\$UsbDir\libusb-1.0.lib" `
    "-DLIBUSB_DLL=$Work\usb\$UsbDir\libusb-1.0.dll"
cmake --build build --config Release --parallel --target hydrasdr

Write-Host "=== packaging $Platform ==="
$Dll = Get-ChildItem -Recurse -Path build -Filter hydrasdr.dll | Select-Object -First 1
if (-not $Dll) { throw 'hydrasdr.dll was not produced' }
Copy-Item $Dll.FullName "$Out\hydrasdr.dll"
Copy-Item "$Work\usb\$UsbDir\libusb-1.0.dll" "$Out\libusb-1.0.dll"
Copy-Item 'hydrasdr\libhydrasdr\LICENSE.md' "$Licenses\libhydrasdr-LICENSE.md"
@(
    "hydrasdr.ref=$Ref",
    "hydrasdr.commit=$Commit",
    "libusb.version=$LibusbVersion"
) | Set-Content -Encoding ascii "$Out\build-info.properties"
Get-ChildItem $Out
