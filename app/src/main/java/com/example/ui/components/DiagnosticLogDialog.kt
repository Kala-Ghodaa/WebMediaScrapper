package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.logging.DiagnosticLogEntry
import com.example.data.logging.DiagnosticLogManager
import com.example.data.logging.LogCategory
import com.example.data.logging.LogSeverity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassBorderLight
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VividViolet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticLogDialog(
    initialFilterUrl: String? = null,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val allLogs by DiagnosticLogManager.logsFlow.collectAsState()

    var searchQuery by remember { mutableStateOf(initialFilterUrl ?: "") }
    var selectedCategory by remember { mutableStateOf<LogCategory?>(null) }
    var errorsOnly by remember { mutableStateOf(false) }

    val filteredLogs = remember(allLogs, searchQuery, selectedCategory, errorsOnly) {
        allLogs.filter { entry ->
            val matchesCategory = selectedCategory == null || entry.category == selectedCategory
            val matchesError = !errorsOnly || entry.severity == LogSeverity.ERROR
            val matchesSearch = searchQuery.isBlank() ||
                entry.message.contains(searchQuery, ignoreCase = true) ||
                (entry.url?.contains(searchQuery, ignoreCase = true) == true) ||
                (entry.stackTrace?.contains(searchQuery, ignoreCase = true) == true) ||
                entry.tag.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesError && matchesSearch
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, GlassBorderLight, RoundedCornerShape(24.dp)),
            color = MidnightBg.copy(alpha = 0.96f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Diagnostic Logs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${filteredLogs.size} of ${allLogs.size} entries · Real-time network & scraper capture",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                DiagnosticLogManager.shareLogs(context)
                            },
                            modifier = Modifier.testTag("share_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Logs",
                                tint = ElectricCyan
                            )
                        }

                        IconButton(
                            onClick = {
                                val fullLogText = DiagnosticLogManager.getAllLogsFormatted()
                                clipboardManager.setText(AnnotatedString(fullLogText))
                                Toast.makeText(context, "All logs copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("copy_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy All Logs",
                                tint = VividViolet
                            )
                        }

                        IconButton(
                            onClick = {
                                DiagnosticLogManager.clear()
                                Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("clear_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Logs",
                                tint = TextMuted
                            )
                        }

                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.testTag("close_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("logs_search_input"),
                    placeholder = { Text("Filter by URL, domain, header, or error…", color = TextMuted) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextMuted
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = GlassBorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = GlassSurfaceElevated,
                        unfocusedContainerColor = GlassSurfaceElevated
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Chips Row
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null && !errorsOnly,
                            onClick = {
                                selectedCategory = null
                                errorsOnly = false
                            },
                            label = { Text("All (${allLogs.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.25f),
                                selectedLabelColor = ElectricCyan
                            )
                        )
                    }
                    item {
                        val errorCount = remember(allLogs) {
                            allLogs.count { it.severity == LogSeverity.ERROR }
                        }
                        FilterChip(
                            selected = errorsOnly,
                            onClick = { errorsOnly = !errorsOnly },
                            label = { Text("Errors ($errorCount)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CoralError.copy(alpha = 0.25f),
                                selectedLabelColor = CoralError
                            )
                        )
                    }
                    items(LogCategory.values()) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = if (selectedCategory == cat) null else cat
                            },
                            label = { Text(cat.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VividViolet.copy(alpha = 0.25f),
                                selectedLabelColor = VividViolet
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Log Items List
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (allLogs.isEmpty()) "No diagnostic logs captured yet.\nStart scraping or downloading to see live network telemetry."
                            else "No logs match the current filters.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .testTag("diagnostic_logs_list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { logEntry ->
                            DiagnosticLogItemCard(logEntry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticLogItemCard(entry: DiagnosticLogEntry) {
    var expanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val categoryColor = when (entry.category) {
        LogCategory.HTTP_NETWORK -> ElectricCyan
        LogCategory.SCRAPER -> VividViolet
        LogCategory.RECOVERY -> AmberWarning
        LogCategory.ALBUM_QUEUE -> EmeraldSuccess
        LogCategory.GENERAL -> TextSecondary
    }

    val levelBgColor = when (entry.severity) {
        LogSeverity.ERROR -> CoralError.copy(alpha = 0.15f)
        LogSeverity.WARN -> AmberWarning.copy(alpha = 0.12f)
        LogSeverity.INFO -> GlassSurfaceElevated
    }

    val levelBorderColor = when (entry.severity) {
        LogSeverity.ERROR -> CoralError.copy(alpha = 0.6f)
        LogSeverity.WARN -> AmberWarning.copy(alpha = 0.5f)
        LogSeverity.INFO -> GlassBorderSubtle
    }

    val timeString = remember(entry.timestamp) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, levelBorderColor, RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
        color = levelBgColor
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header Row: Time, Category Badge, Status Code, Expand Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = timeString,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextMuted
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(categoryColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = entry.category.name,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = categoryColor
                        )
                    }

                    if (entry.responseCode != null) {
                        val statusColor = if (entry.responseCode in 200..299) EmeraldSuccess else CoralError
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusColor.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "HTTP ${entry.responseCode}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Message
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.severity == LogSeverity.ERROR) CoralError else TextPrimary,
                fontWeight = if (entry.severity == LogSeverity.ERROR) FontWeight.SemiBold else FontWeight.Normal
            )

            // URL if present
            if (!entry.url.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.url,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = ElectricCyan.copy(alpha = 0.85f),
                    maxLines = if (expanded) 10 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Expanded Details (Headers, Stack trace)
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))

                if (!entry.requestHeaders.isNullOrEmpty()) {
                    Text(
                        text = "Request Headers:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    val reqHeaderText = entry.requestHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                    Text(
                        text = reqHeaderText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TextMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MidnightBg, RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (!entry.responseHeaders.isNullOrEmpty()) {
                    Text(
                        text = "Response Headers:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    val resHeaderText = entry.responseHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                    Text(
                        text = resHeaderText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = TextMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MidnightBg, RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (!entry.stackTrace.isNullOrBlank()) {
                    Text(
                        text = "Error Stack Trace:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CoralError
                    )
                    Text(
                        text = entry.stackTrace,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = CoralError.copy(alpha = 0.9f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CoralError.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Copy single entry button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Copy Entry",
                        fontSize = 10.sp,
                        color = VividViolet,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                val singleLog = "[${timeString}] [${entry.category}] ${entry.message}\nURL: ${entry.url.orEmpty()}\n${entry.stackTrace.orEmpty()}"
                                clipboardManager.setText(AnnotatedString(singleLog))
                                Toast.makeText(context, "Copied log entry", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
