[CmdletBinding()]
param(
    [switch]$Apply,
    [switch]$Restore,
    [string]$ProfileDirectory = (Join-Path $env:LOCALAPPDATA 'CST3492Assistant')
)

$ErrorActionPreference = 'Stop'
$hostName = 'agi.tongji.edu.cn'
$backupPath = Join-Path $ProfileDirectory 'network-before-repair.json'
$resultPath = Join-Path $ProfileDirectory 'network-repair-result.json'
$internetSettings = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings'

function Restore-Network($backup) {
    Set-NetIPInterface -InterfaceIndex $backup.interfaceIndex -AddressFamily IPv4 `
        -AutomaticMetric Disabled -InterfaceMetric $backup.interfaceMetric
    if ($backup.automaticMetric -eq 'Enabled') {
        Set-NetIPInterface -InterfaceIndex $backup.interfaceIndex -AddressFamily IPv4 -AutomaticMetric Enabled
    }
    if ($backup.proxyOverrideExisted) {
        Set-ItemProperty -LiteralPath $internetSettings -Name ProxyOverride -Value $backup.proxyOverride
    } else {
        Remove-ItemProperty -LiteralPath $internetSettings -Name ProxyOverride -ErrorAction SilentlyContinue
    }
    Clear-DnsClientCache
}

if ($Apply -or $Restore) {
    $principal = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Administrator permission is required to change the VPN interface metric.'
    }
}
if ($Restore) {
    Restore-Network (Get-Content -LiteralPath $backupPath -Raw | ConvertFrom-Json)
    [ordered]@{status='RESTORED';date=(Get-Date -Format o);backup=$backupPath} |
        ConvertTo-Json | Set-Content -LiteralPath $resultPath -Encoding utf8
    Write-Output 'Restored the saved VPN metric and proxy exceptions.'
    return
}

$adapters = @(Get-NetAdapter | Where-Object {
    $_.InterfaceDescription -eq 'Array Networks VPN Adapter' -and $_.Status -eq 'Up'
})
if ($adapters.Count -ne 1) { throw 'Connect exactly one campus MotionPro VPN before running this repair.' }
$interface = Get-NetIPInterface -InterfaceIndex $adapters[0].ifIndex -AddressFamily IPv4
$campusAddresses = @(Resolve-DnsName $hostName -Server 127.0.0.1 -Type A -DnsOnly -ErrorAction Stop |
    Where-Object IPAddress | Select-Object -ExpandProperty IPAddress)
if (-not $campusAddresses -or @($campusAddresses | Where-Object { $_ -notlike '10.*' }).Count) {
    throw 'The campus DNS did not return the expected private network addresses; no settings changed.'
}
$proxy = Get-ItemProperty -LiteralPath $internetSettings
if (-not $Apply) {
    [PSCustomObject]@{ Interface=$interface.InterfaceAlias; CurrentMetric=$interface.InterfaceMetric;
        ProposedMetric=1; ProxyExceptionToAdd=$hostName; CampusDnsAddresses=$campusAddresses;
        Apply=$false } | ConvertTo-Json
    return
}

New-Item -ItemType Directory -Path $ProfileDirectory -Force | Out-Null
if (Test-Path -LiteralPath $backupPath) { throw 'A backup already exists. Restore it before applying another repair.' }
$backup = [ordered]@{ interfaceIndex=$interface.InterfaceIndex; interfaceMetric=$interface.InterfaceMetric;
    automaticMetric="$($interface.AutomaticMetric)"; proxyOverrideExisted=($null -ne $proxy.PSObject.Properties['ProxyOverride']);
    proxyOverride=$proxy.ProxyOverride; date=(Get-Date -Format o) }
$backup | ConvertTo-Json | Set-Content -LiteralPath $backupPath -Encoding utf8
try {
    Set-NetIPInterface -InterfaceIndex $interface.InterfaceIndex -AddressFamily IPv4 `
        -AutomaticMetric Disabled -InterfaceMetric 1
    $exceptions = @($proxy.ProxyOverride -split ';' | Where-Object { $_ })
    if ($hostName -notin $exceptions) { $exceptions += $hostName }
    Set-ItemProperty -LiteralPath $internetSettings -Name ProxyOverride -Value ($exceptions -join ';')
    Clear-DnsClientCache
    $resolved = @(Resolve-DnsName $hostName -Type A -DnsOnly -ErrorAction Stop |
        Where-Object IPAddress | Select-Object -ExpandProperty IPAddress)
    if (-not $resolved -or @($resolved | Where-Object { $_ -notin $campusAddresses }).Count) {
        throw 'Default DNS still selects a non-campus address.'
    }
    [ordered]@{status='APPLIED';date=(Get-Date -Format o);addresses=$resolved;backup=$backupPath} |
        ConvertTo-Json | Set-Content -LiteralPath $resultPath -Encoding utf8
    Write-Output 'Campus DNS priority and the Tongji-only proxy exception were applied and verified.'
} catch {
    $failure = $_.Exception.Message
    Restore-Network $backup
    [ordered]@{status='ROLLED_BACK';date=(Get-Date -Format o);error=$failure} |
        ConvertTo-Json | Set-Content -LiteralPath $resultPath -Encoding utf8
    throw "Repair rolled back: $failure"
}
