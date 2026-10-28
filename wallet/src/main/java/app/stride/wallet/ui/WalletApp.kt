package app.stride.wallet.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stride.host.OpenRequest
import app.stride.ui.NavItem
import app.stride.ui.StrideNavBar
import app.stride.ui.theme.StrideColors
import app.stride.wallet.domain.BatchImport
import app.stride.wallet.domain.FiscalParse
import app.stride.wallet.model.LoyaltyCard
import app.stride.wallet.model.Receipt
import kotlinx.coroutines.launch

private val TABS = listOf("receipts" to "Receipts", "cards" to "Cards")

/**
 * The Wallet app: Receipts (fiscal receipts read from their QR code) and Cards (loyalty cards as barcodes).
 * Sub-screens are a small stack of route strings; back pops one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletApp(open: OpenRequest, onHome: (() -> Unit)?) {
    val vm: WalletViewModel = viewModel()
    val d by vm.data.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf("receipts") }
    val stack = remember { mutableStateListOf<String>() }
    var askLink by rememberSaveable { mutableStateOf(false) }

    fun push(r: String) { stack.add(r) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun toast(s: String) = Toast.makeText(context, s, Toast.LENGTH_LONG).show()

    /** A scanned or pasted text: save the receipt and open it. */
    fun receipt(text: String) {
        val added = vm.addReceipt(text)
        when {
            added == null -> toast("That isn't a fiscal receipt's QR code (it should link to suf.purs.gov.rs).")
            else -> {
                if (!added.second) toast("Already saved, opening it.")
                tab = "receipts"
                push("receipt:${added.first}")
            }
        }
    }
    fun scanReceipt() = Scanner.live(context, qrOnly = true, onResult = { receipt(it.value) }, onError = ::toast)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var batch by remember { mutableStateOf<List<BatchItem>>(emptyList()) }
    var batchOpen by remember { mutableStateOf(false) }

    /** One photo opens its receipt; several are read one by one and reported together (what was saved, what failed). */
    fun readPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            Scanner.fromImage(context, uris[0], qrOnly = true, onResult = { found ->
                val link = found.firstOrNull { FiscalParse.verificationUrl(it.value) != null }
                if (link == null) toast("No receipt QR code in that picture.") else receipt(link.value)
            }, onError = ::toast)
            return
        }
        tab = "receipts"
        batch = uris.map { BatchItem(it) }
        batchOpen = true
        scope.launch {
            val done = mutableListOf<BatchItem>()
            uris.forEachIndexed { i, uri ->
                done += runCatching { Scanner.read(context, uri) }.fold(
                    onSuccess = { codes ->
                        val links = BatchImport.links(codes.map { it.value })
                        if (links.isEmpty()) listOf(BatchItem(uri, fixed = BatchImport.State.NO_QR))
                        else links.map { l ->
                            vm.addReceipt(l)?.let { (id, isNew) -> BatchItem(uri, id, isNew, fixed = null) } ?: BatchItem(uri, fixed = BatchImport.State.NO_QR)
                        }
                    },
                    onFailure = { listOf(BatchItem(uri, fixed = BatchImport.State.UNREADABLE)) },
                )
                batch = done + uris.drop(i + 1).map { BatchItem(it) }
            }
            if (!batchOpen) toast("Photos read: " + BatchImport.summary(batch.map { it.state(vm.data.value, vm.loading.value) }))
        }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris -> readPhotos(uris) }

    /** Deleted at once; the snackbar's Undo puts it back. */
    fun deleteReceipt(r: Receipt) {
        vm.deleteReceipt(r.id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar("Receipt deleted", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.restoreReceipt(r)
        }
    }
    fun pickPhoto() = photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    val maniFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            toast(if (text == null) "Couldn't open that file." else vm.importMani(text))
            tab = "receipts"
        }
    }
    fun importMani() = maniFile.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*"))
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(open.id) {
        if (open.id == 0) return@LaunchedEffect
        when (open.target) {
            "scan" -> scanReceipt()
            "cards", "receipts" -> { stack.clear(); tab = open.target }
            "noqr", "fuel", "prices" -> push(open.target)
            else -> when {
                open.target.startsWith("card:") -> { stack.clear(); tab = "cards"; push(open.target) }
                open.target.startsWith("receipt:") -> { stack.clear(); tab = "receipts"; push(open.target) }
                open.target.startsWith("company:") -> { stack.clear(); tab = "receipts"; push(open.target) }
                // Shared to "Hive Receipt" (ReceiptShareActivity): save it and open it, like a scan.
                open.target.startsWith("link:") -> { stack.clear(); receipt(Uri.decode(open.target.removePrefix("link:"))) }
            }
        }
    }

    if (askLink) {
        val clip = remember {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        }
        var text by rememberSaveable { mutableStateOf(clip.takeIf { FiscalParse.verificationUrl(it) != null }.orEmpty()) }
        AlertDialog(
            onDismissRequest = { askLink = false },
            title = { Text("Receipt link") },
            text = { OutlinedTextField(text, { text = it }, placeholder = { Text("https://suf.purs.gov.rs/v/?vl=…") }, minLines = 2) },
            confirmButton = { TextButton(onClick = { askLink = false; receipt(text) }, enabled = text.isNotBlank()) { Text("Add", color = WalletAccent) } },
            dismissButton = { TextButton(onClick = { askLink = false }) { Text("Cancel") } },
        )
    }

    val route = stack.lastOrNull()
    if (route != null) {
        BackHandler { pop() }
        when {
            route.startsWith("receipt:") -> route.removePrefix("receipt:").let { id -> ReceiptScreen(vm, d, id, id in loading, ::pop) }
            route == "noqr" -> NoQrScreen(vm, onFound = { id -> pop(); tab = "receipts"; push("receipt:$id") }, onBack = ::pop)
            route == "fuel" -> FuelScreen(vm, d, onReceipt = { push("receipt:$it") }, onBack = ::pop)
            route.startsWith("company:") -> CompanyReportScreen(d, route.removePrefix("company:"), onReceipt = { push("receipt:$it") }, onBack = ::pop)
            route == "prices" -> PricesScreen(d, onProduct = { push("product:$it") }, onBack = ::pop)
            route.startsWith("product:") -> ProductScreen(vm, d, route.removePrefix("product:"), onReceipt = { push("receipt:$it") }, onBack = ::pop)
            route.startsWith("card:") -> CardScreen(vm, d, route.removePrefix("card:"), onEdit = { push("edit:" + route.removePrefix("card:")) }, onBack = ::pop)
            // Saved: back to all cards, where the new one now sits, with a small note.
            route == "new-card" -> CardForm(LoyaltyCard("", "", ""), onSave = { c ->
                vm.saveCard(c); stack.clear(); tab = "cards"; Toast.makeText(context, "Card saved", Toast.LENGTH_SHORT).show()
            }, onBack = ::pop)
            route.startsWith("edit:") -> {
                val c = d.cards.firstOrNull { it.id == route.removePrefix("edit:") }
                if (c == null) LaunchedEffect(route) { pop() } else CardForm(c, onSave = { vm.saveCard(it); pop(); Toast.makeText(context, "Card saved", Toast.LENGTH_SHORT).show() }, onBack = ::pop)
            }
            else -> LaunchedEffect(route) { pop() }
        }
        return
    }

    Scaffold(
        modifier = Modifier.walletGlow(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { if (onHome != null) IconButton(onClick = onHome) { Icon(Icons.Filled.Apps, "All apps") } },
                title = { Text(if (tab == "cards") "Cards" else "Wallet", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    if (tab == "receipts") {
                        IconButton(onClick = ::scanReceipt) { Icon(Icons.Filled.QrCodeScanner, "Scan a receipt", tint = WalletAccent) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More ways to add", tint = StrideColors.Dim) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Receipt without QR") }, leadingIcon = { Icon(Icons.Filled.CameraAlt, null) }, onClick = { menu = false; push("noqr") })
                                DropdownMenuItem(text = { Text("Receipts from photos") }, leadingIcon = { Icon(Icons.Filled.Image, null) }, onClick = { menu = false; pickPhoto() })
                                DropdownMenuItem(text = { Text("Paste a receipt link") }, leadingIcon = { Icon(Icons.Filled.Link, null) }, onClick = { menu = false; askLink = true })
                                DropdownMenuItem(text = { Text("Import from Mani (CSV)") }, leadingIcon = { Icon(Icons.Filled.FileDownload, null) }, onClick = { menu = false; importMani() })
                            }
                        }
                    } else IconButton(onClick = { push("new-card") }) { Icon(Icons.Filled.Add, "Add a card", tint = WalletAccent) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            StrideNavBar(
                TABS.map { (id, title) -> NavItem(title, if (id == "cards") Icons.Filled.CreditCard else Icons.AutoMirrored.Filled.ReceiptLong) },
                TABS.indexOfFirst { it.first == tab },
            ) { tab = TABS[it].first }
        },
    ) { padding ->
        if (tab == "cards") CardsTab(d, padding, onCard = { push("card:$it") }, onAdd = { push("new-card") })
        else ReceiptsTab(d, loading, padding, onScan = ::scanReceipt, onPhoto = ::pickPhoto, onLink = { askLink = true }, onImport = ::importMani, onNoQr = { push("noqr") }, onFuel = { push("fuel") }, onPrices = { push("prices") }, onCompany = { push("company:$it") }, onDelete = ::deleteReceipt, onReceipt = { push("receipt:$it") })
        if (batchOpen && batch.isNotEmpty()) BatchReport(batch, d, loading, onOpen = { batchOpen = false; tab = "receipts"; push("receipt:$it") }, onDone = { batchOpen = false })
    }
}
