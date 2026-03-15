[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,

    [Parameter(Mandatory = $true)]
    [string]$ProjectKey,

    [string]$SyncDir = ".ygflow",

    [string[]]$FlowCodes = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Encode-Segment {
    param([Parameter(Mandatory = $true)][string]$Value)
    return [System.Uri]::EscapeDataString($Value)
}

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RelativePath,

        [switch]$Raw
    )

    $uri = $script:NormalizedBaseUrl + $RelativePath
    if ($Raw) {
        return Invoke-WebRequest -Uri $uri -Method Get -UseBasicParsing -TimeoutSec 30
    }
    return Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec 30
}

function Normalize-Json {
    param([Parameter(Mandatory = $true)][string]$Json)
    return ($Json | ConvertFrom-Json) | ConvertTo-Json -Depth 100 -Compress
}

function Read-FileUtf8 {
    param([Parameter(Mandatory = $true)][string]$Path)
    return [System.IO.File]::ReadAllText((Resolve-Path $Path), [System.Text.Encoding]::UTF8)
}

function Resolve-PublishedVersionNo {
    param([Parameter(Mandatory = $true)][string]$FlowCode)

    $versions = @(Invoke-Api "/api/projects/$($script:EncodedProjectKey)/flows/$(Encode-Segment $FlowCode)/versions")
    $published = $versions | Where-Object { $_.published -eq $true } | Select-Object -First 1
    if ($null -eq $published) {
        return $null
    }
    return [int]$published.versionNo
}

$script:NormalizedBaseUrl = $BaseUrl.TrimEnd("/")
$script:EncodedProjectKey = Encode-Segment $ProjectKey
$rootDir = [System.IO.Path]::GetFullPath($SyncDir)
$rulesDir = Join-Path $rootDir "rules"

$failures = New-Object System.Collections.Generic.List[string]

Write-Host "Verify project '$ProjectKey' against $script:NormalizedBaseUrl" -ForegroundColor Cyan
Write-Host "Local sync dir: $rootDir" -ForegroundColor Cyan

$entrypointsPath = Join-Path $rootDir "entrypoints.json"
if (-not (Test-Path $entrypointsPath)) {
    $failures.Add("Missing local entrypoints file: $entrypointsPath")
} else {
    $remoteEntrypoints = (Invoke-Api "/api/projects/$script:EncodedProjectKey/entrypoints" -Raw).Content
    $localEntrypoints = Read-FileUtf8 $entrypointsPath
    if ((Normalize-Json $remoteEntrypoints) -ne (Normalize-Json $localEntrypoints)) {
        $failures.Add("entrypoints.json does not match orchestrator")
    } else {
        Write-Host "OK entrypoints.json" -ForegroundColor Green
    }
}

$flows = @(Invoke-Api "/api/projects/$script:EncodedProjectKey/flows")
if ($FlowCodes.Count -gt 0) {
    $allowed = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($code in $FlowCodes) {
        if (-not [string]::IsNullOrWhiteSpace($code)) {
            [void]$allowed.Add($code)
        }
    }
    $flows = @($flows | Where-Object { $allowed.Contains([string]$_.code) })
}

foreach ($flow in $flows) {
    $flowCode = [string]$flow.code
    if ([string]::IsNullOrWhiteSpace($flowCode)) {
        continue
    }

    $publishedVersionNo = Resolve-PublishedVersionNo -FlowCode $flowCode
    if ($null -eq $publishedVersionNo) {
        Write-Host "SKIP $flowCode (no published version)" -ForegroundColor Yellow
        continue
    }

    $localFile = Join-Path $rulesDir ($flowCode + ".json")
    if (-not (Test-Path $localFile)) {
        $failures.Add("Missing local rule file for published flow '$flowCode': $localFile")
        continue
    }

    $remoteVersion = Invoke-Api "/api/projects/$script:EncodedProjectKey/flows/$(Encode-Segment $flowCode)/versions/$publishedVersionNo"
    $remoteJson = [string]$remoteVersion.contentJson
    $localJson = Read-FileUtf8 $localFile

    if ((Normalize-Json $remoteJson) -ne (Normalize-Json $localJson)) {
        $failures.Add("Rule file mismatch for '$flowCode' published v$publishedVersionNo")
        continue
    }

    Write-Host "OK $flowCode v$publishedVersionNo" -ForegroundColor Green
}

if ($failures.Count -gt 0) {
    Write-Host ""
    Write-Host "Verification failed:" -ForegroundColor Red
    foreach ($failure in $failures) {
        Write-Host " - $failure" -ForegroundColor Red
    }
    exit 1
}

Write-Host ""
Write-Host "Verification passed." -ForegroundColor Cyan
