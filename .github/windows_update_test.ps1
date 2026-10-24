# Temporary: repeats what Settings → Check for updates does (start the installer, close the app 0.6 s later)
# and checks the files really got replaced, with and without the installer's --updated mode.
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

foreach ($variant in @('', '--updated')) {
  for ($i = 1; $i -le 3; $i++) {
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
    Write-Host ("RESULT args='{0}' try {1}: installer exit={2} files replaced={3} app windows={4} {5}" -f $variant, $i, $(if ($finished) { $p.ExitCode } else { '-' }), $replaced, $wins.Count, $stuck)
  }
}
Get-Process -Name PrivateMuch, PrivateMuch-Setup-test -ErrorAction SilentlyContinue | Stop-Process -Force
