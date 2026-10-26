[CmdletBinding()]
param(
    [string]$JdkPath = $env:JAVA_HOME,
    [string]$IdePath,
    [string]$GradleHome,
    [switch]$IncludeIde
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($JdkPath) -or -not (Test-Path -LiteralPath (Join-Path $JdkPath 'bin\javac.exe'))) {
    throw 'Provide -JdkPath pointing to a full JDK 25 (an IDEA 262 JBR with javac also works).'
}
$JdkPath = (Resolve-Path -LiteralPath $JdkPath).Path
$jdkVersion = & (Join-Path $JdkPath 'bin\javac.exe') -version 2>&1
if ($LASTEXITCODE -ne 0 -or "$jdkVersion" -notmatch '^javac 25(?:\.|$)') {
    throw "A full JDK 25 is required; found: $jdkVersion"
}
$launcher = if ($GradleHome) { Join-Path $GradleHome 'bin\gradle.bat' } else { Join-Path $repoRoot 'gradlew.bat' }
if (-not (Test-Path -LiteralPath $launcher)) { throw "Gradle launcher not found: $launcher" }
$launcher = (Resolve-Path -LiteralPath $launcher).Path
if ($IncludeIde) {
    if (-not $IdePath -or -not (Test-Path -LiteralPath (Join-Path $IdePath 'product-info.json'))) {
        throw 'Provide -IdePath pointing to IntelliJ IDEA 2026.2 when using -IncludeIde.'
    }
    $product = Get-Content -LiteralPath (Join-Path $IdePath 'product-info.json') -Raw | ConvertFrom-Json
    if ($product.productCode -notin @('IU', 'IC') -or $product.buildNumber -notlike '262.*') {
        throw 'The plugin targets IntelliJ IDEA build 262.*.'
    }
    $IdePath = (Resolve-Path -LiteralPath $IdePath).Path.Replace('\', '/')
}

function Invoke-Verification([string[]]$TaskArguments) {
    & $launcher @TaskArguments --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Verification failed: $($TaskArguments -join ' ')" }
}

$previousJavaHome = $env:JAVA_HOME
Push-Location $repoRoot
try {
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JdkPath).Path
    Invoke-Verification @('-p', 'rag-standalone', 'test')
    Invoke-Verification @('-p', 'integration-standalone', 'test', 'prepareAcceptanceMaterials')
    if ($IncludeIde) {
        Invoke-Verification @('build', 'buildPlugin', 'verifyPluginStructure',
            'verifyPluginProjectConfiguration', "-PlocalIdePath=$IdePath")
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
