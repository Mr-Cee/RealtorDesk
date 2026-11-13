param([switch]$Installer, [switch]$Test)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force build/lib,build/classes,dist | Out-Null
& java tools/CreateAppIcon.java build/branding
if ($LASTEXITCODE -ne 0) { throw 'App icon generation failed' }
$appVersion = (Get-Content VERSION -Raw).Trim()
if ($appVersion -notmatch '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$') { throw 'Invalid VERSION' }
Set-Content -LiteralPath build/BuildInfo.java -Encoding utf8 -Value "final class BuildInfo { static final String VERSION = `"$appVersion`"; }"
Get-ChildItem build/classes -Filter '*.class' -File | Remove-Item
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
$sources = @(Get-ChildItem src -Filter '*.java' -File | ForEach-Object FullName)
& javac -encoding UTF-8 --release 21 -cp 'build/lib/*' -d build/classes build/BuildInfo.java @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
& jar --create --file build/lib/RealtorApp.jar --main-class RealtorApp -C build/classes . -C build/branding app-icon.png
if ($LASTEXITCODE -ne 0) { throw 'JAR creation failed' }
if ($Test) {
 New-Item -ItemType Directory -Force build/testclasses | Out-Null
 $testSources = @(Get-ChildItem tests -Filter '*.java' -File | ForEach-Object FullName)
 & javac -encoding UTF-8 --release 21 -cp 'build/lib/*;build/classes' -d build/testclasses @testSources
 if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
 & java --enable-native-access=ALL-UNNAMED -cp 'build/lib/*;build/classes;build/testclasses' UpdaterTest
 if ($LASTEXITCODE -ne 0) { throw 'Updater tests failed' }
 & java '-Djava.awt.headless=true' -cp 'build/lib/*;build/classes;build/testclasses' ThemeTest
 if ($LASTEXITCODE -ne 0) { throw 'Theme tests failed' }
}
if ($Installer) {
 $wixCommand = if (Test-Path build/wix5/wix.exe) { Join-Path $PSScriptRoot 'build/wix5/wix.exe' } else { 'wix' }
 if (!(Get-Command $wixCommand -ErrorAction SilentlyContinue)) { throw 'WiX is required. See README.md for setup.' }
 $runtimePath = Join-Path $PSScriptRoot 'build/runtime-tls'
 if (!(Test-Path "$runtimePath/bin/javaw.exe")) {
  & jlink --add-modules java.desktop,java.sql,java.net.http,jdk.unsupported,jdk.crypto.ec --strip-debug --no-header-files --no-man-pages --output $runtimePath
  if ($LASTEXITCODE -ne 0) { throw 'Bundled runtime creation failed' }
 }
 if ($Test) {
  & "$runtimePath/bin/java.exe" --enable-native-access=ALL-UNNAMED -cp 'build/lib/*;build/classes;build/testclasses' UpdaterTest
  if ($LASTEXITCODE -ne 0) { throw 'Bundled runtime checks failed' }
  & "$runtimePath/bin/java.exe" '-Djava.awt.headless=true' -cp 'build/lib/*;build/classes;build/testclasses' ThemeTest
  if ($LASTEXITCODE -ne 0) { throw 'Bundled runtime theme checks failed' }
 }
 $nativeOutput = Join-Path $PSScriptRoot ("build/native-" + [Guid]::NewGuid().ToString('N'))
 & jpackage --type app-image --name RealtorDesk --app-version $appVersion --vendor RealtorDesk --description RealtorDesk --input build/lib --main-jar RealtorApp.jar --main-class RealtorApp --runtime-image $runtimePath --icon build/branding/RealtorDesk.ico --dest $nativeOutput --java-options '--enable-native-access=ALL-UNNAMED'
 if ($LASTEXITCODE -ne 0) { throw 'Native RealtorDesk.exe launcher creation failed' }
 $imagePath = Join-Path $nativeOutput 'RealtorDesk'
 if ($Test) {
  $configPath = Join-Path $imagePath 'app/RealtorDesk.cfg'
  $originalConfig = [IO.File]::ReadAllText($configPath)
  $probeJar = Join-Path $imagePath 'app/LauncherProbe.jar'
  $reportPath = Join-Path $nativeOutput 'launcher-check.txt'
  try {
   & jar --create --file $probeJar -C build/testclasses LauncherProbe.class
   if ($LASTEXITCODE -ne 0) { throw 'Launcher probe packaging failed' }
   $probeConfig = $originalConfig.Replace('app.mainclass=RealtorApp','app.mainclass=LauncherProbe')
   $probeConfig = $probeConfig.Replace('app.classpath=', 'app.classpath=$APPDIR\LauncherProbe.jar;')
   [IO.File]::WriteAllText($configPath, $probeConfig, [Text.UTF8Encoding]::new($false))
   $probeProcess = Start-Process -FilePath (Join-Path $imagePath 'RealtorDesk.exe') -ArgumentList ('"' + $reportPath + '"') -WindowStyle Hidden -PassThru
   if (!$probeProcess.WaitForExit(30000)) { $probeProcess.Kill(); throw 'Native launcher probe timed out' }
   if ($probeProcess.ExitCode -ne 0 -or !(Test-Path $reportPath)) { throw 'Native launcher probe failed' }
   $report = Get-Content $reportPath -Raw
   if (!$report.Contains("version $appVersion")) { throw 'Native launcher version mismatch' }
   Write-Output $report
  } finally {
   [IO.File]::WriteAllText($configPath, $originalConfig, [Text.UTF8Encoding]::new($false))
   Remove-Item -LiteralPath $probeJar -ErrorAction SilentlyContinue
  }
 }
 & $wixCommand build installer/RealtorDesk.wxs installer/InstallUI.wxs -arch x64 -d "AppVersion=$appVersion" -d "RuntimeSource=$imagePath\runtime" -d "AppSource=$imagePath\app" -d "LauncherSource=$imagePath" -d "IconSource=$PSScriptRoot\build\branding\RealtorDesk.ico" -o "dist/RealtorDesk-$appVersion.msi"
 if ($LASTEXITCODE -ne 0) { throw 'Windows MSI installer creation failed' }
 $installerName = "RealtorDesk-$appVersion.msi"
 $hash = (Get-FileHash -LiteralPath "dist/$installerName" -Algorithm SHA256).Hash.ToLowerInvariant()
 Set-Content -LiteralPath "dist/$installerName.sha256" -Encoding ascii -Value "$hash  $installerName"
}
