# Stride: notes for Claude Code

Personal Android app for Stefan (Pixel 10 Pro). Everything user-facing is in English.

## Status
The code was written without a compiler (no Maven access where it was written). It has never been built.
Expect small compile errors on the first build: wrong imports, Compose API signatures, icon names.

## Build
- CI: `.github/workflows/build.yml` builds a signed release APK on every push to `main` and publishes it as a GitHub Release. Target scheme: tag `android-b<N>`, N = versionCode = run_number, asset `Stride.apk` (the in-app update banner depends on it).
- Local: `gradle assembleRelease` with Gradle 8.9, JDK 17, Android SDK 35 (no wrapper committed; add one with `gradle wrapper --gradle-version 8.9` if useful).
- Signing: no keystore exists yet and none may be committed. `app/build.gradle.kts` reads `STRIDE_KEYSTORE_PATH` and `STRIDE_KEYSTORE_PASSWORD` (alias `stride`). CI must decode the keystore from secrets `STRIDE_KEYSTORE_BASE64` + `STRIDE_KEYSTORE_PASSWORD`, like Streakli does. Once the first release is out, never change the key, or updates won't install over the old app.

## In-app updates
`app/stride/update/`: on launch and on every return to the foreground, release builds read
`https://api.github.com/repos/stefanruvceski/stride/releases` (no token; the repo must stay public), take the
highest non-draft `android-b<N>` release with an `.apk` asset and show a banner if N > `BuildConfig.VERSION_CODE`.
Pure logic in `UpdateLogic.kt` (unit tested); download + FileProvider + installer in `Updater.kt`. Disabled in debug.
Don't rename `.github/workflows/build.yml`: `run_number` restarts at 1 for a new workflow and versionCode must keep increasing.

## Plans, check-offs, widgets
- Plans are per week: `AppData.weekPlans[monday]`, else `settings.plan` (the default). Always look days up with
  `AppData.planFor(date)`. Changing the default pins the old plan on earlier weeks so history and trends don't change.
- Sunday from 18:00 a WorkManager check (`reminder/PlanReminder.kt`) notifies once if next week isn't approved;
  the tap opens `PlanReviewScreen` (shift ±1 day, change days, approve).
- `AppData.manualDone`: days checked off by hand (no watch data); shown in `StrideColors.Manual` (blue).
- Home-screen widgets (Glance, `widget/Widgets.kt`): Today and Weight, refreshed after every data change.
- Strength: `domain/Progression.kt` suggests the next bell after 10+ reps (program: failure at 6-10); rest timer in the editor.
- Workout mode (`ui/screens/Workout.kt`): exercises with per-set kg/reps/done (`SetEntry`), pictures and steps from
  Free Exercise DB (public domain; index in `assets/exercises.json`, photos from raw.githubusercontent via Coil).
  Presets carry exact `dbId`s; custom names are matched by `data/ExerciseMatcher.kt`. Sessions save on every change.
- `domain/Training.kt`: records (history per exercise), warm-ups (~40%/70% bells, `SetEntry.warmup`, excluded
  from volume/records/progression), deload (suggested after 5 steady weeks or an HRV drop; `AppData.deloadWeeks`,
  ~70% bells), weekly targets (Zone 2 = HR zone 2 minutes, sauna minutes, plan), weight trend/ETA, day swaps.
- Reminders (`reminder/PlanReminder.kt`, WorkManager every 15 min): Sunday 18:00 plan review, 06:00 today + recovery,
  19:00 if a planned workout isn't done; each once per day/week.
- Share images (`share/`): 1080x1920 bitmaps drawn with android.graphics (fonts in `res/font`, OFL): run/walk with
  the GPS route (`/activity/{id}/streams?types=latlng`, lat in `data`, lng in `data2`), strength, swim. Day screen → Share.
- Recovery: wellness (sleep, HRV, resting HR, …) kept in `AppData.wellness` (120 days); `domain/Recovery.kt`
  compares with the 7-day average for a readiness call. Values sit on the date you wake up.

## Design
Match Streakli, the user's habit tracker: repo `stefanruvceski/habit-tracker`, folder `apps/mobile/src`
(`lib/theme.ts`, `components/ui.tsx`, `components/WeekStrip.tsx`, `components/Charts.tsx`, `screens/TodayScreen.tsx`).
Palette and card/tile/ring/strip styles are already ported in `ui/theme/Theme.kt` and `ui/Components.kt`. Dark theme only.

## Data source
intervals.icu REST API, Basic auth with username `API_KEY` and the user's key as password, athlete id `0` = key owner.
Endpoints used: `GET /api/v1/athlete/0/activities?oldest&newest`, `GET /api/v1/athlete/0/wellness?oldest&newest`,
`GET /api/v1/activity/{id}/intervals`, `PUT /api/v1/athlete/0/wellness/{date}`, `GET /api/v1/athlete/0`.
