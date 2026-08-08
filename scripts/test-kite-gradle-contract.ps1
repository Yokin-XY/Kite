[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'kite-gradle-argument-contract.ps1')

function Assert-True([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
}

function Assert-Rejected([string[]]$Arguments, [string]$Label) {
    $rejected = $false
    try {
        Resolve-KiteGradleArguments -GradleArguments $Arguments | Out-Null
    } catch {
        $rejected = $true
    }
    Assert-True $rejected "$Label 应被参数合同拒绝"
}

$defaults = @(Resolve-KiteGradleArguments -GradleArguments @(':app:assembleDebug'))
Assert-True ($defaults -contains '--no-daemon') '包装器必须强制 --no-daemon'
Assert-True ($defaults -contains '--max-workers=2') '包装器必须强制 --max-workers=2'
Assert-True ($defaults -contains '--console=plain') '包装器必须固定纯文本控制台输出'

$consistent = @(
    Resolve-KiteGradleArguments -GradleArguments @(
        ':app:assembleDebug', '--no-daemon', '-Dorg.gradle.daemon=false',
        '--max-workers', '2', '-Dorg.gradle.workers.max=2',
        '--console=plain', '--console=plain'
    )
)
Assert-True (@($consistent | Where-Object { $_ -eq '--no-daemon' }).Count -eq 1) '一致 daemon 参数不应重复'
Assert-True (@($consistent | Where-Object { $_ -eq '--max-workers=2' }).Count -eq 1) '一致 worker 参数应规范化'
Assert-True (@($consistent | Where-Object { $_ -eq '--console=plain' }).Count -eq 1) '一致控制台参数不应重复'

$narrow = @(
    Resolve-KiteGradleArguments -GradleArguments @(
        ':app:assembleDebug', '-Dorg.gradle.daemon=false', '--max-workers=1'
    )
)
Assert-True (@($narrow | Where-Object { $_ -eq '--no-daemon' }).Count -eq 1) '安全的 daemon 系统属性应规范化'
Assert-True (@($narrow | Where-Object { $_ -eq '--max-workers=1' }).Count -eq 1) '调用方应能把 worker 进一步收窄为 1'

Assert-Rejected @('--daemon') '显式 daemon'
Assert-Rejected @('-Dorg.gradle.daemon=true') 'daemon 系统属性'
Assert-Rejected @('--max-workers=3') 'worker 长参数'
Assert-Rejected @('--max-workers', '0') '非法 worker 分离参数'
Assert-Rejected @('-Dorg.gradle.workers.max=8') 'worker 系统属性'
Assert-Rejected @('--max-workers=1', '--max-workers=2') '相互冲突的 worker 参数'

$properties = Get-Content -LiteralPath (Join-Path $repositoryRoot 'gradle.properties')
Assert-True ($properties -contains 'org.gradle.daemon=false') '直接 gradlew 必须默认禁用 daemon'
Assert-True ($properties -contains 'org.gradle.workers.max=2') '直接 gradlew 必须默认限制 2 workers'

Write-Output 'KITE_GRADLE_CONTRACT status=passed daemon=false workers=2'
