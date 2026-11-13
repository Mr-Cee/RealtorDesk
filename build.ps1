param([switch]$Installer)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force build/lib,build/classes,dist | Out-Null
$deps = @{
 'sqlite-jdbc-3.51.3.0.jar' = 'https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.51.3.0/sqlite-jdbc-3.51.3.0.jar'
 'gson-2.13.2.jar' = 'https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar'
 'slf4j-api-2.0.18.jar' = 'https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/2.0.18/slf4j-api-2.0.18.jar'
}
foreach ($name in $deps.Keys) {
 if (!(Test-Path "build/lib/$name")) {
  $cacheRelative = ([uri]$deps[$name]).AbsolutePath.Substring('/maven2/'.Length)
  $cached = Join-Path $env:USERPROFILE ".m2/repository/$cacheRelative"
  if (Test-Path $cached) { Copy-Item -LiteralPath $cached -Destination "build/lib/$name" }
  else {
   $temporary = "build/lib/$name.download"
   Invoke-WebRequest $deps[$name] -OutFile $temporary
   Move-Item -LiteralPath $temporary -Destination "build/lib/$name" -Force
  }
 }
}
& javac -encoding UTF-8 --release 21 -cp 'build/lib/*' -d build/classes src/RealtorApp.java
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
& jar --create --file build/lib/RealtorApp.jar --main-class RealtorApp -C build/classes .
if ($LASTEXITCODE -ne 0) { throw 'JAR creation failed' }
if ($Installer) {
 & jpackage --type exe --name RealtorDesk --app-version 0.1.0 --input build/lib --main-jar RealtorApp.jar --dest dist --win-per-user-install --win-menu --win-shortcut --win-dir-chooser --win-upgrade-uuid '41fd3758-60c3-471c-933b-0a12968038ec' --java-options '--enable-native-access=ALL-UNNAMED'
 if ($LASTEXITCODE -ne 0) { throw 'Installer creation failed (Windows packaging requires WiX)' }
}
