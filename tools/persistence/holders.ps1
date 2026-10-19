# Polls the Windows Restart Manager for processes holding a file open (read-only query;
# changes nothing). Usage: holders.ps1 <path> <seconds>
param([string]$Path, [int]$Seconds = 20)
Add-Type -TypeDefinition @"
using System; using System.Runtime.InteropServices; using System.Collections.Generic;
public static class Rm {
  [StructLayout(LayoutKind.Sequential)] struct UNIQUE { public int pid; public System.Runtime.InteropServices.ComTypes.FILETIME t; }
  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)] struct INFO {
    public UNIQUE p; [MarshalAs(UnmanagedType.ByValTStr, SizeConst=256)] public string app;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst=64)] public string svc; public int type; public uint status; public uint sess;
    [MarshalAs(UnmanagedType.Bool)] public bool restartable; }
  [DllImport("rstrtmgr.dll", CharSet=CharSet.Unicode)] static extern int RmStartSession(out uint h, int f, string k);
  [DllImport("rstrtmgr.dll")] static extern int RmEndSession(uint h);
  [DllImport("rstrtmgr.dll", CharSet=CharSet.Unicode)] static extern int RmRegisterResources(uint h, uint n, string[] f, uint a, IntPtr b, uint c, string[] d);
  [DllImport("rstrtmgr.dll")] static extern int RmGetList(uint h, out uint need, ref uint n, [In, Out] INFO[] i, ref uint r);
  public static List<string> Who(string path) {
    var res = new List<string>(); uint h; if (RmStartSession(out h, 0, Guid.NewGuid().ToString()) != 0) return res;
    try { RmRegisterResources(h, 1, new[]{path}, 0, IntPtr.Zero, 0, null);
      uint need = 0, n = 0, r = 0; int rc = RmGetList(h, out need, ref n, null, ref r);
      if (rc == 234 && need > 0) { var arr = new INFO[need]; n = need; if (RmGetList(h, out need, ref n, arr, ref r) == 0)
        for (int i = 0; i < n; i++) res.Add(arr[i].p.pid + ":" + arr[i].app + ":" + arr[i].svc); } }
    finally { RmEndSession(h); } return res; }
}
"@
$seen = @{}
$end = (Get-Date).AddSeconds($Seconds)
while ((Get-Date) -lt $end) {
  foreach ($w in [Rm]::Who($Path)) { if (-not $seen.ContainsKey($w)) { $seen[$w] = 0 }; $seen[$w]++ }
}
$seen.GetEnumerator() | Sort-Object Value -Descending | ForEach-Object { "{0,6} x {1}" -f $_.Value, $_.Key }
