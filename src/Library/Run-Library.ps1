param(
    [switch]$Tests,
    [switch]$Web,
    [switch]$SmokeTest,
    [switch]$CompileOnly
)
# model need change then BookReviewService java :104 and LibraryApiHandlers java :6062
# powershell -ExecutionPolicy Bypass -File "Run-Library.ps1"
$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:GOOGLE_BOOKS_API_KEY = "AIzaSyBKMNFbGxR0Zj7ihJWsPqbj4SwCH0LprWk"
$env:INFERENCE_BASE_URL = "http://127.0.0.1:1234/v1"
$env:INFERENCE_API_KEY = ""
$env:INFERENCE_MODEL = "local-model"
$env:S3_ENDPOINT = "https://s3.us.archive.org/"
$env:S3_REGION = "us-east-1"
$env:S3_ACCESS_KEY = "yvYHv4GfeuJ7Kqut"
$env:S3_SECRET_KEY = "ElDaSOgwK30QTEag"
$env:IA_ACCESS_KEY = "yvYHv4GfeuJ7Kqut"
$env:IA_SECRET_KEY = "ElDaSOgwK30QTEag"
$env:GGUF_MODEL_PATH = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "Meta-Llama-3.1-8B-Instruct_Q4_K_S.gguf"))

# Ensure absolute path to Meta-Llama model - NEVER use SmolLM2
$metaLlamaModel = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "Meta-Llama-3.1-8B-Instruct-Q4_K_S.gguf"))
$env:GGUF_MODEL_PATH = $metaLlamaModel

# Verify Meta-Llama model exists
if (!(Test-Path $metaLlamaModel)) {
    Write-Host "ERROR: Meta-Llama model not found at: $metaLlamaModel" -ForegroundColor Red
    throw "Meta-Llama model file is missing!"
}

# Define JavaFX path
$javafxLib = "C:\Program Files\Java\javafx-sdk-21.0.10\lib"
$javafxModules = "javafx.controls,javafx.fxml"

Write-Host "JAVA_HOME set to: $env:JAVA_HOME" -ForegroundColor Green
Write-Host "Google Books API key set." -ForegroundColor Green
Write-Host "GGUF model path (ABSOLUTE): $env:GGUF_MODEL_PATH" -ForegroundColor Green
Write-Host "JavaFX path: $javafxLib" -ForegroundColor Green
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
$llamaJar = Join-Path $sourceRoot "Library\third-party\llama\llama-4.1.0.jar"
Write-Host "Llama jar: $llamaJar" -ForegroundColor Green

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
        & $javac -encoding UTF-8 -cp $llamaJar -d $testOutput "@$testSources"
        if ($LASTEXITCODE -ne 0) {
            throw "Integration test compilation failed."
        }

        if (!$CompileOnly) {
            Write-Host "Running integration tests..."
            & $java -cp "$testOutput;$llamaJar" Library.Test.LibraryIntegrationTest
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
        & $javac -encoding UTF-8 -cp $llamaJar -d $fxOutput "@$fxSources"
        if ($LASTEXITCODE -ne 0) {
            throw "Application compilation failed."
        }

        if (!$CompileOnly) {
            $appArgs = @(
                "-cp", "$fxOutput;$llamaJar",
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