param(
    [switch]$Tests,
    [switch]$Web,
    [switch]$CompileOnly
)

$ErrorActionPreference = "Stop"

function Resolve-ToolPath {
    param(
        [string[]]$Candidates,
        [string]$ToolName
    )

    foreach ($candidate in $Candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return $candidate
        }
    }

    $command = Get-Command $ToolName -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    throw "Cannot find $ToolName."
}

function Ensure-Directory {
    param([string]$Path)

    if (!(Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Write-SourceList {
    param(
        [string[]]$Folders,
        [string]$OutputFile
    )

    $sourceFiles = Get-ChildItem -Recurse -File $Folders -Filter *.java |
        Select-Object -ExpandProperty FullName

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($OutputFile, $sourceFiles, $utf8NoBom)
}

$workspaceRoot = Resolve-Path $PSScriptRoot
$sourceRoot = Resolve-Path (Join-Path $workspaceRoot "..")

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$projectRoot = Resolve-Path (Join-Path $scriptDir "..\..")

$javac = Join-Path $projectRoot "src\runtime\jdk-25.0.2\bin\javac.exe"
$java = Join-Path $projectRoot "src\runtime\jdk-25.0.2\bin\java.exe"

Push-Location $sourceRoot
try {
    if (!$Tests -and !$Web) {
        Write-Host "Usage examples:"
        Write-Host "  .\Run-Library.ps1 -Tests"
        Write-Host "  .\Run-Library.ps1 -Web"
        Write-Host "  .\Run-Library.ps1 -Tests -Web"
        exit 0
    }

    if ($Tests) {
        $testOutput = Join-Path $sourceRoot "out-tests"
        $testSources = Join-Path $sourceRoot "sources-tests.txt"

        Write-Host "Preparing test source list..."
        Write-SourceList -Folders @(
            "Library\Exception",
            "Library\Model",
            "Library\Repository",
            "Library\Security",
            "Library\Service",
            "Library\Test"
        ) -OutputFile $testSources

        Ensure-Directory -Path $testOutput

        Write-Host "Compiling integration tests..."
        & $javac -encoding UTF-8 -d $testOutput "@$testSources"
        if ($LASTEXITCODE -ne 0) {
            throw "Integration test compilation failed."
        }

        if (!$CompileOnly) {
            Write-Host "Running integration tests..."
            & $java -cp $testOutput Library.Test.LibraryIntegrationTest
            if ($LASTEXITCODE -ne 0) {
                throw "Integration tests failed."
            }
        }
    }

    if ($Web) {
        $webOutput = Join-Path $sourceRoot "out-web"
        $webSources = Join-Path $sourceRoot "sources-all.txt"

        Write-Host "Preparing full source list..."
        Write-SourceList -Folders @("Library") -OutputFile $webSources

        Ensure-Directory -Path $webOutput

        Write-Host "Compiling web application..."
        & $javac -encoding UTF-8 -d $webOutput "@$webSources"
        if ($LASTEXITCODE -ne 0) {
            throw "Web application compilation failed."
        }

        if (!$CompileOnly) {
            Write-Host "Starting web application at http://localhost:8080 ..."
            & $java -cp $webOutput Library.Js.LibraryManagementApp
            if ($LASTEXITCODE -ne 0) {
                throw "Web application run failed."
            }
        }
    }

    Write-Host "Done."
}
finally {
    Pop-Location
}
