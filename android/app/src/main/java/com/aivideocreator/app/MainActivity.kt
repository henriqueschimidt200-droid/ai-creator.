package com.aivideocreator.app

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64

class MainActivity : ComponentActivity() {
    private var pickedImage by mutableStateOf<Uri?>(null)
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickedImage = uri
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var serverUrl by remember { mutableStateOf("http://10.0.2.2:8000") }
            var prompt by remember { mutableStateOf("") }
            var negativePrompt by remember { mutableStateOf("") }
            var duration by remember { mutableIntStateOf(5) }
            var mode by remember { mutableStateOf("Texto para vídeo") }
            var busy by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf("Pronto para criar.") }
            var videoFile by remember { mutableStateOf<File?>(null) }
            var showDuration by remember { mutableStateOf(false) }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF9C7BFF),
                    secondary = Color(0xFF41D9C3),
                    background = Color(0xFF0B0D18),
                    surface = Color(0xFF171A2B)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B0D18)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color(0xFF20204A), Color(0xFF0B0D18))))
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("AI VIDEO CREATOR", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Text("Transforme suas ideias em vídeos com IA", color = Color(0xFFBFC3E8))
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("URL do servidor") },
                            supportingText = { Text("Ex.: http://10.0.2.2:8000 no emulador") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = mode == "Texto para vídeo",
                                onClick = { mode = "Texto para vídeo" },
                                label = { Text("Texto → vídeo") }
                            )
                            FilterChip(
                                selected = mode == "Imagem para vídeo",
                                onClick = { mode = "Imagem para vídeo" },
                                label = { Text("Imagem → vídeo") }
                            )
                        }
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            label = { Text("Descreva o vídeo") },
                            placeholder = { Text("Ex.: um gato correndo numa cidade futurista, estilo cinematográfico") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = negativePrompt,
                            onValueChange = { negativePrompt = it },
                            label = { Text("O que evitar (opcional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (mode == "Imagem para vídeo") {
                            OutlinedButton(
                                onClick = { imagePicker.launch("image/*") },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (pickedImage == null) "Escolher imagem" else "Imagem selecionada ✓") }
                        }
                        Text("Duração desejada", color = Color.White)
                        Box {
                            OutlinedButton(onClick = { showDuration = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("$duration segundos  ▾")
                            }
                            DropdownMenu(expanded = showDuration, onDismissRequest = { showDuration = false }) {
                                (5..60 step 5).forEach { seconds ->
                                    DropdownMenuItem(
                                        text = { Text("$seconds segundos") },
                                        onClick = { duration = seconds; showDuration = false }
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = {
                                if (prompt.trim().length < 3) {
                                    status = "Escreva uma descrição com pelo menos 3 caracteres."
                                    return@Button
                                }
                                if (mode == "Imagem para vídeo" && pickedImage == null) {
                                    status = "Escolha uma imagem primeiro."
                                    return@Button
                                }
                                busy = true
                                status = "Enviando pedido para o servidor..."
                                scope.launch {
                                    try {
                                        val bytes = withContext(Dispatchers.IO) {
                                            val json = JSONObject()
                                            json.put("prompt", prompt.trim())
                                            json.put("negative_prompt", negativePrompt.trim().ifBlank { JSONObject.NULL })
                                            json.put("duration_seconds", duration)
                                            if (mode == "Imagem para vídeo") {
                                                val input = context.contentResolver.openInputStream(pickedImage!!)
                                                    ?: throw Exception("Não foi possível abrir a imagem.")
                                                val imageBytes = input.use { stream ->
                                                    val out = ByteArrayOutputStream()
                                                    stream.copyTo(out)
                                                    out.toByteArray()
                                                }
                                                if (imageBytes.size > 10 * 1024 * 1024) {
                                                    throw Exception("A imagem deve ter no máximo 10 MB.")
                                                }
                                                json.put("image_base64", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
                                            }
                                            val base = serverUrl.trim().trimEnd('/')
                                            if (!base.startsWith("http://") && !base.startsWith("https://")) {
                                                throw Exception("A URL deve começar com http:// ou https://")
                                            }
                                            val endpoint = if (mode == "Texto para vídeo") "/generate/text" else "/generate/image"
                                            val conn = (URL(base + endpoint).openConnection() as HttpURLConnection)
                                            conn.requestMethod = "POST"
                                            conn.connectTimeout = 30000
                                            conn.readTimeout = 600000
                                            conn.doOutput = true
                                            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
                                            val code = conn.responseCode
                                            if (code !in 200..299) {
                                                val err = (conn.errorStream?.bufferedReader()?.use { it.readText() }).orEmpty()
                                                conn.disconnect()
                                                throw Exception("Servidor HTTP $code: ${err.take(500)}")
                                            }
                                            val output = ByteArrayOutputStream()
                                            conn.inputStream.use { it.copyTo(output) }
                                            conn.disconnect()
                                            output.toByteArray()
                                        }
                                        val outFile = withContext(Dispatchers.IO) {
                                            File(context.cacheDir, "ai-video-${System.currentTimeMillis()}.mp4").apply {
                                                writeBytes(bytes)
                                            }
                                        }
                                        videoFile = outFile
                                        status = "Vídeo recebido (${(bytes.size / 1024).coerceAtLeast(1)} KB). Toque em abrir vídeo."
                                    } catch (e: Exception) {
                                        status = "Erro: ${e.message ?: "não foi possível gerar o vídeo"}"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (busy) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Gerando...")
                            } else Text("Gerar vídeo")
                        }
                        videoFile?.let { file ->
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            val values = ContentValues().apply {
                                                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                                                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AI Video Creator")
                                            }
                                            val dest = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                                                ?: throw Exception("Não foi possível salvar na galeria.")
                                            context.contentResolver.openOutputStream(dest)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                                            dest
                                        } else {
                                            Uri.fromFile(file)
                                        }
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "video/mp4")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        status = "Não foi possível abrir/salvar o vídeo: ${e.message}"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Salvar e abrir vídeo") }
                        }
                        Text(status, color = if (status.startsWith("Erro")) Color(0xFFFF9B9B) else Color(0xFFB8F7E8))
                        Text(
                            "Nota: a duração real depende do modelo/provedor. Pedidos longos podem ser recusados ou resultar em clipes menores.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFBFC3E8)
                        )
                    }
                }
            }
        }
    }
}
