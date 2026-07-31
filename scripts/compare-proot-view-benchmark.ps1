param(
    [Parameter(Mandatory = $true)]
    [ValidateCount(2, 8)]
    [string[]]$ResultPaths,
    [string]$ReferenceVariant = 'baseline',
    [string]$OutputDirectory = ''
)

$ErrorActionPreference = 'Stop'

$results = foreach ($path in $ResultPaths) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "基准结果不存在：$path"
    }
    $result = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    if ($result.schema -ne 'kite_proot_view_benchmark_v1') {
        throw "不支持的基准 schema：$path"
    }
    $result
}

$reference = @($results | Where-Object variant -eq $ReferenceVariant)
if ($reference.Count -ne 1) {
    throw "必须且只能有一份参考变体 $ReferenceVariant，实际为 $($reference.Count) 份。"
}
$reference = $reference[0]

$rows = [System.Collections.Generic.List[object]]::new()
foreach ($candidate in $results) {
    foreach ($metric in $candidate.summary) {
        $baselineMetric = @($reference.summary | Where-Object name -eq $metric.name)
        if ($baselineMetric.Count -ne 1) {
            continue
        }
        $baselineMetric = $baselineMetric[0]
        $rows.Add([pscustomobject]@{
            variant = $candidate.variant
            metric = $metric.name
            samples = $metric.samples
            failures = $metric.failures
            p50Ms = [double]$metric.p50Ms
            p50Ratio = if ([double]$baselineMetric.p50Ms -eq 0) {
                $null
            } else {
                [Math]::Round([double]$metric.p50Ms / [double]$baselineMetric.p50Ms, 3)
            }
            p95Ms = [double]$metric.p95Ms
            p95Ratio = if ([double]$baselineMetric.p95Ms -eq 0) {
                $null
            } else {
                [Math]::Round([double]$metric.p95Ms / [double]$baselineMetric.p95Ms, 3)
            }
        })
    }
}

$metadata = foreach ($result in $results) {
    [pscustomobject]@{
        variant = $result.variant
        suite = $result.suite
        runtimeSha256 = $result.metadata.runtime_sha256
        thermalStart = [long]$result.metadata.thermal_start
        thermalEnd = [long]$result.metadata.thermal_end
        viewStorageKbStart = [long]$result.metadata.view_storage_kb_start
        viewStorageKbEnd = [long]$result.metadata.view_storage_kb_end
    }
}

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $repoRoot = Split-Path $PSScriptRoot -Parent
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutputDirectory = Join-Path $repoRoot "local-artifacts\proot-view-comparison\$timestamp"
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

$report = [ordered]@{
    schema = 'kite_proot_view_comparison_v1'
    capturedAt = (Get-Date).ToString('o')
    referenceVariant = $ReferenceVariant
    inputs = @($ResultPaths)
    metadata = @($metadata)
    metrics = @($rows)
}
$jsonPath = Join-Path $OutputDirectory 'comparison.json'
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8

$rows |
    Where-Object variant -ne $ReferenceVariant |
    Sort-Object metric, variant |
    Format-Table variant, metric, p50Ms, p50Ratio, p95Ms, p95Ratio, failures -AutoSize
$metadata | Format-Table -AutoSize
Write-Output "RESULT_JSON=$jsonPath"
