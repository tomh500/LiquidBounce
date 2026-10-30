param(
    [string]$Root = (Join-Path $PSScriptRoot "..\..")
)

$ErrorActionPreference = "Stop"

$list = Get-Content -Raw (Join-Path $Root "config\list") | ConvertFrom-Json
if ($list.Count -lt 1) {
    throw "config/list must contain at least one configuration"
}

foreach ($entry in $list) {
    if ([string]::IsNullOrWhiteSpace($entry.setting_id)) {
        throw "Every list entry needs setting_id"
    }

    $configPath = Join-Path $Root ("config\{0}.json" -f $entry.setting_id)
    if (-not (Test-Path -LiteralPath $configPath)) {
        throw "Missing configuration file: $configPath"
    }

    $config = Get-Content -Raw $configPath | ConvertFrom-Json
    if ($config.name -ne "autoconfig") {
        throw "$configPath must be an AutoConfig document"
    }
}

$listedIds = @($list | ForEach-Object { $_.setting_id })
foreach ($file in Get-ChildItem -LiteralPath (Join-Path $Root 'config') -Filter '*.json') {
    if ($file.BaseName -cnotin $listedIds) {
        throw "Configuration is absent from config/list: $($file.Name)"
    }
}

Write-Output "CloudServer files are valid."
