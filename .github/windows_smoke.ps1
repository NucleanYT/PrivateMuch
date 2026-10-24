# Installs PrivateMuch-Setup.exe on the build machine, starts the app and checks that its window really opens.
param([string]$Installer = 'desktop/dist/PrivateMuch-Setup.exe')
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
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  public struct RECT { public int Left, Top, Right, Bottom; }
}
'@

Write-Host "Installing $Installer"
$setup = Start-Process -FilePath $Installer -ArgumentList '/S' -Wait -PassThru
Write-Host "Installer exit code: $($setup.ExitCode)"
$exe = Join-Path $env:LOCALAPPDATA 'Programs\privatemuch\PrivateMuch.exe'
if (-not (Test-Path $exe)) { Write-Host "Not installed at $exe"; exit 1 }

$log = Join-Path $env:RUNNER_TEMP 'privatemuch-log.txt'
$app = Start-Process -FilePath $exe -ArgumentList '--enable-logging=stderr' -RedirectStandardError $log -PassThru
Start-Sleep -Seconds 25

$procs = @(Get-Process -Name PrivateMuch -ErrorAction SilentlyContinue)
Write-Host "Main process exited: $($app.HasExited) (exit code: $(if ($app.HasExited) { $app.ExitCode } else { '-' }))"
Write-Host "PrivateMuch processes running: $($procs.Count)"
$ids = $procs | ForEach-Object { [uint32]$_.Id }

$visible = 0
$found = New-Object System.Collections.ArrayList
[Win]::EnumWindows({
  param($h, $p)
  $wpid = [uint32]0
  [void][Win]::GetWindowThreadProcessId($h, [ref]$wpid)
  if ($ids -contains $wpid) {
    $title = New-Object System.Text.StringBuilder 256
    [void][Win]::GetWindowText($h, $title, 256)
    $r = New-Object Win+RECT
    [void][Win]::GetWindowRect($h, [ref]$r)
    $shown = [Win]::IsWindowVisible($h)
    if ($title.Length -gt 0 -or $shown) {
      [void]$found.Add("window pid=$wpid visible=$shown title='$title' at $($r.Left),$($r.Top) size $($r.Right - $r.Left)x$($r.Bottom - $r.Top)")
    }
    if ($shown -and $title.ToString() -eq 'PrivateMuch') { $script:visible++ }
  }
  return $true
}, [IntPtr]::Zero) | Out-Null
$found | ForEach-Object { Write-Host $_ }

Write-Host '---- app log ----'
if (Test-Path $log) { Get-Content $log -Tail 80 }

$procs | Stop-Process -Force -ErrorAction SilentlyContinue
if ($visible -lt 1) { Write-Host 'FAILED: the PrivateMuch window did not open'; exit 1 }
Write-Host 'The PrivateMuch window opened'
