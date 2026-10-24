package app.stride.host

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stride.ui.GlassCard
import app.stride.ui.glowBackground
import app.stride.ui.theme.StrideColors
import app.stride.update.UpdateBanner
import app.stride.update.UpdateViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle as DateStyle
import java.util.Locale

/**
 * The app's home: a card per module (Health today, more later). Opening the app lands here;
 * widgets, notifications and shortcuts go straight into the right module. Back from a module
 * returns home. Open targets "<module>/<target>" pick the module; a bare target goes to the first.
 */
@Composable
fun ModuleHost(modules: List<AppModule>, open: OpenRequest) {
    var current by rememberSaveable { mutableStateOf<String?>(null) }
    var forward by rememberSaveable { mutableStateOf(OpenRequest(0, "")) }
    LaunchedEffect(open.id) {
        if (open.id == 0) return@LaunchedEffect
        val id = open.target.substringBefore('/', "")
        val module = modules.firstOrNull { it.id == id } ?: modules.first()
        current = module.id
        forward = OpenRequest(open.id, if (id == module.id) open.target.substringAfter('/') else open.target)
    }
    val module = modules.firstOrNull { it.id == current }
    if (module != null) {
        BackHandler { current = null }
        module.Content(forward, onHome = { current = null })
        return
    }
    Home(modules) { current = it.id }
}

@Composable
private fun Home(modules: List<AppModule>, onOpen: (AppModule) -> Unit) {
    val context = LocalContext.current
    val updates: UpdateViewModel = viewModel()
    val update by updates.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { updates.onForeground(context) }
    val today = LocalDate.now()
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..22 -> "Good evening"
        else -> "Hello"
    }
    LazyColumn(
        Modifier.fillMaxSize().background(StrideColors.Bg).glowBackground(StrideColors.Accent, StrideColors.Accent2).statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)) {
                Text(
                    "${today.dayOfWeek.getDisplayName(DateStyle.FULL, Locale.US)}, ${today.month.getDisplayName(DateStyle.SHORT, Locale.US)} ${today.dayOfMonth}".uppercase(),
                    style = TextStyle(fontSize = 12.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold),
                    color = StrideColors.Faint,
                )
                Text(greeting, style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold), color = StrideColors.Text)
                Text(Brand.NAME, style = MaterialTheme.typography.titleMedium, color = StrideColors.Accent)
            }
        }
        item {
            UpdateBanner(
                state = update,
                onInstall = { updates.install(context) },
                onDismiss = updates::dismiss,
                onOpenSettings = { updates.openPermissionSettings(context) },
                onCancelPermission = updates::cancelPermission,
            )
        }
        items(modules, key = { it.id }) { m -> ModuleCard(m) { onOpen(m) } }
        item { ComingSoon() }
    }
}

@Composable
private fun ModuleCard(m: AppModule, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick), tint = m.accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(m.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(m.icon, null, tint = m.accent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = StrideColors.Text)
                Text(m.subtitle, style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = m.accent)
        }
        Spacer(Modifier.height(12.dp))
        m.HomeSummary()
    }
}

/** A quiet placeholder so the home screen reads as "more apps go here". */
@Composable
private fun ComingSoon() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(StrideColors.Elev.copy(alpha = 0.5f)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.AddCircleOutline, null, tint = StrideColors.Faint)
        Spacer(Modifier.width(12.dp))
        Text("More apps will live here, like Habits.", style = MaterialTheme.typography.bodyMedium, color = StrideColors.Faint)
    }
}
