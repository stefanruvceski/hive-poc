package app.stride.wallet.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import app.stride.ui.theme.StrideColors
import app.stride.wallet.domain.ReceiptOcr
import app.stride.wallet.domain.VerifyFields
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/** The tax office's page for checking a receipt by its printed fields. */
private const val VERIFY_URL = "https://suf.purs.gov.rs/verify"

/**
 * A receipt whose QR code won't scan (smudged print, torn corner): photograph it, Hive reads the four printed fields
 * (total, PFR time, PFR number, counter), the user checks them, and the tax office's own "verify" page finds the
 * receipt. The page is shown, so a field Hive filled wrong can be fixed right there.
 */
@Composable
fun NoQrScreen(vm: WalletViewModel, onFound: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var total by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var counter by rememberSaveable { mutableStateOf("") }
    var reading by remember { mutableStateOf(false) }
    var web by rememberSaveable { mutableStateOf(false) }
    // Opened in Chrome: waiting for a screenshot of the QR code the tax office's page shows.
    var chrome by rememberSaveable { mutableStateOf(false) }
    val fields = VerifyFields(total.trim(), time.trim(), number.trim().uppercase(), counter.trim())

    fun read(uri: Uri) {
        reading = true
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
        if (image == null) { reading = false; Toast.makeText(context, "Couldn't open that picture", Toast.LENGTH_SHORT).show(); return }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { t ->
                val f = ReceiptOcr.read(t.text)
                if (f.total.isNotBlank()) total = f.total
                if (f.time.isNotBlank()) time = f.time
                if (f.number.isNotBlank()) number = f.number
                if (f.counter.isNotBlank()) counter = f.counter
                val found = listOf(f.total, f.time, f.number, f.counter).count { it.isNotBlank() }
                Toast.makeText(context, if (found == 4) "Read all four fields. Check them, then look it up." else "Read $found of 4 fields; type the rest from the receipt.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { Toast.makeText(context, it.message ?: "Couldn't read the picture", Toast.LENGTH_LONG).show() }
            .addOnCompleteListener { reading = false; recognizer.close() }
    }

    val photoFile = remember { File(File(context.cacheDir, "camera").apply { mkdirs() }, "receipt.jpg") }
    val photoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.updates", photoFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) read(photoUri) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) read(uri) }

    /** A screenshot of the result page in Chrome: its QR code is the receipt's link, saved like a scan. */
    val screenshot = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) Scanner.fromImage(context, uri, qrOnly = true, onResult = { found ->
            val link = found.firstOrNull { app.stride.wallet.domain.FiscalParse.verificationUrl(it.value) != null }
            val added = link?.let { vm.addReceipt(it.value) }
            if (added == null) Toast.makeText(context, "No receipt QR code in that screenshot. Scroll the tax office's page to the QR code and try again.", Toast.LENGTH_LONG).show()
            else { runCatching { photoFile.delete() }; onFound(added.first) }
        }, onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() })
    }
    val scope = rememberCoroutineScope()
    var copying by remember { mutableStateOf(false) }
    /**
     * The four values into the clipboard one after another, so the keyboard's clipboard history (Gboard) holds them
     * all: in Chrome, tap a box, then the value. Last field first, so the list reads top to bottom like the form.
     */
    fun copyAll(values: List<String>) {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        copying = true
        scope.launch {
            values.reversed().forEach { v ->
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Receipt field", v))
                delay(900)
            }
            copying = false
            Toast.makeText(context, "Copied. In Chrome, tap a box, then pick the value from the keyboard's clipboard.", Toast.LENGTH_LONG).show()
        }
    }

    fun openChrome() {
        val view = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(VERIFY_URL))
        runCatching { context.startActivity(android.content.Intent(view).setPackage("com.android.chrome")) }
            .recoverCatching { context.startActivity(view) }
            .onSuccess { chrome = true }
            .onFailure { Toast.makeText(context, "No browser to open the tax office's page", Toast.LENGTH_SHORT).show() }
    }

    if (web) {
        VerifyWebScreen(fields, onBack = { web = false }) { url, html ->
            val id = vm.addFromVerifyPage(url, html)
            if (id != null) {
                runCatching { photoFile.delete() }
                onFound(id)
                true
            } else false
        }
        return
    }

    WalletScreen("Receipt without QR", onBack) {
        item {
            Glass(tint = WalletAccent) {
                Text("QR code won't scan?", color = StrideColors.Text, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Every fiscal receipt prints four fields under the QR code. With them the tax office finds the receipt just like the QR does. Photograph the bottom of the receipt and Hive reads them, or type them in.",
                    color = StrideColors.Dim, fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { camera.launch(photoUri) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = WalletAccent)) {
                        Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Take a photo")
                    }
                    OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Image, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("From photos")
                    }
                }
                if (reading) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = WalletAccent)
                    Spacer(Modifier.size(8.dp))
                    Text("Reading the receipt…", color = StrideColors.Dim, fontSize = 13.sp)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(total, { total = it }, label = { Text("Total · Укупан износ") }, placeholder = { Text("3.001,50") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(time, { time = it }, label = { Text("PFR time · ПФР време") }, placeholder = { Text("19.10.2026. 08:56:24") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(number, { number = it }, label = { Text("PFR number · ПФР број рачуна") }, placeholder = { Text("FA73YEEA-FA73YEEA-102736") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(counter, { counter = it }, label = { Text("Counter · Бројач рачуна") }, placeholder = { Text("102736/103225ПП") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Watch for O and 0, G and 6, B and 8: the PFR number mixes letters and digits.", color = StrideColors.Faint, fontSize = 12.sp)
            }
        }
        // What to type on the tax office's form, the way its boxes take it (the type ПП sets itself).
        if (fields.complete) item {
            Glass(tint = WalletAccent) {
                Caps("Type these on the tax office's page")
                val values = listOf(
                    "ПФР број рачуна" to fields.number,
                    "Бројач рачуна" to ReceiptOcr.formCounter(fields.counter),
                    "Укупан износ" to (ReceiptOcr.formAmount(fields.total) ?: fields.total),
                    "ПФР време" to (ReceiptOcr.formTime(fields.time) ?: fields.time),
                )
                values.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(value, color = StrideColors.Text, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
                TextButton(onClick = { copyAll(values.map { it.second }) }, enabled = !copying, contentPadding = PaddingValues(0.dp)) {
                    Text(if (copying) "Copying…" else "Copy all four (for the keyboard's clipboard)", color = WalletAccent)
                }
            }
        }
        item {
            Button(onClick = ::openChrome, enabled = fields.complete, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = WalletAccent)) {
                Text("Open the tax office in Chrome")
            }
            TextButton(onClick = { web = true }, enabled = fields.complete, modifier = Modifier.fillMaxWidth()) {
                Text("Or try it inside Hive", color = StrideColors.Dim)
            }
        }
        if (chrome) item {
            Glass(tint = StrideColors.Accent) {
                Text("Back from Chrome?", color = StrideColors.Text, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Fill the form there and tap Пошаљи. When it says Рачун је проверен, scroll to the receipt's QR code, long-press it → Share image → Hive Receipt: it's saved. Or take a screenshot, come back and pick it.",
                    color = StrideColors.Dim, fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { screenshot.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = StrideColors.Accent),
                ) {
                    Icon(Icons.Filled.Image, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Pick the screenshot")
                }
            }
        }
    }
}

/**
 * The tax office's verify page in a WebView. The PFR number and the counter are set directly; the amount and the
 * time boxes are masked (a value set from code shows, then vanishes when the form is sent), so Hive types them with
 * real key presses, like a person would. The user checks the form and taps Пошаљи; when the page shows a receipt
 * (or its QR link), [onPage] saves it and returns true.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerifyWebScreen(fields: VerifyFields, onBack: () -> Unit, onPage: (url: String, html: String) -> Boolean) {
    var view by remember { mutableStateOf<WebView?>(null) }
    var done by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Hive fills the form…") }
    val scope = rememberCoroutineScope()
    val payload = remember(fields) {
        buildJsonObject {
            put("number", fields.number)
            put("counter", ReceiptOcr.formCounter(fields.counter))
        }.toString()
    }
    val amount = remember(fields) { ReceiptOcr.formAmount(fields.total) ?: fields.total }
    val time = remember(fields) { ReceiptOcr.formTime(fields.time) ?: fields.time }

    /** Focuses the box Hive tagged as [tag], clears it and types [text] key by key. False when there's no such box. */
    suspend fun type(v: WebView, tag: String, text: String): Boolean {
        val ok = v.js("(function(){var e=document.querySelector('[data-hive=$tag]');if(!e)return 'no';e.scrollIntoView({block:'center'});e.focus();return 'ok'})()")
        if (!ok.contains("ok")) return false
        v.requestFocus()
        delay(150)
        fun key(code: Int) {
            v.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            v.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        key(KeyEvent.KEYCODE_MOVE_END)
        repeat(40) { key(KeyEvent.KEYCODE_DEL) }
        repeat(40) { key(KeyEvent.KEYCODE_FORWARD_DEL) }
        val keys = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        for (c in text) {
            keys.getEvents(charArrayOf(c))?.forEach { v.dispatchKeyEvent(it) }
            delay(25)
        }
        delay(150)
        return true
    }

    fun check(v: WebView) {
        if (done) return
        v.evaluateJavascript(FILL_JS.replace("__FIELDS__", payload)) { n ->
            // "4": the form was just found and filled; type the two masked boxes once.
            if (n?.trim('"') == "4") scope.launch {
                val typed = type(v, "amount", amount) and type(v, "time", time)
                v.js("(function(){if(document.activeElement)document.activeElement.blur();return 1})()")
                status = if (typed) "Check the four fields, then tap Пошаљи" else "Type the amount and time, then tap Пошаљи"
            }
        }
        v.evaluateJavascript("(function(){return document.documentElement.outerHTML})()") { result ->
            val html = runCatching { Json.parseToJsonElement(result).jsonPrimitive.content }.getOrDefault("")
            if (!done && html.isNotEmpty() && onPage(v.url.orEmpty(), html)) done = true
        }
    }
    // Pages that change without a new load (the result shown in place) are caught by looking again every 1.5 s.
    LaunchedEffect(view) {
        val v = view ?: return@LaunchedEffect
        while (!done) { delay(1500); check(v) }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Column { Text("Tax office"); Text(status, fontSize = 12.sp, color = StrideColors.Dim) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { p ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(android.graphics.Color.WHITE)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(v: WebView, url: String?) { check(v) }
                    }
                    loadUrl(VERIFY_URL)
                    view = this
                }
            },
            modifier = Modifier.fillMaxSize().padding(p),
        )
    }
}

/**
 * Finds the verify form's four boxes by name, id, placeholder, aria-label or label text (the counter first, since
 * "Бројач" starts like "Број"), sets the PFR number and the counter, and tags the amount and time boxes
 * (data-hive) for typing. Returns 4 the first time it finds all four, 0 after that.
 */
private const val FILL_JS = """
(function(f){
  if (window.__hiveFilled) return 0;
  function own(el){
    var t=(el.name||'')+' '+(el.id||'')+' '+(el.placeholder||'')+' '+(el.getAttribute('aria-label')||'');
    if (el.id){ var l=document.querySelector('label[for="'+el.id+'"]'); if (l) t+=' '+l.textContent; }
    var wrap=el.closest('label'); if (wrap) t+=' '+wrap.textContent;
    return t.toLowerCase();
  }
  function near(el){ var p=el.parentElement; return p ? (p.textContent||'').slice(0,60).toLowerCase() : ''; }
  function set(el,v){
    var d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el),'value');
    if (d && d.set) d.set.call(el,v); else el.value=v;
    ['input','change','blur'].forEach(function(e){ el.dispatchEvent(new Event(e,{bubbles:true})); });
  }
  var ins=[].slice.call(document.querySelectorAll('input')).filter(function(i){ return !/hidden|submit|button|checkbox|radio/.test(i.type); });
  if (ins.length < 4) return 0;
  var used=[];
  function pick(re){
    for (var pass=0; pass<2; pass++) for (var i=0;i<ins.length;i++){
      if (used.indexOf(i)>=0) continue;
      if (re.test(pass==0 ? own(ins[i]) : near(ins[i]))){ used.push(i); return ins[i]; }
    }
    return null;
  }
  var n=0;
  var c=pick(/бројач|brojač|brojac|counter/); if (c){ set(c,f.counter); n++; }
  var t=pick(/време|vreme|time|datum|date/); if (t){ t.setAttribute('data-hive','time'); n++; }
  var a=pick(/износ|iznos|amount|total|укупн|ukupn/); if (a){ a.setAttribute('data-hive','amount'); n++; }
  var b=pick(/број|broj|number|invoice|пфр|pfr/); if (b){ set(b,f.number); n++; }
  if (n==4) window.__hiveFilled=true;
  return n;
})(__FIELDS__)
"""

/** Runs [script] and waits for its result (as JSON text). */
private suspend fun WebView.js(script: String): String = suspendCancellableCoroutine { c ->
    evaluateJavascript(script) { if (c.isActive) c.resume(it.orEmpty()) }
}
