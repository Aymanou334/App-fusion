package com.appfusion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.appfusion.data.HistoryStore
import com.appfusion.data.LocalPdfProcessor
import com.appfusion.domain.Operation
import com.appfusion.domain.WorkflowDefinition
import com.appfusion.domain.WorkflowEngine
import com.appfusion.domain.WorkflowStep
import com.appfusion.domain.WorkflowInput
import com.appfusion.domain.RunStatus
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private lateinit var history: HistoryStore
    private val historyItems = mutableStateListOf<String>()
    private var pendingImport by mutableStateOf<Uri?>(null)
    private var inputText by mutableStateOf("")
    private var outputText by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Your files stay on this device.")
    private var executionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        history = HistoryStore(this)
        historyItems.addAll(history.list())
        consumeIntent(intent)
        setContent { AppFusionScreen() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (intent.type == "application/pdf") intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri else null
                if (uri != null) pendingImport = uri
                else intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { inputText = it.toString() }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                pendingImport = uris?.firstOrNull()
                if ((uris?.size ?: 0) > 1) statusMessage = "Using the first PDF. Multiple-file workflows are not available yet."
            }
        }
    }

    @Composable
    private fun AppFusionScreen() {
        val context = LocalContext.current
        val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) { pendingImport = uri; statusMessage = "PDF selected. Choose a processing action." }
        }
        val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) saveResult(uri, context)
        }

        MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF8BC6B4)) else lightColorScheme(primary = Color(0xFF176B58))) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        Text("APP FUSION", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("Turn a file into\nsomething useful.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Build a simple, private workflow. Processing happens on your device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Create workflow", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text("Import a PDF or paste text, then clean it up or make a short local summary.", style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) { Text("Choose PDF") }
                                    OutlinedButton(onClick = { pasteText(context) }) { Text("Paste text") }
                                }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it; pendingImport = null },
                            modifier = Modifier.fillMaxWidth(), minLines = 4,
                            label = { Text(if (pendingImport == null) "Text to process" else "Text extracted from PDF") },
                            placeholder = { Text("Paste text here or share a PDF with App Fusion") }
                        )
                    }
                    if (pendingImport != null) item {
                        Card { Column(Modifier.padding(14.dp)) {
                            Text("PDF ready", fontWeight = FontWeight.SemiBold)
                            Text(pendingImport?.lastPathSegment ?: "Shared PDF", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { pendingImport = null }) { Text("Remove file") }
                        } }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (busy) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                OutlinedButton(onClick = { executionJob?.cancel() }) { Text("Cancel") }
                            } else {
                                Button(enabled = inputText.isNotBlank() || pendingImport != null, onClick = { runWorkflow(false) }) { Text("Clean text") }
                                OutlinedButton(enabled = inputText.isNotBlank() || pendingImport != null, onClick = { runWorkflow(true) }) { Text("Short summary") }
                            }
                        }
                    }
                    item { Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (outputText.isNotBlank()) item {
                        Card(shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Your result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(outputText, style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { savePicker.launch("app-fusion-result.txt") }) { Text("Save result") }
                                    OutlinedButton(onClick = { copyResult(context) }) { Text("Copy") }
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Recent activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (historyItems.isNotEmpty()) TextButton(onClick = { history.clear(); historyItems.clear(); Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show() }) { Text("Clear") }
                        }
                    }
                    if (historyItems.isEmpty()) item {
                        Card { Text("No workflows yet. Your completed runs will appear here.", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else items(historyItems) { line -> Card { Text(line, Modifier.fillMaxWidth().padding(16.dp)) } }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    private fun runWorkflow(summary: Boolean) {
        executionJob?.cancel()
        busy = true
        statusMessage = if (pendingImport != null) "Reading PDF locally…" else "Processing on this device…"
        val uri = pendingImport
        val rawText = inputText
        executionJob = lifecycleScope.launch {
            try {
                val steps = buildList {
                    if (uri != null) add(WorkflowStep(Operation.EXTRACT_PDF_TEXT)) else add(WorkflowStep(Operation.ACCEPT_TEXT))
                    add(WorkflowStep(Operation.CLEAN_TEXT))
                    if (summary) add(WorkflowStep(Operation.SUMMARIZE_TEXT))
                    add(WorkflowStep(Operation.DISPLAY_RESULT))
                }
                val workflow = WorkflowDefinition("mvp", if (summary) "Clean and summarize" else "Clean text", steps)
                val adapter = LocalPdfProcessor(contentResolver, { PDFBoxResourceLoader.init(applicationContext) })
                val source = if (uri != null) WorkflowInput.PdfUri(uri.toString()) else WorkflowInput.Text(rawText)
                val run = WorkflowEngine(adapter).execute(workflow, source)
                if (run.status == RunStatus.SUCCEEDED) {
                    outputText = run.results.last().value as String
                    val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
                    val historyLabel = "${workflow.name} · $time"
                    history.add(historyLabel)
                    historyItems.add(0, historyLabel)
                    while (historyItems.size > 8) historyItems.removeAt(historyItems.lastIndex)
                    statusMessage = "Done. No data was sent to an external service."
                } else statusMessage = run.error ?: "The workflow could not be completed."
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                statusMessage = "Processing cancelled."
                throw cancelled
            } catch (error: Exception) {
                statusMessage = error.message ?: "Something went wrong while processing this input."
            } finally { busy = false }
        }
    }

    private fun saveResult(uri: Uri, context: Context) {
        runCatching { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(outputText) } ?: error("Could not open the selected destination.") }
            .onSuccess { Toast.makeText(context, "Result saved", Toast.LENGTH_SHORT).show() }
            .onFailure { statusMessage = "Could not save the result: ${it.message ?: "storage error"}" }
    }

    private fun pasteText(context: Context) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val pasted = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        if (!pasted.isNullOrBlank()) { inputText = pasted; pendingImport = null }
        else Toast.makeText(context, "There is no text to paste", Toast.LENGTH_SHORT).show()
    }

    private fun copyResult(context: Context) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("App Fusion result", outputText))
        Toast.makeText(context, "Result copied", Toast.LENGTH_SHORT).show()
    }
}
