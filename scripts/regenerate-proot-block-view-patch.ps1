param(
    [string]$ReferenceRoot = 'D:\xm\KFshell\build\external\termux-proot-block-reference',
    [string]$CandidateRoot = 'D:\xm\KFshell\build\external\termux-proot-block-view',
    [string]$OutputPath = (Join-Path (Split-Path $PSScriptRoot -Parent) 'assets\proot\patches\kf-proot-block-view-v2.patch')
)

$ErrorActionPreference = 'Stop'
$externalRoot = Split-Path $ReferenceRoot -Parent
$referenceName = Split-Path $ReferenceRoot -Leaf
$candidateName = Split-Path $CandidateRoot -Leaf
$modifiedFiles = @(
    'src/GNUmakefile',
    'src/cli/cli.c',
    'src/execve/elf.c',
    'src/execve/elf.h',
    'src/execve/enter.c',
    'src/execve/exit.c',
    'src/execve/ldso.c',
    'src/execve/shebang.c',
    'src/extension/ashmem_memfd/ashmem_memfd.c',
    'src/extension/extension.c',
    'src/extension/extension.h',
    'src/extension/kf_procfs/kf_procfs.c',
    'src/extension/kf_txn/kf_txn.c',
    'src/extension/kf_view/kf_view.c',
    'src/extension/link2symlink/link2symlink.c',
    'src/loader/assembly.S',
    'src/loader/loader-info.awk',
    'src/path/path.c',
    'src/syscall/seccomp.c',
    'src/syscall/sysnums-arm.h',
    'src/syscall/sysnums-arm64.h',
    'src/syscall/sysnums-i386.h',
    'src/syscall/sysnums-sh4.h',
    'src/syscall/sysnums-x32.h',
    'src/syscall/sysnums-x86_64.h',
    'src/syscall/sysnums.list',
    'src/tracee/event.c',
    'src/tracee/mem.c',
    'src/tracee/mem.h',
    'src/tracee/telemetry.c',
    'src/tracee/telemetry.h',
    'src/tracee/tracee.c',
    'src/tracee/tracee.h'
)
$newFiles = @(
    'src/extension/kf_block_probe/kf_block_probe.c',
    'src/extension/kf_block_probe/kf_block_probe.h',
    'src/extension/kf_block_store/kf_block_store.c',
    'src/extension/kf_block_store/kf_block_store.h',
    'src/extension/kf_view/kf_view.h',
    'src/loader/loader-info-m32.c'
)

function Invoke-GitNoIndexDiff {
    param([string]$Left, [string]$Right)
    $lines = @(& git -c core.safecrlf=false diff --no-index --src-prefix=a/ --dst-prefix=b/ -- $Left $Right 2>$null)
    if ($LASTEXITCODE -eq 0) {
        return ''
    }
    if ($LASTEXITCODE -ne 1) {
        throw "生成 PRoot 块级变化层补丁失败：$Left -> $Right，退出码 $LASTEXITCODE"
    }
    return (($lines -join "`n") + "`n")
}

$chunks = [System.Collections.Generic.List[string]]::new()
Push-Location $externalRoot
try {
    foreach ($relative in $modifiedFiles) {
        $chunks.Add((Invoke-GitNoIndexDiff -Left "$referenceName/$relative" -Right "$candidateName/$relative"))
    }
    foreach ($relative in $newFiles) {
        $chunks.Add((Invoke-GitNoIndexDiff -Left 'NUL' -Right "$candidateName/$relative"))
    }
} finally {
    Pop-Location
}

$patch = ($chunks -join '')
$patch = $patch.Replace("a/$referenceName/", 'a/')
$patch = $patch.Replace("b/$candidateName/", 'b/')
$patch = $patch.Replace("a/$candidateName/", 'a/')
$outputDirectory = Split-Path $OutputPath -Parent
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
[System.IO.File]::WriteAllText(
    $OutputPath,
    $patch,
    [System.Text.UTF8Encoding]::new($false)
)
Write-Output "PATCH=$OutputPath"
Write-Output "SHA256=$((Get-FileHash -LiteralPath $OutputPath -Algorithm SHA256).Hash)"
