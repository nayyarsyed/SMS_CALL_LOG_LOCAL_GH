# SMS & Call Logs Local Backup

> A local-first Android utility for backing up SMS messages and call history to storage you choose.

**No cloud upload is implemented.** Backups are written to the device or user-selected storage. They contain sensitive personal data, so protect the files and share them only with people you trust.

## What it does

- Lists device SMS messages and call logs in separate tabs.
- Creates incremental, duplicate-resistant backups in **JSON, XML, and CSV**.
- Stores a local SHA-256 record index in SQLite.
- Uses Android's Storage Access Framework to let you choose an SD-card folder.
- Offers `Downloads/SMS_Call_Backup` when you choose the Downloads destination or have no SD card.
- Shows backup counts, last success/failure, and failure reasons on the dashboard.
- Supports hourly, daily, selected weekdays, and monthly backup schedules with WorkManager.

## Screens

| Dashboard | SMS | Call Logs |
| --- | --- | --- |
| Counts, backup status, destination, and schedule | Device messages, direction, date, and expandable body | Number, call type, date, and duration |

Settings include format selection, scheduling, and app/permission information.

## Storage and file behavior

The app detects a mounted removable volume and lets you choose a folder through Android's system picker. If no SD card is available, or you select Downloads, it writes to `Downloads/SMS_Call_Backup` using Android MediaStore. No all-files or legacy external-storage permission is requested. A removable card still needs to be writable and remain connected; Android and the card's storage provider control whether access can be granted.

Files use stable names:

| Data | JSON | XML | CSV |
| --- | --- | --- | --- |
| SMS | `sms_backup.json` | `sms_backup.xml` | `sms_backup.csv` |
| Call logs | `call_log_backup.json` | `call_log_backup.xml` | `call_log_backup.csv` |

The app stages a complete, valid snapshot and replaces the corresponding file rather than raw-appending. This avoids duplicate rows and malformed JSON/XML, and is more compatible with SAF providers. It also means each run rewrites the selected file, which may take longer as the history grows. The local index is deduplicated using SHA-256 over the record type, timestamp, phone number, direction/call type, SMS body, and call duration.

## Privacy and security

- The app requests `READ_SMS` and `READ_CALL_LOG` to read and display the selected device data.
- Backup files and the local index contain sensitive records. **Exported files are not encrypted**; anyone with access to the selected storage can read them.
- CSV values are escaped for CSV syntax, but spreadsheet applications may interpret formula-like SMS content as a formula. Treat CSV files as untrusted input; avoid opening them directly in a spreadsheet if the source messages are untrusted.
- The app manifest does not declare internet access and disables Android app-data backup. No cloud sync or upload feature is implemented.
- SAF folder access is granted by the user. Downloads output is written by MediaStore.
- No real SMS, contacts, phone numbers, or credentials are intentionally included in the source examples.

**Security review status: not certified as “100% safe.”** Static review identified the two possible exposure scenarios above (unencrypted exports and CSV formula interpretation). They are documented here, not fixed. Review your intended release, test on supported devices, and decide whether those risks are acceptable before distributing the app.

## Android compatibility

The configured minimum SDK is **Android 11 (API 30)**, target SDK is **35**, and compile SDK is **36.1**. Android 11–16 are the intended range, but real-device testing is still necessary. Android 17 uses API 37 and has not been built or tested with this project; Android 17 installation and behavior are therefore **not guaranteed**.

An SD card is optional. Without one, the user can choose Downloads. Installation and storage access depend on the device, Android version, OEM behavior, and user-granted access.

SMS and Call Log permissions are restricted by Android and Google Play policy. The app is currently intended for private/sideloaded distribution. Play publishing requires satisfying the applicable policy and review; installing the app does not guarantee these permissions will be granted.

Scheduled execution uses WorkManager, which is best-effort. Android may delay a scheduled backup for battery and system constraints; it is not an exact alarm.

## Build

Open the project in Android Studio, install the Android SDK configured by the Gradle project, and run the `app` configuration. On Windows, the debug build can be created from the project directory with:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Project structure

| File | Responsibility |
| --- | --- |
| `MainActivity.kt` | Dashboard, SMS and call-log screens, permissions, settings, and storage selection |
| `DeviceDataReader.kt` | Reads SMS and call records from Android providers |
| `BackupCoordinator.kt` | Runs each data-type backup and records outcomes |
| `BackupDatabase.kt` | Local SQLite deduplication index and counts |
| `BackupFileWriter.kt` | JSON, XML, and CSV snapshot serialization and storage |
| `BackupScheduler.kt`, `ScheduledBackupWorker.kt` | WorkManager scheduling and background backup |
| `ScheduleActivity.kt` | Schedule controls |

## License

No license has been specified yet. All rights and reuse permissions remain with the project owner unless a license is added.
