[CmdletBinding()]
param(
    [switch]$Online
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$assetRoot = Join-Path $repoRoot 'assets'
$manifestRoot = Join-Path $assetRoot 'resources'
$failures = [System.Collections.Generic.List[string]]::new()
$notes = [System.Collections.Generic.List[string]]::new()
$manifests = @{}

function Add-Failure([string]$Message) {
    $failures.Add($Message)
}

function Read-Property([object]$Object, [string]$Name) {
    if ($null -eq $Object) { return $null }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

Get-ChildItem -LiteralPath $manifestRoot -Directory | Sort-Object Name | ForEach-Object {
    $manifestPath = Join-Path $_.FullName 'manifest.json'
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        return
    }
    try {
        $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    } catch {
        Add-Failure "JSON 无法解析：$manifestPath；$($_.Exception.Message)"
        return
    }
    $resourceId = [string](Read-Property $manifest 'id')
    if ([string]::IsNullOrWhiteSpace($resourceId)) {
        Add-Failure "资源目录缺少 id：$manifestPath"
        return
    }
    if ($resourceId -ne $_.Name) {
        Add-Failure "目录名与资源 id 不一致：$($_.Name) != $resourceId"
    }
    if ($manifests.ContainsKey($resourceId)) {
        Add-Failure "资源 id 重复：$resourceId"
        return
    }
    $manifests[$resourceId] = $manifest
}

foreach ($resourceId in @($manifests.Keys | Sort-Object)) {
    $manifest = $manifests[$resourceId]
    $relations = Read-Property $manifest 'relations'
    $display = Read-Property $manifest 'display'
    foreach ($dependencyId in @((Read-Property $relations 'base')) + @((Read-Property $relations 'defaults'))) {
        if ([string]::IsNullOrWhiteSpace([string]$dependencyId)) { continue }
        if ($dependencyId -eq $resourceId) {
            Add-Failure "资源依赖自身：$resourceId"
        } elseif (-not $manifests.ContainsKey([string]$dependencyId)) {
            Add-Failure "资源依赖不存在：$resourceId -> $dependencyId"
        }
    }
    foreach ($recommendation in @((Read-Property $display 'recommendations'))) {
        $targetId = [string](Read-Property $recommendation 'resourceId')
        if (-not [string]::IsNullOrWhiteSpace($targetId) -and -not $manifests.ContainsKey($targetId)) {
            Add-Failure "推荐资源不存在：$resourceId -> $targetId"
        }
    }

    $base = Read-Property $manifest 'base'
    $icon = Read-Property $base 'icon'
    $media = Read-Property $display 'media'
    $assetPaths = @()
    if ([string](Read-Property $icon 'type') -eq 'asset') {
        $assetPaths += @((Read-Property $icon 'value'))
    }
    $assetPaths += @((Read-Property $media 'asset'))
    foreach ($assetPath in $assetPaths) {
        if ([string]::IsNullOrWhiteSpace([string]$assetPath)) { continue }
        $candidate = Join-Path $assetRoot ([string]$assetPath)
        if (-not (Test-Path -LiteralPath $candidate)) {
            Add-Failure "资源静态文件不存在：$resourceId -> $assetPath"
        }
    }

    $source = Read-Property $manifest 'source'
    $sourceType = [string](Read-Property $source 'type')
    if ($sourceType -eq 'bundled') {
        $sourceAsset = [string](Read-Property $source 'asset')
        if ([string]::IsNullOrWhiteSpace($sourceAsset) -or
            -not (Test-Path -LiteralPath (Join-Path $assetRoot $sourceAsset))) {
            Add-Failure "内置资源载荷不存在：$resourceId -> $sourceAsset"
        }
    }
}

function Test-DependencyPath([string]$ResourceId, [System.Collections.Generic.List[string]]$Path) {
    if ($Path.Contains($ResourceId)) {
        Add-Failure "资源依赖形成环：$(($Path + $ResourceId) -join ' -> ')"
        return
    }
    $Path.Add($ResourceId)
    $manifest = $manifests[$ResourceId]
    $relations = Read-Property $manifest 'relations'
    foreach ($dependencyId in @((Read-Property $relations 'base'))) {
        if ($manifests.ContainsKey([string]$dependencyId)) {
            Test-DependencyPath ([string]$dependencyId) $Path
        }
    }
    $Path.RemoveAt($Path.Count - 1)
}

foreach ($resourceId in @($manifests.Keys)) {
    Test-DependencyPath $resourceId ([System.Collections.Generic.List[string]]::new())
}

if ($Online) {
    $npmCommand = Get-Command npm -ErrorAction SilentlyContinue
    $curlCommand = Get-Command curl.exe -ErrorAction SilentlyContinue
    if ($null -eq $npmCommand) { Add-Failure '在线审计缺少 npm 命令' }
    if ($null -eq $curlCommand) { Add-Failure '在线审计缺少 curl.exe' }

    foreach ($resourceId in @($manifests.Keys | Sort-Object)) {
        $manifest = $manifests[$resourceId]
        $source = Read-Property $manifest 'source'
        if ([string](Read-Property $source 'type') -ne 'npm' -or $null -eq $npmCommand) { continue }
        $packageName = [string](Read-Property $source 'package')
        $managedCommands = @((Read-Property (Read-Property $manifest 'management') 'managedCommands'))
        $installActions = @((Read-Property (Read-Property $manifest 'actions') 'install')) |
            Where-Object { $null -ne $_ }
        $installSteps = @($installActions | ForEach-Object { @((Read-Property $_ 'steps')) }) |
            Where-Object { $null -ne $_ }
        $createsCustomCommands = @($installSteps | Where-Object { [string](Read-Property $_ 'type') -ne 'npm' }).Count -gt 0
        if ($createsCustomCommands) {
            $notes.Add("跳过 npm 命令核验（安装计划还会生成自定义命令）：$resourceId")
            continue
        }
        $npmOutput = & $npmCommand.Source view "$packageName@latest" version bin --json 2>&1
        if ($LASTEXITCODE -ne 0) {
            Add-Failure "npm 元数据读取失败：$resourceId -> $packageName"
            continue
        }
        try {
            $metadata = ($npmOutput -join [Environment]::NewLine) | ConvertFrom-Json
        } catch {
            Add-Failure "npm 元数据无法解析：$resourceId -> $packageName"
            continue
        }
        $binValue = Read-Property $metadata 'bin'
        $publishedCommands = if ($binValue -is [string]) {
            @($packageName.Split('/')[-1])
        } elseif ($null -eq $binValue) {
            @()
        } else {
            @($binValue.PSObject.Properties.Name)
        }
        foreach ($commandName in $managedCommands) {
            if ([string]$commandName -notin $publishedCommands) {
                Add-Failure "npm 包未发布受管命令：$resourceId -> $packageName -> $commandName"
            }
        }
        $notes.Add("npm $resourceId：$($metadata.version)；命令 $($publishedCommands -join ', ')")
    }

    $urls = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($manifest in $manifests.Values) {
        $source = Read-Property $manifest 'source'
        foreach ($name in @('repository', 'url', 'latestUrl', 'upstream')) {
            $url = [string](Read-Property $source $name)
            if ($url.StartsWith('https://', [System.StringComparison]::OrdinalIgnoreCase)) {
                [void]$urls.Add($url)
            }
        }
    }
    if ($null -ne $curlCommand) {
        foreach ($url in @($urls | Sort-Object)) {
            $status = & $curlCommand.Source -sS -L --max-time 25 -o NUL -w '%{http_code}' -- $url
            if ($LASTEXITCODE -ne 0 -or [int]$status -lt 200 -or [int]$status -ge 400) {
                Add-Failure "上游地址不可达：$status $url"
            }
        }
        $notes.Add("上游地址：已核验 $($urls.Count) 个 HTTPS 入口")
    }
}

$notes | ForEach-Object { Write-Output "INFO  $_" }
if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error "FAIL  $_" }
    exit 1
}
Write-Output "PASS  资源 manifest 审计通过：$($manifests.Count) 张；在线=$Online"
