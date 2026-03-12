param(
    [switch]$Tests,
    [switch]$JavaFX,
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
        Select-Object -ExpandProperty FullName

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($OutputFile, $sourceFiles, $utf8NoBom)
}

$workspaceRoot = Resolve-Path $PSScriptRoot
$sourceRoot = Resolve-Path (Join-Path $workspaceRoot "..")

# $javac = Resolve-ToolPath -ToolName "javac" -Candidates @(
#     "C:\Users\lam09\AppData\Roaming\Code\User\globalStorage\pleiades.java-extension-pack-jdk\java\17\bin\javac.exe",
#     "C:\Program Files\Java\jdk-24\bin\javac.exe",
#     "C:\Users\lam09\.jdks\openjdk-24.0.1\bin\javac.exe"
# )

# $java = Resolve-ToolPath -ToolName "java" -Candidates @(
#     "C:\Users\lam09\AppData\Roaming\Code\User\globalStorage\pleiades.java-extension-pack-jdk\java\17\bin\java.exe",
#     "C:\Program Files\Java\jdk-24\bin\java.exe",
#     "C:\Users\lam09\.jdks\openjdk-24.0.1\bin\java.exe"
# )

# $javaFxLib = "C:\Users\lam09\.javafx\javafx-sdk-17.0.2\lib"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$projectRoot = Resolve-Path (Join-Path $scriptDir "..\..")  # Goes up 2 levels to project root

$javac = Join-Path $projectRoot "src\runtime\jdk-25.0.2\bin\javac.exe"     # Added src\
$java = Join-Path $projectRoot "src\runtime\jdk-25.0.2\bin\java.exe"       # Added src\
$javaFxLib = Join-Path $projectRoot "src\runtime\javafx-sdk-17.0.2\lib"


if (!(Test-Path $javaFxLib)) {
    Write-Warning "JavaFX SDK not found at $javaFxLib"
    Write-Warning "JavaFX compile/run options will fail until that folder exists."
}

Push-Location $sourceRoot
try {
    if (!$Tests -and !$JavaFX -and !$SmokeTest) {
        Write-Host "Usage examples:"
        Write-Host "  .\Run-Library.ps1 -Tests"
        Write-Host "  .\Run-Library.ps1 -JavaFX"
        Write-Host "  .\Run-Library.ps1 -SmokeTest"
        Write-Host "  .\Run-Library.ps1 -Tests -JavaFX"
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

    if ($JavaFX -or $SmokeTest) {
        $fxOutput = Join-Path $sourceRoot "out-fx"
        $fxSources = Join-Path $sourceRoot "sources-all.txt"

        Write-Host "Preparing full source list..."
        Write-SourceList -Folders @("Library") -OutputFile $fxSources

        Ensure-Directory -Path $fxOutput

        Write-Host "Compiling JavaFX application..."
        & $javac --module-path $javaFxLib --add-modules javafx.controls -encoding UTF-8 -d $fxOutput "@$fxSources"
        if ($LASTEXITCODE -ne 0) {
            throw "JavaFX compilation failed."
        }

        if (!$CompileOnly) {
            $appArgs = @(
                "--module-path", $javaFxLib,
                "--add-modules", "javafx.controls",
                "-cp", $fxOutput,
                "Library.Ui.LibraryManagementApp"
            )

            if ($SmokeTest) {
                $appArgs += "--smoke-test"
                Write-Host "Running JavaFX smoke test..."
            } else {
                Write-Host "Starting JavaFX application..."
            }

            & $java @appArgs
            if ($LASTEXITCODE -ne 0) {
                throw "JavaFX run failed."
            }
        }
    }

    Write-Host "Done."
}
finally {
    Pop-Location
}
