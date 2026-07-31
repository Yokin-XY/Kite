param(
    [ValidateSet('baseline', 'candidate-disabled', 'view-empty', 'view-populated')]
    [string]$Variant = 'baseline',
    [string]$CandidateBinary = '',
    [string]$Levels = '1,4,8,16',
    [string]$DeviceSerial = '3f8bbaad'
)

$ErrorActionPreference = 'Stop'

$adb = 'D:\KF\Android\Sdk\platform-tools\adb.exe'
$packageName = 'com.kite.app'
$remoteScript = '/data/local/tmp/kite-proot-view-concurrency-benchmark.sh'
$remoteCandidate = '/data/local/tmp/kite-proot-view-candidate'
$privateCandidate = 'files/runtime/bin/proot-view-benchmark'
$localScript = Join-Path $PSScriptRoot 'proot-view-concurrency-benchmark.sh'
$repoRoot = Split-Path $PSScriptRoot -Parent

if ($Variant -ne 'baseline' -and [string]::IsNullOrWhiteSpace($CandidateBinary)) {
    throw '非 baseline 变体必须指定候选 PRoot。'
}

& $adb -s $DeviceSerial push $localScript $remoteScript | Out-Null
if ($LASTEXITCODE -ne 0) { throw '推送并发基准脚本失败。' }
& $adb -s $DeviceSerial shell chmod 755 $remoteScript
if ($LASTEXITCODE -ne 0) { throw '设置并发基准脚本权限失败。' }
if ($Variant -ne 'baseline') {
    & $adb -s $DeviceSerial push $CandidateBinary $remoteCandidate | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '推送候选 PRoot 失败。' }
    & $adb -s $DeviceSerial shell run-as $packageName cp $remoteCandidate $privateCandidate
    if ($LASTEXITCODE -ne 0) { throw '复制候选 PRoot 到应用私有目录失败。' }
    & $adb -s $DeviceSerial shell run-as $packageName chmod 700 $privateCandidate
    if ($LASTEXITCODE -ne 0) { throw '设置候选 PRoot 权限失败。' }
}

$lines = @(& $adb -s $DeviceSerial shell run-as $packageName sh $remoteScript $Variant $Levels 2>&1)
$exitCode = $LASTEXITCODE
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outputDir = Join-Path $repoRoot "local-artifacts\proot-view-concurrency\$timestamp-$Variant"
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$rawPath = Join-Path $outputDir 'raw.tsv'
$lines | Set-Content -LiteralPath $rawPath -Encoding utf8
if ($exitCode -ne 0) {
    $lines
    throw "设备并发基准失败，退出码 $exitCode；输出：$rawPath"
}

$metadata = [ordered]@{}
$samples = [System.Collections.Generic.List[object]]::new()
foreach ($line in $lines) {
    $parts = $line -split "`t"
    if ($parts.Count -ge 3 -and $parts[0] -eq 'meta') {
        $metadata[$parts[1]] = $parts[2]
    }
    if ($parts.Count -eq 6 -and $parts[0] -eq 'metric') {
        $samples.Add([pscustomobject]@{
            concurrency = [int]$parts[2]
            durationNs = [long]$parts[3]
            failures = [int]$parts[4]
            maxTotalRssKb = [long]$parts[5]
        })
    }
}
if ($samples.Count -eq 0) {
    throw "并发基准没有产生样本；输出：$rawPath"
}

$result = [ordered]@{
    schema = 'kite_proot_view_concurrency_benchmark_v1'
    capturedAt = (Get-Date).ToString('o')
    deviceSerial = $DeviceSerial
    variant = $Variant
    metadata = $metadata
    samples = @($samples)
}
$jsonPath = Join-Path $outputDir 'result.json'
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8
$samples | Format-Table -AutoSize
$metadata | Format-Table -AutoSize
Write-Output "RESULT_JSON=$jsonPath"
