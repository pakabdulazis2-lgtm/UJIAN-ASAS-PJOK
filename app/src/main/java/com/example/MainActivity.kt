package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.AiQuestionGenerator
import com.example.data.ExamRepository
import com.example.data.SchoolConfig
import com.example.data.UiState
import com.example.data.configureAppCheck
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.HomeModeScreen
import com.example.ui.screens.StudentExamScreen
import com.example.ui.screens.StudentLoginScreen
import com.example.ui.screens.TeacherDashboardScreen
import com.example.ui.theme.UjianPjokTheme
import com.example.viewmodel.ExamViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_HOME = "home"
const val ROUTE_TEACHER = "teacher"
const val ROUTE_STUDENT_LOGIN = "student_login"
const val ROUTE_STUDENT_EXAM = "student_exam"

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}

@Composable
fun AppRoot(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        UjianPjokTheme {
            AuthScreen(onAuthSuccess = {})
        }
    } else {
        key(user.uid) {
            AuthenticatedApp(currentUserId = user.uid)
        }
    }
}

@Composable
private fun AuthenticatedApp(currentUserId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: ExamViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ExamViewModel(
                    repository = ExamRepository(db),
                    aiGenerator = AiQuestionGenerator(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val configState by viewModel.schoolConfigState.collectAsStateWithLifecycle()
    val questionsState by viewModel.questionsState.collectAsStateWithLifecycle()
    val studentsState by viewModel.studentsState.collectAsStateWithLifecycle()
    val resultsState by viewModel.resultsState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiGenerationState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val examSession by viewModel.examSession.collectAsStateWithLifecycle()

    val config = (configState as? UiState.Success)?.data ?: SchoolConfig(userId = currentUserId)
    val questions = (questionsState as? UiState.Success)?.data ?: emptyList()
    val students = (studentsState as? UiState.Success)?.data ?: emptyList()
    val results = (resultsState as? UiState.Success)?.data ?: emptyList()

    val navController = rememberNavController()

    UjianPjokTheme(themeStyle = config.themeStyle) {
        if (configState is UiState.Loading && questionsState is UiState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME
            ) {
                composable(ROUTE_HOME) {
                    HomeModeScreen(
                        config = config,
                        questionCount = questions.size,
                        studentCount = students.size,
                        onOpenTeacherMode = { navController.navigate(ROUTE_TEACHER) },
                        onOpenStudentMode = { navController.navigate(ROUTE_STUDENT_LOGIN) },
                        onSignOutGoogle = {
                            signOut(credentialManager, scope)
                        }
                    )
                }

                composable(ROUTE_TEACHER) {
                    TeacherDashboardScreen(
                        userId = currentUserId,
                        config = config,
                        questions = questions,
                        students = students,
                        results = results,
                        aiState = aiState,
                        statusMessage = statusBanner,
                        onClearStatusMessage = viewModel::clearBannerMessage,
                        onBackToHome = { navController.popBackStack() },
                        onSaveConfig = { updated -> viewModel.saveSchoolConfig(updated) },
                        onSaveQuestion = { q -> viewModel.saveQuestion(q) },
                        onDeleteQuestion = { id -> viewModel.deleteQuestion(id) },
                        onDeleteAllInGrade = { g, list -> viewModel.deleteAllQuestionsForGrade(g, list) },
                        onImportQuestions = { list -> viewModel.importQuestions(list) },
                        onGenerateAiQuestions = { grade, topic, count, existing ->
                            viewModel.generateQuestionsWithAi(grade, topic, count, existing)
                        },
                        onResetAiState = viewModel::resetAiState,
                        onSaveStudent = { s -> viewModel.saveStudent(s) },
                        onDeleteStudent = { id -> viewModel.deleteStudent(id) },
                        onDeleteResult = { id -> viewModel.deleteResult(id) }
                    )
                }

                composable(ROUTE_STUDENT_LOGIN) {
                    StudentLoginScreen(
                        config = config,
                        students = students,
                        questions = questions,
                        onBack = { navController.popBackStack() },
                        onStartExam = { student ->
                            if (viewModel.startStudentExam(student, config, questions)) {
                                navController.navigate(ROUTE_STUDENT_EXAM) {
                                    popUpTo(ROUTE_HOME)
                                }
                            }
                        }
                    )
                }

                composable(ROUTE_STUDENT_EXAM) {
                    val active = examSession
                    if (active != null) {
                        StudentExamScreen(
                            session = active,
                            onSelectAnswer = viewModel::selectAnswer,
                            onToggleUnsure = viewModel::toggleUnsure,
                            onJumpToQuestion = viewModel::jumpToQuestion,
                            onRecordCheat = viewModel::recordCheatViolation,
                            onDismissCheatWarning = viewModel::dismissCheatWarning,
                            onLockExam = { viewModel.lockAndSubmitExam(autoLocked = false) },
                            onExitFinished = {
                                viewModel.clearExamSession()
                                navController.popBackStack(ROUTE_HOME, inclusive = false)
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
