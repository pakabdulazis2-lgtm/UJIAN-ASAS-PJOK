package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class SchoolConfig(
    @DocumentId val id: String = "school",
    val userId: String = "",
    val examTitle: String = "UJIAN ASAS PJOK",
    val schoolName: String = "SD Negeri Harapan Bangsa",
    val teacherName: String = "Abdul Azis, S.Pd.",
    val teacherPassword: String = "Abdulazis19",
    val logoData: String? = null,
    val themeStyle: String = "TEAL",
    val shuffleQuestions: Boolean = true,
    val examDurationMinutes: Long = 90L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ExamQuestion(
    @DocumentId val id: String = "",
    val userId: String = "",
    val grade: Long = 1L,
    val orderNumber: Long = 1L,
    val questionText: String = "",
    val optionA: String = "",
    val optionB: String = "",
    val optionC: String = "",
    val optionD: String = "",
    val correctAnswer: String = "A",
    val explanation: String? = null,
    val imageData: String? = null,
    val sourceType: String = "MANUAL",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudentAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val grade: Long = 1L,
    val password: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AnswerDetail(
    val questionId: String = "",
    val originalNumber: Long = 0L,
    val questionText: String = "",
    val selectedAnswer: String = "",
    val correctAnswer: String = "",
    val correct: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "questionId" to questionId,
        "originalNumber" to originalNumber,
        "questionText" to questionText.take(200),
        "selectedAnswer" to selectedAnswer,
        "correctAnswer" to correctAnswer,
        "correct" to correct
    )
}

@Keep
@IgnoreExtraProperties
data class ExamResult(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val grade: Long = 1L,
    val examTitle: String = "UJIAN ASAS PJOK",
    val totalQuestions: Long = 0L,
    val correctCount: Long = 0L,
    val wrongCount: Long = 0L,
    val unansweredCount: Long = 0L,
    val score: Long = 0L,
    val durationSecondsUsed: Long = 0L,
    val autoLocked: Boolean = false,
    val cheatCount: Long = 0L,
    val cheatLogs: List<String> = emptyList(),
    val answerDetails: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiGenerationState {
    data object Idle : AiGenerationState
    data class Generating(val current: Int, val total: Int, val statusText: String) : AiGenerationState
    data class Success(val addedCount: Int) : AiGenerationState
    data class Error(val message: String) : AiGenerationState
}

data class ActiveExamSession(
    val student: StudentAccount,
    val examTitle: String,
    val questions: List<ExamQuestion>,
    val answers: Map<String, String>,
    val unsureQuestionIds: Set<String>,
    val currentIndex: Int,
    val remainingSeconds: Long,
    val totalDurationSeconds: Long,
    val cheatCount: Int,
    val cheatLogs: List<String>,
    val pendingCheatWarning: String? = null,
    val submittedResult: ExamResult? = null
)
