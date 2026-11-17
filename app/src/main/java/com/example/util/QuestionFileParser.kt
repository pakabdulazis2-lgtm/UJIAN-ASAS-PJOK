package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.ExamQuestion
import org.json.JSONArray
import org.json.JSONObject

object QuestionFileParser {

    fun parseFromUri(
        context: Context,
        uri: Uri,
        userId: String,
        grade: Int,
        startingOrderNumber: Int
    ): Result<List<ExamQuestion>> {
        return try {
            val rawText = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: return Result.failure(IllegalStateException("Tidak dapat membaca isi file."))

            val trimmed = rawText.trim().removePrefix("\uFEFF")
            if (trimmed.isBlank()) {
                return Result.failure(IllegalStateException("File soal kosong."))
            }

            val parsed = when {
                trimmed.startsWith("[") || trimmed.startsWith("{") ->
                    parseJson(trimmed, userId, grade, startingOrderNumber)
                looksLikeDelimited(trimmed) ->
                    parseDelimited(trimmed, userId, grade, startingOrderNumber)
                else ->
                    parseStructuredText(trimmed, userId, grade, startingOrderNumber)
            }

            if (parsed.isEmpty()) {
                Result.failure(
                    IllegalStateException(
                        "Format file tidak dikenali. Gunakan CSV/TXT/JSON sesuai panduan format."
                    )
                )
            } else {
                Result.success(parsed.take(150))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Gagal membaca file soal: ${e.localizedMessage}"))
        }
    }

    private fun looksLikeDelimited(text: String): Boolean {
        val firstLines = text.lineSequence().take(5).toList()
        return firstLines.any { line ->
            line.count { it == ';' } >= 5 || line.count { it == '|' } >= 5 || line.count { it == '\t' } >= 5 ||
                (line.count { it == ',' } >= 5 && !line.trimStart().startsWith("1."))
        }
    }

    private fun parseJson(
        text: String,
        userId: String,
        grade: Int,
        startingOrderNumber: Int
    ): List<ExamQuestion> {
        val arr = if (text.startsWith("[")) {
            JSONArray(text)
        } else {
            JSONObject(text).optJSONArray("questions") ?: JSONArray()
        }
        val list = mutableListOf<ExamQuestion>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val q = obj.optString("questionText", obj.optString("soal", obj.optString("pertanyaan"))).trim()
            val a = obj.optString("optionA", obj.optString("a", obj.optString("pilihanA"))).trim()
            val b = obj.optString("optionB", obj.optString("b", obj.optString("pilihanB"))).trim()
            val c = obj.optString("optionC", obj.optString("c", obj.optString("pilihanC"))).trim()
            val d = obj.optString("optionD", obj.optString("d", obj.optString("pilihanD"))).trim()
            val ans = obj.optString("correctAnswer", obj.optString("kunci", obj.optString("jawaban", "A")))
                .trim().uppercase().take(1)
            val exp = obj.optString("explanation", obj.optString("pembahasan")).trim()
            val img = obj.optString("imageData", obj.optString("gambar")).trim()

            if (q.isNotBlank() && a.isNotBlank() && b.isNotBlank()) {
                list.add(
                    ExamQuestion(
                        userId = userId,
                        grade = grade.toLong(),
                        orderNumber = (startingOrderNumber + list.size).toLong(),
                        questionText = q,
                        optionA = a,
                        optionB = b,
                        optionC = c.ifBlank { "-" },
                        optionD = d.ifBlank { "-" },
                        correctAnswer = if (ans in listOf("A", "B", "C", "D")) ans else "A",
                        explanation = exp.ifBlank { null },
                        imageData = img.ifBlank { null },
                        sourceType = "IMPORT"
                    )
                )
            }
        }
        return list
    }

    private fun parseDelimited(
        text: String,
        userId: String,
        grade: Int,
        startingOrderNumber: Int
    ): List<ExamQuestion> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val sample = lines.first()
        val delimiter = when {
            sample.count { it == '|' } >= 5 -> '|'
            sample.count { it == ';' } >= 5 -> ';'
            sample.count { it == '\t' } >= 5 -> '\t'
            else -> ','
        }

        val list = mutableListOf<ExamQuestion>()
        lines.forEachIndexed { idx, line ->
            val cols = splitCsvLine(line, delimiter)
            if (cols.size >= 6) {
                val firstLower = cols[0].trim().lowercase()
                if (idx == 0 && (firstLower.contains("pertanyaan") || firstLower.contains("soal") || firstLower == "no")) {
                    return@forEachIndexed
                }
                // Support optional leading number column if cols.size >= 7 and cols[0] is numeric
                val offset = if (cols.size >= 7 && cols[0].trim().all { it.isDigit() }) 1 else 0
                val q = cols.getOrNull(offset)?.trim().orEmpty()
                val a = cols.getOrNull(offset + 1)?.trim().orEmpty()
                val b = cols.getOrNull(offset + 2)?.trim().orEmpty()
                val c = cols.getOrNull(offset + 3)?.trim().orEmpty()
                val d = cols.getOrNull(offset + 4)?.trim().orEmpty()
                val ansRaw = cols.getOrNull(offset + 5)?.trim()?.uppercase().orEmpty()
                val ans = ansRaw.firstOrNull { it in listOf('A', 'B', 'C', 'D') }?.toString() ?: "A"
                val exp = cols.getOrNull(offset + 6)?.trim().orEmpty()
                val img = cols.getOrNull(offset + 7)?.trim().orEmpty()

                if (q.isNotBlank() && a.isNotBlank() && b.isNotBlank()) {
                    list.add(
                        ExamQuestion(
                            userId = userId,
                            grade = grade.toLong(),
                            orderNumber = (startingOrderNumber + list.size).toLong(),
                            questionText = q,
                            optionA = a,
                            optionB = b,
                            optionC = c.ifBlank { "-" },
                            optionD = d.ifBlank { "-" },
                            correctAnswer = ans,
                            explanation = exp.ifBlank { null },
                            imageData = img.ifBlank { null },
                            sourceType = "IMPORT"
                        )
                    )
                }
            }
        }
        return list
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }

    private fun parseStructuredText(
        text: String,
        userId: String,
        grade: Int,
        startingOrderNumber: Int
    ): List<ExamQuestion> {
        val list = mutableListOf<ExamQuestion>()
        var currentQuestion = StringBuilder()
        var optA = ""
        var optB = ""
        var optC = ""
        var optD = ""
        var ans = "A"
        var exp = ""

        fun flush() {
            val qText = currentQuestion.toString().trim()
            if (qText.isNotBlank() && optA.isNotBlank() && optB.isNotBlank()) {
                list.add(
                    ExamQuestion(
                        userId = userId,
                        grade = grade.toLong(),
                        orderNumber = (startingOrderNumber + list.size).toLong(),
                        questionText = qText,
                        optionA = optA,
                        optionB = optB,
                        optionC = optC.ifBlank { "-" },
                        optionD = optD.ifBlank { "-" },
                        correctAnswer = ans,
                        explanation = exp.ifBlank { null },
                        sourceType = "IMPORT"
                    )
                )
            }
            currentQuestion = StringBuilder()
            optA = ""
            optB = ""
            optC = ""
            optD = ""
            ans = "A"
            exp = ""
        }

        val numRegex = Regex("""^\d+[\.\)]\s+(.+)$""")
        val optRegex = Regex("""^([A-Da-d])[\.\)]\s+(.+)$""")
        val ansRegex = Regex("""^(?:kunci|jawaban|answer)\s*[:=]\s*([A-Da-d]).*$""", RegexOption.IGNORE_CASE)
        val expRegex = Regex("""^(?:pembahasan|penjelasan|catatan)\s*[:=]\s*(.+)$""", RegexOption.IGNORE_CASE)

        text.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEach

            val numMatch = numRegex.matchEntire(line)
            val optMatch = optRegex.matchEntire(line)
            val ansMatch = ansRegex.matchEntire(line)
            val expMatch = expRegex.matchEntire(line)

            when {
                ansMatch != null -> {
                    ans = ansMatch.groupValues[1].uppercase()
                }
                expMatch != null -> {
                    exp = expMatch.groupValues[1].trim()
                }
                optMatch != null -> {
                    when (optMatch.groupValues[1].uppercase()) {
                        "A" -> optA = optMatch.groupValues[2].trim()
                        "B" -> optB = optMatch.groupValues[2].trim()
                        "C" -> optC = optMatch.groupValues[2].trim()
                        "D" -> optD = optMatch.groupValues[2].trim()
                    }
                }
                numMatch != null -> {
                    if (optA.isNotBlank() || currentQuestion.isNotBlank()) {
                        flush()
                    }
                    currentQuestion.append(numMatch.groupValues[1].trim())
                }
                else -> {
                    if (optA.isBlank()) {
                        if (currentQuestion.isNotEmpty()) currentQuestion.append(" ")
                        currentQuestion.append(line)
                    }
                }
            }
        }
        flush()
        return list
    }
}
