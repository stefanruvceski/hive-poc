package app.stride.host

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import app.stride.ui.theme.StrideColors

/**
 * Shows the modules. With one module it is that module, full screen (today's app). With several,
 * a home screen of module icons; open requests "<module>/<target>" go straight to that module.
 */
@Composable
fun ModuleHost(modules: List<AppModule>, open: OpenRequest) {
    if (modules.size == 1) {
        modules[0].Content(open, onHome = null)
        return
    }
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
    Column(Modifier.fillMaxSize().background(StrideColors.Bg).statusBarsPadding().padding(16.dp)) {
        Text("Stride", style = MaterialTheme.typography.headlineSmall, color = StrideColors.Text)
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(modules, key = { it.id }) { m ->
                Column(Modifier.clickable { current = m.id }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(m.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(m.icon, null, tint = m.accent, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(m.title, style = MaterialTheme.typography.labelMedium, color = StrideColors.Text, maxLines = 1)
                }
            }
        }
    }
}
