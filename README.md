# Tend (formerly Stride)

A personal Android "home" for several small apps. The first one, **Health** (formerly Stride), is a training diary for a weekly plan based on Huberman's Protocols (swimming, running, kettlebells, sauna).
Pulls workouts from a Garmin watch through intervals.icu. Visual design matches Streakli
(`stefanruvceski/habit-tracker`, `apps/mobile`): dark navy, bordered cards, emerald accent.

## Features
- **Week**: plan vs. done for each day (done / missed / today / upcoming), progress ring, Mon–Sun strip, minutes, swim meters, run km, auto summary.
- **Intensity analysis** per day type:
  - Monday easy swim: distance, pace /100 m, avg HR vs. target, stops
  - Friday run: duration, avg HR vs. zone (default 140–155), time above ceiling, how much to slow down
  - Saturday intense swim: reps from intervals.icu, fastest/slowest, pace fade across reps
  - Strength days: exercises, volume, sauna
  - Wednesday: warning if a rest day turned into a workout
- **Trends** (8 weeks): easy swim pace, run HR and pace, intense swim max HR, % of plan completed, total minutes, weight.
- **Strength**: quick kettlebell logging (exercise, kg, sets, reps, sauna), prefilled from the last session of the same type, per-exercise progress.
- **Weight**: manual entry (optionally sent to intervals.icu) plus Garmin scale weight via intervals.icu, chart and goal.
- Plan, HR targets and durations are editable in Settings. Data is stored locally on the phone.

## Connecting
1. Create an intervals.icu account and connect Garmin Connect in its Settings.
2. intervals.icu → Settings → Developer Settings → generate an API key.
3. In the app: ⚙ → paste the key → Test connection → Save. Leave Athlete ID as `0`.

## Getting the APK
1. Push to `main` in the **public** GitHub repo (public so the app can check Releases without a token; no secrets are in the code).
2. Actions → "Build Stride APK" runs automatically (~5–8 min).
3. On the phone: repo → Releases → latest → `Stride.apk` → install.
4. Every change: push → new release. The app shows a "New version available" banner and installs it in one tap.

Releases are signed with a keystore kept in GitHub secrets (`STRIDE_KEYSTORE_BASE64`, `STRIDE_KEYSTORE_PASSWORD`), never in the repo.

## Tech
Kotlin 2.0, Jetpack Compose (Material 3), kotlinx.serialization, OkHttp. AGP 8.9, Gradle 8.11.1, JDK 17, compileSdk 36 (targetSdk 35), minSdk 26.
No Gradle wrapper is committed; CI uses `gradle/actions/setup-gradle` with Gradle 8.11.1.

Three Gradle modules, so more apps can live inside Stride later:

```
app/      thin shell: MainActivity, StrideApplication, Modules (the module list), launcher icon, shortcuts
core/     shared: theme + components, in-app updates, Claude client, host contract (AppModule, Host, AppInfo)
health/   the Stride module (HealthModule):
  model/      data classes (plan, intervals.icu DTOs, strength, weight)
  data/       local JSON store, intervals.icu, Health Connect, backup
  domain/     Analyzer (plan matching, verdicts, weekly summary, trends), goals, notices, formatting
  ui/         StrideApp (navigation), AppViewModel, screens
  widget/ share/ live/ reminder/ garmin/ coach/
```
