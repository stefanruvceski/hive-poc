package app.stride.format

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.stride.ui.FCard
import app.stride.ui.Segment
import app.stride.ui.theme.StrideColors

/** The Hive-wide date format choice; the same card in Hive settings, Health settings and Money setup. */
@Composable
fun DateFormatCard() {
    val context = LocalContext.current
    FCard {
        Text("Date format", style = MaterialTheme.typography.titleMedium)
        Text("Used in every app in Hive.", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateStyle.entries.forEach { s -> Segment("${s.label}\n${s.example}", Dates.style == s) { Dates.set(context, s) } }
        }
    }
}
