param(
    [Parameter(Mandatory = $true)]
    [string]$CandidateBinary,
    [ValidateRange(1, 4096)]
    [int]$SizeMiB = 128,
    [ValidateRange(1, 20)]
    [int]$Iterations = 3,
    [string]$DeviceSerial = '3f8bbaad'
)

$ErrorActionPreference = 'Stop'

$adb = 'D:\KF\Android\Sdk\platform-tools\adb.exe'
$packageName = 'com.kite.app'
$remoteScript = '/data/local/tmp/kite-proot-view-copyup-benchmark.sh'
$remoteCandidate = '/data/local/tmp/kite-proot-view-candidate'
$privateCandidate = 'files/runtime/bin/proot-view-benchmark'
$localScript = Join-Path $PSScriptRoot 'proot-view-copyup-benchmark.sh'
$repoRoot = Split-Path $PSScriptRoot -Parent

if (-not (Test-Path -LiteralPath $CandidateBinary)) {
    throw "候选 PRoot 不存在：$CandidateBinary"
}

& $adb -s $DeviceSerial push $localScript $remoteScript | Out-Null
if ($LASTEXITCODE -ne 0) { throw '推送 copy-up 基准脚本失败。' }
& $adb -s $DeviceSerial shell chmod 755 $remoteScript
if ($LASTEXITCODE -ne 0) { throw '设置 copy-up 基准脚本权限失败。' }
& $adb -s $DeviceSerial push $CandidateBinary $remoteCandidate | Out-Null
if ($LASTEXITCODE -ne 0) { throw '推送候选 PRoot 失败。' }
& $adb -s $DeviceSerial shell run-as $packageName cp $remoteCandidate $privateCandidate
if ($LASTEXITCODE -ne 0) { throw '复制候选 PRoot 到应用私有目录失败。' }
& $adb -s $DeviceSerial shell run-as $packageName chmod 700 $privateCandidate
if ($LASTEXITCODE -ne 0) { throw '设置候选 PRoot 权限失败。' }

$lines = @(& $adb -s $DeviceSerial shell run-as $packageName sh $remoteScript $SizeMiB $Iterations 2>&1)
$exitCode = $LASTEXITCODE
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outputDir = Join-Path $repoRoot "local-artifacts\proot-view-copyup\$timestamp-$($SizeMiB)mib"
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$rawPath = Join-Path $outputDir 'raw.tsv'
$lines | Set-Content -LiteralPath $rawPath -Encoding utf8

if ($exitCode -ne 0) {
    $lines
    throw "设备 copy-up 基准失败，退出码 $exitCode；输出：$rawPath"
}

$metadata = [ordered]@{}
$samples = [System.Collections.Generic.List[object]]::new()
foreach ($line in $lines) {
    $parts = $line -split "`t"
    if ($parts.Count -ge 3 -and $parts[0] -eq 'meta') {
        $metadata[$parts[1]] = $parts[2]
    }
    if ($parts.Count -eq 8 -and $parts[0] -eq 'metric') {
        $samples.Add([pscustomobject]@{
            iteration = [int]$parts[2]
            durationNs = [long]$parts[3]
            exitCode = [int]$parts[4]
            maxRssKb = [long]$parts[5]
            readBytes = [long]$parts[6]
            writeBytes = [long]$parts[7]
        })
    }
}
if ($samples.Count -ne $Iterations) {
    throw "copy-up 样本数量不符；输出：$rawPath"
}

$ordered = @($samples.durationNs | Sort-Object)
$result = [ordered]@{
    schema = 'kite_proot_view_copyup_benchmark_v1'
    capturedAt = (Get-Date).ToString('o')
    deviceSerial = $DeviceSerial
    metadata = $metadata
    summary = [ordered]@{
        samples = $samples.Count
        failures = @($samples | Where-Object exitCode -ne 0).Count
        minMs = [Math]::Round($ordered[0] / 1e6, 3)
        p50Ms = [Math]::Round($ordered[[Math]::Floor(($ordered.Count - 1) * 0.5)] / 1e6, 3)
        maxMs = [Math]::Round($ordered[-1] / 1e6, 3)
        maxRssKb = ($samples.maxRssKb | Measure-Object -Maximum).Maximum
        maxReadBytes = ($samples.readBytes | Measure-Object -Maximum).Maximum
        maxWriteBytes = ($samples.writeBytes | Measure-Object -Maximum).Maximum
    }
    samples = @($samples)
}
$jsonPath = Join-Path $outputDir 'result.json'
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8
$samples | Format-Table -AutoSize
$result.summary | Format-List
Write-Output "RESULT_JSON=$jsonPath"
