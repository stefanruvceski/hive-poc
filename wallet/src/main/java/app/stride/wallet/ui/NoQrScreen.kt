package app.stride.wallet.ui

import android.annotation.SuppressLint
import android.net.Uri
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
        item {
            Button(onClick = { web = true }, enabled = fields.complete, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = WalletAccent)) {
                Text("Look it up at the tax office")
            }
        }
    }
}

/**
 * The tax office's verify page in a WebView: Hive fills the four fields (matched by their labels, so a changed
 * page still mostly works) and presses "Провери"; when the page shows a receipt (or its QR link), [onPage] saves it
 * and returns true. Until then the user sees the page and can fix or submit it by hand.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerifyWebScreen(fields: VerifyFields, onBack: () -> Unit, onPage: (url: String, html: String) -> Boolean) {
    var view by remember { mutableStateOf<WebView?>(null) }
    var done by remember { mutableStateOf(false) }
    val payload = remember(fields) {
        buildJsonObject {
            put("total", ReceiptOcr.formAmount(fields.total) ?: fields.total)
            put("plain", ReceiptOcr.plainAmount(fields.total) ?: fields.total)
            put("time", fields.time)
            put("iso", ReceiptOcr.isoTime(fields.time) ?: fields.time)
            put("number", fields.number)
            put("counter", ReceiptOcr.formCounter(fields.counter))
        }.toString()
    }
    fun check(v: WebView) {
        if (done) return
        v.evaluateJavascript(FILL_JS.replace("__FIELDS__", payload), null)
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
                title = { Column { Text("Tax office"); Text("Hive fills the form; fix a field here if needed", fontSize = 12.sp, color = StrideColors.Dim) } },
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
 * Fills the verify form: each input is matched by its name, id, placeholder, aria-label or label text (the counter
 * first, since "Бројач" starts like "Број"), React-style setters and input/change events so the page notices, then
 * the check button is pressed once all four are in.
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
  var t=pick(/време|vreme|time|datum|date/); if (t){ set(t, t.type=='datetime-local' ? f.iso : f.time); n++; }
  var a=pick(/износ|iznos|amount|total|укупн|ukupn/); if (a){ set(a, a.type=='number' ? f.plain : f.total); n++; }
  var b=pick(/број|broj|number|invoice|пфр|pfr/); if (b){ set(b,f.number); n++; }
  if (n==4){
    window.__hiveFilled=true;
    var btn=[].slice.call(document.querySelectorAll('button,input[type=submit]')).filter(function(x){ return /пошаљ|posalj|pošalj|провер|prover|verif|потврд|potvrd|send|submit/i.test(x.textContent||x.value||''); })[0]
      || document.querySelector('button[type=submit],input[type=submit]');
    if (btn) setTimeout(function(){ btn.click(); }, 300);
  }
  return n;
})(__FIELDS__)
"""
