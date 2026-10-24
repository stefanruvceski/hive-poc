package app.stride.ui.screens

import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Search
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HotTub
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.data.ExerciseLibrary
import app.stride.data.ExerciseMatcher
import app.stride.live.LiveWorkout
import app.stride.garmin.GarminExercises
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stride.data.LibraryExercise
import app.stride.domain.Deload
import app.stride.domain.Progression
import app.stride.domain.Records
import app.stride.domain.Warmups
import app.stride.domain.mondayOf
import app.stride.domain.dateShort
import app.stride.domain.dayLong
import app.stride.domain.fmtDur
import app.stride.domain.fmtKg
import app.stride.domain.parseNum
import app.stride.domain.planFor
import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.DefaultPlan
import app.stride.model.ExerciseEntry
import app.stride.model.SetEntry
import app.stride.model.StrengthSession
import app.stride.ui.AppViewModel
import app.stride.ui.FCard
import app.stride.ui.GlassCard
import app.stride.ui.SectionLabel
import app.stride.ui.theme.StrideColors
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.UUID

// ------------------------------------------------------------------ session setup

private fun lastWeight(data: AppData, name: String): Double? =
    data.strength.filter { it.logged }.sortedBy { it.date }.flatMap { it.exercises }.lastOrNull { it.name == name }?.weightKg

/**
 * A fresh workout for [date]: last logged session of the same kind (same exercises, kg and reps,
 * nothing ticked yet), else the program presets at 2 x 8.
 */
private fun newSession(data: AppData, date: LocalDate, kind: DayKind): StrengthSession {
    val last = data.strength.filter { it.kind == kind && it.logged && it.date < date.toString() }.maxByOrNull { it.date }
    val deload = isDeload(data, date)
    val base = last?.exercises?.map { e -> e.copy(done = false) to e.workingSets.map { it.copy(done = false) } }
        ?: DefaultPlan.presets(kind).map { p ->
            val kg = lastWeight(data, p.name) ?: 16.0
            ExerciseEntry(p.name, p.group, dbId = p.dbId, done = false) to List(2) { SetEntry(kg, 8) }
        }
    val exercises = base.map { (e, work) ->
        val sets = if (deload) work.map { it.copy(kg = Deload.lighter(it.kg)) } else work
        e.withSets(if (data.settings.warmups) Warmups.apply(sets) else sets)
    }
    val plan = data.planFor(date)
    return StrengthSession(
        id = UUID.randomUUID().toString(),
        date = date.toString(),
        kind = kind,
        exercises = exercises,
        sauna = plan?.sauna ?: false,
        saunaMinutes = if (plan?.sauna == true) 15 else 0,
        finished = false,
    )
}

internal fun isDeload(data: AppData, date: LocalDate) = mondayOf(date).toString() in data.deloadWeeks

// ------------------------------------------------------------------ screen

/**
 * Workout mode: list of the day's exercises → tap one for its picture, steps and sets →
 * tick sets (starts the rest timer) → "Done" moves on to the next exercise.
 * Every change is saved immediately, so leaving the app mid-workout loses nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(vm: AppViewModel, data: AppData, date: LocalDate, sessionId: String?, onBack: () -> Unit, onHistory: (String) -> Unit) {
    val context = LocalContext.current
    val library by produceState(emptyList<LibraryExercise>()) { value = ExerciseLibrary.load(context) }
    val planKind = data.planFor(date)?.kind?.takeIf { it.isStrength }

    // An existing session (given, or already started for this day), else an unsaved draft.
    val existingId = remember(sessionId) {
        sessionId ?: data.strength.firstOrNull { it.date == date.toString() && (planKind == null || it.kind == planKind) }?.id
    }
    var draft by remember { mutableStateOf(newSession(data, date, planKind ?: DayKind.STRENGTH_LEGS)) }
    val stored = data.strength.firstOrNull { it.id == (existingId ?: draft.id) }
    val session = stored ?: draft
    fun commit(s: StrengthSession) = vm.saveStrength(s)

    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pickingPicture by remember { mutableStateOf(false) }
    val timer = rememberRestTimer()
    val garminUi by vm.garminUi.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshGarmin() }

    // Lock-screen notification with the current set and rest; follows every change to the session.
    LaunchedEffect(stored, timer.endAt, data.settings.liveWorkout) {
        if (stored != null && data.settings.liveWorkout) LiveWorkout.show(context, stored, timer.endAt, timer.length)
        else if (stored == null || !data.settings.liveWorkout) LiveWorkout.stop(context)
    }
    // A rest started from the notification shows up in the in-app timer too.
    LaunchedEffect(data) {
        val end = LiveWorkout.restEnd(context)
        if (end > timer.endAt && end > System.currentTimeMillis()) timer.endAt = end
    }

    BackHandler(enabled = open != null) { open = null }

    fun libFor(e: ExerciseEntry): LibraryExercise? =
        e.dbId?.let { id -> library.firstOrNull { it.id == id } } ?: ExerciseMatcher.best(library, e.name)

    /** A record this exercise sets against all earlier sessions, if any. */
    fun recordFor(e: ExerciseEntry): Records.Record? =
        if (!e.done) null else Records.record(e, Records.history(data.strength.filter { it.id != session.id && it.date < session.date }, e.name))

    fun updateExercise(i: Int, f: (ExerciseEntry) -> ExerciseEntry) {
        commit(session.copy(exercises = session.exercises.mapIndexed { j, e -> if (j == i) f(e) else e }))
    }

    val current = open?.let { session.exercises.getOrNull(it) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (current != null) "${open!! + 1} of ${session.exercises.size}" else shortKind(session.kind))
                        Text("${dayLong(date)}, ${dateShort(date)}", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (open != null) open = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, if (open != null) "All exercises" else "Back")
                    }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = {
                            val i = open!!
                            open = null
                            commit(session.copy(exercises = session.exercises.filterIndexed { j, _ -> j != i }))
                        }) { Icon(Icons.Filled.Delete, "Remove exercise") }
                    } else {
                        if (garminUi.connected) {
                            if (garminUi.busy) CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                            else IconButton(onClick = { vm.sendToGarmin(session, data.planFor(date)?.title ?: shortKind(session.kind)) }) {
                                Icon(Icons.Filled.Watch, "Send to Garmin")
                            }
                        }
                    }
                    if (current == null && stored != null) {
                        IconButton(onClick = { LiveWorkout.stop(context); vm.deleteStrength(stored.id); vm.toast("Workout deleted"); onBack() }) {
                            Icon(Icons.Filled.Delete, "Delete workout")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = { RestTimerBar(timer) },
    ) { padding ->
        if (current != null) {
            val i = open!!
            // Unfinished exercises other than this one: the next is the first after it, else the first before it.
            val others = session.exercises.withIndex().filter { (j, e) -> j != i && !e.done }
            val next = others.firstOrNull { it.index > i } ?: others.firstOrNull()
            ExerciseDetail(
                e = current,
                lib = libFor(current),
                last = Progression.lastEntry(data.strength.filter { it.logged }, current.name, date.toString(), session.id),
                padding = padding,
                nextName = next?.value?.name,
                onChange = { f -> updateExercise(i, f) },
                onSetDone = { timer.start() },
                onPickPicture = { pickingPicture = true },
                onHistory = { onHistory(current.name) },
                onDone = {
                    val finished = current.withSets(current.expandedSets().map { it.copy(done = true) }).copy(done = true)
                    updateExercise(i) { finished }
                    recordFor(finished)?.let { vm.toast("🏆 New record: ${Records.describe(finished.name, it)}") }
                    open = next?.index // null = all done, back to the list
                },
            )
        } else {
            WorkoutList(
                session = session,
                isStored = stored != null,
                padding = padding,
                libFor = ::libFor,
                recordFor = ::recordFor,
                deload = isDeload(data, date),
                onOpen = { open = it },
                onKind = { k ->
                    if (stored == null) {
                        draft = newSession(data, date, k)
                    } else {
                        commit(newSession(data, date, k).copy(id = stored.id))
                    }
                },
                onAdd = { adding = true },
                onSauna = { on, min -> commit(session.copy(sauna = on, saunaMinutes = min)) },
                onNote = { commit(session.copy(note = it)) },
                onFinish = {
                    commit(session.copy(finished = true))
                    vm.toast("Workout saved · ${session.exercises.count { it.done }} exercises · ${fmtKg(session.volume)} kg")
                    onBack()
                },
            )
        }
    }

    if (adding) {
        ExercisePickerDialog(
            title = "Add exercise",
            library = library,
            allowCustom = true,
            onDismiss = { adding = false },
            onPick = { name, lib ->
                adding = false
                val kg = lastWeight(data, name) ?: if (GarminExercises.isBodyweight(name)) 0.0 else 16.0
                val group = lib?.muscles?.firstOrNull()?.replaceFirstChar { it.uppercase() }.orEmpty()
                val entry = ExerciseEntry(name, group, dbId = lib?.id, done = false).withSets(List(2) { SetEntry(kg, 8) })
                commit(session.copy(exercises = session.exercises + entry))
                open = session.exercises.size
            },
        )
    }
    if (pickingPicture && current != null) {
        val i = open!!
        ExercisePickerDialog(
            title = "Picture for \"${current.name}\"",
            library = library,
            initialQuery = current.name,
            allowCustom = false,
            onDismiss = { pickingPicture = false },
            onPick = { _, lib ->
                pickingPicture = false
                if (lib != null) updateExercise(i) { it.copy(dbId = lib.id) }
            },
        )
    }
}

// ------------------------------------------------------------------ list

@Composable
private fun WorkoutList(
    session: StrengthSession,
    isStored: Boolean,
    padding: PaddingValues,
    libFor: (ExerciseEntry) -> LibraryExercise?,
    recordFor: (ExerciseEntry) -> Records.Record?,
    deload: Boolean,
    onOpen: (Int) -> Unit,
    onKind: (DayKind) -> Unit,
    onAdd: () -> Unit,
    onSauna: (Boolean, Int) -> Unit,
    onNote: (String) -> Unit,
    onFinish: () -> Unit,
) {
    val done = session.exercises.count { it.done }
    val total = session.exercises.size
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 4.dp, 16.dp, padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth(), tint = StrideColors.Accent) {
                Text(
                    if (done == 0) "Ready when you are" else if (done == total) "All exercises done" else "$done of $total exercises done",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(StrideColors.Elev2)) {
                    if (total > 0 && done > 0) {
                        Box(Modifier.fillMaxWidth(done.toFloat() / total).height(6.dp).clip(RoundedCornerShape(3.dp)).background(StrideColors.Accent))
                    }
                }
                if (done == 0 && session.exercises.none { e -> e.setList.any { it.done } }) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        STRENGTH_KINDS.forEach { k ->
                            FilterChip(selected = session.kind == k, onClick = { if (session.kind != k) onKind(k) }, label = { Text(shortKind(k)) })
                        }
                    }
                }
                if (deload) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Deload week: bells at about 70% of usual. Same exercises, easier sets, no failure.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StrideColors.Manual,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(DefaultPlan.STRENGTH_RULES, style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
            }
        }

        itemsIndexed(session.exercises) { i, e ->
            ExerciseRow(e, libFor(e), recordFor(e) != null) { onOpen(i) }
        }

        item {
            TextButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Add exercise")
            }
        }

        item {
            FCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.HotTub, null, tint = StrideColors.Accent2)
                    Spacer(Modifier.width(12.dp))
                    Text("Sauna", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = session.sauna, onCheckedChange = { onSauna(it, if (it) session.saunaMinutes.takeIf { m -> m > 0 } ?: 15 else 0) })
                }
                if (session.sauna) {
                    Spacer(Modifier.height(8.dp))
                    NumCell("minutes", session.saunaMinutes.toDouble(), decimal = false, modifier = Modifier.fillMaxWidth()) { onSauna(true, it.toInt()) }
                    Spacer(Modifier.height(4.dp))
                    Text("Sent to the watch as a timer after the last exercise.", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
                }
            }
        }
        item {
            var note by remember(session.id) { mutableStateOf(session.note) }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it; onNote(it) },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(onClick = onFinish, enabled = done > 0 || isStored, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (session.finished) "Save" else "Finish workout")
            }
        }
    }
}

@Composable
private fun ExerciseRow(e: ExerciseEntry, lib: LibraryExercise?, record: Boolean, onClick: () -> Unit) {
    val sets = e.workingSets.ifEmpty { e.expandedSets() }
    val kgs = sets.map { it.kg }.distinct()
    val summary = "${sets.size} × ${sets.map { it.reps }.distinct().joinToString("/")}" +
        if (kgs.any { it > 0 }) " · ${kgs.joinToString("/") { fmtKg(it) }} kg" else ""
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (e.done) StrideColors.Accent.copy(alpha = 0.08f) else StrideColors.Elev,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (e.done) StrideColors.Accent.copy(alpha = 0.4f) else StrideColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ExercisePicture(lib, Modifier.size(60.dp), animate = false, corner = 12)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (e.group.isNotEmpty()) Text(e.group.uppercase(), style = MaterialTheme.typography.labelSmall, color = StrideColors.Accent)
                Text(
                    e.name + if (record) "  🏆" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = StrideColors.Text,
                    maxLines = 2,
                )
                Text(summary, style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
            }
            Icon(
                if (e.done) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = if (e.done) StrideColors.Accent else StrideColors.Faint,
            )
        }
    }
}

// ------------------------------------------------------------------ one exercise

@Composable
private fun ExerciseDetail(
    e: ExerciseEntry,
    lib: LibraryExercise?,
    last: ExerciseEntry?,
    padding: PaddingValues,
    nextName: String?,
    onHistory: () -> Unit,
    onChange: ((ExerciseEntry) -> ExerciseEntry) -> Unit,
    onSetDone: () -> Unit,
    onPickPicture: () -> Unit,
    onDone: () -> Unit,
) {
    val sets = e.expandedSets()
    var showAllSteps by remember(e.name) { mutableStateOf(false) }
    fun setSets(list: List<SetEntry>) = onChange { it.withSets(list) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 4.dp, 16.dp, padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ExercisePicture(lib, Modifier.fillMaxWidth().aspectRatio(4f / 3f), animate = true, corner = 20, onMissing = onPickPicture)
        }
        item {
            Column {
                if (e.group.isNotEmpty()) Text(e.group.uppercase(), style = MaterialTheme.typography.labelMedium, color = StrideColors.Accent)
                Text(e.name, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold), color = StrideColors.Text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    lib?.let { Text("Picture: ${it.name}", style = MaterialTheme.typography.bodySmall, color = StrideColors.Faint, modifier = Modifier.weight(1f)) }
                        ?: Spacer(Modifier.weight(1f))
                    TextButton(onClick = onHistory) { Text("History") }
                    TextButton(onClick = onPickPicture) {
                        Icon(Icons.Filled.ImageSearch, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (lib == null) "Find picture" else "Change")
                    }
                }
            }
        }
        lib?.steps?.takeIf { it.isNotEmpty() }?.let { steps ->
            item {
                FCard {
                    Text("How to", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    (if (showAllSteps) steps else steps.take(2)).forEachIndexed { n, step ->
                        Text("${n + 1}. $step", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    if (steps.size > 2) {
                        TextButton(onClick = { showAllSteps = !showAllSteps }) { Text(if (showAllSteps) "Less" else "All ${steps.size} steps") }
                    }
                }
            }
        }
        Progression.hint(last)?.let { h ->
            item { ProgressionHint(h) { kg -> setSets(Warmups.apply(sets.filter { !it.warmup }.map { it.copy(kg = kg) }).let { if (sets.any { it.warmup }) it else it.filter { s -> !s.warmup } }) } }
        }
        item {
            FCard {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("SET", style = MaterialTheme.typography.labelSmall, color = StrideColors.Faint, modifier = Modifier.width(36.dp))
                    Text("KG", style = MaterialTheme.typography.labelSmall, color = StrideColors.Faint, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text("REPS", style = MaterialTheme.typography.labelSmall, color = StrideColors.Faint, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(56.dp))
                }
                val warmCount = sets.count { it.warmup }
                sets.forEachIndexed { n, set ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).alpha(if (set.warmup && !set.done) 0.75f else 1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (set.warmup) "W" else "${n - warmCount + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (set.done) StrideColors.Accent else if (set.warmup) StrideColors.Amber else StrideColors.Text,
                            modifier = Modifier.width(36.dp),
                        )
                        NumCell("kg", set.kg, decimal = true, modifier = Modifier.weight(1f)) { v -> setSets(sets.mapIndexed { k, s -> if (k == n) s.copy(kg = v) else s }) }
                        Spacer(Modifier.width(8.dp))
                        NumCell("reps", set.reps.toDouble(), decimal = false, modifier = Modifier.weight(1f)) { v -> setSets(sets.mapIndexed { k, s -> if (k == n) s.copy(reps = v.toInt()) else s }) }
                        IconButton(onClick = {
                            val nowDone = !set.done
                            setSets(sets.mapIndexed { k, s -> if (k == n) s.copy(done = nowDone) else s })
                            if (nowDone) onSetDone()
                        }, modifier = Modifier.width(56.dp)) {
                            Icon(
                                if (set.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                if (set.done) "Set done" else "Mark set done",
                                tint = if (set.done) StrideColors.Accent else StrideColors.Faint,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                }
                Row {
                    TextButton(onClick = { setSets(sets + (sets.lastOrNull { !it.warmup }?.copy(done = false) ?: SetEntry(16.0, 8))) }) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add set")
                    }
                    if (sets.count { !it.warmup } > 1) {
                        TextButton(onClick = { setSets(sets.dropLast(1)) }) {
                            Icon(Icons.Filled.Remove, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remove set")
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Filled.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (nextName == null) "Done · back to all exercises" else "Done · next: $nextName",
                    maxLines = 1,
                )
            }
        }
    }
}

/** Number field that keeps what's typed ("16." while typing) and reports parsed values. */
@Composable
private fun NumCell(label: String, value: Double, decimal: Boolean, modifier: Modifier = Modifier, onValue: (Double) -> Unit) {
    val shown = if (decimal) fmtKg(value) else value.toInt().toString()
    var text by remember(shown) { mutableStateOf(if (value == 0.0 && !decimal) "" else shown) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            if (v.all { it.isDigit() || (decimal && (it == '.' || it == ',')) }) {
                text = v
                (if (decimal) parseNum(v) else v.toIntOrNull()?.toDouble())?.let(onValue)
            }
        },
        label = { Text(label) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

// ------------------------------------------------------------------ pictures

/**
 * The exercise's two photos (start and end position), alternating like a short GIF.
 * Without a match it shows a placeholder that opens the picture search.
 */
@Composable
fun ExercisePicture(lib: LibraryExercise?, modifier: Modifier, animate: Boolean, corner: Int, onMissing: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(corner.dp)
    if (lib == null) {
        Box(
            modifier.clip(shape).background(StrideColors.Elev2).let { if (onMissing != null) it.clickable { onMissing() } else it },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.FitnessCenter, null, tint = StrideColors.Faint)
                if (onMissing != null) Text("Tap to find a picture", style = MaterialTheme.typography.bodySmall, color = StrideColors.Faint)
            }
        }
        return
    }
    var frame by remember(lib.id) { mutableIntStateOf(0) }
    LaunchedEffect(lib.id, animate) {
        if (!animate) return@LaunchedEffect
        while (true) {
            delay(1100)
            frame = 1 - frame
        }
    }
    Box(modifier.clip(shape).background(StrideColors.Elev2)) {
        if (!animate) {
            // Thumbnails: fill the square, no bars.
            AsyncImage(model = lib.images.first(), contentDescription = lib.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            return@Box
        }
        lib.images.forEachIndexed { i, url ->
            val a by animateFloatAsState(if (i == frame) 1f else 0f, label = "frame")
            // Behind: the same photo cropped, blurred and dimmed, so there are no empty bars beside it.
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(a).blur(28.dp),
            )
            Box(Modifier.fillMaxSize().alpha(a).background(StrideColors.Bg.copy(alpha = 0.45f)))
            // Front: the whole photo.
            AsyncImage(
                model = url,
                contentDescription = if (i == 0) "${lib.name}, start position" else "${lib.name}, end position",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().alpha(a),
            )
        }
    }
}

/**
 * Full-screen exercise browser: search as you type, filter by equipment and muscle group
 * (kettlebell exercises first). Optionally adds a custom name without a picture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerDialog(
    title: String,
    library: List<LibraryExercise>,
    initialQuery: String = "",
    allowCustom: Boolean,
    onDismiss: () -> Unit,
    onPick: (String, LibraryExercise?) -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }
    var equipment by remember { mutableStateOf(if (initialQuery.isBlank()) "kettlebells" else null) }
    var group by remember { mutableStateOf<String?>(null) }
    val results = remember(query, library, equipment, group) { ExerciseMatcher.browse(library, query, equipment, group) }
    val equipmentChips = listOf("kettlebells" to "Kettlebell", null to "All", "body only" to "Bodyweight", "dumbbell" to "Dumbbell", "bands" to "Bands")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(StrideColors.Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close") }
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search ${library.size} exercises") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                equipmentChips.forEach { (value, label) ->
                    FilterChip(selected = equipment == value, onClick = { equipment = value }, label = { Text(label) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = group == null, onClick = { group = null }, label = { Text("All muscles") })
                ExerciseMatcher.GROUPS.forEach { (g, _) ->
                    FilterChip(selected = group == g, onClick = { group = if (group == g) null else g }, label = { Text(g) })
                }
            }
            Text(
                "${results.size} exercise${if (results.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = StrideColors.Faint,
                modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 2.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                if (allowCustom && query.isNotBlank()) {
                    item {
                        Text(
                            "+ Add \"${query.trim()}\" as typed",
                            color = StrideColors.Accent,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(query.trim(), ExerciseMatcher.best(library, query)) }.padding(vertical = 12.dp),
                        )
                    }
                }
                itemsIndexed(results, key = { _, ex -> ex.id }) { _, ex ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(ex.name, ex) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExercisePicture(ex, Modifier.size(56.dp), animate = false, corner = 12)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ex.name, style = MaterialTheme.typography.bodyLarge, color = StrideColors.Text)
                            Text(
                                (ex.muscles.take(3) + ex.equipment).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = StrideColors.Faint,
                            )
                        }
                    }
                }
                if (results.isEmpty()) {
                    item {
                        Text(
                            "Nothing found. Try fewer words or another filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StrideColors.Dim,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ progression hint

/** "Last time" line with the progressive-overload suggestion. */
@Composable
fun ProgressionHint(h: Progression.Hint, onUse: (Double) -> Unit) {
    val (text, color, useKg) = when (h) {
        is Progression.Hint.GoHeavier -> Triple(
            "Last: ${fmtKg(h.lastKg)} kg × ${h.lastReps}. Top of the range, go ${fmtKg(h.nextKg)} kg",
            StrideColors.Accent, h.nextKg,
        )
        is Progression.Hint.AddRep -> Triple(
            "Last: ${fmtKg(h.lastKg)} kg × ${h.lastReps}. Same bell, aim for ${h.lastReps + 1}+",
            StrideColors.Dim, null,
        )
        is Progression.Hint.GoLighter -> Triple(
            "Last: ${fmtKg(h.lastKg)} kg × ${h.lastReps}. Below 6 reps, try ${fmtKg(h.lighterKg)} kg",
            StrideColors.Warn, h.lighterKg,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(1f))
        if (useKg != null) TextButton(onClick = { onUse(useKg) }) { Text("Use") }
    }
}

// ------------------------------------------------------------------ rest timer

@Stable
class RestTimerState {
    var length by mutableIntStateOf(120)
    var endAt by mutableLongStateOf(0L)
    val running: Boolean get() = endAt > 0L
    fun start() {
        endAt = System.currentTimeMillis() + length * 1000L
    }
}

@Composable
fun rememberRestTimer(): RestTimerState = remember { RestTimerState() }

/**
 * Rest timer pinned to the bottom: pick 1:30 / 2:00 / 3:00; ticking a set starts it.
 * Vibrates at zero and keeps the screen on while running.
 */
@Composable
fun RestTimerBar(state: RestTimerState) {
    val context = LocalContext.current
    val view = LocalView.current
    var left by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.endAt) {
        if (state.endAt == 0L) return@LaunchedEffect
        while (true) {
            val ms = state.endAt - System.currentTimeMillis()
            left = ((ms + 999) / 1000).toInt().coerceAtLeast(0)
            if (ms <= 0) {
                vibrate(context)
                state.endAt = 0L
                break
            }
            delay(200)
        }
    }
    DisposableEffect(state.running) {
        view.keepScreenOn = state.running
        onDispose { view.keepScreenOn = false }
    }

    Surface(color = StrideColors.Elev, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Timer, null, tint = if (state.running) StrideColors.Accent else StrideColors.Dim)
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.running) fmtDur(left.toDouble()) else "Rest",
                style = MaterialTheme.typography.titleMedium,
                color = if (state.running) StrideColors.Accent else StrideColors.Text,
                modifier = Modifier.width(56.dp),
            )
            if (state.running) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { state.endAt += 30_000 }) { Text("+30 s") }
                OutlinedButton(onClick = { state.endAt = 0L }) { Text("Stop") }
            } else {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(90, 120, 180).forEach { sec ->
                        FilterChip(selected = state.length == sec, onClick = { state.length = sec }, label = { Text(fmtDur(sec.toDouble())) })
                    }
                }
                Button(onClick = { state.start() }) { Text("Start") }
            }
        }
    }
}

private fun vibrate(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 600), -1))
    }
}
