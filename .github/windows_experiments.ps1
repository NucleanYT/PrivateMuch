# Temporary: tries the ways a person really installs and opens PrivateMuch, to find why it may not open.
param([string]$Dist = 'desktop/dist')
$ErrorActionPreference = 'Continue'

Add-Type @'
using System;
using System.Runtime.InteropServices;
using System.Text;
public static class Win2 {
  public delegate bool EnumProc(IntPtr h, IntPtr p);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc f, IntPtr p);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
}
'@

$dir = Join-Path $env:LOCALAPPDATA 'Programs\privatemuch'
$exe = Join-Path $dir 'PrivateMuch.exe'

function Count-Windows {
  $ids = @(Get-Process -Name PrivateMuch -ErrorAction SilentlyContinue | ForEach-Object { [uint32]$_.Id })
  $script:n = 0
  [Win2]::EnumWindows({
    param($h, $p)
    $wpid = [uint32]0
    [void][Win2]::GetWindowThreadProcessId($h, [ref]$wpid)
    if ($ids -contains $wpid -and [Win2]::IsWindowVisible($h)) {
      $t = New-Object System.Text.StringBuilder 256
      [void][Win2]::GetWindowText($h, $t, 256)
      if ($t.ToString() -eq 'PrivateMuch') { $script:n++ }
    }
    return $true
  }, [IntPtr]::Zero) | Out-Null
  return $script:n
}
function Stop-App { Get-Process -Name PrivateMuch -ErrorAction SilentlyContinue | Stop-Process -Force; Start-Sleep 3 }
function Crashes([datetime]$Since) {
  Get-WinEvent -FilterHashtable @{ LogName = 'Application'; StartTime = $Since } -ErrorAction SilentlyContinue |
    Where-Object { $_.ProviderName -in 'Application Error', 'Windows Error Reporting' } |
    ForEach-Object { Write-Host "    [$($_.ProviderName) $($_.Id)] $(($_.Message -replace '\s+', ' ').Substring(0, [Math]::Min(400, ($_.Message -replace '\s+', ' ').Length)))" }
}
function Report([string]$What) {
  $p = @(Get-Process -Name PrivateMuch -ErrorAction SilentlyContinue).Count
  Write-Host ("RESULT {0}: processes={1} visible windows={2}" -f $What, $p, (Count-Windows))
}

Write-Host '== A. Installing 6 times in a row (fresh, then over the top, then after uninstalling)'
for ($i = 1; $i -le 6; $i++) {
  if ($i -eq 4 -and (Test-Path "$dir\Uninstall PrivateMuch.exe")) {
    $u = Start-Process "$dir\Uninstall PrivateMuch.exe" -ArgumentList '/S' -Wait -PassThru
    Start-Sleep 5
    Write-Host "  uninstall exit=$($u.ExitCode) folder left=$(Test-Path $exe)"
  }
  $since = Get-Date
  $s = Start-Process "$Dist/PrivateMuch-Setup.exe" -ArgumentList '/S' -Wait -PassThru
  Write-Host "  install $i exit=$($s.ExitCode) exe=$(Test-Path $exe)"
  Crashes $since
}

Write-Host '== B. Normal install (no /S), which opens the app when it finishes'
Stop-App
$since = Get-Date
# not -Wait: that also waits for the app the installer opens
$s = Start-Process "$Dist/PrivateMuch-Setup.exe" -PassThru
[void]$s.WaitForExit(120000)
Write-Host "  install exit=$($s.ExitCode)"
Start-Sleep 20
Report 'after normal install'
Crashes $since

Write-Host '== C. Opening a second time while it is open'
Start-Process $exe
Start-Sleep 15
Report 'second launch'
Stop-App

Write-Host '== D. Start menu shortcut'
$lnk = Get-ChildItem "$env:APPDATA\Microsoft\Windows\Start Menu\Programs" -Recurse -Filter 'PrivateMuch*.lnk' | Select-Object -First 1
$desk = Get-ChildItem ([Environment]::GetFolderPath('Desktop')) -Filter 'PrivateMuch*.lnk' -ErrorAction SilentlyContinue | Select-Object -First 1
Write-Host "  start menu: $($lnk.FullName)  desktop: $($desk.FullName)"
if ($lnk) { Start-Process $lnk.FullName; Start-Sleep 20; Report 'start menu shortcut' }

Write-Host '== E. Close normally, reopen 1 second later'
Get-Process -Name PrivateMuch -ErrorAction SilentlyContinue | ForEach-Object { [void]$_.CloseMainWindow() }
Start-Sleep 1
Start-Process $exe
Start-Sleep 20
Report 'reopen after close'
Start-Sleep 5
Report 'reopen after close, 5s later'
Stop-App

Write-Host '== F. Temp folder with Greek letters and spaces (like a Greek Windows user name)'
$greek = Join-Path $env:LOCALAPPDATA "Temp\Νίκος Παπαδόπουλος"
New-Item -ItemType Directory -Force $greek | Out-Null
$short = (cmd /c "for %I in (`"$greek`") do @echo %~sI")
Write-Host "  long: $greek  short: $short"
foreach ($t in @($greek, $short)) {
  $since = Get-Date
  $psi = New-Object System.Diagnostics.ProcessStartInfo $exe
  $psi.UseShellExecute = $false
  $psi.EnvironmentVariables['TEMP'] = $t
  $psi.EnvironmentVariables['TMP'] = $t
  $psi.RedirectStandardError = $true
  $psi.Arguments = '--enable-logging=stderr'
  $proc = [System.Diagnostics.Process]::Start($psi)
  Start-Sleep 20
  Report "TEMP=$t"
  Write-Host "  exited=$($proc.HasExited) $(if ($proc.HasExited) { "code=$($proc.ExitCode)"; $proc.StandardError.ReadToEnd() })"
  Crashes $since
  Write-Host "  profile folders: $((Get-ChildItem $t -Filter 'PrivateMuch-profile-*' -ErrorAction SilentlyContinue).Count)"
  Stop-App
}

Write-Host '== G. Data folder: what the app keeps'
Get-ChildItem "$env:APPDATA\PrivateMuch" -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "  $($_.Name) $($_.Length)" }
Write-Host "  leftover profiles in TEMP: $((Get-ChildItem $env:TEMP -Filter 'PrivateMuch-profile-*' -ErrorAction SilentlyContinue).Count)"
