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

# Starts the app, waits, and returns $true if a visible "PrivateMuch" window appeared.
function Test-Window([string]$Exe, [string]$Name) {
  Write-Host "==== Starting $Name ($Exe)"
  $log = Join-Path $env:RUNNER_TEMP "$Name-log.txt"
  $since = Get-Date
  $app = Start-Process -FilePath $Exe -ArgumentList '--enable-logging=stderr' -RedirectStandardError $log -PassThru
  Start-Sleep -Seconds 20
  $procs = @(Get-Process -Name PrivateMuch -ErrorAction SilentlyContinue)
  Write-Host "Main process exited: $($app.HasExited) $(if ($app.HasExited) { "(exit code $($app.ExitCode))" })"
  Write-Host "PrivateMuch processes running: $($procs.Count)"
  $ids = $procs | ForEach-Object { [uint32]$_.Id }
  $script:shown = 0
  [Win]::EnumWindows({
    param($h, $p)
    $wpid = [uint32]0
    [void][Win]::GetWindowThreadProcessId($h, [ref]$wpid)
    if ($ids -contains $wpid) {
      $title = New-Object System.Text.StringBuilder 256
      [void][Win]::GetWindowText($h, $title, 256)
      $visible = [Win]::IsWindowVisible($h)
      if ($title.Length -gt 0 -or $visible) { Write-Host "window pid=$wpid visible=$visible title='$title'" }
      if ($visible -and $title.ToString() -eq 'PrivateMuch') { $script:shown++ }
    }
    return $true
  }, [IntPtr]::Zero) | Out-Null
  Write-Host "---- $Name log ----"
  if (Test-Path $log) { Get-Content $log -Tail 60 | Out-Host }
  Show-Crashes $since
  $procs | Stop-Process -Force -ErrorAction SilentlyContinue
  Start-Sleep -Seconds 3
  return ($script:shown -gt 0)
}

$unpacked = Test-Window (Resolve-Path "$Dist/win-unpacked/PrivateMuch.exe") 'unpacked'
Write-Host "Unpacked app window opened: $unpacked"

Write-Host '==== Installing'
$since = Get-Date
$setup = Start-Process -FilePath "$Dist/PrivateMuch-Setup.exe" -ArgumentList '/S' -Wait -PassThru
Write-Host "Installer exit code: $($setup.ExitCode)"
Show-Crashes $since
$dir = Join-Path $env:LOCALAPPDATA 'Programs\privatemuch'
if (Test-Path $dir) { Get-ChildItem $dir | ForEach-Object { Write-Host "  $($_.Name) $($_.Length)" } } else { Write-Host "No install folder at $dir" }

$installed = $false
$exe = Join-Path $dir 'PrivateMuch.exe'
if (Test-Path $exe) { $installed = Test-Window $exe 'installed' }
Write-Host "Installed app window opened: $installed"

if (-not ($unpacked -and $installed)) { Write-Host 'FAILED: the PrivateMuch window did not open'; exit 1 }
Write-Host 'The PrivateMuch window opened'
