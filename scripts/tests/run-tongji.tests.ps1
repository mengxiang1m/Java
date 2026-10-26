$ErrorActionPreference = 'Stop'
$launcherScript = Join-Path (Split-Path -Parent $PSScriptRoot) 'run-tongji.ps1'
$temporaryParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
$testDirectory = Join-Path $temporaryParent ('CST3492-Launcher-' + [Guid]::NewGuid().ToString('N'))
$saved = @{}
$names = @('JAVA_HOME','CST3492_CHAT_MODE','CST3492_LLM_PROVIDER','CST3492_LLM_BASE_URL',
    'CST3492_LLM_MODEL','CST3492_LLM_JSON_MODE','CST3492_LLM_API_KEY')
foreach ($name in $names) { $saved[$name] = [Environment]::GetEnvironmentVariable($name) }
$callerDirectory = (Get-Location).Path
try {
    New-Item -ItemType Directory -Path "$testDirectory\gradle\bin","$testDirectory\jdk\bin" -Force | Out-Null
    New-Item -ItemType File -Path "$testDirectory\jdk\bin\javac.exe" -Force | Out-Null
    $fakeGradle = "$testDirectory\gradle\bin\gradle.bat"
    '@exit /b 0' | Set-Content -LiteralPath $fakeGradle
    @{provider='tongji';baseUrl='https://example.invalid/v1';model='test-fixture';jsonMode=$true;
        courseDirectory=$testDirectory;jdkPath="$testDirectory\jdk";idePath=$testDirectory;
        gradleHome="$testDirectory\gradle"} | ConvertTo-Json | Set-Content -LiteralPath "$testDirectory\config.json"
    ConvertTo-SecureString 'test-only-placeholder' -AsPlainText -Force | ConvertFrom-SecureString |
        Set-Content -LiteralPath "$testDirectory\tongji-key.dpapi"

    Remove-Item Env:CST3492_LLM_API_KEY -ErrorAction SilentlyContinue
    & $launcherScript -Mode Live -ProfileDirectory $testDirectory
    if ($null -ne [Environment]::GetEnvironmentVariable('CST3492_LLM_API_KEY')) {
        throw 'An originally absent key variable must remain absent after a successful launch.'
    }
    if ((Get-Location).Path -ne $callerDirectory) { throw 'Caller directory changed.' }
    Write-Output 'PASS: success restores absent key and caller directory.'

    $env:CST3492_LLM_API_KEY = 'previous-test-only-value'
    $env:CST3492_CHAT_MODE = 'mock'
    & $launcherScript -Mode Live -ProfileDirectory $testDirectory
    if ($env:CST3492_LLM_API_KEY -ne 'previous-test-only-value' -or $env:CST3492_CHAT_MODE -ne 'mock') {
        throw 'Existing process settings were not restored.'
    }
    Write-Output 'PASS: success restores existing process settings.'

    '@exit /b 2' | Set-Content -LiteralPath $fakeGradle
    $failed = $false
    try { & $launcherScript -Mode Live -ProfileDirectory $testDirectory } catch { $failed = $true }
    if (-not $failed -or $env:CST3492_LLM_API_KEY -ne 'previous-test-only-value' -or (Get-Location).Path -ne $callerDirectory) {
        throw 'Failed launch did not restore process state.'
    }
    Write-Output 'PASS: failed launch restores process state.'
} finally {
    foreach ($name in $names) {
        if ($null -eq $saved[$name]) { Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue }
        else { Set-Item -LiteralPath "Env:$name" -Value $saved[$name] }
    }
    $resolvedTarget = [IO.Path]::GetFullPath($testDirectory)
    if (-not $resolvedTarget.StartsWith($temporaryParent, [StringComparison]::OrdinalIgnoreCase) -or
            -not ([IO.Path]::GetFileName($resolvedTarget)).StartsWith('CST3492-Launcher-')) {
        throw 'Refusing to clean an unexpected temporary target.'
    }
    if (Test-Path -LiteralPath $resolvedTarget) { Remove-Item -LiteralPath $resolvedTarget -Recurse -Force }
}
exit 0
