package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AiGenerationState
import com.example.data.ExamQuestion
import com.example.data.ExamResult
import com.example.data.SchoolConfig
import com.example.data.StudentAccount

private data class TeacherTabItem(
    val label: String,
    val icon: ImageVector,
    val tag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherDashboardScreen(
    userId: String,
    config: SchoolConfig,
    questions: List<ExamQuestion>,
    students: List<StudentAccount>,
    results: List<ExamResult>,
    aiState: AiGenerationState,
    statusMessage: String?,
    onClearStatusMessage: () -> Unit,
    onBackToHome: () -> Unit,
    onSaveConfig: (SchoolConfig) -> Unit,
    onSaveQuestion: (ExamQuestion) -> Unit,
    onDeleteQuestion: (String) -> Unit,
    onDeleteAllInGrade: (Int, List<ExamQuestion>) -> Unit,
    onImportQuestions: (List<ExamQuestion>) -> Unit,
    onGenerateAiQuestions: (grade: Int, topic: String, count: Int, existingInGrade: List<ExamQuestion>) -> Unit,
    onResetAiState: () -> Unit,
    onSaveStudent: (StudentAccount) -> Unit,
    onDeleteStudent: (String) -> Unit,
    onDeleteResult: (String) -> Unit
) {
    BackHandler(onBack = onBackToHome)

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusMessage)
            onClearStatusMessage()
        }
    }

    val tabs = remember {
        listOf(
            TeacherTabItem("Bank Soal", Icons.Filled.LibraryBooks, "tab_bank_soal"),
            TeacherTabItem("Daftar Nilai", Icons.AutoMirrored.Filled.FactCheck, "tab_daftar_nilai"),
            TeacherTabItem("Siswa", Icons.Filled.Groups, "tab_siswa"),
            TeacherTabItem("Pengaturan", Icons.Filled.Settings, "tab_pengaturan")
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Mode Guru • ${config.examTitle}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackToHome,
                            modifier = Modifier.testTag("teacher_back_home")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali ke Beranda"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar {
                        tabs.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, maxLines = 1) },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail {
                        tabs.forEachIndexed { index, item ->
                            NavigationRailItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (selectedTab) {
                        0 -> TeacherBankSoalTab(
                            userId = userId,
                            questions = questions,
                            aiState = aiState,
                            onSaveQuestion = onSaveQuestion,
                            onDeleteQuestion = onDeleteQuestion,
                            onDeleteAllInGrade = onDeleteAllInGrade,
                            onImportQuestions = onImportQuestions,
                            onGenerateAiQuestions = onGenerateAiQuestions,
                            onResetAiState = onResetAiState
                        )
                        1 -> TeacherDaftarNilaiTab(
                            config = config,
                            results = results,
                            onDeleteResult = onDeleteResult
                        )
                        2 -> TeacherSiswaTab(
                            userId = userId,
                            students = students,
                            onSaveStudent = onSaveStudent,
                            onDeleteStudent = onDeleteStudent
                        )
                        3 -> TeacherPengaturanTab(
                            config = config,
                            onSaveConfig = onSaveConfig
                        )
                    }
                }
            }
        }
    }
}
