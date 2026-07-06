param(
    [string]$Serial = "3f8bbaad"
)

$ErrorActionPreference = "Stop"

$proot = "/data/user/0/com.kite.app/files/runtime/bin/proot"
$lib = "/data/user/0/com.kite.app/files/runtime/lib"
$tmp = "/data/user/0/com.kite.app/files/runtime/tmp"
$rootfs = "/data/user/0/com.kite.app/files/runtime/containers/ubuntu-main/rootfs"
$workspace = "/data/user/0/com.kite.app/files/runtime/shared/ubuntu-main"
$resolv = "/data/user/0/com.kite.app/files/runtime/tmp/resolv.conf"

function ConvertTo-SafeStatusText {
    param([string]$Value)

    if ($null -eq $Value) {
        return ""
    }

    $safe = $Value
    $safe = $safe -replace '[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}', '<account>'
    $safe = $safe -replace '(?i)("?(?:access[_-]?token|refresh[_-]?token|id[_-]?token|api[_-]?key|secret|authorization|code)"?\s*[:=]\s*)"?[^",\s}]+', '$1<redacted>'
    $safe = $safe -replace '(?i)(sk-[A-Za-z0-9_-]{16,})', '<api-key>'
    $safe = $safe -replace '(?i)(sess-[A-Za-z0-9_-]{16,})', '<session>'
    return $safe
}

$inner = @'
export PATH=/workspace/.kf/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
echo ---codex-version---
HOME=/workspace/.kf/software/kite.codex.cli/user-home codex --version 2>&1 || true
echo ---codex-login-status---
HOME=/workspace/.kf/software/kite.codex.cli/user-home codex login status 2>&1 || true
echo ---claude-version---
HOME=/workspace/.kf/software/kite.claude.code/user-home claude --version 2>&1 || true
echo ---claude-auth-status---
HOME=/workspace/.kf/software/kite.claude.code/user-home claude auth status 2>&1 || true
'@ -replace "(`r`n|`n|`r)", "; "

$remote = "run-as com.kite.app sh -c 'PROOT_TMP_DIR=$tmp LD_LIBRARY_PATH=$lib $proot --link2symlink -0 -r $rootfs -w /workspace -b /dev:/dev -b /proc:/proc -b /sys:/sys -b ${workspace}:/workspace -b ${resolv}:/etc/resolv.conf /bin/bash -lc `"$inner`"'"

& adb -s $Serial shell $remote | ForEach-Object {
    ConvertTo-SafeStatusText $_.ToString()
}
