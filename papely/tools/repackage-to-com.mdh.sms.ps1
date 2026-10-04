# Repackage AOSP Messaging (_upstream, com.android.messaging) -> com.mdh.sms (Papely)
# Run from Default Project dir. Copies _upstream/src to papely/full-src then rewrites package
# AND moves com/android/messaging -> com/mdh/sms. Idempotent.
$ErrorActionPreference = 'Stop'
$root = 'C:\Users\MDH\Documents\Default Project'
$src = Join-Path $root '_upstream\src'
$dst = Join-Path $root 'papely\full-src'
if (Test-Path $dst) { Remove-Item -Recurse -Force $dst }
New-Item -ItemType Directory -Path $dst | Out-Null
Copy-Item -Recurse -Path (Join-Path $src '*') -Destination $dst

$files = Get-ChildItem $dst -Recurse -Include '*.java','*.xml' -File
$count = 0
foreach ($f in $files) {
  $t = Get-Content $f.FullName -Raw
  $n = $t -replace 'com\.android\.messaging', 'com.mdh.sms'
  if ($n -ne $t) { Set-Content -NoNewline -Path $f.FullName -Value $n; $count++ }
}
$old = Join-Path $dst 'com\android\messaging'
$newParent = Join-Path $dst 'com\mdh\sms'
if (Test-Path $old) {
  New-Item -ItemType Directory -Path $newParent -Force | Out-Null
  Get-ChildItem $old | ForEach-Object { Move-Item $_.FullName (Join-Path $newParent $_.Name) -Force }
  Remove-Item (Join-Path $dst 'com\android') -Recurse -Force
}
$bridge = Join-Path $root 'papely\bridge\PapelyUnknownFilter.java'
$bridgeDst = Join-Path $dst 'com\mdh\sms\util\PapelyUnknownFilter.java'
if (Test-Path $bridge) { Copy-Item $bridge $bridgeDst -Force }
Write-Output "Rewrote $count files -> $dst (com.mdh.sms)"
