$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$taskClasses = Join-Path $taskRoot 'build\eprotectplus-regression-classes'
New-Item -ItemType Directory -Force -Path $taskClasses | Out-Null
$taskJdk = 'C:\Program Files\Java\jdk-17\bin'
$taskSources = @(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'src\eprotectplus\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
$taskSources += Join-Path $PSScriptRoot 'EprotectPlusRegression.java'
& (Join-Path $taskJdk 'javac.exe') -encoding UTF-8 -d $taskClasses @taskSources
if ($LASTEXITCODE -ne 0) { throw 'Regression compilation failed' }
$taskWork = Join-Path $taskRoot 'build\eprotectplus-regression-work'
New-Item -ItemType Directory -Force -Path $taskWork | Out-Null
Push-Location $taskWork
try {
    & (Join-Path $taskJdk 'java.exe') -cp $taskClasses EprotectPlusRegression
    if ($LASTEXITCODE -ne 0) { throw 'Regression checks failed' }
} finally {
    Pop-Location
}
