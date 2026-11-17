package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ExamRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireMatchedUid(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Silakan masuk dengan akun Google terlebih dahulu.")
        check(uid == expectedUserId) { "Sesi pengguna berubah; operasi dibatalkan." }
        return uid
    }

    fun observeSchoolConfig(userId: String): Flow<SchoolConfig> =
        db.collection("users").document(userId)
            .collection("config").document("school")
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    SchoolConfig::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: SchoolConfig(userId = userId)
            }

    fun observeQuestions(userId: String): Flow<List<ExamQuestion>> =
        db.collection("users").document(userId)
            .collection("questions")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ExamQuestion::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(compareBy({ it.grade }, { it.orderNumber }))
            }

    fun observeStudents(userId: String): Flow<List<StudentAccount>> =
        db.collection("users").document(userId)
            .collection("students")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    StudentAccount::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(compareBy({ it.grade }, { it.fullName.lowercase() }))
            }

    fun observeResults(userId: String): Flow<List<ExamResult>> =
        db.collection("users").document(userId)
            .collection("results")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ExamResult::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    suspend fun ensureDefaultConfig(userId: String): Result<Unit> =
        try {
            val uid = requireMatchedUid(userId)
            val docRef = db.collection("users").document(uid)
                .collection("config").document("school")
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "examTitle" to "UJIAN ASAS PJOK",
                    "schoolName" to "SD Negeri Harapan Bangsa",
                    "teacherName" to "Abdul Azis, S.Pd.",
                    "teacherPassword" to "Abdulazis19",
                    "themeStyle" to "TEAL",
                    "shuffleQuestions" to true,
                    "examDurationMinutes" to 90L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId/config/school")
            Result.failure(e)
        }

    suspend fun saveSchoolConfig(userId: String, config: SchoolConfig): Result<Unit> =
        try {
            val uid = requireMatchedUid(userId)
            val docRef = db.collection("users").document(uid)
                .collection("config").document("school")
            val snap = docRef.get().await()
            val safeTitle = config.examTitle.trim().ifBlank { "UJIAN ASAS PJOK" }.take(100)
            val safeSchool = config.schoolName.trim().ifBlank { "Sekolah Dasar" }.take(120)
            val safeTeacher = config.teacherName.trim().ifBlank { "Guru PJOK" }.take(100)
            val safePass = config.teacherPassword.trim().ifBlank { "Abdulazis19" }.take(64)
            val safeTheme = config.themeStyle.takeIf { it in listOf("TEAL", "CRIMSON", "BLUE", "FOREST") } ?: "TEAL"
            val safeDuration = config.examDurationMinutes.coerceIn(5L, 240L)

            if (snap.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "examTitle" to safeTitle,
                    "schoolName" to safeSchool,
                    "teacherName" to safeTeacher,
                    "teacherPassword" to safePass,
                    "themeStyle" to safeTheme,
                    "shuffleQuestions" to config.shuffleQuestions,
                    "examDurationMinutes" to safeDuration,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val logo = config.logoData?.trim()
                if (!logo.isNullOrBlank()) {
                    updates["logoData"] = logo.take(350000)
                } else {
                    updates["logoData"] = FieldValue.delete()
                }
                docRef.update(updates).await()
            } else {
                val createMap = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "examTitle" to safeTitle,
                    "schoolName" to safeSchool,
                    "teacherName" to safeTeacher,
                    "teacherPassword" to safePass,
                    "themeStyle" to safeTheme,
                    "shuffleQuestions" to config.shuffleQuestions,
                    "examDurationMinutes" to safeDuration,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val logo = config.logoData?.trim()
                if (!logo.isNullOrBlank()) {
                    createMap["logoData"] = logo.take(350000)
                }
                docRef.set(createMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId/config/school")
            Result.failure(e)
        }

    suspend fun upsertQuestion(userId: String, question: ExamQuestion): Result<Unit> =
        try {
            val uid = requireMatchedUid(userId)
            val col = db.collection("users").document(uid).collection("questions")
            val isNew = question.id.isBlank()
            val docRef = if (isNew) col.document() else col.document(question.id)
            val safeGrade = question.grade.coerceIn(1L, 6L)
            val safeOrder = question.orderNumber.coerceIn(1L, 1000L)
            val safeText = question.questionText.trim().ifBlank { "Pertanyaan PJOK" }.take(2000)
            val safeA = question.optionA.trim().ifBlank { "Pilihan A" }.take(600)
            val safeB = question.optionB.trim().ifBlank { "Pilihan B" }.take(600)
            val safeC = question.optionC.trim().ifBlank { "Pilihan C" }.take(600)
            val safeD = question.optionD.trim().ifBlank { "Pilihan D" }.take(600)
            val safeAns = question.correctAnswer.uppercase().takeIf { it in listOf("A", "B", "C", "D") } ?: "A"
            val safeSource = question.sourceType.takeIf { it in listOf("MANUAL", "AI", "IMPORT") } ?: "MANUAL"
            val safeExp = question.explanation?.trim()?.take(1500)
            val safeImg = question.imageData?.trim()?.take(350000)

            if (isNew) {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "grade" to safeGrade,
                    "orderNumber" to safeOrder,
                    "questionText" to safeText,
                    "optionA" to safeA,
                    "optionB" to safeB,
                    "optionC" to safeC,
                    "optionD" to safeD,
                    "correctAnswer" to safeAns,
                    "sourceType" to safeSource,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!safeExp.isNullOrBlank()) payload["explanation"] = safeExp
                if (!safeImg.isNullOrBlank()) payload["imageData"] = safeImg
                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "grade" to safeGrade,
                    "orderNumber" to safeOrder,
                    "questionText" to safeText,
                    "optionA" to safeA,
                    "optionB" to safeB,
                    "optionC" to safeC,
                    "optionD" to safeD,
                    "correctAnswer" to safeAns,
                    "sourceType" to safeSource,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                updates["explanation"] = if (!safeExp.isNullOrBlank()) safeExp else FieldValue.delete()
                updates["imageData"] = if (!safeImg.isNullOrBlank()) safeImg else FieldValue.delete()
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId/questions")
            Result.failure(e)
        }

    suspend fun batchAddQuestions(userId: String, questions: List<ExamQuestion>): Result<Int> =
        try {
            val uid = requireMatchedUid(userId)
            val col = db.collection("users").document(uid).collection("questions")
            var totalWritten = 0
            questions.chunked(300).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { q ->
                    val docRef = col.document()
                    val payload = mutableMapOf<String, Any>(
                        "userId" to uid,
                        "grade" to q.grade.coerceIn(1L, 6L),
                        "orderNumber" to q.orderNumber.coerceIn(1L, 1000L),
                        "questionText" to q.questionText.trim().ifBlank { "Pertanyaan PJOK" }.take(2000),
                        "optionA" to q.optionA.trim().ifBlank { "Pilihan A" }.take(600),
                        "optionB" to q.optionB.trim().ifBlank { "Pilihan B" }.take(600),
                        "optionC" to q.optionC.trim().ifBlank { "Pilihan C" }.take(600),
                        "optionD" to q.optionD.trim().ifBlank { "Pilihan D" }.take(600),
                        "correctAnswer" to (q.correctAnswer.uppercase().takeIf { it in listOf("A", "B", "C", "D") } ?: "A"),
                        "sourceType" to (q.sourceType.takeIf { it in listOf("MANUAL", "AI", "IMPORT") } ?: "AI"),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    q.explanation?.trim()?.takeIf { it.isNotBlank() }?.let {
                        payload["explanation"] = it.take(1500)
                    }
                    q.imageData?.trim()?.takeIf { it.isNotBlank() }?.let {
                        payload["imageData"] = it.take(350000)
                    }
                    batch.set(docRef, payload)
                }
                batch.commit().await()
                totalWritten += chunk.size
            }
            Result.success(totalWritten)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$userId/questions")
            Result.failure(e)
        }

    suspend fun deleteQuestion(userId: String, questionId: String): Result<Unit> =
        try {
            val uid = requireMatchedUid(userId)
            require(questionId.isNotBlank())
            db.collection("users").document(uid)
                .collection("questions").document(questionId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$userId/questions/$questionId")
            Result.failure(e)
        }

    suspend fun deleteAllQuestionsForGrade(userId: String, questionIds: List<String>): Result<Unit> =
        try {
            val uid = requireMatchedUid(userId)
            val col = db.collection("users").document(uid).collection("questions")
            questionIds.chunked(300).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { id ->
                    if (id.isNotBlank()) batch.delete(col.document(id))
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$userId/questions")
            Result.failure(e)
        }

    suspend fun upsertStudent(userId: String, student: StudentAccount): Result<Unit> =
        try {
            val uid = requireMatchedUid(userId)
            val col = db.collection("users").document(uid).collection("students")
            val isNew = student.id.isBlank()
            val docRef = if (isNew) col.document() else col.document(student.id)
            val safeName = student.fullName.trim().ifBlank { "Siswa" }.take(100)
            val safeGrade = student.grade.coerceIn(1L, 6L)
            val safePass = student.password.trim().ifBlank { "1234" }.take(64)

            if (isNew) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "fullName" to safeName,
                        "grade" to safeGrade,
                        "password" to safePass,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "fullName" to safeName,
                        "grade" to safeGrade,
                        "password" to safePass,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId/students")
            Result.failure(e)
        }

    suspend fun deleteStudent(userId: String, studentId: String): Result<Unit> =
        try {
            val uid = requireMatchedUid(userId)
            require(studentId.isNotBlank())
            db.collection("users").document(uid)
                .collection("students").document(studentId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$userId/students/$studentId")
            Result.failure(e)
        }

    suspend fun submitExamResult(userId: String, result: ExamResult): Result<Unit> =
        try {
            val uid = requireMatchedUid(userId)
            val docRef = db.collection("users").document(uid)
                .collection("results").document()
            val payload = mapOf(
                "userId" to uid,
                "studentId" to result.studentId.take(128),
                "studentName" to result.studentName.trim().ifBlank { "Siswa" }.take(100),
                "grade" to result.grade.coerceIn(1L, 6L),
                "examTitle" to result.examTitle.trim().ifBlank { "UJIAN ASAS PJOK" }.take(100),
                "totalQuestions" to result.totalQuestions.coerceIn(0L, 200L),
                "correctCount" to result.correctCount.coerceIn(0L, 200L),
                "wrongCount" to result.wrongCount.coerceIn(0L, 200L),
                "unansweredCount" to result.unansweredCount.coerceIn(0L, 200L),
                "score" to result.score.coerceIn(0L, 100L),
                "durationSecondsUsed" to result.durationSecondsUsed.coerceIn(0L, 86400L),
                "autoLocked" to result.autoLocked,
                "cheatCount" to result.cheatCount.coerceIn(0L, 1000L),
                "cheatLogs" to result.cheatLogs.take(50),
                "answerDetails" to result.answerDetails.take(200),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$userId/results")
            Result.failure(e)
        }

    suspend fun deleteResult(userId: String, resultId: String): Result<Unit> =
        try {
            val uid = requireMatchedUid(userId)
            require(resultId.isNotBlank())
            db.collection("users").document(uid)
                .collection("results").document(resultId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$userId/results/$resultId")
            Result.failure(e)
        }
}
