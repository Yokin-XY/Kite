param(
    [string]$ReferenceRoot = 'D:\xm\KFshell\build\external\termux-proot-view-reference',
    [string]$CandidateRoot = 'D:\xm\KFshell\build\external\termux-proot-view',
    [string]$OutputPath = (Join-Path (Split-Path $PSScriptRoot -Parent) 'assets\proot\patches\kf-proot-view-v1.patch')
)

$ErrorActionPreference = 'Stop'
$externalRoot = Split-Path $ReferenceRoot -Parent
$referenceName = Split-Path $ReferenceRoot -Leaf
$candidateName = Split-Path $CandidateRoot -Leaf
$modifiedFiles = @(
    'src/GNUmakefile',
    'src/cli/cli.c',
    'src/extension/extension.c',
    'src/extension/extension.h',
    'src/path/path.c'
)
$newFiles = @(
    'src/extension/kf_view/kf_view.c'
)

function Invoke-GitNoIndexDiff {
    param([string]$Left, [string]$Right)
    $lines = @(& git -c core.safecrlf=false diff --no-index --src-prefix=a/ --dst-prefix=b/ -- $Left $Right 2>$null)
    if ($LASTEXITCODE -ne 1) {
        throw "生成 PRoot View 补丁失败：$Left -> $Right，退出码 $LASTEXITCODE"
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
exit 0
