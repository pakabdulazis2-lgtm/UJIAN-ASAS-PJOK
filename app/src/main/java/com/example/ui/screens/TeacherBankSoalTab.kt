package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AiGenerationState
import com.example.data.ExamQuestion
import com.example.ui.theme.StatusAnsweredGreen
import com.example.ui.theme.StatusAnsweredGreenContainer
import com.example.util.ImageUtils
import com.example.util.QuestionFileParser
import com.example.util.SmartImage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TeacherBankSoalTab(
    userId: String,
    questions: List<ExamQuestion>,
    aiState: AiGenerationState,
    onSaveQuestion: (ExamQuestion) -> Unit,
    onDeleteQuestion: (String) -> Unit,
    onDeleteAllInGrade: (Int, List<ExamQuestion>) -> Unit,
    onImportQuestions: (List<ExamQuestion>) -> Unit,
    onGenerateAiQuestions: (grade: Int, topic: String, count: Int, existingInGrade: List<ExamQuestion>) -> Unit,
    onResetAiState: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedGrade by remember { mutableIntStateOf(1) }
    var editingQuestion by remember { mutableStateOf<ExamQuestion?>(null) }
    var showAiDialog by remember { mutableStateOf(false) }
    var showFormatGuide by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val gradeQuestions = remember(questions, selectedGrade) {
        questions.filter { it.grade.toInt() == selectedGrade }
            .sortedBy { it.orderNumber }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val startNum = (gradeQuestions.maxOfOrNull { it.orderNumber }?.toInt() ?: 0) + 1
            val parsed = QuestionFileParser.parseFromUri(
                context = context,
                uri = uri,
                userId = userId,
                grade = selectedGrade,
                startingOrderNumber = startNum
            )
            parsed.onSuccess { list ->
                onImportQuestions(list)
            }.onFailure { err ->
                Toast.makeText(context, err.localizedMessage ?: "Gagal impor file", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Grade 1..6 selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (1..6).forEach { grade ->
                val count = questions.count { it.grade.toInt() == grade }
                FilterChip(
                    selected = selectedGrade == grade,
                    onClick = { selectedGrade = grade },
                    label = { Text("Kelas $grade ($count)") },
                    modifier = Modifier.testTag("grade_chip_$grade")
                )
            }
        }

        // Action Buttons: AI, Import File, Manual Add
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showAiDialog = true },
                modifier = Modifier.testTag("btn_ai_generate"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Buat Soal AI")
            }
            OutlinedButton(
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf("text/*", "text/csv", "application/json", "application/octet-stream")
                    )
                },
                modifier = Modifier.testTag("btn_import_file"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Impor File")
            }
            OutlinedButton(
                onClick = {
                    val nextNum = (gradeQuestions.maxOfOrNull { it.orderNumber }?.toInt() ?: 0) + 1
                    editingQuestion = ExamQuestion(
                        userId = userId,
                        grade = selectedGrade.toLong(),
                        orderNumber = nextNum.toLong(),
                        sourceType = "MANUAL"
                    )
                },
                modifier = Modifier.testTag("btn_add_manual"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tambah Manual")
            }
            IconButton(onClick = { showFormatGuide = true }) {
                Icon(Icons.Filled.HelpOutline, contentDescription = "Panduan format file soal")
            }
            if (gradeQuestions.isNotEmpty()) {
                IconButton(onClick = { showDeleteAllConfirm = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Hapus semua soal kelas ini",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Quick jump by question number (Nomor Soal 1..100+)
        if (gradeQuestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(gradeQuestions) { index, q ->
                    Surface(
                        onClick = {
                            scope.launch { listState.animateScrollToItem(index) }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "#${q.orderNumber}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (gradeQuestions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "Belum ada soal untuk Kelas $selectedGrade",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Gunakan \"Buat Soal AI\" untuk menghasilkan hingga 100+ soal PJOK otomatis, impor dari file CSV/TXT/JSON, atau tambah manual.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(gradeQuestions, key = { _, item -> item.id.ifBlank { "${item.orderNumber}_${item.questionText}" } }) { _, q ->
                    TeacherQuestionCard(
                        question = q,
                        onEdit = { editingQuestion = q },
                        onDelete = { onDeleteQuestion(q.id) }
                    )
                }
            }
        }
    }

    if (editingQuestion != null) {
        QuestionEditorDialog(
            initial = editingQuestion!!,
            onDismiss = { editingQuestion = null },
            onSave = { updated ->
                onSaveQuestion(updated)
                editingQuestion = null
            }
        )
    }

    if (showAiDialog) {
        AiQuestionDialog(
            grade = selectedGrade,
            currentCount = gradeQuestions.size,
            aiState = aiState,
            onGenerate = { topic, count ->
                onGenerateAiQuestions(selectedGrade, topic, count, gradeQuestions)
            },
            onDismiss = {
                onResetAiState()
                showAiDialog = false
            }
        )
    }

    if (showFormatGuide) {
        AlertDialog(
            onDismissRequest = { showFormatGuide = false },
            title = { Text("Format File Impor Soal") },
            text = {
                Text(
                    "Mendukung file .TXT, .CSV, atau .JSON:\n\n" +
                        "1. Format CSV / Pemisah titik koma (;):\n" +
                        "Pertanyaan;Opsi A;Opsi B;Opsi C;Opsi D;Kunci;Pembahasan\n\n" +
                        "2. Format Teks Bernomor (.txt):\n" +
                        "1. Gerakan lari termasuk gerak...\n" +
                        "A. Lokomotor\nB. Nonlokomotor\nC. Manipulatif\nD. Statis\n" +
                        "Jawaban: A\nPembahasan: Berlari berpindah tempat.\n\n" +
                        "3. Format JSON:\n" +
                        "Array objek dengan field questionText, optionA..optionD, correctAnswer, explanation."
                )
            },
            confirmButton = {
                TextButton(onClick = { showFormatGuide = false }) { Text("Mengerti") }
            }
        )
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("Hapus Semua Soal Kelas $selectedGrade?") },
            text = { Text("${gradeQuestions.size} soal pada Kelas $selectedGrade akan dihapus permanen.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllInGrade(selectedGrade, gradeQuestions)
                        showDeleteAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Hapus Semua") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun TeacherQuestionCard(
    question: ExamQuestion,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "No. ${question.orderNumber}",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = question.sourceType,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit soal ${question.orderNumber}")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Hapus soal ${question.orderNumber}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (!question.imageData.isNullOrBlank()) {
                SmartImage(
                    imageData = question.imageData,
                    contentDescription = "Gambar soal ${question.orderNumber}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Fit
                )
            }

            Text(
                text = question.questionText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            val options = listOf(
                "A" to question.optionA,
                "B" to question.optionB,
                "C" to question.optionC,
                "D" to question.optionD
            )
            options.forEach { (letter, text) ->
                val isKey = question.correctAnswer.equals(letter, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isKey) StatusAnsweredGreenContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isKey) BorderStroke(1.dp, StatusAnsweredGreen) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$letter. $text",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isKey) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isKey) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Kunci jawaban",
                                tint = StatusAnsweredGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (!question.explanation.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Bantuan Jawaban Guru: ${question.explanation}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionEditorDialog(
    initial: ExamQuestion,
    onDismiss: () -> Unit,
    onSave: (ExamQuestion) -> Unit
) {
    val context = LocalContext.current
    var orderText by remember { mutableStateOf(initial.orderNumber.toString()) }
    var questionText by remember { mutableStateOf(initial.questionText) }
    var optionA by remember { mutableStateOf(initial.optionA) }
    var optionB by remember { mutableStateOf(initial.optionB) }
    var optionC by remember { mutableStateOf(initial.optionC) }
    var optionD by remember { mutableStateOf(initial.optionD) }
    var correctAnswer by remember { mutableStateOf(initial.correctAnswer.ifBlank { "A" }) }
    var explanation by remember { mutableStateOf(initial.explanation.orEmpty()) }
    var imageData by remember { mutableStateOf(initial.imageData.orEmpty()) }
    var imageUrlInput by remember { mutableStateOf(if (initial.imageData?.startsWith("http") == true) initial.imageData else "") }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            ImageUtils.uriToCompressedDataUri(context, uri)
                .onSuccess { dataUri ->
                    imageData = dataUri
                    imageUrlInput = ""
                }
                .onFailure { err ->
                    Toast.makeText(context, err.localizedMessage, Toast.LENGTH_SHORT).show()
                }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Tambah Soal Kelas ${initial.grade}" else "Edit Soal No. ${initial.orderNumber}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = orderText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) orderText = it },
                    label = { Text("Nomor Soal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    label = { Text("Pertanyaan") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = optionA, onValueChange = { optionA = it }, label = { Text("Pilihan A") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = optionB, onValueChange = { optionB = it }, label = { Text("Pilihan B") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = optionC, onValueChange = { optionC = it }, label = { Text("Pilihan C") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = optionD, onValueChange = { optionD = it }, label = { Text("Pilihan D") }, modifier = Modifier.fillMaxWidth())

                Text("Kunci Jawaban Benar:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("A", "B", "C", "D").forEach { letter ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = correctAnswer == letter,
                                onClick = { correctAnswer = letter }
                            )
                            Text(letter)
                        }
                    }
                }

                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = { Text("Bantuan Jawaban / Pembahasan Guru") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Gambar Soal (Opsional):", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pilih Galeri")
                    }
                    if (imageData.isNotBlank()) {
                        TextButton(onClick = {
                            imageData = ""
                            imageUrlInput = ""
                        }) { Text("Hapus Gambar") }
                    }
                }
                OutlinedTextField(
                    value = imageUrlInput,
                    onValueChange = {
                        imageUrlInput = it
                        if (it.startsWith("http")) imageData = it
                    },
                    label = { Text("Atau tempel Link URL gambar (https://...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (imageData.isNotBlank()) {
                    SmartImage(
                        imageData = imageData,
                        contentDescription = "Preview gambar soal",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val num = orderText.toLongOrNull()?.coerceIn(1L, 1000L) ?: initial.orderNumber
                    onSave(
                        initial.copy(
                            orderNumber = num,
                            questionText = questionText,
                            optionA = optionA,
                            optionB = optionB,
                            optionC = optionC,
                            optionD = optionD,
                            correctAnswer = correctAnswer,
                            explanation = explanation.ifBlank { null },
                            imageData = imageData.ifBlank { null }
                        )
                    )
                },
                enabled = questionText.isNotBlank() && optionA.isNotBlank() && optionB.isNotBlank()
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun AiQuestionDialog(
    grade: Int,
    currentCount: Int,
    aiState: AiGenerationState,
    onGenerate: (topic: String, count: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var topic by remember {
        mutableStateOf("Gerak Dasar Lokomotor, Nonlokomotor, Manipulatif, Permainan Bola, Senam, dan Pola Hidup Sehat")
    }
    var countSlider by remember { mutableFloatStateOf(20f) }
    val isGenerating = aiState is AiGenerationState.Generating

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Buat Soal PJOK dengan AI (Kelas $grade)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Saat ini Kelas $grade memiliki $currentCount soal. AI dapat membuat hingga 120 soal per sesi dan langsung dilengkapi kunci & bantuan pembahasan guru.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Materi / Topik Soal PJOK") },
                    minLines = 2,
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Jumlah soal dibuat: ${countSlider.roundToInt()} soal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = countSlider,
                    onValueChange = { countSlider = it },
                    valueRange = 5f..120f,
                    enabled = !isGenerating
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(10, 25, 50, 100, 120).forEach { preset ->
                        FilterChip(
                            selected = countSlider.roundToInt() == preset,
                            onClick = { if (!isGenerating) countSlider = preset.toFloat() },
                            label = { Text("$preset") }
                        )
                    }
                }

                when (aiState) {
                    is AiGenerationState.Generating -> {
                        val progress = if (aiState.total > 0) aiState.current.toFloat() / aiState.total else 0f
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = aiState.statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is AiGenerationState.Success -> {
                        Surface(
                            color = StatusAnsweredGreenContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Selesai! ${aiState.addedCount} soal baru berhasil ditambahkan.",
                                color = StatusAnsweredGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    is AiGenerationState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = aiState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    AiGenerationState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            if (aiState is AiGenerationState.Success) {
                Button(onClick = onDismiss) { Text("Selesai") }
            } else {
                Button(
                    onClick = { onGenerate(topic.trim(), countSlider.roundToInt()) },
                    enabled = !isGenerating && topic.isNotBlank(),
                    modifier = Modifier.testTag("ai_start_generate_button")
                ) {
                    Text(if (isGenerating) "Memproses..." else "Generate Soal")
                }
            }
        },
        dismissButton = {
            if (!isGenerating && aiState !is AiGenerationState.Success) {
                TextButton(onClick = onDismiss) { Text("Tutup") }
            }
        }
    )
}
