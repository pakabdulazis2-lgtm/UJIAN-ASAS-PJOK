package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.ExamQuestion
import com.example.data.SchoolConfig
import com.example.data.StudentAccount
import com.example.ui.theme.StatusUnansweredRed
import com.example.ui.theme.StatusUnansweredRedContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentLoginScreen(
    config: SchoolConfig,
    students: List<StudentAccount>,
    questions: List<ExamQuestion>,
    onBack: () -> Unit,
    onStartExam: (StudentAccount) -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedGrade by remember { mutableIntStateOf(1) }
    var selectedStudent by remember { mutableStateOf<StudentAccount?>(null) }
    var showStudentDropdown by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val studentsInGrade = remember(students, selectedGrade) {
        students.filter { it.grade.toInt() == selectedGrade }
    }
    val questionsInGradeCount = remember(questions, selectedGrade) {
        questions.count { it.grade.toInt() == selectedGrade }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Login Siswa • ${config.examTitle}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("student_login_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Pilih Kelas & Nama Kamu",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (1..6).forEach { g ->
                                FilterChip(
                                    selected = selectedGrade == g,
                                    onClick = {
                                        selectedGrade = g
                                        selectedStudent = null
                                        errorMessage = null
                                    },
                                    label = { Text("Kelas $g") }
                                )
                            }
                        }

                        // Student name dropdown from Mode Guru data
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showStudentDropdown = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("student_picker_button")
                            ) {
                                Text(
                                    text = selectedStudent?.fullName
                                        ?: if (studentsInGrade.isEmpty()) "Belum ada nama siswa di Kelas $selectedGrade"
                                        else "Pilih Nama Siswa (${studentsInGrade.size} tersedia)",
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = showStudentDropdown,
                                onDismissRequest = { showStudentDropdown = false }
                            ) {
                                if (studentsInGrade.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Belum ada data siswa Kelas $selectedGrade (minta Guru menambahkan)") },
                                        onClick = { showStudentDropdown = false }
                                    )
                                } else {
                                    studentsInGrade.forEach { st ->
                                        DropdownMenuItem(
                                            text = { Text("${st.fullName} (Kelas ${st.grade})") },
                                            onClick = {
                                                selectedStudent = st
                                                showStudentDropdown = false
                                                errorMessage = null
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                errorMessage = null
                            },
                            label = { Text("Sandi Siswa") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Lihat sandi"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("student_login_password")
                        )

                        if (errorMessage != null) {
                            Surface(
                                color = StatusUnansweredRedContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = errorMessage.orEmpty(),
                                    color = StatusUnansweredRed,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val st = selectedStudent
                                when {
                                    st == null -> errorMessage = "Silakan pilih nama siswa terlebih dahulu."
                                    passwordInput != st.password -> errorMessage = "Password anda salah. Periksa kembali sandi dari guru."
                                    questionsInGradeCount == 0 -> errorMessage = "Belum ada soal ujian untuk Kelas $selectedGrade. Hubungi guru."
                                    else -> onStartExam(st)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("btn_start_exam"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Mulai Ujian ($questionsInGradeCount Soal)", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                // Kid-friendly rules card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Waktu Pengerjaan: ${config.examDurationMinutes} Menit",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "• Waktu hitung mundur dimulai otomatis setelah kamu login.\n" +
                                "• Warna nomor soal: Hijau = Sudah dijawab, Abu-abu = Ragu-ragu, Merah = Belum dijawab.\n" +
                                "• Kamu bebas mengubah jawaban sebelum mengunci.\n" +
                                "• Jika waktu habis, jawaban otomatis dikunci.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = StatusUnansweredRed)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Jangan keluar aplikasi atau membuka Google/AI selama ujian karena akan terdeteksi otomatis oleh sistem guru!",
                                style = MaterialTheme.typography.labelLarge,
                                color = StatusUnansweredRed
                            )
                        }
                    }
                }
            }
        }
    }
}
