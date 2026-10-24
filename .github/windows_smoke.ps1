# Installs PrivateMuch-Setup.exe on the build machine, starts the app and checks that its window really opens.
param([string]$Dist = 'desktop/dist')
$ErrorActionPreference = 'Continue'

Add-Type @'
using System;
using System.Runtime.InteropServices;
using System.Text;
public static class Win {
  public delegate bool EnumProc(IntPtr h, IntPtr p);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc f, IntPtr p);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
}
'@

function Show-Crashes([datetime]$Since) {
  Get-WinEvent -FilterHashtable @{ LogName = 'Application'; StartTime = $Since } -ErrorAction SilentlyContinue |
    Where-Object { $_.ProviderName -in 'Application Error', 'Windows Error Reporting', '.NET Runtime' } |
    ForEach-Object { Write-Host "[$($_.ProviderName) $($_.Id)] $($_.Message -replace '\s+', ' ')" }
}

# Lists PrivateMuch's windows and returns how many visible "PrivateMuch" windows there are.
function Count-Windows {
  $ids = @(Get-Process -Name PrivateMuch -ErrorAction SilentlyContinue | ForEach-Object { [uint32]$_.Id })
  $script:shown = 0
  [Win]::EnumWindows({
    param($h, $p)
    $wpid = [uint32]0
    [void][Win]::GetWindowThreadProcessId($h, [ref]$wpid)
    if ($ids -contains $wpid) {
      $title = New-Object System.Text.StringBuilder 256
      [void][Win]::GetWindowText($h, $title, 256)
      $visible = [Win]::IsWindowVisible($h)
      if ($visible) { Write-Host "window pid=$wpid title='$title'" }
      if ($visible -and $title.ToString() -eq 'PrivateMuch') { $script:shown++ }
    }
    return $true
  }, [IntPtr]::Zero) | Out-Null
  return $script:shown
}

# Starts the app, waits, and returns $true if a visible "PrivateMuch" window appeared.
function Test-Window([string]$Exe, [string]$Name) {
  Write-Host "==== Starting $Name ($Exe)"
  $log = Join-Path $env:RUNNER_TEMP "$Name-log.txt"
  $since = Get-Date
  $app = Start-Process -FilePath $Exe -ArgumentList '--enable-logging=stderr' -RedirectStandardError $log -PassThru
  Start-Sleep -Seconds 20
  Write-Host "Main process exited: $($app.HasExited) $(if ($app.HasExited) { "(exit code $($app.ExitCode))" })"
  Write-Host "PrivateMuch processes running: $(@(Get-Process -Name PrivateMuch -ErrorAction SilentlyContinue).Count)"
  $shown = Count-Windows
  Write-Host "---- $Name log ----"
  if (Test-Path $log) { Get-Content $log -Tail 60 | Out-Host }
  Show-Crashes $since
  Get-Process -Name PrivateMuch -ErrorAction SilentlyContinue | Stop-Process -Force
  Start-Sleep -Seconds 3
  return ($shown -gt 0)
}

$unpacked = Test-Window (Resolve-Path "$Dist/win-unpacked/PrivateMuch.exe") 'unpacked'
Write-Host "Unpacked app window opened: $unpacked"

# A first install takes a different path from an update, and the installer used to crash there at random,
# so install fresh several times, uninstalling in between.
$dir = Join-Path $env:LOCALAPPDATA 'Programs\privatemuch'
$crashes = 0
for ($i = 1; $i -le 8; $i++) {
  $since = Get-Date
  $setup = Start-Process -FilePath "$Dist/PrivateMuch-Setup.exe" -ArgumentList '/S' -Wait -PassThru
  $ok = $setup.ExitCode -eq 0 -and (Test-Path (Join-Path $dir 'PrivateMuch.exe'))
  Write-Host "Fresh install $i exit code: $($setup.ExitCode) installed: $ok"
  if (-not $ok) { $crashes++; Show-Crashes $since }
  if ($i -lt 8 -and (Test-Path "$dir\Uninstall PrivateMuch.exe")) {
    $u = Start-Process "$dir\Uninstall PrivateMuch.exe" -ArgumentList '/S' -Wait -PassThru
    # the uninstaller hands over to a copy of itself, so wait until the files are really gone
    for ($w = 0; $w -lt 30 -and (Test-Path $dir); $w++) { Start-Sleep -Seconds 1 }
    Start-Sleep -Seconds 2
    Write-Host "  uninstall exit code: $($u.ExitCode) app left: $(Test-Path (Join-Path $dir 'PrivateMuch.exe'))"
  }
  Get-ChildItem 'HKCU:\Software' -ErrorAction SilentlyContinue | Where-Object { (Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue).InstallLocation -like '*privatemuch*' } |
    ForEach-Object { if ($i -lt 8) { Remove-Item $_.PSPath -Recurse -Force } }
}
Write-Host "Installer crashes: $crashes of 8"
if (Test-Path $dir) { Get-ChildItem $dir | ForEach-Object { Write-Host "  $($_.Name) $($_.Length)" } } else { Write-Host "No install folder at $dir" }

$installed = $false
$exe = Join-Path $dir 'PrivateMuch.exe'
if (Test-Path $exe) { $installed = Test-Window $exe 'installed' }
Write-Host "Installed app window opened: $installed"

# Opening it again while it's open brings the same window to the front instead of starting a second copy.
$single = $false
if ($installed) {
  Write-Host '==== Opening it twice'
  Start-Process $exe
  Start-Sleep -Seconds 15
  Start-Process $exe
  Start-Sleep -Seconds 10
  $n = Count-Windows
  Write-Host "Visible PrivateMuch windows after opening it twice: $n"
  $single = $n -eq 1
  Get-Process -Name PrivateMuch -ErrorAction SilentlyContinue | Stop-Process -Force
}

if ($crashes -gt 0) { Write-Host 'FAILED: the installer crashed'; exit 1 }
if (-not ($unpacked -and $installed)) { Write-Host 'FAILED: the PrivateMuch window did not open'; exit 1 }
if (-not $single) { Write-Host 'FAILED: opening it twice did not leave exactly one window'; exit 1 }
Write-Host 'The PrivateMuch window opened'
