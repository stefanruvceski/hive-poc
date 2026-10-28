package app.stride.wallet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stride.wallet.data.FiscalClient
import app.stride.wallet.data.WalletStore
import app.stride.wallet.data.CardImages
import app.stride.wallet.data.NoQrPhotos
import app.stride.wallet.data.QrRepairClient
import app.stride.wallet.model.NoQrPhoto
import app.stride.wallet.domain.Aliases
import app.stride.wallet.domain.Brands
import app.stride.wallet.domain.FiscalParse
import app.stride.wallet.domain.ManiImport
import app.stride.wallet.widget.WalletWidgets
import app.stride.finance.FinanceHub
import app.stride.finance.SpendCategory
import app.stride.wallet.model.Company
import app.stride.wallet.model.LoyaltyCard
import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptStatus
import app.stride.wallet.model.WalletData
import app.stride.wallet.model.Warranty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

/** Wallet's state and changes, saved on every change. Receipts are fetched from the tax office in the background. */
class WalletViewModel(app: Application) : AndroidViewModel(app) {
    private val store = WalletStore(app)
    private val saveLock = Mutex()
    private val _data = MutableStateFlow(WalletData())
    val data: StateFlow<WalletData> = _data.asStateFlow()

    private val _loading = MutableStateFlow<Set<String>>(emptySet())
    /** Receipts being fetched right now. */
    val loading: StateFlow<Set<String>> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            _data.value = withContext(Dispatchers.IO) { store.load() }
            backfillBuyers()
            retryPending()
        }
    }

    private fun id(prefix: String) = "${prefix}_${UUID.randomUUID().toString().take(12)}"
    private fun now() = Instant.now().toString()

    private fun update(f: (WalletData) -> WalletData) {
        val before = _data.value
        _data.value = f(before)
        val cardsChanged = before.cards != _data.value.cards
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock { store.save(_data.value) }
            if (before.receipts != _data.value.receipts || before.companies != _data.value.companies) FinanceHub.changed()
            if (cardsChanged) WalletWidgets.refresh(getApplication<Application>())
        }
    }

    // ---------------------------------------------------------------- receipts

    /**
     * Saves the receipt behind a scanned QR link and fetches its details. Returns its id and whether it is new
     * (the same receipt scanned twice opens the saved one); null when the text isn't a fiscal receipt link.
     */
    fun addReceipt(text: String): Pair<String, Boolean>? {
        val url = FiscalParse.verificationUrl(text) ?: return null
        val key = FiscalParse.key(url)
        _data.value.receipts.firstOrNull { FiscalParse.key(it.url) == key }?.let { return it.id to false }
        val r = Receipt(id("rcp"), url, addedAt = now())
        update { it.copy(receipts = it.receipts + r) }
        fetch(r.id)
        return r.id to true
    }

    /** Fetches (again) one receipt's details from the tax office. */
    fun fetch(id: String) {
        val r = _data.value.receipts.firstOrNull { it.id == id } ?: return
        if (id in _loading.value) return
        _loading.value = _loading.value + id
        viewModelScope.launch {
            val answer = withContext(Dispatchers.IO) { FiscalClient.fetch(r.url) }
            val next: (Receipt) -> Receipt = when (answer) {
                is FiscalClient.Answer.Body -> { x -> FiscalParse.parse(x, answer.text) ?: x.copy(status = ReceiptStatus.FAILED, error = "The tax office's page didn't contain a receipt.") }
                is FiscalClient.Answer.Http -> { x ->
                    if (answer.code == 404) x.copy(status = ReceiptStatus.FAILED, error = "The tax office doesn't know this receipt.")
                    else x.copy(status = if (x.status == ReceiptStatus.OK) x.status else ReceiptStatus.PENDING, error = "The tax office answered ${answer.code}. Try again later.")
                }
                FiscalClient.Answer.Offline -> { x -> x.copy(status = if (x.status == ReceiptStatus.OK) x.status else ReceiptStatus.PENDING, error = "No connection. It will be read when you're online.") }
            }
            update { d ->
                val fetched = d.receipts.firstOrNull { it.id == id }?.let(next) ?: return@update d
                // A receipt imported from Mani and now scanned: keep the full one, with what the user added to the import.
                val imported = d.receipts.firstOrNull { it.id != id && it.source != null && ManiImport.same(it, fetched) }
                val merged = if (imported == null) fetched else fetched.copy(
                    note = fetched.note.ifBlank { imported.note },
                    starred = fetched.starred || imported.starred,
                    category = fetched.category ?: imported.category,
                    warranties = fetched.warranties + imported.warranties,
                    buyerTin = fetched.buyerTin ?: imported.buyerTin,
                )
                d.copy(receipts = d.receipts.filter { it.id != imported?.id }.map { if (it.id == id) merged else it })
            }
            _loading.value = _loading.value - id
        }
    }

    /**
     * A receipt found on the tax office's "verify without QR" page: its QR link when the page shows one (then it is
     * fetched like a scan), else the page itself. Returns the receipt's id, null when the page holds no receipt.
     */
    fun addFromVerifyPage(url: String, html: String): String? {
        (FiscalParse.verificationUrl(url) ?: FiscalParse.verificationUrl(html))?.let { link -> return addReceipt(link)?.first }
        val parsed = FiscalParse.parseHtml(Receipt(id("rcp"), "", addedAt = now()), html) ?: return null
        _data.value.receipts.firstOrNull { it.invoiceNumber != null && it.invoiceNumber == parsed.invoiceNumber }?.let { return it.id }
        val imported = _data.value.receipts.firstOrNull { it.source == "mani" && ManiImport.same(it, parsed) }
        update { d -> d.copy(receipts = d.receipts.filter { it.id != imported?.id } + parsed.copy(source = "verify", buyerTin = parsed.buyerTin ?: imported?.buyerTin, note = imported?.note.orEmpty())) }
        return parsed.id
    }

    /** A Money category for this receipt (null = filed by its kind). */
    fun setMoneyCategory(id: String, categoryId: String?) = update { d -> d.copy(receipts = d.receipts.map { if (it.id == id) it.copy(moneyCategory = categoryId) else it }) }

    private val _moneyCategories = MutableStateFlow<List<SpendCategory>>(emptyList())
    /** Money's spending categories, to file a receipt under one of them. */
    val moneyCategories: StateFlow<List<SpendCategory>> = _moneyCategories.asStateFlow()

    fun loadMoneyCategories() {
        viewModelScope.launch {
            _moneyCategories.value = withContext(Dispatchers.IO) { runCatching { FinanceHub.categories?.categories(getApplication<Application>()) }.getOrNull().orEmpty() }
        }
    }

    /** The Money category a receipt is filed under: picked by hand, else where Money files its kind. */
    suspend fun moneyCategoryFor(r: Receipt): SpendCategory? = withContext(Dispatchers.IO) {
        val book = FinanceHub.categories ?: return@withContext null
        val app = getApplication<Application>()
        runCatching {
            r.moneyCategory?.let { id -> book.categories(app).firstOrNull { it.id == id } }
                ?: book.categoryFor(app, Brands.category(r).name)
        }.getOrNull()
    }

    /** Kilometres at a fill-up (null clears it). */
    fun setOdometer(id: String, km: Int?) = update { d -> d.copy(receipts = d.receipts.map { if (it.id == id) it.copy(odometer = km) else it }) }

    /** Reads the receipts that were scanned offline. */
    fun retryPending() = _data.value.receipts.filter { it.status == ReceiptStatus.PENDING }.forEach { fetch(it.id) }

    /** Receipts saved before buyers were read: take the buyer's PIB from their printed text. */
    private fun backfillBuyers() {
        val todo = _data.value.receipts.filter { !it.ownerManual && it.buyerTin == null && it.journal != null }
            .mapNotNull { r -> FiscalParse.buyerTinIn(r.journal!!)?.let { r.id to it } }.toMap()
        if (todo.isNotEmpty()) update { d -> d.copy(receipts = d.receipts.map { r -> todo[r.id]?.let { r.copy(buyerTin = it) } ?: r }) }
    }

    /** Who a receipt is for, set by hand: a company's PIB, or null for personal. */
    fun setOwner(id: String, pib: String?) = update { d -> d.copy(receipts = d.receipts.map { if (it.id == id) it.copy(buyerTin = pib?.trim()?.ifEmpty { null }, ownerManual = true) else it }) }

    /**
     * Names a company by its PIB (an empty name forgets it). Naming a real PIB like a Mani group ("Obsidian IT")
     * moves that group's imported receipts to the PIB.
     */
    fun nameCompany(pib: String, name: String) = update { d ->
        val n = name.trim()
        val group = d.companies.firstOrNull { ManiImport.isGroup(it.pib) && !ManiImport.isGroup(pib) && it.name.equals(n, ignoreCase = true) }
        val rest = d.companies.filter { it.pib != pib && it.pib != group?.pib }
        d.copy(
            companies = if (n.isEmpty()) rest else rest + Company(pib, n),
            receipts = if (group == null) d.receipts else d.receipts.map { if (it.buyerTin == group.pib) it.copy(buyerTin = pib) else it },
        )
    }

    /** Adds the receipts of a Mani CSV export; returns a line for the user. */
    fun importMani(text: String): String {
        if (!ManiImport.looksLikeMani(text)) return "That isn't a Mani export (Profile › Preuzimanje računa)."
        val res = ManiImport.parse(text, _data.value.receipts, { id("rcp") }, now()) ?: return "Nothing could be read from that file."
        update { d -> d.copy(receipts = d.receipts + res.receipts, companies = d.companies + res.companies.filter { c -> d.companies.none { it.pib == c.pib } }) }
        return buildString {
            append("Imported ${res.receipts.size} receipt${if (res.receipts.size == 1) "" else "s"}")
            if (res.duplicates > 0) append(", ${res.duplicates} already here")
            if (res.skipped > 0) append(", ${res.skipped} unreadable")
            append(".")
        }
    }

    fun toggleStar(id: String) = update { d -> d.copy(receipts = d.receipts.map { if (it.id == id) it.copy(starred = !it.starred) else it }) }

    /** A category chosen by hand; null goes back to the guess. */
    fun setCategory(id: String, category: String?) = update { d -> d.copy(receipts = d.receipts.map { if (it.id == id) it.copy(category = category) else it }) }

    fun addWarranty(receiptId: String, item: String, months: Int) {
        val w = Warranty(id("wty"), item.trim(), months)
        update { d -> d.copy(receipts = d.receipts.map { if (it.id == receiptId) it.copy(warranties = it.warranties + w) else it }) }
    }

    fun deleteWarranty(id: String, warrantyId: String) = update { d ->
        d.copy(receipts = d.receipts.map { if (it.id == id) it.copy(warranties = it.warranties.filter { w -> w.id != warrantyId }) else it })
    }

    fun setNote(id: String, note: String) = update { d -> d.copy(receipts = d.receipts.map { if (it.id == id) it.copy(note = note) else it }) }

    fun deleteReceipt(id: String) = update { d -> d.copy(receipts = d.receipts.filter { it.id != id }) }

    /** Undo of a delete: puts the receipt back as it was. */
    fun restoreReceipt(r: Receipt) = update { d -> if (d.receipts.any { it.id == r.id }) d else d.copy(receipts = d.receipts + r) }

    // ---------------------------------------------------------------- prices: products and shops merged by hand

    /** [b]'s product becomes a spelling of [a]'s (a keeps its name). */
    fun mergeProducts(a: String, b: String) = update { d -> d.copy(productGroups = Aliases.merge(d.productGroups, a, b)) }

    /** The name a product is shown under; blank goes back to the most used one. */
    fun renameProduct(key: String, name: String?) = update { d -> d.copy(productGroups = Aliases.rename(d.productGroups, key, name)) }

    fun unmergeProduct(key: String) = update { d -> d.copy(productGroups = Aliases.unmerge(d.productGroups, key)) }

    /** "Not the same product": stops suggesting the pair. */
    fun notSameProduct(a: String, b: String) = update { d -> d.copy(notSame = (d.notSame + Aliases.pair(a, b)).distinct()) }

    /** [b]'s shop becomes part of [a]'s, which keeps [name] (what it was shown as) unless it already had one. */
    fun mergeShops(a: String, b: String, name: String) = update { d ->
        val merged = Aliases.merge(d.shopGroups, a, b)
        d.copy(shopGroups = if (Aliases.group(merged, a)?.name == null) Aliases.rename(merged, a, name) else merged)
    }

    fun renameShop(key: String, name: String?) = update { d -> d.copy(shopGroups = Aliases.rename(d.shopGroups, key, name)) }

    fun unmergeShop(key: String) = update { d -> d.copy(shopGroups = Aliases.unmerge(d.shopGroups, key)) }

    // ---------------------------------------------------------------- photos without a readable QR

    /** Keeps a picked photo whose QR couldn't be read (copied into app files) on the "Without QR" list; its id. */
    suspend fun keepNoQr(uri: android.net.Uri): String? {
        val name = withContext(Dispatchers.IO) { NoQrPhotos.save(getApplication(), uri) } ?: return null
        return adoptNoQr(name)
    }

    /** A photo already copied into filesDir/wallet_noqr (by "Hive Receipt") onto the "Without QR" list; its id. */
    fun adoptNoQr(file: String): String {
        val id = id("nqr")
        update { d -> d.copy(noQr = d.noQr + NoQrPhoto(id, file, now())) }
        return id
    }

    /** Off the list, and its file deleted (the receipt was saved, or the user threw it away). */
    fun removeNoQr(id: String) {
        val p = _data.value.noQr.firstOrNull { it.id == id } ?: return
        NoQrPhotos.delete(getApplication(), p.file)
        update { d -> d.copy(noQr = d.noQr.filter { it.id != id }) }
        if (pendingNoQr == id) pendingNoQr = null
    }

    /** The photo being checked right now (through Chrome, it comes back as a shared link); done when a receipt arrives. */
    var pendingNoQr: String? = null

    /** Where a photo's trip to the QR repair server stands. */
    sealed interface Repair {
        data object Running : Repair
        data class Found(val receiptId: String) : Repair
        data object NotFound : Repair
        data class Failed(val message: String) : Repair
    }

    private val _repairs = MutableStateFlow<Map<String, Repair>>(emptyMap())
    /** Per "Without QR" photo id; absent = never sent (no key, or not tried yet). */
    val repairs: StateFlow<Map<String, Repair>> = _repairs.asStateFlow()

    fun canRepair(): Boolean = QrRepairClient.key(getApplication()) != null

    /**
     * Sends a "Without QR" photo to the repair server (when the user has set its key). A link that comes back is
     * saved like a scan and the photo leaves the list. Each photo goes once unless [again].
     */
    fun repairNoQr(id: String, again: Boolean = false) {
        val p = _data.value.noQr.firstOrNull { it.id == id } ?: return
        val now = _repairs.value[id]
        if (!canRepair() || now == Repair.Running || (now != null && !again)) return
        _repairs.value = _repairs.value + (id to Repair.Running)
        viewModelScope.launch {
            val answer = withContext(Dispatchers.IO) { QrRepairClient.repair(getApplication(), NoQrPhotos.file(getApplication(), p.file)) }
            val state = when (answer) {
                is QrRepairClient.Answer.Link -> addReceipt(answer.url)?.let { (receiptId, _) ->
                    removeNoQr(id)
                    android.widget.Toast.makeText(getApplication(), "QR code repaired: receipt saved.", android.widget.Toast.LENGTH_SHORT).show()
                    Repair.Found(receiptId)
                } ?: Repair.NotFound
                QrRepairClient.Answer.NotFound -> Repair.NotFound
                is QrRepairClient.Answer.Failed -> Repair.Failed(answer.message)
            }
            _repairs.value = _repairs.value + (id to state)
        }
    }

    // ---------------------------------------------------------------- loyalty cards

    /** Adds or replaces a card; returns its id. */
    fun saveCard(c: LoyaltyCard): String {
        val x = if (c.id.isBlank()) c.copy(id = id("card"), addedAt = now()) else c
        _data.value.cards.firstOrNull { it.id == x.id }?.image?.takeIf { it != x.image }?.let { CardImages.delete(getApplication<Application>(), it) }
        update { d -> if (d.cards.any { it.id == x.id }) d.copy(cards = d.cards.map { if (it.id == x.id) x else it }) else d.copy(cards = d.cards + x) }
        return x.id
    }

    fun deleteCard(id: String) {
        _data.value.cards.firstOrNull { it.id == id }?.image?.let { CardImages.delete(getApplication<Application>(), it) }
        update { d -> d.copy(cards = d.cards.filter { it.id != id }) }
    }

    /** Copies a picked photo of a card's front into the app (scaled down); returns its file name, null on failure. */
    suspend fun saveCardImage(uri: android.net.Uri): String? = withContext(Dispatchers.IO) { CardImages.save(getApplication<Application>(), uri) }

    fun togglePin(id: String) = update { d -> d.copy(cards = d.cards.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }) }

    /** Opened at the till: moves the card up the list. */
    fun markUsed(id: String) = update { d -> d.copy(cards = d.cards.map { if (it.id == id) it.copy(lastUsed = now(), uses = it.uses + 1) else it }) }
}
