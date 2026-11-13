# RealtorDesk

Initial Windows desktop prototype for an individual realtor. Java 21+, Swing, SQLite. The VERSION file is the source of truth (currently 0.1.15).

## Run and package

From PowerShell with a JDK on PATH:

```powershell
./build.ps1
./run.ps1
./build.ps1 -Test -Installer
```

The build uses pinned JDBC, logging and JSON dependencies from the local Maven cache when available, otherwise downloading them from Maven Central. Installer creation uses jlink, jpackage and WiX to build a per-user Windows MSI that includes the native RealtorDesk.exe launcher, Java runtime and libraries. End users do not need Java, SQLite, or a database service installed separately. Output is in dist/. Keep the upgrade UUID fixed across versions.

Version 0.1.4 introduces a native Windows launcher that hosts Java inside the RealtorDesk.exe process rather than spawning javaw.exe. Desktop and Start menu shortcuts, the taskbar/window icon, and the installed-program icon use the bundled house emblem. Update restarts use RealtorDesk.exe. When an older updater reopens javaw.exe after installing 0.1.4, the app switches to its native launcher before creating windows. The executable needs its adjacent app and runtime folders; it is not a standalone single-file distribution. The installer remains an MSI, which is also the format the in-app updater downloads.

Native packaging tests temporarily run the actual generated EXE with a probe entry point to verify the process name, bundled JVM, icon and SQLite; the probe is removed and the production entry point restored before building the MSI. Local jpackage may be blocked by the Work session's Windows writable-directory checks; the GitHub Windows runner performs the complete native build and launcher verification.

Version 0.1.5 caches verified installers under `%TEMP%/RealtorDesk-updates`. Choosing Not now retains the installer across restarts. Each update check asks GitHub for the latest stable version, then rehashes a matching saved installer before offering installation; only missing or damaged installers are downloaded again. Cache identities include the source URL, hash and size. Verified downloads left by older versions can also be reused. Cache locks and an active installer PID marker prevent normal download/cleanup operations from touching an update in progress.

After the updated installed app opens, startup cleanup removes installers and logs for that version and older versions, including recognized pre-cache temporary folders. It also runs on subsequent update checks. Pending newer installers do not expire when installation is deferred or fails. Failure logs and interrupted incomplete downloads expire after seven days. Cleanup is restricted to app-owned temporary folders, rejects paths that escape those folders through links, and leaves unrelated folders alone. Development launches do not trigger installed-version cleanup. If Windows has a file locked, cleanup retries on a later startup/check. Client records, attachments, and Windows Installer's own repair/uninstall cache are not touched.

Version 0.1.1 adds a folder-selection screen with a Browse dialog. Choose a writable installation folder; setup remains per-user and does not request administrator rights. Both shortcuts resolve the chosen folder, and setup saves it for future upgrades. Client data remains under `%LOCALAPPDATA%/RealtorDesk`. To relocate an existing installation, uninstall it first and then install to the new folder; the uninstaller leaves client data in place.

To use WiX 5 alongside other WiX installations, run from this project folder:

```powershell
dotnet tool install wix --version 5.0.2 --tool-path ./build/wix5
```

The build prefers this project-local WiX installation. WiX 7 requires explicit acceptance of its maintenance-fee EULA; this project does not accept it automatically. The Windows x64 MSI builds successfully with WiX 5.0.2. Its contents were inspected for the application, dependencies and bundled runtime, and SQLite insert/read and version-comparison checks pass with that runtime. Installation, shortcuts, upgrades and full UI behavior still need end-to-end testing on Windows.

## Current features

Version 0.1.15 separates First name and Last name in the Clients table and add/edit dialog, with live client search and a Clear button. Search covers names, phone, email, client type, status and notes; it ignores case/accents, treats punctuation literally, and supports phone searches without formatting. Multiple words narrow the results across fields, and a count shows matching versus total clients. Search remains active through refresh and edits, and double-click editing still resolves the correct client under filtering/sorting.

The transactional schema migration adds first_name and last_name, initially splitting the first word from the remaining name text. The existing full-name column is left unchanged during migration, and all IDs, types, notes and linked records remain intact. Multiword/compound names can be corrected in Edit; the existing full name is shown for comparison when applicable. Either name field can be blank, but at least one is required, allowing single names and business clients. Saving recomputes the combined display name used by Properties, Calls, Documents and client pickers. Repeat migrations preserve corrected split names.

Version 0.1.8 groups Documents by client. The tab opens with client names, contact details and document counts, including clients with no attachments. Click a client, or select one and press Enter/Open client documents, to open that client's document section. Back to clients returns to the overview. Add document links the chosen file to the open client automatically; Open document or double-click opens an attachment. Navigation and opening use client/document IDs, so identically named clients remain separate. Returning to the Documents tab refreshes the visible list, including client-name changes. Existing attachment rows, paths and copied files are preserved; no database or file migration is required.

Version 0.1.7 adds client types: Unspecified, Buyer, Seller, or Buyer & Seller. The type is available when adding/editing a client and is displayed in the Clients table separately from status. Existing client records migrate to Unspecified, preserving their IDs, contact details, notes, properties, call history and documents. Schema migration is transactional and repeatable. Double-clicking a client opens the same edit dialog as Edit selected; both resolve the correct client ID after table sorting, filtering or column reordering. Blank-space, single and right clicks do not open an editor.

Version 0.1.6 adds seven built-in themes in Settings: Classic Light, Midnight, Ocean, Forest, Plum, Sandstone and Slate. Choosing a theme previews it immediately across the app and its dialogs. Save persists the stable theme ID in the existing user settings file; it is loaded before the main window is shown, including after updates. Cancel or closing Settings restores the previously saved theme. Missing or unrecognized theme IDs safely use Classic Light. Update-repository preferences are preserved. Settings saves use a temporary file and atomic replacement where supported. Theme switching requires no app restart or extra dependencies.

- Add/edit clients and their contact details, status and notes.
- Add client-linked properties and open addresses in Google Maps in the default browser.
- Log calls with optional validated follow-up dates.
- Copy document attachments into app-managed storage and open them.
- Sort tables; refresh to show changes in related client names.
- Settings at the top left, alongside a Help menu containing Check for updates and About RealtorDesk. About shows the installed version, app description, local data folder and project address.
- Help > Check for updates and optional startup notification for newer stable GitHub Releases. Download progress, SHA-256 verification, installation in the current folder, and automatic restart after successful installation.

## Data

Records, settings, and copied attachments live in `%LOCALAPPDATA%/RealtorDesk`, outside the installation directory. They persist across application upgrades. Each Windows user has a separate database. Back up the entire directory with the app closed. The database and documents are not encrypted by the app. Map searches transmit the selected address to Google. No client data is uploaded to GitHub.

## Release process

The public repository is https://github.com/Mr-Cee/RealtorDesk and is the default update source. Commit code and tests first, then increment VERSION as the final release commit on main. The Windows release workflow runs tests, builds an MSI with bundled Java, uploads the installer and checksum to a draft release, verifies the uploaded hash, and publishes it as the latest release. The workflow can also be run manually from Actions. A published version is never overwritten: increment VERSION for the next release. Releases are the installable builds; uploading source code alone does not update installed programs.

The VERSION file generates the Java version constant and MSI version/name during build. Keep numeric versions compatible with Windows Installer (major/minor up to 255, build up to 65535). WiX 5.0.2 is pinned in the workflow. The workflow runs on Windows with Java 21; local builds can use a newer JDK. GitHub's temporary workflow token publishes releases; no token is shipped in the app.

Users on 0.1.0 or 0.1.1 must install 0.1.2 once from Releases to obtain the new updater. Thereafter choose Check for updates, Download, then Install and restart. The app accepts only stable releases with an exactly named Windows MSI and a GitHub SHA-256 asset digest. It downloads into a unique temporary folder, checks size and hash, then starts an external Windows PowerShell helper. The helper waits for the app to close, runs Windows Installer in passive mode with the existing installation directory, and reopens the app only on success. On failure, the installer and diagnostic logs remain in the temporary folder. No scripts are downloaded from GitHub. Startup checks report failures silently; manual checks show an error and preserve the running app. In-app installation is available only when launched from a properly installed copy, not from the development build.

Integrity checks use HTTPS and GitHub's asset digest; the MSI is not code-signed. Code signing, backups, schema migration infrastructure, editing other record types, reminders, embedded maps and multi-device sync remain future work. Automated updater tests cover version selection, release metadata, redirects, corrupt/truncated/oversized downloads, temporary-file cleanup and PowerShell handoff parsing. Full installation/restart testing still requires a Windows installed copy.

This is a foundation prototype, not yet a production client-record system. Validate installation/upgrades and implement backups before storing your only copy of business records.

## Release notes

The app displays What's New once on the first launch of each version (including the first version with this feature). Help > What's New reopens the current notes anytime. Notes are bundled for offline access, and the last viewed version is saved with your preferences. Edit release-notes.txt when updating VERSION; the same file supplies the in-app popup and GitHub release notes.

Seller and Buyer & Seller clients can store an optional seller address in the client editor. The Clients tab displays the address and includes it in live search, including searches combining names with street, city or ZIP. Switching to another client type disables address editing while preserving the saved address. Existing clients receive a blank address on upgrade.

The Clients tab hides internal IDs and includes Date Last Communicated. Add/Edit Client provides a calendar with month/year navigation, Today, and Clear. Dates are optional, display as YYYY-MM-DD, and are searchable. Existing client dates start blank and are preserved when editing other fields.

Documents has separate instant searches: find clients by name, phone or email, then search their documents by filename or added date. Both provide Clear and matching counts. Client search is retained when returning from a folder. A newly opened client starts with all documents; refreshing or adding a file retains that folder's current search.

Add/Edit Client has a Key Dates section below Client information for optional Start, Offer and Close dates. Each uses the calendar chooser and can be cleared. The database stores separate ISO date columns (date_start, date_offer, date_close), with SQL NULL for unknown dates, ready for future reports calculating Start-to-Offer, Offer-to-Close and Start-to-Close intervals. Existing clients begin with blank milestones.

Linked properties are shared records between Clients and Properties. Add/Edit Client supports attaching an unassigned property, creating or editing property details, and detaching a property without deleting it. A client can have multiple properties; a property has one optional client. Properties supports editing and optional assignment. Both tabs refresh together. Client editor changes commit in a single transaction; Cancel does not save draft properties. Existing seller addresses are converted once into linked records unless a matching property already exists for that client; the original address field remains stored for compatibility. The client list/search now uses the linked property addresses.
