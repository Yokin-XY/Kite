param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [switch]$Details,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
$sourceRoots = @(
    Join-Path $RepoRoot 'app/src/main/java'
    Join-Path $RepoRoot 'app/src/main/kotlin'
)
$hanLiteral = '"(?:\\.|[^"\\])*[一-龥](?:\\.|[^"\\])*"'
$directUi = 'text\s*=|\.text\s*=|hint\s*=|contentDescription\s*=|setTitle\s*\(|setMessage\s*\(|setPositiveButton\s*\(|setNegativeButton\s*\(|Toast\.makeText\s*\('
$presentationFile = '(Projector|Presentation|Contract|UiProjector|Models)\.kt$'

$findings = foreach ($root in $sourceRoots) {
    if (-not (Test-Path -LiteralPath $root -PathType Container)) { continue }
    Get-ChildItem -LiteralPath $root -Recurse -File -Include '*.kt', '*.java' | ForEach-Object {
        $file = $_
        $lineNumber = 0
        Get-Content -LiteralPath $file.FullName | ForEach-Object {
            $lineNumber += 1
            $line = $_
            if ($line -notmatch $hanLiteral) { return }
            $relativePath = [IO.Path]::GetRelativePath($RepoRoot, $file.FullName).Replace('\', '/')
            $category = if ($line -match $directUi) {
                'direct-ui'
            } elseif ($file.Name -match $presentationFile) {
                'presentation-state'
            } elseif ($relativePath -match '/(foundation|platform|bridge|run|diagnostics)/') {
                'runtime-or-diagnostic'
            } else {
                'review-needed'
            }
            [pscustomobject]@{
                category = $category
                path = $relativePath
                line = $lineNumber
                text = $line.Trim()
            }
        }
    }
}

$summary = [ordered]@{
    scannedFiles = @($findings | Select-Object -ExpandProperty path -Unique).Count
    candidateLines = @($findings).Count
    directUi = @($findings | Where-Object category -eq 'direct-ui').Count
    presentationState = @($findings | Where-Object category -eq 'presentation-state').Count
    runtimeOrDiagnostic = @($findings | Where-Object category -eq 'runtime-or-diagnostic').Count
    reviewNeeded = @($findings | Where-Object category -eq 'review-needed').Count
}

if ($Json) {
    [pscustomobject]@{
        summary = [pscustomobject]$summary
        findings = if ($Details) { @($findings) } else { @() }
    } | ConvertTo-Json -Depth 5
    exit 0
}

'Kite localization hard-code audit'
$summary.GetEnumerator() | ForEach-Object { '{0}: {1}' -f $_.Key, $_.Value }
if ($Details) {
    ''
    $findings | Sort-Object category, path, line | Format-Table category, path, line, text -AutoSize -Wrap
}

exit 0
