package app.stride

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import app.stride.ui.OpenRequest
import app.stride.widget.Widgets
import app.stride.reminder.PlanReminder
import app.stride.ui.StrideApp
import app.stride.ui.theme.StrideTheme

class MainActivity : ComponentActivity() {
    // Bumped when the Sunday notification is tapped, so the app opens next week's plan.
    private val planReviewRequest = mutableIntStateOf(0)

    // Where a widget asked to open ("workout", a tab id, "day:YYYY-MM-DD"), with a counter so repeats still fire.
    private val openRequest = mutableStateOf(OpenRequest(0, ""))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        PlanReminder.schedule(this)
        if (savedInstanceState == null) handle(intent)
        setContent {
            StrideTheme {
                StrideApp(planReviewRequest = planReviewRequest.intValue, openRequest = openRequest.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(PlanReminder.EXTRA_REVIEW_PLAN, false) == true) {
            intent.removeExtra(PlanReminder.EXTRA_REVIEW_PLAN)
            planReviewRequest.intValue++
        }
        intent?.getStringExtra(Widgets.EXTRA_OPEN)?.let { target ->
            intent.removeExtra(Widgets.EXTRA_OPEN)
            openRequest.value = OpenRequest(openRequest.value.id + 1, target)
        }
    }
}
