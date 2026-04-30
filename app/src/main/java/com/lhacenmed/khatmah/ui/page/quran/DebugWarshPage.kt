package com.lhacenmed.khatmah.ui.page.quran

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import java.io.File

/**
 * Debug page — renders a Warsh XML vector from internal storage via Canvas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugWarshPage() {
    val context = LocalContext.current
    val nav     = LocalNavController.current

    // Parse once; null = file missing or parse error
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val vector = remember {
        val file = File(context.filesDir, "warsh/xml/001.xml")
        if (!file.exists()) {
            errorMsg = "File not found: ${file.absolutePath}"
            return@remember null
        }
        runCatching {
            file.inputStream().use { VectorXmlParser.parse(it) }
        }.onFailure {
            errorMsg = "Parse error: ${it.localizedMessage}"
        }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Warsh 001") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier         = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (vector != null) {
                VectorFileCanvas(
                    vector   = vector,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text     = errorMsg ?: "Unknown error",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}