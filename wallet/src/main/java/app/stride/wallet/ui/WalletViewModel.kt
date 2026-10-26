package app.stride.wallet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stride.wallet.data.FiscalClient
import app.stride.wallet.data.WalletStore
import app.stride.wallet.domain.FiscalParse
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
        _data.value = f(_data.value)
        viewModelScope.launch(Dispatchers.IO) { saveLock.withLock { store.save(_data.value) } }
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
            update { d -> d.copy(receipts = d.receipts.map { if (it.id == id) next(it) else it }) }
            _loading.value = _loading.value - id
        }
    }

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

    /** Names a company by its PIB (an empty name forgets it). */
    fun nameCompany(pib: String, name: String) = update { d ->
        val rest = d.companies.filter { it.pib != pib }
        d.copy(companies = if (name.isBlank()) rest else rest + Company(pib, name.trim()))
    }

    fun addWarranty(receiptId: String, item: String, months: Int) {
        val w = Warranty(id("wty"), item.trim(), months)
        update { d -> d.copy(receipts = d.receipts.map { if (it.id == receiptId) it.copy(warranties = it.warranties + w) else it }) }
    }

    fun deleteWarranty(id: String, warrantyId: String) = update { d ->
        d.copy(receipts = d.receipts.map { if (it.id == id) it.copy(warranties = it.warranties.filter { w -> w.id != warrantyId }) else it })
    }

    fun setNote(id: String, note: String) = update { d -> d.copy(receipts = d.receipts.map { if (it.id == id) it.copy(note = note) else it }) }

    fun deleteReceipt(id: String) = update { d -> d.copy(receipts = d.receipts.filter { it.id != id }) }

    // ---------------------------------------------------------------- loyalty cards

    /** Adds or replaces a card; returns its id. */
    fun saveCard(c: LoyaltyCard): String {
        val x = if (c.id.isBlank()) c.copy(id = id("card"), addedAt = now()) else c
        update { d -> if (d.cards.any { it.id == x.id }) d.copy(cards = d.cards.map { if (it.id == x.id) x else it }) else d.copy(cards = d.cards + x) }
        return x.id
    }

    fun deleteCard(id: String) = update { d -> d.copy(cards = d.cards.filter { it.id != id }) }

    /** Opened at the till: moves the card up the list. */
    fun markUsed(id: String) = update { d -> d.copy(cards = d.cards.map { if (it.id == id) it.copy(lastUsed = now(), uses = it.uses + 1) else it }) }
}
