param([Parameter(Mandatory=$true)][string]$GradleLib)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $root
try {
    New-Item -ItemType Directory -Force -Path '.tooling' | Out-Null
    $compiler = Get-ChildItem -LiteralPath $GradleLib -Filter 'kotlin-compiler-embeddable-*.jar' | Select-Object -First 1 -ExpandProperty FullName
    $stdlib = Get-ChildItem -LiteralPath $GradleLib -Filter 'kotlin-stdlib-*.jar' | Select-Object -First 1 -ExpandProperty FullName
    $cp = @($compiler,$stdlib) -join [IO.Path]::PathSeparator
    & java -cp (Join-Path $GradleLib '*') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath $cp -d '.tooling/syntax.jar' 'tools/SyntaxCheck.kt'
    if ($LASTEXITCODE -ne 0) { throw 'Syntax checker compilation failed' }
    & java -cp ('.tooling/syntax.jar' + [IO.Path]::PathSeparator + (Join-Path $GradleLib '*')) SyntaxCheckKt
    if ($LASTEXITCODE -ne 0) { throw 'Kotlin syntax errors detected' }
} finally { Pop-Location }
