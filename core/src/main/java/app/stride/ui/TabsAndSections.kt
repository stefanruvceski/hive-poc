package app.stride.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.stride.model.LayoutPrefs
import app.stride.ui.theme.StrideColors

/**
 * Every tab of a module with its sections: show or hide each and move it up or down. One screen
 * for every module (they pass their own tab list and prefs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsAndSectionsScreen(all: List<TabDef>, prefs: LayoutPrefs, onPrefs: (LayoutPrefs) -> Unit, onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Tabs and sections") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = { onPrefs(LayoutPrefs()) }) { Text("Reset all") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val tabs = LayoutOps.tabs(prefs, all)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 4.dp, 16.dp, padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Tip: long-press anywhere on a tab to edit its sections right there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StrideColors.Dim,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            items(tabs, key = { it.id }) { tab ->
                val i = tabs.indexOf(tab)
                val shown = tab.id !in prefs.hiddenTabs
                val expanded = open == tab.id
                FCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clickable { open = if (expanded) null else tab.id }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = StrideColors.Dim)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(tab.title, style = MaterialTheme.typography.titleMedium, color = if (shown) StrideColors.Text else StrideColors.Faint)
                                val hiddenCount = tab.sections.count { LayoutOps.hidden(prefs, tab, it) }
                                Text(
                                    if (!shown) "Hidden tab" else "${tab.sections.size - hiddenCount} of ${tab.sections.size} sections shown",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StrideColors.Dim,
                                )
                            }
                        }
                        IconButton(onClick = { onPrefs(LayoutOps.moveTab(prefs, tab, -1, all)) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Move tab up") }
                        IconButton(onClick = { onPrefs(LayoutOps.moveTab(prefs, tab, 1, all)) }, enabled = i < tabs.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "Move tab down") }
                        Switch(checked = shown, onCheckedChange = { onPrefs(LayoutOps.toggleTab(prefs, tab, all)) })
                    }
                    AnimatedVisibility(expanded) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            val sections = LayoutOps.sections(prefs, tab)
                            sections.forEachIndexed { j, s ->
                                if (j > 0) HorizontalDivider(color = StrideColors.Border)
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val visible = !LayoutOps.hidden(prefs, tab, s)
                                    Text(s.title, style = MaterialTheme.typography.bodyMedium, color = if (visible) StrideColors.Text else StrideColors.Faint, modifier = Modifier.weight(1f).padding(start = 32.dp))
                                    IconButton(onClick = { onPrefs(LayoutOps.moveSection(prefs, tab, s, -1)) }, enabled = j > 0, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Filled.KeyboardArrowUp, "Move up")
                                    }
                                    IconButton(onClick = { onPrefs(LayoutOps.moveSection(prefs, tab, s, 1)) }, enabled = j < sections.lastIndex, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Filled.KeyboardArrowDown, "Move down")
                                    }
                                    Switch(checked = visible, onCheckedChange = { onPrefs(LayoutOps.toggleSection(prefs, tab, s)) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
