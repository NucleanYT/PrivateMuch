# Does what Settings → Check for updates does (start the installer, close the app 0.6 s later) and checks the
# installer really replaced the app's files and opened it again.
param([string]$Dist = 'desktop/dist')
$ErrorActionPreference = 'Continue'

Add-Type @'
using System;
using System.Runtime.InteropServices;
using System.Text;
public static class Upd {
  public delegate bool EnumProc(IntPtr h, IntPtr p);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc f, IntPtr p);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
}
'@

$dir = Join-Path $env:LOCALAPPDATA 'Programs\privatemuch'
$exe = Join-Path $dir 'PrivateMuch.exe'
$marker = Join-Path $dir 'LICENSE.electron.txt'
$original = Get-Content $marker -Raw
$setup = Join-Path $env:TEMP 'PrivateMuch-Setup-test.exe'
Copy-Item "$Dist/PrivateMuch-Setup.exe" $setup -Force

function Windows-Of([string[]]$Names) {
  $ids = @(Get-Process -Name $Names -ErrorAction SilentlyContinue | ForEach-Object { [uint32]$_.Id })
  $script:list = New-Object System.Collections.ArrayList
  [Upd]::EnumWindows({
    param($h, $p)
    $wpid = [uint32]0
    [void][Upd]::GetWindowThreadProcessId($h, [ref]$wpid)
    if ($ids -contains $wpid -and [Upd]::IsWindowVisible($h)) {
      $t = New-Object System.Text.StringBuilder 256
      [void][Upd]::GetWindowText($h, $t, 256)
      [void]$script:list.Add("$wpid '$t'")
    }
    return $true
  }, [IntPtr]::Zero) | Out-Null
  return $script:list
}

$failed = 0
foreach ($variant in @('')) {
  for ($i = 1; $i -le 2; $i++) {
    Get-Process -Name PrivateMuch, PrivateMuch-Setup-test -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 2
    Set-Content $marker 'old version' -NoNewline
    Start-Process $exe
    Start-Sleep -Seconds 12
    $p = if ($variant) { Start-Process $setup -ArgumentList $variant -PassThru } else { Start-Process $setup -PassThru }
    Start-Sleep -Milliseconds 600
    Get-Process -Name PrivateMuch -ErrorAction SilentlyContinue | ForEach-Object { [void]$_.CloseMainWindow() }
    $finished = $p.WaitForExit(60000)
    $stuck = if ($finished) { '' } else { 'installer still open: ' + ((Windows-Of 'PrivateMuch-Setup-test') -join ', ') }
    Start-Sleep -Seconds 15
    $replaced = (Get-Content $marker -Raw -ErrorAction SilentlyContinue) -eq $original
    $wins = Windows-Of 'PrivateMuch'
    Write-Host ("Update {0}: installer exit={1} files replaced={2} app windows={3} {4}" -f $i, $(if ($finished) { $p.ExitCode } else { '-' }), $replaced, $wins.Count, $stuck)
    if (-not ($finished -and $replaced -and $wins.Count -eq 1)) { $failed++ }
  }
}
Get-Process -Name PrivateMuch, PrivateMuch-Setup-test -ErrorAction SilentlyContinue | Stop-Process -Force
if ($failed) { Write-Host "FAILED: $failed of 2 updates"; exit 1 }
Write-Host 'Updating over the running app works'
