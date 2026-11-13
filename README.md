# RealtorDesk

Initial Windows desktop prototype for an individual realtor. Java 21+, Swing, SQLite. Version 0.1.0.

## Run and package

From PowerShell with a JDK on PATH:

```powershell
./build.ps1
./run.ps1
./build.ps1 -Installer
```

The build uses pinned JDBC, logging and JSON dependencies from the local Maven cache when available, otherwise downloading them from Maven Central. Installer creation uses jlink and WiX directly to build a per-user Windows MSI that includes the Java runtime and libraries. End users do not need Java, SQLite, or a database service installed separately. Output is in dist/. Keep the upgrade UUID fixed across versions.

To use WiX 5 alongside other WiX installations, run from this project folder:

```powershell
dotnet tool install wix --version 5.0.2 --tool-path ./build/wix5
```

The build prefers this project-local WiX installation. WiX 7 requires explicit acceptance of its maintenance-fee EULA; this project does not accept it automatically. The Windows x64 MSI builds successfully with WiX 5.0.2. Its contents were inspected for the application, dependencies and bundled runtime, and SQLite insert/read and version-comparison checks pass with that runtime. Installation, shortcuts, upgrades and full UI behavior still need end-to-end testing on Windows.

## Current features

- Add/edit clients and their contact details, status and notes.
- Add client-linked properties and open addresses in Google Maps in the default browser.
- Log calls with optional validated follow-up dates.
- Copy document attachments into app-managed storage and open them.
- Sort tables; refresh to show changes in related client names.
- Configure a public GitHub owner/repository; check latest releases at startup and on demand. New stable semantic versions offer a browser link to the release download page.

## Data

Records, settings, and copied attachments live in `%LOCALAPPDATA%/RealtorDesk`, outside the installation directory. They persist across application upgrades. Each Windows user has a separate database. Back up the entire directory with the app closed. The database and documents are not encrypted by the app. Map searches transmit the selected address to Google. No client data is uploaded to GitHub.

## Release process

The public repository is https://github.com/Mr-Cee/RealtorDesk and is the default update source. Set the same numeric version in RealtorApp.VERSION, build.ps1 and installer/RealtorDesk.wxs. Build and test the Windows installer, create a matching vMAJOR.MINOR.PATCH GitHub Release, and attach the MSI. No embedded GitHub token is needed for public release checks. Downloads and installation currently require user action in the browser. Code signing, verified in-app downloads, automated release builds, backups, schema migration infrastructure, editing other record types, reminders, embedded maps and multi-device sync remain future work.

This is a foundation prototype, not yet a production client-record system. Validate installation/upgrades and implement backups before storing your only copy of business records.
