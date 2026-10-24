# Clicks PrivateMuch's own minimize / maximize / close buttons with the real mouse, on the top bar and with the
# Private tabs screen open. (Windows can turn a click into a window drag before the page sees it.)
param([string]$Exe = (Join-Path $env:LOCALAPPDATA 'Programs\privatemuch\PrivateMuch.exe'))
$ErrorActionPreference = 'Continue'

Add-Type @'
using System;
using System.Runtime.InteropServices;
using System.Text;
public static class Mouse {
  public delegate bool EnumProc(IntPtr h, IntPtr p);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc f, IntPtr p);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] public static extern bool IsZoomed(IntPtr h);
  [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
  [DllImport("user32.dll")] public static extern uint GetDpiForWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint flags, int dx, int dy, uint data, UIntPtr extra);
}
'@

# Positions of the buttons in the top bar, in page pixels from the window's right edge (they don't depend on its width).
$TABS = 240; $MINIMIZE = 107; $MAXIMIZE = 67; $CLOSE = 27; $Y = 26

function Find-Window {
  $ids = @(Get-Process -Name PrivateMuch -ErrorAction SilentlyContinue | ForEach-Object { [uint32]$_.Id })
  $script:found = [IntPtr]::Zero
  [Mouse]::EnumWindows({
    param($h, $p)
    $wpid = [uint32]0
    [void][Mouse]::GetWindowThreadProcessId($h, [ref]$wpid)
    $t = New-Object System.Text.StringBuilder 256
    [void][Mouse]::GetWindowText($h, $t, 256)
    if ($ids -contains $wpid -and [Mouse]::IsWindowVisible($h) -and $t.ToString() -eq 'PrivateMuch') { $script:found = $h }
    return $true
  }, [IntPtr]::Zero) | Out-Null
  return $script:found
}

function Click([IntPtr]$h, [int]$FromRight) {
  $scale = [Mouse]::GetDpiForWindow($h) / 96
  $r = New-Object Mouse+RECT
  [void][Mouse]::GetClientRect($h, [ref]$r)
  $origin = New-Object Mouse+POINT
  [void][Mouse]::ClientToScreen($h, [ref]$origin)
  $x = $origin.X + $r.Right - [int]($FromRight * $scale)
  $y = $origin.Y + [int]($Y * $scale)
  [void][Mouse]::SetForegroundWindow($h)
  [void][Mouse]::SetCursorPos($x, $y)
  Start-Sleep -Milliseconds 400
  [Mouse]::mouse_event(0x2, 0, 0, 0, [UIntPtr]::Zero)  # left button down
  Start-Sleep -Milliseconds 80
  [Mouse]::mouse_event(0x4, 0, 0, 0, [UIntPtr]::Zero)  # left button up
  Start-Sleep -Milliseconds 1500
}

$failed = 0
function Check([string]$What, [bool]$Ok) {
  Write-Host ("{0} {1}" -f $(if ($Ok) { 'ok  ' } else { 'FAIL' }), $What)
  if (-not $Ok) { $script:failed++ }
}

Get-Process -Name PrivateMuch -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2
$app = Start-Process $Exe -PassThru
Start-Sleep -Seconds 15
$h = Find-Window
if ($h -eq [IntPtr]::Zero) { Write-Host 'FAIL no PrivateMuch window'; exit 1 }
Write-Host "Window found, DPI $([Mouse]::GetDpiForWindow($h))"

foreach ($where in 'top bar', 'Private tabs screen') {
  if ($where -ne 'top bar') { Click $h $TABS }
  Click $h $MAXIMIZE
  Check "maximize on the $where" ([Mouse]::IsZoomed($h))
  Click $h $MAXIMIZE
  Check "restore on the $where" (-not [Mouse]::IsZoomed($h))
  Click $h $MINIMIZE
  Check "minimize on the $where" ([Mouse]::IsIconic($h))
  [void][Mouse]::ShowWindow($h, 9)  # restore it for the next check
  Start-Sleep -Seconds 1
}
Click $h $CLOSE
Check 'close on the Private tabs screen' ($app.WaitForExit(15000))

Get-Process -Name PrivateMuch -ErrorAction SilentlyContinue | Stop-Process -Force
if ($failed) { Write-Host "FAILED: $failed button checks"; exit 1 }
Write-Host 'All window buttons work with the real mouse'
