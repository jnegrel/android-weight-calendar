# Weight Calendar – Architecture notes

For anyone who wants to read, change or translate the code. The code itself is commented
with beginners in mind: these notes give the overall picture.

## Stack

- **Kotlin**, UI in **Jetpack Compose** (Material 3, always-dark theme)
- **Room** (SQLite) for the data, **SharedPreferences** for the few settings
- **WorkManager** for the background tasks (reminder, daily backup)
- **DocumentFile** / Storage Access Framework for the backup folder
- No network access, no other dependencies. Versions are in `gradle/libs.versions.toml`.

## Data flow

```
 WeightEntry ─ one row of the "weights" table (date, kg, estimated, note)
      │
 WeightDao ─── the SQL queries (Room generates the code)
      │
 WeightDatabase ─ the database, version and migrations
      │
 WeightRepository ─ the app's rules: estimated days, CSV import/export
      │
 HomeViewModel ─── what the screens show (StateFlow), survives rotation
      │
 HomeScreen / SettingsScreen ─ Compose UI, redrawn when the data changes
```

The database is observed as a `Flow`: after any change, Room emits the new list, the
ViewModel turns it into `Map<LocalDate, Day>` (`toDays()`: each day with its change and
colour), and Compose redraws what depends on it.

## Files

```
app/src/main/java/no/jene/weightcalendar/
├── MainActivity.kt          entry point: edge-to-edge, theme, HomeScreen
├── data/
│   ├── WeightEntry.kt       the table row
│   ├── WeightDao.kt         queries
│   ├── WeightDatabase.kt    Room database, LocalDate converter, migrations
│   ├── WeightRepository.kt  estimated-day rule, import/export
│   ├── Csv.kt               CSV format (pure Kotlin, unit-tested)
│   ├── Trend.kt             colour rule, toDays()
│   ├── Smoothing.kt         centred 7-day and 30-day averages
│   ├── WeightUnit.kt        kg / lb
│   └── Settings.kt          goal, reminder, backup, unit (SharedPreferences)
├── reminder/Reminder.kt     daily reminder: scheduling, worker, notification
├── backup/Backup.kt         daily backup: scheduling, worker, clean-up
└── ui/
    ├── HomeScreen.kt        home screen, file pickers, ⋮ menu, navigation to settings
    ├── HomeViewModel.kt     state and actions for both screens
    ├── MonthCalendar.kt     calendar grid, selected-day line
    ├── WeightGraph.kt       graph (Canvas), periods, goal label
    ├── SettingsScreen.kt    settings list, unit dialog
    ├── *Dialog.kt           pop-ups: daily log, goal, reminder, backup
    ├── AppHeader.kt         the blue top bar
    ├── LocalWeightUnit.kt   the display unit, available to all composables
    └── theme/               colours and Material theme
app/src/main/res/
├── values*/strings.xml      texts, one folder per language
├── xml/locales_config.xml   languages offered in Android's per-app language setting
└── drawable, mipmap         icons
app/schemas/                 Room's description of each database version (keep in git)
tools/ods_to_csv.py          one-off converter from the original spreadsheet
```

## Rules worth knowing

- **Data is always in kg**: in the database, in CSV files, for the colour thresholds.
  `WeightUnit` converts only for display and input. A weight typed in lb is stored rounded
  to 0.01 kg, which makes the lb → kg → lb round trip exact (tested).
- **Estimated days** (`WeightRepository.refillAround`) are always the straight line between
  the nearest measured days. Every save or delete redraws them around the changed date,
  inside one transaction. A note can only exist on a measured day.
- **Floating point**: daily changes are rounded to 0.01 kg before being compared with the
  ±0.5 thresholds (otherwise 64.1 − 63.6 = 0.4999999999999929 would be yellow).
- **Averages are centred** (the day and as many days before as after); the window shrinks
  at the edges. They are computed on all data, then cut to the period shown.
- **Graph colours** use the change of the average per week, with the same thresholds.

## Database changes

The schema version is in `WeightDatabase.kt`. To change the table (for instance add a
column):

1. Change `WeightEntry`.
2. Increase `version` in `@Database`.
3. Add a `Migration(old, new)` with the SQL that converts an existing database, and register
   it in `addMigrations(...)`. Without it, the app crashes at start for existing users.
4. Build: Room writes `app/schemas/.../<version>.json`; commit it.
5. Test the update: install the previous release, then the new one over it, and check the
   data is still there.

## Adding or fixing a translation

- Texts are in `app/src/main/res/values/strings.xml` (English, the default) and
  `values-fr`, `values-nb`, `values-de`, `values-es`. Each file has the same names
  (`name="..."`) with the text of its language.
- `%1$s` / `%1$d` are values inserted by the code (a weight, a date, a number): keep them.
- `pattern_*` entries are date formats (`EEEE` weekday, `d` day, `MMMM`/`MMM` month,
  `yyyy` year; text between `'` quotes is kept as is).
- `plurals` have one text per grammatical number (`one`, `other`…).
- An apostrophe must be written `\'` (or use the typographic ’).
- **New language**: copy `values/strings.xml` to `values-<code>/strings.xml`
  (`it`, `pt`, `sv`…), translate it, and add `<locale android:name="<code>"/>` to
  `res/xml/locales_config.xml`. Check that buttons and labels still fit on screen.

## Tests

- `app/src/test/` – unit tests, run on the computer in seconds:
  `./gradlew testDebugUnitTest`. Colour rule, averages, CSV format, units, input parsing.
- `app/src/androidTest/` – run on a device or emulator, with a temporary in-memory database
  (your data is not touched): `./gradlew connectedDebugAndroidTest`. Estimated days on
  save/edit/delete, CSV export/import round trip.
  ⚠️ They run on **every** connected device; to use only the emulator:
  `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest`.

In Android Studio: right-click the `test` or `androidTest` folder → *Run Tests*.

## Build variants and releases

- **Debug** (`./gradlew assembleDebug`, or ▶ in Android Studio): application id
  `no.jene.weightcalendar.debug`, name *Weight Cal. (debug)*. It installs next to the
  release app, with its own data.
- **Release** (`./gradlew assembleRelease`): shrunk with R8 (about 1.3 MB) and signed.
  The signing key is **not** in the repository: `app/build.gradle.kts` reads its location
  and passwords from `~/.gradle/gradle.properties`:

  ```properties
  WEIGHTCAL_STORE_FILE=/path/to/weight-calendar.jks
  WEIGHTCAL_STORE_PASSWORD=...
  WEIGHTCAL_KEY_ALIAS=weight-calendar
  WEIGHTCAL_KEY_PASSWORD=...
  ```

  Without them, the release build is produced unsigned. **Keep the key and its passwords
  safe**: Android only installs an update signed with the same key as the installed app.

Release checklist:

1. Increase `versionCode` (Android refuses an update with the same or a lower number) and
   set `versionName` in `app/build.gradle.kts`.
2. Run the tests.
3. `./gradlew assembleRelease`, check the signature:
   `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`.
4. Install it over the previous release on an emulator and check the data survives.
5. Commit, tag (`git tag -a vX.Y -m "Weight Calendar X.Y"`), push with
   `git push --follow-tags`, and attach the APK to a GitHub release.
