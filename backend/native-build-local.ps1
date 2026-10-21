$ErrorActionPreference = "Stop"

if (-not $env:GRAALVM_HOME) {
    $env:GRAALVM_HOME = "D:\graalvm-25.4.4.1.1+1.1"
}
if (-not (Test-Path $env:GRAALVM_HOME)) {
    throw "GRAALVM_HOME not found: $env:GRAALVM_HOME"
}
if (-not $env:FIREBASE_PROJECT_ID) {
    $env:FIREBASE_PROJECT_ID = "aot-build-placeholder"
}

$env:JAVA_HOME = $env:GRAALVM_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Push-Location $PSScriptRoot
try {
    & mvn -B -ntp -DskipTests -Pnative native:compile
    if ($LASTEXITCODE -ne 0) { throw "native build failed with exit code $LASTEXITCODE" }
}
finally {
    Pop-Location
}
