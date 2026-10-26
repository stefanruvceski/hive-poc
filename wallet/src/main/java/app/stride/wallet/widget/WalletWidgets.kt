package app.stride.wallet.widget

import android.content.Context
import android.service.quicksettings.TileService
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import app.stride.host.Host
import app.stride.ui.theme.StrideColors
import app.stride.wallet.data.WalletStore
import app.stride.wallet.domain.CardBrands
import app.stride.wallet.domain.CardOrder
import app.stride.wallet.model.LoyaltyCard
import app.stride.widget.Frame
import app.stride.widget.Kicker
import app.stride.widget.OpenKey
import app.stride.widget.scale
import app.stride.widget.sp
import app.stride.widget.style
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Redraws the Wallet widget after the cards change. Never throws. */
object WalletWidgets {
    suspend fun refresh(context: Context) {
        runCatching { CardsWidget().updateAll(context) }
    }
}

/** The cards on a home-screen widget: pinned and last used first; a tap opens that card at the till. */
class CardsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cards = withContext(Dispatchers.IO) { CardOrder.forWidget(WalletStore(context).load().cards, 4) }
        provideContent { CardsContent(cards) }
    }
}

class CardsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CardsWidget()
}

@Composable
private fun CardsContent(cards: List<LoyaltyCard>) {
    val k = scale(250f, 180f)
    val context = LocalContext.current
    Frame(open = "wallet/cards", padding = 12) {
        Kicker("Loyalty cards", k)
        Spacer(GlanceModifier.height(6.dp))
        if (cards.isEmpty()) {
            Text("Add your cards in Wallet", style = style(StrideColors.Faint, sp(13f, k)))
            return@Frame
        }
        cards.forEach { c ->
            val brand = CardBrands.find(c.name)
            val color = Color(brand?.let { if (it.light) it.color2 else it.color } ?: c.color)
            Row(
                GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clickable(actionStartActivity(Host.component(context), actionParametersOf(OpenKey to "wallet/card:${c.id}"))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(GlanceModifier.width((30 * k).dp).height((20 * k).dp).cornerRadius(5.dp).background(color)) {}
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text((if (c.pinned) "📌 " else "") + c.name.ifBlank { "Card" }, style = style(StrideColors.Text, sp(13.5f, k), bold = true), maxLines = 1)
                    if (c.number.length > 4) Text("•••• " + c.number.takeLast(4), style = style(StrideColors.Dim, sp(10.5f, k)), maxLines = 1)
                }
            }
        }
    }
}

/** Quick Settings tile: one tap opens the loyalty cards. */
class CardsTile : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Host.openIntent(this, "wallet/cards")
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
