$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (!(Test-Path build/lib/RealtorApp.jar)) { & ./build.ps1 }
& java --enable-native-access=ALL-UNNAMED -cp 'build/lib/*' RealtorApp
