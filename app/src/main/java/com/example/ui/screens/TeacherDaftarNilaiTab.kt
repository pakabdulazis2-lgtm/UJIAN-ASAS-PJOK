package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ExamResult
import com.example.data.SchoolConfig
import com.example.ui.theme.StatusAnsweredGreen
import com.example.ui.theme.StatusAnsweredGreenContainer
import com.example.ui.theme.StatusUnansweredRed
import com.example.ui.theme.StatusUnansweredRedContainer
import com.example.util.ExportFormat
import com.example.util.ExportManager
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun TeacherDaftarNilaiTab(
    config: SchoolConfig,
    results: List<ExamResult>,
    onDeleteResult: (String) -> Unit
) {
    val context = LocalContext.current
    var gradeFilter by remember { mutableStateOf<Int?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedResult by remember { mutableStateOf<ExamResult?>(null) }
    var showExportMenu by remember { mutableStateOf(false) }

    val filtered = remember(results, gradeFilter, searchQuery) {
        results.filter { r ->
            (gradeFilter == null || r.grade.toInt() == gradeFilter) &&
                (searchQuery.isBlank() || r.studentName.contains(searchQuery, ignoreCase = true))
        }
    }

    val avgScore = remember(filtered) {
        if (filtered.isEmpty()) 0 else filtered.map { it.score }.average().toInt()
    }
    val cheatedTotal = remember(filtered) {
        filtered.count { it.cheatCount > 0L }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Class Filter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = gradeFilter == null,
                onClick = { gradeFilter = null },
                label = { Text("Semua (${results.size})") }
            )
            (1..6).forEach { g ->
                val count = results.count { it.grade.toInt() == g }
                FilterChip(
                    selected = gradeFilter == g,
                    onClick = { gradeFilter = g },
                    label = { Text("Kelas $g ($count)") }
                )
            }
        }

        // Search + Export/Print Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari nama siswa...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Box {
                Button(
                    onClick = { showExportMenu = true },
                    modifier = Modifier.testTag("btn_export_menu"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cetak")
                }
                DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Cetak Langsung (PDF)") },
                        leadingIcon = { Icon(Icons.Filled.Print, contentDescription = null) },
                        onClick = {
                            showExportMenu = false
                            ExportManager.printPdfDirect(context, config, filtered, gradeFilter)
                                .onFailure { Toast.makeText(context, it.localizedMessage, Toast.LENGTH_LONG).show() }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ekspor PDF (.pdf)") },
                        leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                        onClick = {
                            showExportMenu = false
                            ExportManager.exportAndShare(context, ExportFormat.PDF, config, filtered, gradeFilter)
                                .onFailure { Toast.makeText(context, it.localizedMessage, Toast.LENGTH_LONG).show() }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ekspor Word (.doc)") },
                        leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                        onClick = {
                            showExportMenu = false
                            ExportManager.exportAndShare(context, ExportFormat.WORD, config, filtered, gradeFilter)
                                .onFailure { Toast.makeText(context, it.localizedMessage, Toast.LENGTH_LONG).show() }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ekspor Excel (.xls)") },
                        leadingIcon = { Icon(Icons.Filled.TableChart, contentDescription = null) },
                        onClick = {
                            showExportMenu = false
                            ExportManager.exportAndShare(context, ExportFormat.EXCEL, config, filtered, gradeFilter)
                                .onFailure { Toast.makeText(context, it.localizedMessage, Toast.LENGTH_LONG).show() }
                        }
                    )
                }
            }
        }

        // Summary Stats Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryMiniCard(
                label = "Peserta",
                value = "${filtered.size}",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            SummaryMiniCard(
                label = "Rata-rata",
                value = "$avgScore",
                containerColor = StatusAnsweredGreenContainer,
                contentColor = StatusAnsweredGreen,
                modifier = Modifier.weight(1f)
            )
            SummaryMiniCard(
                label = "Curang",
                value = "$cheatedTotal",
                containerColor = if (cheatedTotal > 0) StatusUnansweredRedContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (cheatedTotal > 0) StatusUnansweredRed else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Assignment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp)
                    )
                    Text("Belum ada hasil ujian siswa", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Setelah siswa mengerjakan dan mengunci ujian di Mode Siswa, nilai dan detail jawaban otomatis muncul di sini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id.ifBlank { "${it.studentId}_${it.createdAt?.seconds}" } }) { result ->
                    ResultSummaryCard(
                        result = result,
                        onViewDetail = { selectedResult = result },
                        onDelete = { onDeleteResult(result.id) }
                    )
                }
            }
        }
    }

    if (selectedResult != null) {
        ResultDetailDialog(
            result = selectedResult!!,
            onDismiss = { selectedResult = null }
        )
    }
}

@Composable
private fun SummaryMiniCard(
    label: String,
    value: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = contentColor, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor)
        }
    }
}

@Composable
private fun ResultSummaryCard(
    result: ExamResult,
    onViewDetail: () -> Unit,
    onDelete: () -> Unit
) {
    val hasCheated = result.cheatCount > 0L
    val dateStr = remember(result.createdAt) {
        result.createdAt?.toDate()?.let {
            SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(it)
        } ?: "Baru saja"
    }

    Card(
        onClick = onViewDetail,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (hasCheated) BorderStroke(1.5.dp, StatusUnansweredRed) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (result.score >= 70) StatusAnsweredGreenContainer else StatusUnansweredRedContainer,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${result.score}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (result.score >= 70) StatusAnsweredGreen else StatusUnansweredRed
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(result.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Kelas ${result.grade} • $dateStr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Benar: ${result.correctCount}  •  Salah: ${result.wrongCount}  •  Kosong: ${result.unansweredCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Hapus nilai", tint = MaterialTheme.colorScheme.error)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (hasCheated) StatusUnansweredRedContainer else StatusAnsweredGreenContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasCheated) Icons.Filled.Warning else Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = if (hasCheated) StatusUnansweredRed else StatusAnsweredGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (hasCheated) "Terindikasi Curang (${result.cheatCount}x)" else "Jujur / Aman",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasCheated) StatusUnansweredRed else StatusAnsweredGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (result.autoLocked) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Kunci Otomatis",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Lihat Detail →",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ResultDetailDialog(
    result: ExamResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(result.studentName, fontWeight = FontWeight.Bold)
                Text(
                    "Kelas ${result.grade} • Nilai: ${result.score} (${result.correctCount} Benar, ${result.wrongCount} Salah)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (result.cheatCount > 0L) {
                    Surface(
                        color = StatusUnansweredRedContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Deteksi Kecurangan (${result.cheatCount} pelanggaran):",
                                style = MaterialTheme.typography.labelLarge,
                                color = StatusUnansweredRed,
                                fontWeight = FontWeight.Bold
                            )
                            result.cheatLogs.forEach { logLine ->
                                Text(
                                    text = "• $logLine",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = StatusUnansweredRed
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        color = StatusAnsweredGreenContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StatusAnsweredGreen)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Siswa tidak terdeteksi keluar aplikasi selama ujian.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StatusAnsweredGreen
                            )
                        }
                    }
                }

                Text(
                    text = "Detail Jawaban Siswa (${result.answerDetails.size} Soal):",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                result.answerDetails.forEachIndexed { idx, map ->
                    val origNo = (map["originalNumber"] as? Number)?.toInt() ?: (idx + 1)
                    val qText = map["questionText"] as? String ?: ""
                    val selected = (map["selectedAnswer"] as? String).orEmpty().ifBlank { "-" }
                    val correctAns = map["correctAnswer"] as? String ?: ""
                    val isRight = map["correct"] as? Boolean ?: false

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isRight) StatusAnsweredGreenContainer.copy(alpha = 0.55f) else StatusUnansweredRedContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${idx + 1}. (Soal #$origNo) $qText",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Jawaban Siswa: $selected  |  Kunci Guru: $correctAns",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isRight) StatusAnsweredGreen else StatusUnansweredRed
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}
