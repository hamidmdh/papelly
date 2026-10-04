# PowerShell port of UnknownSenderFilter.kt — executable spec.
# Mirrors normalize/addressesMatch/classify 1:1 so logic can be verified without a JDK.
$ErrorActionPreference = 'Stop'

function Normalize-Address([string]$a) {
  if ([string]::IsNullOrEmpty($a)) { return '' }
  $t = $a.Trim().ToLower()
  $sb = New-Object System.Text.StringBuilder
  for ($i = 0; $i -lt $t.Length; $i++) {
    $c = $t[$i]
    if ([char]::IsLetterOrDigit($c) -or ($c -eq '+' -and $i -eq 0)) { [void]$sb.Append($c) }
  }
  return $sb.ToString()
}

function Test-AddressesMatch([string]$a, [string]$b) {
  $na = Normalize-Address $a; $nb = Normalize-Address $b
  if ([string]::IsNullOrEmpty($na) -or [string]::IsNullOrEmpty($nb)) { return $false }
  if ($na -eq $nb) { return $true }
  $da = ($na.ToCharArray() | Where-Object { [char]::IsDigit($_) }) -join ''
  $db = ($nb.ToCharArray() | Where-Object { [char]::IsDigit($_) }) -join ''
  if ([string]::IsNullOrEmpty($da) -or [string]::IsNullOrEmpty($db)) { return $false }
  $tailA = if ($da.Length -gt 10) { $da.Substring($da.Length - 10) } else { $da }
  $tailB = if ($db.Length -gt 10) { $db.Substring($db.Length - 10) } else { $db }
  return ($tailA.Length -ge 7 -and $tailA -eq $tailB)
}

function Test-Marketing([string]$body) {
  if ([string]::IsNullOrEmpty($body)) { return $false }
  $l = $body.ToLower()
  if ($l.Contains('http://') -or $l.Contains('https://') -or $l.Contains('bit.ly')) { return $true }
  $kws = @('offer','discount','sale','promo','coupon','cashback','limited time','buy now','free','prize','winner','congratulations','loan','credit card')
  foreach ($k in $kws) { if ($l.Contains($k)) { return $true } }
  return $false
}

function Test-Otp([string]$body) {
  if ([string]::IsNullOrEmpty($body)) { return $false }
  return ($body -match '(?i)\b(otp|passcode|verification|verify|code)\b') -and ($body -match '\b\d{4,8}\b')
}

function Invoke-Classify([string]$sender, [string]$body, [bool]$known, [hashtable]$prefs) {
  $marketing = Test-Marketing $body
  if (-not $prefs.filterUnknownEnabled) { return @{folder='INBOX';notify=$true;marketing=$marketing;reason='filter disabled'} }
  if ($known) { return @{folder='INBOX';notify=$true;marketing=$marketing;reason='known contact'} }
  $norm = Normalize-Address $sender
  if ($norm -ne '' -and $prefs.allowlist -contains $norm) { return @{folder='INBOX';notify=$true;marketing=$marketing;reason='allowlist'} }
  if ($prefs.notifyOtp -and (Test-Otp $body)) { return @{folder='UNKNOWN';notify=$true;marketing=$marketing;reason='otp bypass'} }
  $reason = if ($marketing) { 'unknown sender + marketing signals' } else { 'unknown sender' }
  return @{folder='UNKNOWN';notify=$false;marketing=$marketing;reason=$reason}
}

$fail = 0
function Check($name, $actual, $expected) {
  if ($actual -ne $expected) { Write-Host "FAIL $name : expected=$expected actual=$actual"; $script:fail++ }
  else { Write-Host "PASS $name" }
}

# --- normalize / match ---
Check 'norm-plus' (Normalize-Address '+1 (415) 555-1234') '+14155551234'
Check 'match-cc' (Test-AddressesMatch '+14155551234' '4155551234') $true
Check 'match-exact' (Test-AddressesMatch 'AD-HDFCBK' 'ad-hdfcbk') $true
Check 'nomatch-alpha-vs-num' (Test-AddressesMatch 'AD-HDFCBK' '4155551234') $false
Check 'nomatch-empty' (Test-AddressesMatch '' '123') $false

# --- classify: spec cases ---
$p = @{filterUnknownEnabled=$true; notifyOtp=$false; allowlist=@()}
$r = Invoke-Classify '+14155551234' 'hey, dinner tonight?' $true $p
Check 'known->inbox' $r.folder 'INBOX'; Check 'known->notify' $r.notify $true

$r = Invoke-Classify 'AD-HDFCBK' '50% OFF sale! Buy now http://bit.ly/x' $false $p
Check 'marketing-unknown->unknown' $r.folder 'UNKNOWN'; Check 'marketing-unknown->silent' $r.notify $false; Check 'marketing-flag' $r.marketing $true

$r = Invoke-Classify '+19998887777' 'hello, is this available?' $false $p
Check 'plain-unknown->unknown' $r.folder 'UNKNOWN'; Check 'plain-unknown->silent' $r.notify $false

$r = Invoke-Classify '+19998887777' 'Your OTP is 482913' $false $p
Check 'otp-strict->silent' $r.notify $false
$p2 = @{filterUnknownEnabled=$true; notifyOtp=$true; allowlist=@()}
$r = Invoke-Classify '+19998887777' 'Your OTP is 482913' $false $p2
Check 'otp-bypass->notify' $r.notify $true; Check 'otp-bypass-stays-unknown' $r.folder 'UNKNOWN'

$p3 = @{filterUnknownEnabled=$true; notifyOtp=$false; allowlist=@('+19998887777')}
$r = Invoke-Classify '+1-999-888-7777' 'delivery tomorrow' $false $p3
Check 'allowlist->inbox' $r.folder 'INBOX'; Check 'allowlist->notify' $r.notify $true

$p4 = @{filterUnknownEnabled=$false; notifyOtp=$false; allowlist=@()}
$r = Invoke-Classify 'AD-HDFCBK' 'SALE' $false $p4
Check 'disabled->inbox' $r.folder 'INBOX'; Check 'disabled->notify' $r.notify $true

if ($fail -gt 0) { Write-Host "$fail FAILURES"; exit 1 } else { Write-Host 'ALL TESTS PASSED' }
