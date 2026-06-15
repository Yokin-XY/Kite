param(
    [string]$Distro = "Ubuntu-24.04",
    [string]$Proxy = "http://127.0.0.1:7897",
    [switch]$NoShutdown
)

$ErrorActionPreference = "Stop"

function Set-IniValue {
    param(
        [System.Collections.Generic.List[string]]$Lines,
        [string]$Section,
        [string]$Key,
        [string]$Value
    )

    $sectionPattern = "^\s*\[$([regex]::Escape($Section))\]\s*$"
    $anySectionPattern = "^\s*\[.+\]\s*$"
    $keyPattern = "^\s*$([regex]::Escape($Key))\s*="
    $sectionIndex = -1

    for ($i = 0; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i] -match $sectionPattern) {
            $sectionIndex = $i
            break
        }
    }

    if ($sectionIndex -lt 0) {
        if ($Lines.Count -gt 0 -and $Lines[$Lines.Count - 1].Trim() -ne "") {
            $Lines.Add("")
        }
        $Lines.Add("[$Section]")
        $Lines.Add("$Key=$Value")
        return
    }

    $insertIndex = $Lines.Count
    for ($i = $sectionIndex + 1; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i] -match $anySectionPattern) {
            $insertIndex = $i
            break
        }
        if ($Lines[$i] -match $keyPattern) {
            $Lines[$i] = "$Key=$Value"
            return
        }
    }

    $Lines.Insert($insertIndex, "$Key=$Value")
}

$wslConfigPath = Join-Path $env:USERPROFILE ".wslconfig"
$lines = [System.Collections.Generic.List[string]]::new()
if (Test-Path $wslConfigPath) {
    $existing = Get-Content -Path $wslConfigPath
    foreach ($line in $existing) {
        $lines.Add($line)
    }
    $backup = "$wslConfigPath.kf-backup-$(Get-Date -Format yyyyMMddHHmmss)"
    Copy-Item -LiteralPath $wslConfigPath -Destination $backup -Force
    Write-Host "Backed up existing .wslconfig to: $backup"
}

Set-IniValue -Lines $lines -Section "wsl2" -Key "networkingMode" -Value "mirrored"
Set-IniValue -Lines $lines -Section "wsl2" -Key "autoProxy" -Value "true"
Set-IniValue -Lines $lines -Section "wsl2" -Key "dnsTunneling" -Value "true"
Set-IniValue -Lines $lines -Section "wsl2" -Key "firewall" -Value "true"

Set-Content -Path $wslConfigPath -Value ($lines -join [Environment]::NewLine) -Encoding ascii
Write-Host "Updated WSL host-network config: $wslConfigPath"

$escapedProxy = $Proxy -replace "'", "'\''"
$bash = @'
set -euo pipefail
proxy='__KF_PROXY__'
default_user="$(awk -F= '
  /^\[user\]/{in_user=1; next}
  /^\[/{in_user=0}
  in_user && /^default=/{print $2; exit}
' /etc/wsl.conf 2>/dev/null || true)"
if [ -z "$default_user" ]; then
  default_user="$(getent passwd 1000 | cut -d: -f1 || true)"
fi
if [ -z "$default_user" ]; then
  echo "cannot detect default WSL user" >&2
  exit 1
fi

printf '%s ALL=(ALL) NOPASSWD:ALL\n' "$default_user" > /etc/sudoers.d/99-kf-wsl-nopasswd
chmod 0440 /etc/sudoers.d/99-kf-wsl-nopasswd
visudo -cf /etc/sudoers.d/99-kf-wsl-nopasswd >/dev/null

cat > /usr/local/bin/kf-host-proxy-detect <<'SH'
#!/usr/bin/env sh
set -eu
for proxy in ${KF_HOST_PROXY:-} __KF_PROXY__ http://127.0.0.1:7897; do
  [ -n "$proxy" ] || continue
  endpoint="${proxy#http://}"
  endpoint="${endpoint#https://}"
  host="${endpoint%:*}"
  port="${endpoint##*:}"
  if [ -z "$host" ] || [ -z "$port" ] || [ "$host" = "$port" ]; then
    continue
  fi
  if command -v nc >/dev/null 2>&1 && nc -z -w 1 "$host" "$port" >/dev/null 2>&1; then
    printf '%s\n' "$proxy"
    exit 0
  fi
  if command -v curl >/dev/null 2>&1 && curl -I --max-time 3 -x "$proxy" http://archive.ubuntu.com/ubuntu/ >/dev/null 2>&1; then
    printf '%s\n' "$proxy"
    exit 0
  fi
done
printf 'DIRECT\n'
SH
chmod 0755 /usr/local/bin/kf-host-proxy-detect

cat > /etc/profile.d/kf-host-proxy.sh <<'SH'
#!/usr/bin/env sh
if [ -z "${NO_PROXY:-}" ]; then
  export NO_PROXY="172.31.*,172.30.*,172.29.*,172.28.*,172.27.*,172.26.*,172.25.*,172.24.*,172.23.*,172.22.*,172.21.*,172.20.*,172.19.*,172.18.*,172.17.*,172.16.*,10.*,192.168.*,127.*,localhost,<local>"
  export no_proxy="$NO_PROXY"
fi
if [ -z "${http_proxy:-}" ] && [ -x /usr/local/bin/kf-host-proxy-detect ]; then
  kf_proxy="$(/usr/local/bin/kf-host-proxy-detect 2>/dev/null || true)"
  if [ -n "$kf_proxy" ] && [ "$kf_proxy" != "DIRECT" ]; then
    export http_proxy="$kf_proxy"
    export https_proxy="$kf_proxy"
    export HTTP_PROXY="$kf_proxy"
    export HTTPS_PROXY="$kf_proxy"
  fi
fi
SH
chmod 0644 /etc/profile.d/kf-host-proxy.sh

cat > /etc/apt/apt.conf.d/99kf-host-proxy <<'APT'
Acquire::http::Proxy-Auto-Detect "/usr/local/bin/kf-host-proxy-detect";
Acquire::https::Proxy-Auto-Detect "/usr/local/bin/kf-host-proxy-detect";
APT
chmod 0644 /etc/apt/apt.conf.d/99kf-host-proxy

echo "kf_wsl_default_user=$default_user"
echo "kf_wsl_sudo_nopasswd=enabled"
echo "kf_wsl_proxy_detect=$(/usr/local/bin/kf-host-proxy-detect)"
'@
$bash = $bash.Replace("__KF_PROXY__", $escapedProxy)

$bash | wsl -d $Distro -u root -- bash -s
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

if (-not $NoShutdown) {
    Write-Host "Restarting WSL to apply .wslconfig networking changes..."
    wsl --shutdown
}
