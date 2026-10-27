# Hive (repo "stride"): notes for Claude Code

Personal Android app for Stefan (Pixel 10 Pro). Everything user-facing is in English.
The app is called **Hive** (formerly Tend) (`host/Brand.NAME` + `@string/app_name` in core); Stride lives on inside it as the
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
  to `AppInfo`, calls each module's `onAppStart`), `Modules.all` (the module list), launcher icon (`ic_hive_*`: same-size hexagons in an uneven comb), shortcuts,
  FileProvider, backup rules. `Modules.all` = Health, Habits, Money, Books, Drawify, Wallet, Music. `ModuleHost` is the home screen (greeting, update banner, one card per module with its
  `HomeSummary`; order and hidden apps in `host/HomeOrder`, tested, prefs `hive_home`, edited with the pencil or a long press); opening the app lands there, deep links go straight into a module, back returns home.
- `:core` is shared by every module: theme + components (`ui/theme`, `ui/Components.kt`), in-app updates (`update/`),
  the Claude client (`coach/CoachClient`), `StrideJson`, `DataEvents`, fonts and the notification icon (`ic_stat_hive`, the comb; `app.stride.core.R`),
  and the host contract in `host/`: `AppModule`, `Host` (open the app via `Host.openIntent` / `Host.component`, never a
  direct `MainActivity` reference), `OpenRequest`, `AppInfo` (library modules have no `BuildConfig` of their own).
- Shared in `:core` for every module (use these before writing new ones): `ui/Layout.kt` + `LayoutOps` + `model/LayoutPrefs`
  (hold-to-edit sections, per-module tab lists) + `ui/TabsAndSections.kt` (`TabsAndSectionsScreen`: hide / reorder tabs and
  sections for any module; Health, Habits and Money use it), `ui/Rings.kt` (`RingStack`), `ui/Heatmap.kt` (`YearHeatmap`), `ui/Controls.kt`
  (`PeriodNav`, `ThinBar`, `Segment`, `Chip`, `DateField`), `ui/Components.kt` (cards, `LineChart` with tap tooltips and `more`
  series, `BarChart`, `ProgressRing`, `ui/MoreCharts.kt` (`StackedBarChart` with tap select, `DonutChart`), `WeekStrip`, `StatTile`, nav bar), `ui/Celebration.kt`, `format/Dates` (Hive-wide date style EU / ISO / US, prefs `hive_prefs`; always format dates with it; `DateFormatCard` in Hive settings (home gear), Health settings and Money setup), `data/JsonStore` (one JSON file per
  module, atomic writes), `notify/Notifier` (post + post-once dedupe), `widget/WidgetKit.kt` + `WidgetArt` (Glance frame, text
  scaling, ring/line/month bitmaps). Glance and WorkManager come from core.
- `:habits` (`HabitsModule`, package `app.stride.habits`): Streakli's habit tracker, ported from `stefanruvceski/habit-tracker`
  (`packages/core` + `apps/mobile`). `habits.json` (`HabitsData`: habits, entries date → id → amount, mood, to-dos, settings).
  Pure logic in `domain/` (`HabitStats`, `Routine`, `Insights`, `Starter`; tested). Tabs Today / Month / Year / Habits
  (`HabitLayouts`), `HabitForm`, 58 built-in icons in `assets/habit_icons.json` (`HabitGlyph`), time-of-day reminders
  (`reminder/HabitReminder`, every 15 min), widgets Today (tap to tick) and Progress, a once-a-day "Day closed" celebration.
- `:money` (`MoneyModule`, package `app.stride.money`): Streakli's Finance. `money.json` (`MoneyData`: sources, transactions with
  locked FX rates, expenses, categories, budgets, goal, levels). Pure logic in `domain/Finance` + `FxParse` (tested); live rates
  from currency-api or NBS (`data/FxClient`, keyless). Tabs Income / Reports / Spending / Setup, monthly income reminder.
  Reports (`domain/Reports`, tested; `ui/ReportsTab`): income / spending / net per month by source or category
  (`StackedBarChart`, tap a month → its items), donut + per-slice trend, running total vs last year and goal pace,
  savings rate, weekday spending, biggest items, insights. All income / All expenses (`ui/AllItemsScreen`, filters in
  `domain/Ledger`, tested): search, year, source or category, status, grouped by month; tap to edit or delete.
  **Money = the numbers and the plan, Wallet = the papers and cards.** All spending is counted in Money: typed expenses
  plus Wallet's receipts, which arrive through the core contract `finance/FinanceHub` (modules can't depend on each
  other): Wallet registers `spending` (`data/WalletSpending`: OK receipts → `FeedExpense`, id "wallet:<id>", `open`
  "wallet/receipt:<id>"), Money registers `categories` (`data/MoneyCategories`, a `CategoryBook`); `FinanceHub.changed()`
  after each Wallet save makes Money reload. `domain/Feed` (tested): `mapKinds` files each receipt kind (Wallet's
  `Category` name) under a Money category in `MoneyData.kindMap` (matched by name/synonyms, else a new category),
  `Receipt.moneyCategory` overrides it per receipt; `view` = data + feed expenses (off with `MoneySettings.receipts`).
  Feed rows are read-only in Money (tap opens the receipt in Wallet). Business or personal is one shared tag: the
  company's display name (`Expense.company`, receipt buyer PIB → `Owners.name`); `Feed.scoped` + scope chips on
  Spending and Reports (All / Personal / company). Setup section "receipts" maps kinds and toggles the feed.
  No accounts and no cloud: all three apps keep their data on the phone (Google sign-in may come later, for Hive as a whole).
- `:books` (`BooksModule`, package `app.stride.books`, amber): reading tracker modelled on the user's Notion Books / Quotes
  databases. `books.json` (`BooksData`: books with status to buy / to read / reading / finished / dropped, rating 1–10, dates,
  topics, format, language, pages, core idea, concepts, notes, after-reading checklist; quotes with page, themes, "my take",
  favourite; sessions; the running `ActiveSession`). Pure logic in `domain/BookStats` + `OpenLibraryParse` (tested); search and
  covers from Open Library (keyless, `data/OpenLibrary`, Coil). Tabs Library / Stats / Quotes / Up next (`BooksLayouts`),
  sub-screens as a route stack in `BooksApp` (book page, forms, session timer, all books, settings with JSON export / import).
- `:draw` (`DrawModule`, package `app.stride.draw`): Drawify, ported from `stefanruvceski/drawify` (its own update banner dropped,
  Hive's is used). `DrawApp` (Compose) hosts the original Android views via `AndroidView`: CameraX `PreviewView` and
  `OverlayView` (drag / pinch / rotate, corner mode); `TiltPerspective` (gravity → homography), `ImageFilters` (no white,
  Sobel outline), `BitmapLoader` (EXIF, last image kept). Images shared to Hive (`ACTION_SEND image/*`) open as "draw/share:<uri>".
- `:wallet` (`WalletModule`, package `app.stride.wallet`, sky blue): fiscal receipts (as documents) and loyalty cards, like the Mani.rs app.
  `wallet.json` (`WalletData`: receipts, cards). A receipt is saved from the QR code's tax-office link
  (`suf.purs.gov.rs/v/?vl=…`, deduped by the `vl` value), then `data/FiscalClient` GETs that link with
  `Accept: application/json` (public, no key) and `domain/FiscalParse` (tested) reads `invoiceRequest` / `invoiceResult` /
  `journal`, falling back to the printed text and the HTML page (span ids `shopFullNameLabel`, `totalAmountLabel`, …);
  Cyrillic is shown in Latin. Offline scans stay PENDING and are retried when Wallet opens. `domain/ReceiptStats` (tested):
  months, search, fuel (liters, price per liter, by item name). Loyalty cards: number + `CardFormat`;
  `domain/Barcodes` (tested) guesses the type (EAN check digits, else Code 128), validates and draws it with ZXing;
  the card screen shows it on white at full brightness. Scanning (`ui/Scanner`) is Google Play services: the code
  scanner UI for the camera (no camera permission) and ML Kit (unbundled) for a picked photo. Open targets "scan", "cards".
  Who a receipt is for: `Receipt.buyerTin` = the buyer's PIB when the buyer id is type 10 or 12 (`FiscalParse.companyTin`;
  other types are a person: personal), `ownerManual` keeps the user's choice; companies are named in `WalletData.companies`;
  `domain/Owners` (tested) filters. Warranties per receipt (`Warranty`: item + months from the receipt date),
  `domain/Warranties` (tested: active / ending in 30 days / expired). Look: glass everywhere (`Glass`, `GlassRow`, `GlassPill`,
  `walletGlow` in `ui/WalletCommon`; cards are tinted glass tiles in `CardTile`).
  `domain/Brands` (tested): Serbian chains by legal name → short name, website (logo via Google's favicon service,
  `ShopLogo`, initials as fallback), color and `Category` (also guessed from fuel items or words; per-receipt override
  `Receipt.category`); loyalty programs → color, logo and QR / barcode (`Brands.card`). Wallet shows no totals or charts (that's
  Money's job; the Receipts tab links to "money/spending"); its category chip lists Money's categories when Money is
  there (`Receipt.moneyCategory`), else its own kinds. `Receipt.starred` = ★ Saved.
  `domain/ManiImport` (tested): Mani's CSV (no QR link, no items) → `source = "mani"` receipts; Mani groups become
  companies keyed `group:<name>` (naming a real PIB the same moves them); a later scan of the same receipt (same minute
  and total) replaces the import. Card photos: `data/CardImages` (app files, 1000 px JPEG), `LoyaltyCard.image`.
  Card faces: `domain/CardBrands` (tested, ~65 programs Mani knows) → name, logo domain, two colors, `CardStyle` motif,
  light/dark, QR/barcode; `ui/CardFace` draws them (no third-party artwork in the repo: the repo is public; a user's own
  card photo replaces the face). `CardForm` suggests programs while typing.
  No QR: `ui/NoQrScreen` (route "noqr"): camera/photo → ML Kit text recognition (Latin only, so `domain/ReceiptOcr`,
  tested, finds the four fields by the shape of their values) → editable fields → the tax office's verify page
  (`https://suf.purs.gov.rs/verify`) in a visible WebView; `FILL_JS` fills inputs by label and presses Провери, and
  every page load / 1.5 s the HTML is checked (`WalletViewModel.addFromVerifyPage`: QR link → normal fetch, else
  `FiscalParse.parseHtml`). The form's real markup was never seen when this was written; the user can fix it by hand.
  Fuel diary `ui/FuelScreen` (route "fuel") from `domain/FuelLog` (tested: fills, monthly price per liter, stations,
  L/100 km fill-to-full from `Receipt.odometer`). Till: `CardScreen` is a white screen with only the code; ⋯ = pin /
  edit / delete. `LoyaltyCard.pinned`, order in `domain/CardOrder` (tested). Widget `widget/WalletWidgets` (Glance,
  `CardsWidget`, refreshed on every card change), Quick Settings `CardsTile`, app shortcut "cards"; open targets
  "wallet/cards", "wallet/card:<id>", "wallet/receipt:<id>", "wallet/noqr", "wallet/fuel".
  **Later (asked for, not built yet):** company report: one tap → PDF/CSV of a month's receipts for a company
  (e.g. Obsidian IT) with totals and VAT per rate (the receipts' VAT labels / journal tax table), to send to the
  accountant; plus a "submitted / refunded" mark per receipt.
- `:music` (`MusicModule`, package `app.stride.music`, neon cyan `MusicAccent`): Tempo (the user's yt-dlp app, its repo is
  gone; rebuilt from the session that wrote it). youtubedl-android 0.17.3 (yt-dlp + ffmpeg + aria2c, `engine/Engine`:
  init off the main thread in `onAppStart`, yt-dlp self-update once a day, prefs `music_engine`). `music.json`
  (`MusicData`: downloads, presets, active preset, settings). `domain/Args` (tested) turns `DlOptions` into yt-dlp
  arguments (speed = ffmpeg `atempo` chain, pitch kept; speed/normalize/trim force MP3). Queue: foreground service
  `engine/DownloadService` (dataSync, `Registry` cancels, concurrency from settings), progress in `ProgressBus` (not
  stored), files saved with MediaStore into Music|Movies/Hive Music or Download (why minSdk is 29 for the whole app).
  Player: Media3 `player/PlaybackService` (MediaSession, lock screen) + `Playback` (MediaController state for Compose).
  UI: `ui/MusicApp` (link, presets, speed card, options, downloads, settings), `ui/PlayerViews` (mini player, now
  playing), glass helpers in `ui/MusicCommon`. Look "Abyss" (picked from mocks): near-black navy (`musicGlow()`: navy
  radial from the top + three soft neon lights, cyan / electric blue / teal), Apple-style liquid glass (`glassSurface`:
  white 11% → 2%, bright top line, chromatic sweep rim cyan → blue), `MusicBrand` cyan (main actions, = `MusicAccent`),
  `MusicBrand2` teal, `MusicDeep` blue, `MusicFill` gradient for slider / progress; the Download button stays tinted
  glass with a hint when there's no link yet. Frost: Haze 1.1.1 (last on Kotlin 2.0 / Compose 1.7; don't bump without
  upgrading those): `MusicBackdrop` (glow + floating tiles + the playing cover) is the `haze` source, every
  `glassSurface(haze = LocalHaze.current)` is a `hazeChild`; glass must never sit inside the backdrop (Haze throws). Share → "Hive Music" (`ui/ShareReceiverActivity`, no screen) queues
  with the active preset, or opens "music/add:<url>" when "Ask on Share" is on; "music/player" opens the player.
  The app packs only arm64-v8a and `useLegacyPackaging` (the library runs binaries from the lib folder).
- `:health` is everything Stride does (all packages below), exposed as `HealthModule`. A new app = a new library module
  with its own `AppModule`, added to `settings.gradle.kts`, `app/build.gradle.kts` and `Modules.all`.
  Open targets for a module later become "<moduleId>/<target>".
- Kotlin packages stay `app.stride.*` in every module; only resources moved (use the module's R, e.g. `app.stride.core.R`).
  Manifest entries in library modules use fully qualified class names.

## In-app updates
`core/…/update/`: on launch and on every return to the foreground, release builds read
`https://api.github.com/repos/stefanruvceski/hive/releases` (the repo was renamed from stride; GitHub redirects the old URL, which builds before the rename still use) (no token; the repo must stay public), take the
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
- Notifications: `model.NotifType` (22 types, incl. "All rings closed" and the 20:00 rings reminder; each switchable in Settings → Notifications, `Settings.notifyOn`), pure
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
- Daily rings (Apple Health style): `Settings.dailyRings` (up to 4 `DailyRing(RingKind, amount)`, null = steps 8,000 +
  training 45 min + sleep 7.5 h), progress per day in `domain/Rings.kt` (tested; PLAN follows the day's plan, rest day =
  closed). Week tab section "rings" (`DailyRingsCard`: the day's rings + a Mon–Sun row of small rings), editor
  `DailyRingsEditor` in Settings and the Rings and targets screen; the rings widget shows today's rings.
  Once a day, when all rings close, `RingsCelebration` (StrideApp) plays `core/ui/Celebration` (confetti, pulse, rings spin in; reusable).
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
  `ui/screens/SleepViews.kt`: hypnogram card on the Day screen and the Week tab ("sleep" section), "Sleep stages" section in Trends; `SleepSetupNote` explains what is missing while there are no nights.
- Recovery: wellness (sleep, HRV, resting HR, …) kept in `AppData.wellness` (120 days); `domain/Recovery.kt`
  compares with the 7-day average for a readiness call. Values sit on the date you wake up.

## Coach, live workout, Health Connect, backup, extras
- AI coach (`coach/`): `CoachPrompt` (pure, tested) + `CoachClient` (official Anthropic Java SDK, `claude-opus-5-5`,
  server-side refusal fallback). The key is the user's own (`Settings.anthropicKey`), never in the app or in backups.
  Reviews in `AppData.coach[monday]`; Week tab section "coach"; Sunday 19:00 the worker writes one and notifies.
  Without a key (`coach/CoachShare`): "Ask Claude" copies `CoachHandoff.message` (system + week), opens the Claude app
  (`com.anthropic.claude`, in `<queries>`; else a chooser) and writes `coach-<monday>.json` (`prompt`, empty `response`)
  into the Hive backup folder (`HiveBackup.writeFile`); the reply comes back by Share → "Hive Coach"
  (`CoachShare.ReplyActivity`, goes to the week last sent) or "Paste reply" (`AppViewModel.pasteCoach`), cleaned of
  markdown by `CoachHandoff.clean` (tested), and fills the file's `response`. Sunday 19:00 nudge when there's no key.
  Daily check-in: `CoachPrompt.daily` + `DAILY_SYSTEM` (tested; yesterday in full, last night's sleep/HRV/RHR vs 7-day
  average, the four days before, recovery trend, today's plan, week so far; sections Yesterday / Today / Watch) in
  `AppData.coachDays[date]` (120 days), file `coach-day-<date>.json`. `CoachHandoff.Kind` (WEEK / DAY) + pending key
  "day:<date>" route shared replies. Notification `NotifType.COACH_DAILY` 06:00-12:00 via `CoachShare.notify` (tap →
  "coach" = `CoachScreen` overlay with both cards; button "Ask Claude" → `CoachShare.AskActivity`, opens Claude directly).
  Week tab section "coach_daily". `LayoutOps.sections` puts sections added in an update after their default neighbour.
  R8 keeps for the SDK (Jackson) are in `proguard-rules.pro`.
- Live workout (`live/LiveWorkout.kt`): ongoing lock-screen notification while a stored, unfinished session is open:
  current set (`domain/LiveSet.kt`, tested), rest countdown, "Set done" / "+30 s" actions handled by
  `LiveWorkoutReceiver` (writes the store, `DataEvents.notifyChanged()` makes the app reload).
- Health Connect (`data/HealthSync.kt`, connect-client): fills missing steps/sleep/HRV/resting HR/weight, writes
  finished strength sessions without a watch workout (`AppData.hcExported`). Foreground only (no background read).
- Hive backup (`core/backup/`): `HiveBackup` zips every module's `AppModule.dataFiles` (files/folders in filesDir, plus
  manifest.json) into `hive-backup-YYYY-MM-DD.zip` in a user-picked SAF folder (prefs `hive_prefs`), daily via WorkManager;
  `BackupFormat` (tested) names, zips and prunes (14 daily + newest per month for 12 months). Restore replaces the modules'
  files and closes Hive. `BackupCard` in Hive settings; `RestoreOffer` on home on a fresh install. Keys never in data files:
  Health's `Store` keeps the intervals.icu / Anthropic keys in prefs `secrets` (excluded from Android backup like `garmin`).
- Backup (`data/Backup.kt`): export/restore JSON via the system file picker (Drive works), weekly auto backup into a
  picked folder (SAF tree, `Settings.backupFolder`). Keys are stripped.
- Trends: Challenges and badges (`domain/Badges.kt`), Sleep and HRV insights (`domain/Insights.kt`), Year in review
  (`domain/YearReview.kt` + `share/YearCards.kt`, 4 story cards, share all).
- Garmin gym workouts (`garmin/`, unofficial): `GarminClient` signs in like garth (SSO → OAuth1 → OAuth2, consumer key
  from thegarth S3; only tokens kept in prefs `garmin`, excluded from Android backup), `GarminWorkout` builds the
  workout-service JSON (repeat groups, reps, kg, rest; tested), `GarminExercises` maps names to FIT categories and, where known, exact exercise names (tested; on a 4xx the
  upload is retried without exact names); bodyweight exercises go without weight; a checked sauna becomes a timed step after the last exercise (dropped on the last retry).
  Workout screen → watch icon sends the session, scheduled on its date. Settings → Garmin Connect (MFA supported).
  After a workout, `GarminSets` (tested) copies the phone's sets (reps, kg, exercise) into the watch's strength activity
  that day (activitylist search → `activity-service/activity/{id}/exerciseSets` GET/PUT; watch timing and rests kept,
  extra watch sets dropped, extra phone sets appended); automatic on Finish, or "Copy sets to Garmin" on the Day screen.
- Gym day review: `domain/StrengthReview` (tested): trend per exercise vs the last entry (↑ = ↓), volume change vs the last
  session of the same kind, records, kg/min, work/rest from the watch (`StrengthSession.workSec/restSec/rests`, saved with
  `garminSyncedAt` when sets are copied). Strength days put the sets card right under the verdict; `ActivityDetails(compact)`
  drops what the key tiles show and the HR zones. `ExerciseEntry.toFailure == false` at 8+ reps → `Progression` says heavier.
- Kettlebell first: every preset is a kettlebell library entry (`PresetsTest`); `KB_*` entries borrow photos where the DB has
  no kettlebell one (the workout says "Photo shows the dumbbell version"); `Preset.dbIdFor` moves old sessions to the new ids.
- Per-side exercises: `ExerciseEntry.perSide` (null = `guessPerSide` from the name: single-arm/leg, split squat, lunge…);
  reps are per side, volume counts both sides, the Garmin step note says "per side".
- Quick Settings tile `live/StartWorkoutTile.kt`; launcher shortcuts `res/xml/shortcuts.xml` (EXTRA_OPEN workout/weight/today).

## Design
Match Streakli, the user's habit tracker: repo `stefanruvceski/habit-tracker`, folder `apps/mobile/src`
(`lib/theme.ts`, `components/ui.tsx`, `components/WeekStrip.tsx`, `components/Charts.tsx`, `screens/TodayScreen.tsx`).
Palette and card/tile/ring/strip styles are already ported in `ui/theme/Theme.kt` and `ui/Components.kt`. Dark theme only.

## Data source
intervals.icu REST API, Basic auth with username `API_KEY` and the user's key as password, athlete id `0` = key owner.
Endpoints used: `GET /api/v1/athlete/0/activities?oldest&newest`, `GET /api/v1/athlete/0/wellness?oldest&newest`,
`GET /api/v1/activity/{id}/intervals`, `PUT /api/v1/athlete/0/wellness/{date}`, `GET /api/v1/athlete/0`.
