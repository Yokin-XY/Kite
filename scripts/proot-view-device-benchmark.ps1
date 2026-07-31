param(
    [ValidateSet('baseline', 'candidate-disabled', 'view-empty', 'view-populated', 'view-parent-8', 'view-scoped')]
    [string]$Variant = 'baseline',
    [ValidateSet('smoke', 'daily', 'extreme')]
    [string]$Suite = 'daily',
    [ValidateRange(1, 100)]
    [int]$Iterations = 15,
    [string]$DeviceSerial = '3f8bbaad',
    [string]$CandidateBinary = ''
)

$ErrorActionPreference = 'Stop'

$adb = 'D:\KF\Android\Sdk\platform-tools\adb.exe'
$packageName = 'com.kite.app'
$remoteScript = '/data/local/tmp/kite-proot-view-benchmark.sh'
$remoteCandidate = '/data/local/tmp/kite-proot-view-candidate'
$privateCandidate = 'files/runtime/bin/proot-view-benchmark'
$localScript = Join-Path $PSScriptRoot 'proot-view-device-benchmark.sh'
$repoRoot = Split-Path $PSScriptRoot -Parent

if (-not (Test-Path -LiteralPath $adb)) {
    throw "ADB 不存在：$adb"
}
if (-not (Test-Path -LiteralPath $localScript)) {
    throw "设备脚本不存在：$localScript"
}
if ($Variant -ne 'baseline' -and [string]::IsNullOrWhiteSpace($CandidateBinary)) {
    throw 'View 变体必须通过 -CandidateBinary 指定候选 PRoot，避免误用正式运行时。'
}
if (-not [string]::IsNullOrWhiteSpace($CandidateBinary) -and -not (Test-Path -LiteralPath $CandidateBinary)) {
    throw "候选 PRoot 不存在：$CandidateBinary"
}

$deviceLine = & $adb -s $DeviceSerial get-state 2>&1
if ($LASTEXITCODE -ne 0 -or ($deviceLine -join '').Trim() -ne 'device') {
    throw "目标设备不可用：$DeviceSerial"
}

& $adb -s $DeviceSerial push $localScript $remoteScript | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw '推送 benchmark 脚本失败。'
}
& $adb -s $DeviceSerial shell chmod 755 $remoteScript
if ($LASTEXITCODE -ne 0) {
    throw '设置 benchmark 脚本权限失败。'
}

if (-not [string]::IsNullOrWhiteSpace($CandidateBinary)) {
    & $adb -s $DeviceSerial push $CandidateBinary $remoteCandidate | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw '推送候选 PRoot 失败。'
    }
    & $adb -s $DeviceSerial shell run-as $packageName cp $remoteCandidate $privateCandidate
    if ($LASTEXITCODE -ne 0) {
        throw '复制候选 PRoot 到应用私有目录失败。'
    }
    & $adb -s $DeviceSerial shell run-as $packageName chmod 700 $privateCandidate
    if ($LASTEXITCODE -ne 0) {
        throw '设置候选 PRoot 权限失败。'
    }
}

$remoteCommand = "run-as $packageName sh $remoteScript $Variant $Iterations $Suite"
$lines = @(& $adb -s $DeviceSerial shell $remoteCommand 2>&1)
$exitCode = $LASTEXITCODE

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outputDir = Join-Path $repoRoot "local-artifacts\proot-view-benchmark\$timestamp-$Variant-$Suite"
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$rawPath = Join-Path $outputDir 'raw.tsv'
$lines | Set-Content -LiteralPath $rawPath -Encoding utf8

if ($exitCode -ne 0) {
    throw "设备 benchmark 失败，退出码 $exitCode；原始输出：$rawPath"
}

$metadata = [ordered]@{}
$metrics = [System.Collections.Generic.List[object]]::new()
foreach ($line in $lines) {
    $parts = $line -split "`t"
    if ($parts.Count -ge 3 -and $parts[0] -eq 'meta') {
        $metadata[$parts[1]] = $parts[2]
        continue
    }
    if ($parts.Count -eq 6 -and $parts[0] -eq 'metric') {
        $metrics.Add([pscustomobject]@{
            variant = $parts[1]
            name = $parts[2]
            iteration = [int]$parts[3]
            durationNs = [long]$parts[4]
            exitCode = [int]$parts[5]
        })
    }
}

if ($metrics.Count -eq 0) {
    throw "设备 benchmark 没有产生指标；原始输出：$rawPath"
}

$summary = foreach ($group in ($metrics | Group-Object name)) {
    $ordered = @($group.Group.durationNs | Sort-Object)
    $count = $ordered.Count
    $p50Index = [Math]::Min($count - 1, [Math]::Floor(($count - 1) * 0.50))
    $p95Index = [Math]::Min($count - 1, [Math]::Ceiling(($count - 1) * 0.95))
    [pscustomobject]@{
        name = $group.Name
        samples = $count
        failures = @($group.Group | Where-Object exitCode -ne 0).Count
        minMs = [Math]::Round($ordered[0] / 1e6, 3)
        p50Ms = [Math]::Round($ordered[$p50Index] / 1e6, 3)
        p95Ms = [Math]::Round($ordered[$p95Index] / 1e6, 3)
        maxMs = [Math]::Round($ordered[-1] / 1e6, 3)
    }
}

$result = [ordered]@{
    schema = 'kite_proot_view_benchmark_v1'
    capturedAt = (Get-Date).ToString('o')
    deviceSerial = $DeviceSerial
    variant = $Variant
    suite = $Suite
    metadata = $metadata
    summary = @($summary)
    samples = @($metrics)
}

$jsonPath = Join-Path $outputDir 'result.json'
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8

$summary | Format-Table -AutoSize
Write-Output "RESULT_JSON=$jsonPath"

$failedSamples = @($metrics | Where-Object exitCode -ne 0)
if ($failedSamples.Count -gt 0) {
    throw "设备 benchmark 有 $($failedSamples.Count) 个失败样本；原始输出：$rawPath"
}
