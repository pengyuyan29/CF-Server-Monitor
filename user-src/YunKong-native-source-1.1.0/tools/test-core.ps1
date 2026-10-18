param([Parameter(Mandatory=$true)][string]$GradleLib)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $root
try {
    $out = Join-Path $root '.tooling'
    New-Item -ItemType Directory -Force -Path $out | Out-Null
    & node (Join-Path $PSScriptRoot 'generate-crypto-fixture.cjs') (Join-Path $out 'crypto-fixture.json')
    if ($LASTEXITCODE -ne 0) { throw 'Node fixture generation failed' }
    $stdlib = Get-ChildItem -LiteralPath $GradleLib -Filter 'kotlin-stdlib-*.jar' | Select-Object -First 1 -ExpandProperty FullName
    $serialization = Get-ChildItem -LiteralPath $GradleLib -Filter 'kotlinx-serialization-*-jvm-*.jar' | Select-Object -ExpandProperty FullName
    $classpath = (@($stdlib) + @($serialization)) -join [IO.Path]::PathSeparator
    $sources = @('app/src/main/java/com/yunkong/monitor/data/JsonExt.kt','app/src/main/java/com/yunkong/monitor/data/SingBoxCore.kt','app/src/main/java/com/yunkong/monitor/data/BlogCrypto.kt','tools/CoreChecks.kt')
    & java -cp (Join-Path $GradleLib '*') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath $classpath -d (Join-Path $out 'checks.jar') @sources
    if ($LASTEXITCODE -ne 0) { throw 'Core Kotlin compilation failed' }
    & java -cp ((Join-Path $out 'checks.jar') + [IO.Path]::PathSeparator + $classpath) com.yunkong.monitor.data.CoreChecksKt (Join-Path $out 'crypto-fixture.json')
    if ($LASTEXITCODE -ne 0) { throw 'Core checks failed' }
} finally { Pop-Location }
