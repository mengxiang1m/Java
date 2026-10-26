[CmdletBinding()]
param(
    [ValidateSet('Check', 'Retrieve', 'Live', 'RunIde')]
    [string]$Mode = 'RunIde',
    [string]$CourseDirectory,
    [string]$ProfileDirectory = (Join-Path $env:LOCALAPPDATA 'CST3492Assistant')
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path -LiteralPath $ProfileDirectory -PathType Container)) { throw 'Local Tongji profile is missing.' }
$ProfileDirectory = (Resolve-Path -LiteralPath $ProfileDirectory).Path
$profilePath = Join-Path $ProfileDirectory 'config.json'
$secretPath = Join-Path $ProfileDirectory 'tongji-key.dpapi'
if (-not (Test-Path -LiteralPath $profilePath)) { throw 'Local Tongji profile is missing.' }
$localConfig = Get-Content -LiteralPath $profilePath -Raw | ConvertFrom-Json
if (-not $CourseDirectory) { $CourseDirectory = $localConfig.courseDirectory }
$CourseDirectory = (Resolve-Path -LiteralPath $CourseDirectory).Path
$launcher = Join-Path $localConfig.gradleHome 'bin\gradle.bat'
$javac = Join-Path $localConfig.jdkPath 'bin\javac.exe'
if (-not (Test-Path -LiteralPath $launcher) -or -not (Test-Path -LiteralPath $javac)) {
    throw 'The local profile must point to Gradle 9.6.0 and a full JDK 25.'
}
if ($Mode -eq 'Check') {
    [PSCustomObject]@{Provider=$localConfig.provider;Endpoint=$localConfig.baseUrl;Model=$localConfig.model;
        ProtectedKeyAvailable=(Test-Path -LiteralPath $secretPath);CourseDirectory=$CourseDirectory}
    return
}

$names = @('JAVA_HOME', 'CST3492_CHAT_MODE', 'CST3492_LLM_PROVIDER', 'CST3492_LLM_BASE_URL',
    'CST3492_LLM_MODEL', 'CST3492_LLM_JSON_MODE', 'CST3492_LLM_API_KEY')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
Push-Location $repoRoot
try {
    $env:JAVA_HOME = $localConfig.jdkPath
    if ($Mode -in @('Live', 'RunIde')) {
        if (-not (Test-Path -LiteralPath $secretPath)) { throw 'The encrypted Tongji key is missing.' }
        $protected = Get-Content -LiteralPath $secretPath | ConvertTo-SecureString
        $credential = [System.Management.Automation.PSCredential]::new('unused', $protected)
        $env:CST3492_LLM_API_KEY = $credential.GetNetworkCredential().Password
        $env:CST3492_LLM_PROVIDER = $localConfig.provider
        $env:CST3492_LLM_BASE_URL = $localConfig.baseUrl
        $env:CST3492_LLM_MODEL = $localConfig.model
        $env:CST3492_LLM_JSON_MODE = "$($localConfig.jsonMode)".ToLowerInvariant()
        Remove-Item Env:CST3492_CHAT_MODE -ErrorAction SilentlyContinue
    }
    if ($Mode -eq 'RunIde') {
        & $launcher runIde "-PlocalIdePath=$($localConfig.idePath.Replace('\', '/'))" --no-daemon --console=plain
    } else {
        $live = if ($Mode -eq 'Live') { 'true' } else { 'false' }
        & $launcher -p integration-standalone courseAcceptance "-PcourseDirectory=$CourseDirectory" "-PliveCourse=$live" --no-daemon --console=plain
    }
    if ($LASTEXITCODE -ne 0) { throw 'The run did not complete. Inspect the real-course report and provider/network status.' }
} finally {
    foreach ($name in $names) {
        if ($null -eq $previous[$name]) { Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue }
        else { Set-Item -LiteralPath "Env:$name" -Value $previous[$name] }
    }
    Remove-Variable credential,protected -ErrorAction SilentlyContinue
    Pop-Location
}
