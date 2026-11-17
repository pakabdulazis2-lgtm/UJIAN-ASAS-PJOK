package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ActiveExamSession
import com.example.data.AiGenerationState
import com.example.data.AiQuestionGenerator
import com.example.data.AnswerDetail
import com.example.data.ExamQuestion
import com.example.data.ExamRepository
import com.example.data.ExamResult
import com.example.data.SchoolConfig
import com.example.data.StudentAccount
import com.example.data.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ExamViewModel"

class ExamViewModel(
    private val repository: ExamRepository,
    private val aiGenerator: AiQuestionGenerator,
    private val currentUserId: String
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.ensureDefaultConfig(currentUserId)
        }
    }

    val schoolConfigState: StateFlow<UiState<SchoolConfig>> =
        repository.observeSchoolConfig(currentUserId)
            .map<SchoolConfig, UiState<SchoolConfig>> { UiState.Success(it) }
            .catch { error ->
                val fsError = unwrapFirestoreException(error)
                if (fsError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing school config", fsError ?: error)
                emit(UiState.Error((fsError ?: error).localizedMessage ?: "Gagal memuat pengaturan"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val questionsState: StateFlow<UiState<List<ExamQuestion>>> =
        repository.observeQuestions(currentUserId)
            .map<List<ExamQuestion>, UiState<List<ExamQuestion>>> { UiState.Success(it) }
            .catch { error ->
                val fsError = unwrapFirestoreException(error)
                if (fsError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing questions", fsError ?: error)
                emit(UiState.Error((fsError ?: error).localizedMessage ?: "Gagal memuat soal"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val studentsState: StateFlow<UiState<List<StudentAccount>>> =
        repository.observeStudents(currentUserId)
            .map<List<StudentAccount>, UiState<List<StudentAccount>>> { UiState.Success(it) }
            .catch { error ->
                val fsError = unwrapFirestoreException(error)
                if (fsError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing students", fsError ?: error)
                emit(UiState.Error((fsError ?: error).localizedMessage ?: "Gagal memuat data siswa"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val resultsState: StateFlow<UiState<List<ExamResult>>> =
        repository.observeResults(currentUserId)
            .map<List<ExamResult>, UiState<List<ExamResult>>> { UiState.Success(it) }
            .catch { error ->
                val fsError = unwrapFirestoreException(error)
                if (fsError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing results", fsError ?: error)
                emit(UiState.Error((fsError ?: error).localizedMessage ?: "Gagal memuat daftar nilai"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val _aiGenerationState = MutableStateFlow<AiGenerationState>(AiGenerationState.Idle)
    val aiGenerationState: StateFlow<AiGenerationState> = _aiGenerationState.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _examSession = MutableStateFlow<ActiveExamSession?>(null)
    val examSession: StateFlow<ActiveExamSession?> = _examSession.asStateFlow()

    private var timerJob: Job? = null
    private var lastCheatRecordedAtMs: Long = 0L

    fun clearBannerMessage() {
        _statusBannerMessage.value = null
    }

    fun resetAiState() {
        _aiGenerationState.value = AiGenerationState.Idle
    }

    fun saveSchoolConfig(config: SchoolConfig, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.saveSchoolConfig(currentUserId, config)
            result.onSuccess {
                _statusBannerMessage.value = "Pengaturan berhasil disimpan."
                onSuccess()
            }.onFailure { err ->
                _statusBannerMessage.value = err.localizedMessage ?: "Gagal menyimpan pengaturan."
            }
        }
    }

    fun saveQuestion(question: ExamQuestion, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.upsertQuestion(currentUserId, question)
            result.onSuccess {
                _statusBannerMessage.value = "Soal berhasil disimpan."
                onSuccess()
            }.onFailure { err ->
                _statusBannerMessage.value = err.localizedMessage ?: "Gagal menyimpan soal."
            }
        }
    }

    fun deleteQuestion(questionId: String) {
        viewModelScope.launch {
            repository.deleteQuestion(currentUserId, questionId)
                .onSuccess { _statusBannerMessage.value = "Soal dihapus." }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Gagal menghapus soal." }
        }
    }

    fun deleteAllQuestionsForGrade(grade: Int, questionsInGrade: List<ExamQuestion>) {
        viewModelScope.launch {
            val ids = questionsInGrade.map { it.id }
            repository.deleteAllQuestionsForGrade(currentUserId, ids)
                .onSuccess { _statusBannerMessage.value = "Semua soal Kelas $grade berhasil dihapus." }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Gagal menghapus soal." }
        }
    }

    fun importQuestions(imported: List<ExamQuestion>, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.batchAddQuestions(currentUserId, imported)
            res.onSuccess { count ->
                _statusBannerMessage.value = "$count soal berhasil diimpor dari file."
                onDone(count)
            }.onFailure { err ->
                _statusBannerMessage.value = err.localizedMessage ?: "Gagal mengimpor soal."
            }
        }
    }

    fun generateQuestionsWithAi(
        grade: Int,
        topic: String,
        targetCount: Int,
        existingQuestionsForGrade: List<ExamQuestion>
    ) {
        viewModelScope.launch {
            _aiGenerationState.value = AiGenerationState.Generating(
                current = 0,
                total = targetCount,
                statusText = "Menghubungi AI untuk menyusun $targetCount soal Kelas $grade..."
            )
            val startingNumber = (existingQuestionsForGrade.maxOfOrNull { it.orderNumber }?.toInt() ?: 0) + 1
            val existingStems = existingQuestionsForGrade.map { it.questionText }
            val genResult = aiGenerator.generateQuestions(
                userId = currentUserId,
                grade = grade,
                materialTopic = topic,
                targetCount = targetCount,
                startingOrderNumber = startingNumber,
                existingStems = existingStems,
                onProgress = { soFar, total, statusText ->
                    _aiGenerationState.value = AiGenerationState.Generating(soFar, total, statusText)
                }
            )

            genResult.onSuccess { generated ->
                val saveRes = repository.batchAddQuestions(currentUserId, generated)
                saveRes.onSuccess { savedCount ->
                    _aiGenerationState.value = AiGenerationState.Success(savedCount)
                    _statusBannerMessage.value = "Berhasil menambahkan $savedCount soal AI untuk Kelas $grade."
                }.onFailure { err ->
                    _aiGenerationState.value = AiGenerationState.Error(
                        err.localizedMessage ?: "Gagal menyimpan soal AI."
                    )
                }
            }.onFailure { err ->
                _aiGenerationState.value = AiGenerationState.Error(
                    err.localizedMessage ?: "Gagal membuat soal dengan AI."
                )
            }
        }
    }

    fun saveStudent(student: StudentAccount, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.upsertStudent(currentUserId, student)
                .onSuccess {
                    _statusBannerMessage.value = "Data siswa berhasil disimpan."
                    onSuccess()
                }
                .onFailure { err ->
                    _statusBannerMessage.value = err.localizedMessage ?: "Gagal menyimpan siswa."
                }
        }
    }

    fun deleteStudent(studentId: String) {
        viewModelScope.launch {
            repository.deleteStudent(currentUserId, studentId)
                .onSuccess { _statusBannerMessage.value = "Siswa dihapus." }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Gagal menghapus siswa." }
        }
    }

    fun deleteResult(resultId: String) {
        viewModelScope.launch {
            repository.deleteResult(currentUserId, resultId)
                .onSuccess { _statusBannerMessage.value = "Data nilai dihapus." }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Gagal menghapus nilai." }
        }
    }

    // --- Student Exam Session Controls ---

    fun startStudentExam(
        student: StudentAccount,
        config: SchoolConfig,
        allQuestions: List<ExamQuestion>
    ): Boolean {
        val gradeQuestions = allQuestions.filter { it.grade == student.grade }
        if (gradeQuestions.isEmpty()) {
            _statusBannerMessage.value = "Belum ada soal untuk Kelas ${student.grade}. Hubungi Guru."
            return false
        }

        val ordered = if (config.shuffleQuestions) gradeQuestions.shuffled() else gradeQuestions
        val totalSeconds = config.examDurationMinutes.coerceIn(5L, 240L) * 60L

        timerJob?.cancel()
        lastCheatRecordedAtMs = 0L
        _examSession.value = ActiveExamSession(
            student = student,
            examTitle = config.examTitle,
            questions = ordered,
            answers = emptyMap(),
            unsureQuestionIds = emptySet(),
            currentIndex = 0,
            remainingSeconds = totalSeconds,
            totalDurationSeconds = totalSeconds,
            cheatCount = 0,
            cheatLogs = emptyList(),
            pendingCheatWarning = null,
            submittedResult = null
        )
        startCountdownTimer()
        return true
    }

    private fun startCountdownTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _examSession.value ?: break
                if (current.submittedResult != null) break
                val nextSeconds = (current.remainingSeconds - 1L).coerceAtLeast(0L)
                _examSession.value = current.copy(remainingSeconds = nextSeconds)
                if (nextSeconds <= 0L) {
                    lockAndSubmitExam(autoLocked = true)
                    break
                }
            }
        }
    }

    fun selectAnswer(questionId: String, option: String) {
        val current = _examSession.value ?: return
        if (current.submittedResult != null) return
        val updatedAnswers = current.answers.toMutableMap().apply {
            put(questionId, option)
        }
        _examSession.value = current.copy(answers = updatedAnswers)
    }

    fun toggleUnsure(questionId: String) {
        val current = _examSession.value ?: return
        if (current.submittedResult != null) return
        val updatedUnsure = current.unsureQuestionIds.toMutableSet()
        if (questionId in updatedUnsure) {
            updatedUnsure.remove(questionId)
        } else {
            updatedUnsure.add(questionId)
        }
        _examSession.value = current.copy(unsureQuestionIds = updatedUnsure)
    }

    fun jumpToQuestion(index: Int) {
        val current = _examSession.value ?: return
        if (index in current.questions.indices) {
            _examSession.value = current.copy(currentIndex = index)
        }
    }

    fun recordCheatViolation(reason: String) {
        val current = _examSession.value ?: return
        if (current.submittedResult != null) return
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastCheatRecordedAtMs < 2500L) return
        lastCheatRecordedAtMs = nowMs

        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(nowMs))
        val newCount = current.cheatCount + 1
        val entry = "$timeStr - $reason"
        val updatedLogs = (current.cheatLogs + entry).take(50)
        _examSession.value = current.copy(
            cheatCount = newCount,
            cheatLogs = updatedLogs,
            pendingCheatWarning = "Peringatan Kejujuran #$newCount: Anda terdeteksi $reason. Aktivitas ini tercatat untuk Guru!"
        )
    }

    fun dismissCheatWarning() {
        val current = _examSession.value ?: return
        _examSession.value = current.copy(pendingCheatWarning = null)
    }

    fun lockAndSubmitExam(autoLocked: Boolean) {
        val current = _examSession.value ?: return
        if (current.submittedResult != null) return
        timerJob?.cancel()

        var correctCount = 0L
        var wrongCount = 0L
        var unansweredCount = 0L
        val detailMaps = mutableListOf<Map<String, Any>>()

        current.questions.forEach { q ->
            val selected = current.answers[q.id].orEmpty()
            val isCorrect = selected.isNotBlank() && selected.equals(q.correctAnswer, ignoreCase = true)
            when {
                selected.isBlank() -> unansweredCount++
                isCorrect -> correctCount++
                else -> wrongCount++
            }
            detailMaps.add(
                AnswerDetail(
                    questionId = q.id,
                    originalNumber = q.orderNumber,
                    questionText = q.questionText,
                    selectedAnswer = selected,
                    correctAnswer = q.correctAnswer,
                    correct = isCorrect
                ).toMap()
            )
        }

        val total = current.questions.size.toLong().coerceAtLeast(1L)
        val score = ((correctCount * 100.0) / total).toLong().coerceIn(0L, 100L)
        val usedSeconds = (current.totalDurationSeconds - current.remainingSeconds).coerceAtLeast(0L)

        val result = ExamResult(
            userId = currentUserId,
            studentId = current.student.id,
            studentName = current.student.fullName,
            grade = current.student.grade,
            examTitle = current.examTitle,
            totalQuestions = current.questions.size.toLong(),
            correctCount = correctCount,
            wrongCount = wrongCount,
            unansweredCount = unansweredCount,
            score = score,
            durationSecondsUsed = usedSeconds,
            autoLocked = autoLocked,
            cheatCount = current.cheatCount.toLong(),
            cheatLogs = current.cheatLogs,
            answerDetails = detailMaps.take(200)
        )

        _examSession.value = current.copy(submittedResult = result)

        viewModelScope.launch {
            repository.submitExamResult(currentUserId, result)
                .onFailure { err ->
                    _statusBannerMessage.value = err.localizedMessage ?: "Gagal menyimpan hasil ujian ke server."
                }
        }
    }

    fun clearExamSession() {
        timerJob?.cancel()
        _examSession.value = null
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
}
