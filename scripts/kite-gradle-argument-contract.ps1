Set-StrictMode -Version Latest

function Resolve-KiteGradleArguments {
    [CmdletBinding()]
    param(
        [string[]]$GradleArguments = @()
    )

    $resolved = [Collections.Generic.List[string]]::new()
    $hasWorkerLimit = $false
    $workerLimit = 2
    for ($index = 0; $index -lt $GradleArguments.Count; $index += 1) {
        $argument = [string]$GradleArguments[$index]
        $normalized = $argument.Trim().ToLowerInvariant()
        switch -Regex ($normalized) {
            '^--daemon(?:=true)?$' {
                throw 'Kite 本地构建禁止启用 Gradle daemon；项目固定使用 --no-daemon。'
            }
            '^--daemon=false$|^--no-daemon$' {
                continue
            }
            '^-dorg\.gradle\.daemon=(.+)$' {
                if ($Matches[1] -ne 'false') {
                    throw 'Kite 本地构建禁止通过系统属性启用 Gradle daemon。'
                }
                continue
            }
            '^--max-workers=(.+)$' {
                $candidateLimit = 0
                if (!([int]::TryParse($Matches[1], [ref]$candidateLimit)) -or
                    $candidateLimit -lt 1 -or $candidateLimit -gt 2) {
                    throw 'Kite 本地构建最多允许 2 个 Gradle worker。'
                }
                if ($hasWorkerLimit -and $workerLimit -ne $candidateLimit) {
                    throw 'Kite 本地构建收到相互冲突的 worker 上限。'
                }
                $hasWorkerLimit = $true
                $workerLimit = $candidateLimit
                continue
            }
            '^--max-workers$' {
                if ($index + 1 -ge $GradleArguments.Count) {
                    throw '--max-workers 缺少数值；Kite 仅允许 1 或 2。'
                }
                $workerCountText = ([string]$GradleArguments[$index + 1]).Trim()
                $candidateLimit = 0
                if (!([int]::TryParse($workerCountText, [ref]$candidateLimit)) -or
                    $candidateLimit -lt 1 -or $candidateLimit -gt 2) {
                    throw 'Kite 本地构建最多允许 2 个 Gradle worker。'
                }
                if ($hasWorkerLimit -and $workerLimit -ne $candidateLimit) {
                    throw 'Kite 本地构建收到相互冲突的 worker 上限。'
                }
                $hasWorkerLimit = $true
                $workerLimit = $candidateLimit
                $index += 1
                continue
            }
            '^-dorg\.gradle\.workers\.max=(.+)$' {
                $candidateLimit = 0
                if (!([int]::TryParse($Matches[1], [ref]$candidateLimit)) -or
                    $candidateLimit -lt 1 -or $candidateLimit -gt 2) {
                    throw 'Kite 本地构建禁止把 worker 上限提高到 2 以上。'
                }
                if ($hasWorkerLimit -and $workerLimit -ne $candidateLimit) {
                    throw 'Kite 本地构建收到相互冲突的 worker 上限。'
                }
                $hasWorkerLimit = $true
                $workerLimit = $candidateLimit
                continue
            }
            '^--console=plain$' {
                continue
            }
            default {
                $resolved.Add($argument)
            }
        }
    }

    $resolved.Add('--no-daemon')
    $resolved.Add("--max-workers=$workerLimit")
    $resolved.Add('--console=plain')
    return $resolved.ToArray()
}
