param(
  [Parameter(Mandatory = $true)]
  [string]$PublicIp,

  [string]$PrivateIp,

  [string]$File = "server.properties"
)

if (-not (Test-Path -LiteralPath $File)) {
  Write-Error "File not found: $File"
  exit 1
}

$content = [System.IO.File]::ReadAllText($File)

$updated = [regex]::Replace(
  $content,
  '(^advertised\.listeners=.*?EXTERNAL://)([^:,\s]+)(:\d+)',
  { param($m) $m.Groups[1].Value + $PublicIp + $m.Groups[3].Value },
  [System.Text.RegularExpressions.RegexOptions]::Multiline
)

if ($PrivateIp) {
  $updated = [regex]::Replace(
    $updated,
    '(^advertised\.listeners=.*?INTERNAL://)([^:,\s]+)(:\d+)',
    { param($m) $m.Groups[1].Value + $PrivateIp + $m.Groups[3].Value },
    [System.Text.RegularExpressions.RegexOptions]::Multiline
  )

  $updated = [regex]::Replace(
    $updated,
    '(^controller\.quorum\.voters=.*?@)([^:,\s]+)(:\d+)',
    { param($m) $m.Groups[1].Value + $PrivateIp + $m.Groups[3].Value },
    [System.Text.RegularExpressions.RegexOptions]::Multiline
  )
}

if ($updated -eq $content) {
  Write-Warning "No changes applied. Check patterns or input."
  exit 0
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($File, $updated, $utf8NoBom)

Write-Host "Updated: $File"
Write-Host "Public IP -> $PublicIp"
if ($PrivateIp) {
  Write-Host "Private IP -> $PrivateIp"
}
