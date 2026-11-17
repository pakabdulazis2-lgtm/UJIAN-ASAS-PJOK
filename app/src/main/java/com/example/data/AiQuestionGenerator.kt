package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiQuestionGenerator"

class AiQuestionGenerator {

    private val questionSchema = Schema.obj(
        properties = mapOf(
            "questions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "questionText" to Schema.string(description = "Teks pertanyaan pilihan ganda bahasa Indonesia yang jelas untuk anak SD"),
                        "optionA" to Schema.string(description = "Teks pilihan jawaban A"),
                        "optionB" to Schema.string(description = "Teks pilihan jawaban B"),
                        "optionC" to Schema.string(description = "Teks pilihan jawaban C"),
                        "optionD" to Schema.string(description = "Teks pilihan jawaban D"),
                        "correctAnswer" to Schema.enumeration(
                            values = listOf("A", "B", "C", "D"),
                            description = "Huruf kunci jawaban benar: A, B, C, atau D"
                        ),
                        "explanation" to Schema.string(description = "Penjelasan singkat kunci jawaban sebagai bantuan guru")
                    )
                ),
                description = "Daftar soal pilihan ganda PJOK"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = questionSchema
            }
        )
    }

    suspend fun generateQuestions(
        userId: String,
        grade: Int,
        materialTopic: String,
        targetCount: Int,
        startingOrderNumber: Int,
        existingStems: List<String> = emptyList(),
        onProgress: (generatedSoFar: Int, totalTarget: Int, statusText: String) -> Unit
    ): Result<List<ExamQuestion>> {
        val safeTarget = targetCount.coerceIn(1, 150)
        val batchSize = 20
        val collected = mutableListOf<ExamQuestion>()
        val usedStems = existingStems.map { it.take(60).lowercase() }.toMutableSet()

        try {
            var remaining = safeTarget
            var batchIndex = 1
            while (remaining > 0) {
                val askCount = minOf(batchSize, remaining)
                val fromNum = collected.size + 1
                val toNum = (collected.size + askCount).coerceAtMost(safeTarget)
                onProgress(
                    collected.size,
                    safeTarget,
                    "AI sedang menyusun soal $fromNum–$toNum dari $safeTarget..."
                )

                val recentAvoid = usedStems.toList().takeLast(25).joinToString(" | ")
                val prompt = buildString {
                    append("Buat tepat $askCount soal pilihan ganda (4 opsi: A, B, C, D) ")
                    append("untuk mata pelajaran PJOK (Pendidikan Jasmani, Olahraga, dan Kesehatan) ")
                    append("SD Kelas $grade sesuai Kurikulum Merdeka / ASAS.\n")
                    append("Materi fokus: $materialTopic.\n")
                    append("Ketentuan:\n")
                    append("- Bahasa Indonesia yang sederhana, edukatif, dan sesuai usia anak SD Kelas $grade.\n")
                    append("- Setiap soal WAJIB memiliki 1 kunci jawaban benar (A/B/C/D) dan 3 pengecoh yang masuk akal.\n")
                    append("- Sertakan field 'explanation' berupa penjelasan singkat mengapa kunci tersebut benar untuk membantu guru.\n")
                    append("- Jangan mengulang soal yang sama atau mirip (batch ke-$batchIndex).\n")
                    if (recentAvoid.isNotBlank()) {
                        append("- Hindari mengulang topik kalimat awal berikut: $recentAvoid\n")
                    }
                }

                val response = withTimeout(60_000L) {
                    model.generateContent(prompt)
                }
                val jsonString = response.text
                    ?: throw IllegalStateException("AI mengembalikan respons kosong.")

                val root = JSONObject(jsonString)
                val array = root.getJSONArray("questions")
                var addedInBatch = 0
                for (i in 0 until array.length()) {
                    if (collected.size >= safeTarget) break
                    val obj = array.getJSONObject(i)
                    val qText = obj.optString("questionText").trim()
                    val optA = obj.optString("optionA").trim()
                    val optB = obj.optString("optionB").trim()
                    val optC = obj.optString("optionC").trim()
                    val optD = obj.optString("optionD").trim()
                    val ans = obj.optString("correctAnswer").trim().uppercase()
                        .takeIf { it in listOf("A", "B", "C", "D") } ?: "A"
                    val exp = obj.optString("explanation").trim()

                    if (qText.isNotBlank() && optA.isNotBlank() && optB.isNotBlank()) {
                        val key = qText.take(60).lowercase()
                        if (usedStems.add(key) || batchIndex > 6) {
                            collected.add(
                                ExamQuestion(
                                    userId = userId,
                                    grade = grade.toLong(),
                                    orderNumber = (startingOrderNumber + collected.size).toLong(),
                                    questionText = qText,
                                    optionA = optA,
                                    optionB = optB,
                                    optionC = optC.ifBlank { "-" },
                                    optionD = optD.ifBlank { "-" },
                                    correctAnswer = ans,
                                    explanation = exp.ifBlank { null },
                                    sourceType = "AI"
                                )
                            )
                            addedInBatch++
                        }
                    }
                }

                if (addedInBatch == 0) {
                    // Prevent infinite loop if model returned empty/duplicate batch
                    break
                }
                remaining = safeTarget - collected.size
                batchIndex++
                if (batchIndex > 12) break
            }

            if (collected.isEmpty()) {
                return Result.failure(IllegalStateException("AI tidak menghasilkan soal yang valid. Coba ubah materi."))
            }
            onProgress(collected.size, safeTarget, "Menyimpan ${collected.size} soal ke database...")
            return Result.success(collected)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI generation timed out", e)
            return if (collected.isNotEmpty()) {
                Result.success(collected)
            } else {
                Result.failure(Exception("Pembuatan soal AI melebihi batas waktu 60 detik. Coba lagi atau kurangi jumlah per sesi."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI generation failed", e)
            return Result.failure(Exception(e.localizedMessage ?: "Gagal membuat soal dengan AI."))
        }
    }
}
