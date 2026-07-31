param(
    [Parameter(Mandatory = $true)]
    [string]$CandidateBinary,
    [string]$DeviceSerial = '3f8bbaad',
    [switch]$AllowFailure
)

$ErrorActionPreference = 'Stop'
$adb = 'D:\KF\Android\Sdk\platform-tools\adb.exe'
$packageName = 'com.kite.app'
$remoteScript = '/data/local/tmp/kite-proot-block-view-multiview.sh'
$remoteCandidate = '/data/local/tmp/kite-proot-view-candidate'
$privateCandidate = 'files/runtime/bin/proot-view-benchmark'
$repoRoot = Split-Path $PSScriptRoot -Parent

if (-not (Test-Path -LiteralPath $CandidateBinary)) {
    throw "候选 PRoot 不存在：$CandidateBinary"
}
& $adb -s $DeviceSerial push (Join-Path $PSScriptRoot 'proot-block-view-multiview-fixture.sh') $remoteScript | Out-Null
if ($LASTEXITCODE -ne 0) { throw '推送多 View 夹具失败。' }
& $adb -s $DeviceSerial push $CandidateBinary $remoteCandidate | Out-Null
if ($LASTEXITCODE -ne 0) { throw '推送候选 PRoot 失败。' }
& $adb -s $DeviceSerial shell chmod 755 $remoteScript
& $adb -s $DeviceSerial shell run-as $packageName cp $remoteCandidate $privateCandidate
& $adb -s $DeviceSerial shell run-as $packageName chmod 700 $privateCandidate

$lines = @(& $adb -s $DeviceSerial shell "run-as $packageName sh $remoteScript" 2>&1)
$exitCode = $LASTEXITCODE
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outputDirectory = Join-Path $repoRoot "local-artifacts\proot-block-view-multiview\$timestamp"
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$outputPath = Join-Path $outputDirectory 'result.tsv'
$lines | Set-Content -LiteralPath $outputPath -Encoding utf8
$lines

if ($exitCode -ne 0 -and -not $AllowFailure) {
    throw "多 View 夹具失败，退出码 $exitCode；输出：$outputPath"
}
if ($exitCode -eq 0 -and -not ($lines -contains "result`tBLOCK_VIEW_MULTIVIEW_OK")) {
    throw "多 View 夹具缺少成功标记；输出：$outputPath"
}
Write-Output "EXIT_CODE=$exitCode"
Write-Output "RESULT_TSV=$outputPath"
