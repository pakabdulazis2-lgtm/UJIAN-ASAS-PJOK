package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.ActiveExamSession
import com.example.data.ExamResult
import com.example.ui.theme.StatusAnsweredGreen
import com.example.ui.theme.StatusAnsweredGreenContainer
import com.example.ui.theme.StatusUnansweredRed
import com.example.ui.theme.StatusUnansweredRedContainer
import com.example.ui.theme.StatusUnsureGray
import com.example.ui.theme.StatusUnsureGrayContainer
import com.example.util.SmartImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentExamScreen(
    session: ActiveExamSession,
    onSelectAnswer: (questionId: String, option: String) -> Unit,
    onToggleUnsure: (questionId: String) -> Unit,
    onJumpToQuestion: (Int) -> Unit,
    onRecordCheat: (String) -> Unit,
    onDismissCheatWarning: () -> Unit,
    onLockExam: () -> Unit,
    onExitFinished: () -> Unit
) {
    // Prevent accidental back press during active exam; prompt to lock/submit instead
    var showLockConfirmDialog by remember { mutableStateOf(false) }
    var showNumberGridDialog by remember { mutableStateOf(false) }

    val submitted = session.submittedResult
    if (submitted != null) {
        BackHandler(onBack = onExitFinished)
        StudentFinishView(
            result = submitted,
            onDone = onExitFinished
        )
        return
    }

    BackHandler { showLockConfirmDialog = true }

    // Anti-cheat: Detect app backgrounding / switching to Google / AI / other apps
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                onRecordCheat("keluar dari halaman ujian (membuka browser/aplikasi lain)")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentQuestion = session.questions.getOrNull(session.currentIndex)
    val answeredCount = session.answers.size
    val unsureCount = session.unsureQuestionIds.size
    val unansweredCount = (session.questions.size - answeredCount).coerceAtLeast(0)
    val isUrgentTime = session.remainingSeconds <= 300L

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = session.examTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${session.student.fullName} • Kelas ${session.student.grade}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    Surface(
                        color = if (isUrgentTime) StatusUnansweredRed else MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = "Sisa waktu",
                                tint = if (isUrgentTime) Color.White else MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = formatCountdown(session.remainingSeconds),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isUrgentTime) Color.White else MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }
                    IconButton(onClick = { showNumberGridDialog = true }) {
                        Icon(
                            Icons.Filled.GridView,
                            contentDescription = "Semua nomor soal",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onJumpToQuestion(session.currentIndex - 1) },
                        enabled = session.currentIndex > 0,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Sebelumnya")
                    }

                    if (currentQuestion != null) {
                        val isUnsure = currentQuestion.id in session.unsureQuestionIds
                        OutlinedButton(
                            onClick = { onToggleUnsure(currentQuestion.id) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isUnsure) StatusUnsureGrayContainer else Color.Transparent,
                                contentColor = if (isUnsure) StatusUnsureGray else MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.5.dp, StatusUnsureGray),
                            modifier = Modifier.height(48.dp).testTag("btn_ragu_ragu")
                        ) {
                            Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isUnsure) "Ragu ✓" else "Ragu-ragu")
                        }
                    }

                    if (session.currentIndex < session.questions.lastIndex) {
                        Button(
                            onClick = { onJumpToQuestion(session.currentIndex + 1) },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("btn_next_question")
                        ) {
                            Text("Lanjut")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Button(
                            onClick = { showLockConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusAnsweredGreen),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("btn_lock_exam")
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Kunci Jawaban")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // 3-Color Legend + Lock button shortcut + Cheat badge
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            LegendDot(StatusAnsweredGreen, "Dijawab ($answeredCount)")
                            LegendDot(StatusUnsureGray, "Ragu ($unsureCount)")
                            LegendDot(StatusUnansweredRed, "Belum ($unansweredCount)")
                        }
                        TextButton(
                            onClick = { showLockConfirmDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Kunci", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (session.cheatCount > 0) {
                        Surface(
                            color = StatusUnansweredRedContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusUnansweredRed, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Terdeteksi keluar aplikasi ${session.cheatCount}x (tercatat untuk Guru)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StatusUnansweredRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Horizontal number strip colored Green / Gray / Red
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(session.questions) { idx, q ->
                            val statusColor = questionStatusColor(
                                questionId = q.id,
                                answers = session.answers,
                                unsureIds = session.unsureQuestionIds
                            )
                            val isCurrent = idx == session.currentIndex
                            Surface(
                                onClick = { onJumpToQuestion(idx) },
                                shape = RoundedCornerShape(10.dp),
                                color = statusColor,
                                border = if (isCurrent) BorderStroke(2.5.dp, Color(0xFF0F172A)) else null,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${idx + 1}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Question Body (NO answer key or explanation ever shown!)
            if (currentQuestion != null) {
                val chosen = session.answers[currentQuestion.id]
                val isUnsure = currentQuestion.id in session.unsureQuestionIds
                val currentStatusColor = questionStatusColor(currentQuestion.id, session.answers, session.unsureQuestionIds)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = currentStatusColor,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    val statusLabel = when {
                                        isUnsure -> "Ragu-ragu"
                                        chosen != null -> "Sudah Dijawab ($chosen)"
                                        else -> "Belum Dijawab"
                                    }
                                    Text(
                                        text = "Soal ${session.currentIndex + 1} dari ${session.questions.size} • $statusLabel",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            if (!currentQuestion.imageData.isNullOrBlank()) {
                                SmartImage(
                                    imageData = currentQuestion.imageData,
                                    contentDescription = "Gambar pertanyaan ${session.currentIndex + 1}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            Text(
                                text = currentQuestion.questionText,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    val options = listOf(
                        "A" to currentQuestion.optionA,
                        "B" to currentQuestion.optionB,
                        "C" to currentQuestion.optionC,
                        "D" to currentQuestion.optionD
                    )
                    options.forEach { (letter, text) ->
                        val isSelected = chosen == letter
                        val cardColor = when {
                            isSelected && isUnsure -> StatusUnsureGrayContainer
                            isSelected -> StatusAnsweredGreenContainer
                            else -> MaterialTheme.colorScheme.surface
                        }
                        val borderColor = when {
                            isSelected && isUnsure -> StatusUnsureGray
                            isSelected -> StatusAnsweredGreen
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        }

                        Card(
                            onClick = { onSelectAnswer(currentQuestion.id, letter) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("option_$letter")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) borderColor else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = letter,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Cheat Warning Dialog
    if (!session.pendingCheatWarning.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = onDismissCheatWarning,
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusUnansweredRed) },
            title = { Text("Peringatan Kecurangan!", color = StatusUnansweredRed, fontWeight = FontWeight.Bold) },
            text = { Text(session.pendingCheatWarning) },
            confirmButton = {
                Button(
                    onClick = onDismissCheatWarning,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusUnansweredRed)
                ) {
                    Text("Kembali Mengerjakan")
                }
            }
        )
    }

    // Lock Confirmation Dialog
    if (showLockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLockConfirmDialog = false },
            icon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Kunci & Kumpulkan Jawaban?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pastikan semua jawaban sudah diperiksa. Setelah dikunci, jawaban tidak dapat diubah lagi.")
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LegendDot(StatusAnsweredGreen, "Hijau (Sudah Dijawab): $answeredCount soal")
                            LegendDot(StatusUnsureGray, "Abu-abu (Ragu-ragu): $unsureCount soal")
                            LegendDot(StatusUnansweredRed, "Merah (Belum Dijawab): $unansweredCount soal")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLockConfirmDialog = false
                        onLockExam()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusAnsweredGreen),
                    modifier = Modifier.testTag("confirm_lock_exam")
                ) {
                    Text("Ya, Kunci Sekarang")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLockConfirmDialog = false }) {
                    Text("Periksa Lagi")
                }
            }
        )
    }

    // Full Grid Question Number Modal
    if (showNumberGridDialog) {
        AlertDialog(
            onDismissRequest = { showNumberGridDialog = false },
            title = { Text("Pilih Nomor Soal (${session.questions.size} Soal)") },
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(session.questions) { idx, q ->
                            val color = questionStatusColor(q.id, session.answers, session.unsureQuestionIds)
                            Surface(
                                onClick = {
                                    onJumpToQuestion(idx)
                                    showNumberGridDialog = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = color,
                                border = if (idx == session.currentIndex) BorderStroke(2.5.dp, Color.Black) else null,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${idx + 1}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNumberGridDialog = false }) { Text("Tutup") }
            }
        )
    }
}

@Composable
private fun StudentFinishView(
    result: ExamResult,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_exam_trophy),
                contentDescription = "Ujian Selesai",
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(28.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = if (result.autoLocked) "Waktu Habis! Jawaban Otomatis Dikunci" else "Jawaban Berhasil Dikunci!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Terima kasih ${result.studentName} (Kelas ${result.grade}) telah mengerjakan ${result.examTitle}. Hasil ujian telah dikirim ke Guru PJOK.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ringkasan Pengerjaan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Total Soal: ${result.totalQuestions}")
                    Text("Soal Dijawab: ${result.correctCount + result.wrongCount}")
                    Text("Tidak Dijawab: ${result.unansweredCount}")
                    Text("Waktu Terpakai: ${formatCountdown(result.durationSecondsUsed)}")
                    if (result.cheatCount > 0L) {
                        Text(
                            text = "Catatan Kejujuran: Terdeteksi keluar aplikasi ${result.cheatCount}x",
                            color = StatusUnansweredRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("btn_finish_back_home"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Kembali ke Halaman Utama", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun questionStatusColor(
    questionId: String,
    answers: Map<String, String>,
    unsureIds: Set<String>
): Color = when {
    questionId in unsureIds -> StatusUnsureGray
    answers.containsKey(questionId) -> StatusAnsweredGreen
    else -> StatusUnansweredRed
}

private fun formatCountdown(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}
