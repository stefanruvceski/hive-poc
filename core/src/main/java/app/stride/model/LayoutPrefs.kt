package app.stride.model

import kotlinx.serialization.Serializable

/** Visible tabs and sections; ids come from ui/Layout.kt. Empty lists mean the default order. */
@Serializable
data class LayoutPrefs(
    val hiddenTabs: List<String> = emptyList(),
    val tabOrder: List<String> = emptyList(),
    /** Section order per tab id. */
    val order: Map<String, List<String>> = emptyMap(),
    /** Hidden section ids ("tab.section"). */
    val hidden: List<String> = emptyList(),
)
