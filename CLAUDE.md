# Tend (repo "stride"): notes for Claude Code

Personal Android app for Stefan (Pixel 10 Pro). Everything user-facing is in English.
The app is called **Tend** (`host/Brand.NAME` + `@string/app_name` in core); Stride lives on inside it as the
**Health** module. The repo, package (`app.stride`), release tags and `Stride.apk` keep the old name on purpose:
renaming them would break updates.

## What's new after updates
`app/src/main/assets/whats_new.json` (newest first, unique `id`s, icons listed in `whatsnew/WhatsNew.kt`). On the
first launch after an update `WhatsNewGate` shows the unseen entries; an update without a new entry shows a short
"polished under the hood" note. **Add an entry for every user-visible change**; fixes/refactors need none.

## Status
The code was written without a compiler (no Maven access where it was written). It has never been built.
Expect small compile errors on the first build: wrong imports, Compose API signatures, icon names.

## Build
- CI: `.github/workflows/build.yml` builds a signed release APK on every push to `main` and publishes it as a GitHub Release. Target scheme: tag `android-b<N>`, N = versionCode = run_number, asset `Stride.apk` (the in-app update banner depends on it).
- Local: `gradle assembleRelease` with Gradle 8.11.1, JDK 17, Android SDK 36 (no wrapper committed; add one with `gradle wrapper --gradle-version 8.11.1` if useful).
- Signing: no keystore exists yet and none may be committed. `app/build.gradle.kts` reads `STRIDE_KEYSTORE_PATH` and `STRIDE_KEYSTORE_PASSWORD` (alias `stride`). CI must decode the keystore from secrets `STRIDE_KEYSTORE_BASE64` + `STRIDE_KEYSTORE_PASSWORD`, like Streakli does. Once the first release is out, never change the key, or updates won't install over the old app.

## Modules (ready for a "super-app" of several apps)
- `:app` is a thin shell: `MainActivity` (turns intents into `OpenRequest`s), `StrideApplication` (hands `BuildConfig`
  to `AppInfo`, calls each module's `onAppStart`), `Modules.all` (the module list), launcher icon, shortcuts,
  FileProvider, backup rules. `ModuleHost` is the home screen (greeting, update banner, one card per module with its
  `HomeSummary`); opening the app lands there, deep links go straight into a module, back returns home.
- `:core` is shared by every module: theme + components (`ui/theme`, `ui/Components.kt`), in-app updates (`update/`),
  the Claude client (`coach/CoachClient`), `StrideJson`, `DataEvents`, fonts and the notification icon (`app.stride.core.R`),
  and the host contract in `host/`: `AppModule`, `Host` (open the app via `Host.openIntent` / `Host.component`, never a
  direct `MainActivity` reference), `OpenRequest`, `AppInfo` (library modules have no `BuildConfig` of their own).
- `:health` is everything Stride does (all packages below), exposed as `HealthModule`. A new app = a new library module
  with its own `AppModule`, added to `settings.gradle.kts`, `app/build.gradle.kts` and `Modules.all`.
  Open targets for a module later become "<moduleId>/<target>".
- Kotlin packages stay `app.stride.*` in every module; only resources moved (use the module's R, e.g. `app.stride.core.R`).
  Manifest entries in library modules use fully qualified class names.

## In-app updates
`core/…/update/`: on launch and on every return to the foreground, release builds read
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
- Home-screen widgets (Glance, `widget/Widgets.kt`, 15 of them, `SizeMode.Exact`, text scales with size): Today, Weight,
  Week strip, Target rings, Recovery, Next workout, Month, Strength, Week totals, Weight trend, Week plan, Start workout,
  Last run, Last swim, Streak. Bitmaps (rings, month grid, chart) come from `widget/WidgetArt.kt`. Each needs a receiver in
  the manifest (with `android:label`), `res/xml/widget_*.xml` and a description string. Taps open the app via
  `Widgets.EXTRA_OPEN` ("workout", a tab id, "day:YYYY-MM-DD"), refreshed after every data change.
- Layout: users hide/reorder tabs and sections (`Settings.layout`, ids in `ui/Layouts.kt`, never rename them). Long-press
  any tab → edit mode (`ui/Layout.kt`, `SectionHost`); Settings → Tabs and sections. New sections: add a `SectionDef`
  and a `section("id") { ... }` block in the tab.
- Notifications: `model.NotifType` (18 types, each switchable in Settings → Notifications, `Settings.notifyOn`), pure
  rules in `domain/Notices.kt` (unit tested), posted by `reminder/PlanReminder.kt` (dedupe set in prefs). The worker
  also syncs intervals.icu every ~2 h (`data/Sync.kt`, shared with the app; `Store.update` under `Store.LOCK`) and
  checks for updates twice a day.
- Strength: `domain/Progression.kt` suggests the next bell after 10+ reps (program: failure at 6-10); rest timer in the editor.
- Workout mode (`ui/screens/Workout.kt`): exercises with per-set kg/reps/done (`SetEntry`), pictures and steps from
  Free Exercise DB (public domain; index in `assets/exercises.json`, photos from raw.githubusercontent via Coil).
  Presets carry exact `dbId`s; custom names are matched by `data/ExerciseMatcher.kt`. Sessions save on every change.
  `assets/exercises.json` also has ~50 Stride kettlebell entries (`KB_*` ids, `img` = the DB exercise whose photos they
  reuse). The picker is a full-screen browser: search as you type (prefix match), equipment and muscle-group chips.
- `domain/Training.kt`: records (history per exercise), warm-ups (~40%/70% bells, `SetEntry.warmup`, excluded
  from volume/records/progression), deload (suggested after 5 steady weeks or an HRV drop; `AppData.deloadWeeks`,
  ~70% bells), weight trend/ETA, day swaps.
- Weekly targets: `Settings.weeklyGoals` (up to 4 `WeeklyGoal(GoalKind, amount)`, null = plan + Zone 2 + sauna),
  progress in `domain/Goals.kt` (tested); rings on the Week tab (Edit → `GoalsScreen`), Settings and the rings widget.
- Reminders (`reminder/PlanReminder.kt`, WorkManager every 15 min): Sunday 18:00 plan review, 06:00 today + recovery,
  19:00 if a planned workout isn't done; each once per day/week.
- Share images (`share/`): the preview (`ui/screens/ShareSheet.kt`) lets the user pick colors, map (dark/streets/
  satellite/none), background and what to show (`Settings.share`, `ShareStyle`). Maps need no API key: Esri dark gray
  canvas + World Imagery and tile.openstreetmap.org (darkened), with fallbacks; CARTO was dropped (it now wants a key).
  1080x1920 bitmaps drawn with android.graphics (fonts in `res/font`, OFL): run/walk with
  the GPS route (`/activity/{id}/streams?types=latlng`, lat in `data`, lng in `data2`), strength, swim. Day screen → Share.
- Sleep: nights with stages from Health Connect (`AppData.sleep`, `SleepNight`/`SleepStage`, main session per wake
  date, 120 days). Stages are the watch's; `domain/Sleep.kt` (tested) adds efficiency, latency, WASO, awakenings,
  stage shares vs 13-23% deep / 20-25% REM, Sleep Regularity Index, bedtime spread, 7-day debt and a 0-100 score
  (duration 40, efficiency 20, continuity 10, deep 15, REM 15; renormalized without stages). UI in
  `ui/screens/SleepViews.kt`: hypnogram card on the Day screen, "Sleep stages" section in Trends.
- Recovery: wellness (sleep, HRV, resting HR, …) kept in `AppData.wellness` (120 days); `domain/Recovery.kt`
  compares with the 7-day average for a readiness call. Values sit on the date you wake up.

## Coach, live workout, Health Connect, backup, extras
- AI coach (`coach/`): `CoachPrompt` (pure, tested) + `CoachClient` (official Anthropic Java SDK, `claude-opus-5-5`,
  server-side refusal fallback). The key is the user's own (`Settings.anthropicKey`), never in the app or in backups.
  Reviews in `AppData.coach[monday]`; Week tab section "coach"; Sunday 19:00 the worker writes one and notifies.
  R8 keeps for the SDK (Jackson) are in `proguard-rules.pro`.
- Live workout (`live/LiveWorkout.kt`): ongoing lock-screen notification while a stored, unfinished session is open:
  current set (`domain/LiveSet.kt`, tested), rest countdown, "Set done" / "+30 s" actions handled by
  `LiveWorkoutReceiver` (writes the store, `DataEvents.notifyChanged()` makes the app reload).
- Health Connect (`data/HealthSync.kt`, connect-client): fills missing steps/sleep/HRV/resting HR/weight, writes
  finished strength sessions without a watch workout (`AppData.hcExported`). Foreground only (no background read).
- Backup (`data/Backup.kt`): export/restore JSON via the system file picker (Drive works), weekly auto backup into a
  picked folder (SAF tree, `Settings.backupFolder`). Keys are stripped.
- Trends: Challenges and badges (`domain/Badges.kt`), Sleep and HRV insights (`domain/Insights.kt`), Year in review
  (`domain/YearReview.kt` + `share/YearCards.kt`, 4 story cards, share all).
- Garmin gym workouts (`garmin/`, unofficial): `GarminClient` signs in like garth (SSO → OAuth1 → OAuth2, consumer key
  from thegarth S3; only tokens kept in prefs `garmin`, excluded from Android backup), `GarminWorkout` builds the
  workout-service JSON (repeat groups, reps, kg, rest; tested), `GarminExercises` maps names to FIT categories and, where known, exact exercise names (tested; on a 4xx the
  upload is retried without exact names); bodyweight exercises go without weight.
  Workout screen → watch icon sends the session, scheduled on its date. Settings → Garmin Connect (MFA supported).
- Quick Settings tile `live/StartWorkoutTile.kt`; launcher shortcuts `res/xml/shortcuts.xml` (EXTRA_OPEN workout/weight/today).

## Design
Match Streakli, the user's habit tracker: repo `stefanruvceski/habit-tracker`, folder `apps/mobile/src`
(`lib/theme.ts`, `components/ui.tsx`, `components/WeekStrip.tsx`, `components/Charts.tsx`, `screens/TodayScreen.tsx`).
Palette and card/tile/ring/strip styles are already ported in `ui/theme/Theme.kt` and `ui/Components.kt`. Dark theme only.

## Data source
intervals.icu REST API, Basic auth with username `API_KEY` and the user's key as password, athlete id `0` = key owner.
Endpoints used: `GET /api/v1/athlete/0/activities?oldest&newest`, `GET /api/v1/athlete/0/wellness?oldest&newest`,
`GET /api/v1/activity/{id}/intervals`, `PUT /api/v1/athlete/0/wellness/{date}`, `GET /api/v1/athlete/0`.
