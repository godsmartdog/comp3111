param(
    [switch]$Tests,
    [switch]$Web,
    [switch]$SmokeTest,
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
        Select-Object -ExpandProperty FullName |
        Sort-Object

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($OutputFile, $sourceFiles, $utf8NoBom)
}

$workspaceRoot = Resolve-Path $PSScriptRoot
$sourceRoot = Resolve-Path (Join-Path $workspaceRoot "..")
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$projectRoot = Resolve-Path (Join-Path $scriptDir "..\..")  # Goes up 2 levels to project root

$javaCandidates = @(
    (Join-Path $env:JAVA_HOME "bin\java.exe"),
    (Join-Path $env:JAVA_HOME "bin\java")
)
$javacCandidates = @(
    (Join-Path $env:JAVA_HOME "bin\javac.exe"),
    (Join-Path $env:JAVA_HOME "bin\javac")
)

$java = Resolve-ToolPath -Candidates $javaCandidates -ToolName "java"
$javac = Resolve-ToolPath -Candidates $javacCandidates -ToolName "javac"

Write-Host "Using java: $java"
Write-Host "Using javac: $javac"
$testSources = $null
$fxSources = $null
Push-Location $sourceRoot
try {
    if (!$Tests -and !$Web -and !$SmokeTest) {
        Write-Host "No mode provided. Defaulting to -Web."
        $Web = $true
    }

    if ($Tests) {
        $testOutput = Join-Path $sourceRoot "out-tests"
        $testSources = [System.IO.Path]::GetTempFileName()

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

    if ($Web -or $SmokeTest) {
        $fxOutput = Join-Path $sourceRoot "out-fx"
        $fxSources = [System.IO.Path]::GetTempFileName()

        Write-Host "Preparing full source list..."
        Write-SourceList -Folders @("Library") -OutputFile $fxSources

        Ensure-Directory -Path $fxOutput

        Write-Host "Compiling application (web UI + services)..."
        & $javac -encoding UTF-8 -d $fxOutput "@$fxSources"
        if ($LASTEXITCODE -ne 0) {
            throw "Application compilation failed."
        }

        if (!$CompileOnly) {
            $appArgs = @(
                "-cp", $fxOutput,
                "Library.Ui.LibraryManagementApp"
            )

            if ($SmokeTest) {
                $appArgs += "--smoke-test"
                Write-Host "Running web UI smoke test..."
            } else {
                Write-Host "Starting web UI on http://localhost:8080 ..."
            }

            & $java @appArgs
            if ($LASTEXITCODE -ne 0) {
                throw "Application run failed."
            }
        }
    }

    Write-Host "Done."
}
finally {
    Pop-Location
    if ($testSources -and (Test-Path $testSources)) {
        Remove-Item -Path $testSources -Force -ErrorAction SilentlyContinue
    }
    if ($fxSources -and (Test-Path $fxSources)) {
        Remove-Item -Path $fxSources -Force -ErrorAction SilentlyContinue
    }
}