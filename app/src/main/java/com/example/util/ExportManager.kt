package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.data.ExamResult
import com.example.data.SchoolConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    PDF("PDF (.pdf)", "pdf", "application/pdf"),
    WORD("Word (.doc)", "doc", "application/msword"),
    EXCEL("Excel (.xls)", "xls", "application/vnd.ms-excel")
}

object ExportManager {

    private fun exportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun exportAndShare(
        context: Context,
        format: ExportFormat,
        config: SchoolConfig,
        results: List<ExamResult>,
        gradeFilter: Int?
    ): Result<File> {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val classSuffix = gradeFilter?.let { "Kelas_$it" } ?: "Semua_Kelas"
            val file = File(exportsDir(context), "Daftar_Nilai_${classSuffix}_$timestamp.${format.extension}")

            when (format) {
                ExportFormat.PDF -> writePdf(file, config, results, gradeFilter)
                ExportFormat.WORD -> writeWordHtml(file, config, results, gradeFilter)
                ExportFormat.EXCEL -> writeExcelXml(file, config, results, gradeFilter)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Daftar Nilai ${config.examTitle}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan / Buka ${format.label}"))
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(Exception("Gagal mengekspor file ${format.label}: ${e.localizedMessage}"))
        }
    }

    fun printPdfDirect(
        context: Context,
        config: SchoolConfig,
        results: List<ExamResult>,
        gradeFilter: Int?
    ): Result<Unit> {
        return try {
            val file = File(exportsDir(context), "print_preview_nilai.pdf")
            writePdf(file, config, results, gradeFilter)
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "${config.examTitle} - Daftar Nilai"
            printManager.print(jobName, object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder("Daftar_Nilai_PJOK.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                    callback.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback
                ) {
                    try {
                        FileInputStream(file).use { input ->
                            FileOutputStream(destination.fileDescriptor).use { output ->
                                input.copyTo(output)
                            }
                        }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.message)
                    }
                }
            }, null)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Gagal mencetak dokumen: ${e.localizedMessage}"))
        }
    }

    private fun writePdf(
        file: File,
        config: SchoolConfig,
        results: List<ExamResult>,
        gradeFilter: Int?
    ) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10.5f
        }
        val headerBgPaint = Paint().apply {
            color = Color.rgb(15, 118, 110)
        }
        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cellPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9.5f
        }
        val warnPaint = Paint().apply {
            color = Color.rgb(185, 28, 28)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.8f
        }

        val rowsPerPage = 24
        val chunks = if (results.isEmpty()) listOf(emptyList()) else results.chunked(rowsPerPage)

        chunks.forEachIndexed { pageIndex, chunk ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            var y = 44f
            canvas.drawText(config.schoolName.uppercase(), margin, y, titlePaint)
            y += 18f
            canvas.drawText("DAFTAR NILAI - ${config.examTitle}", margin, y, titlePaint)
            y += 16f
            val kelasLabel = gradeFilter?.let { "Kelas $it" } ?: "Semua Kelas (1-6)"
            canvas.drawText(
                "Guru PJOK: ${config.teacherName}  |  Filter: $kelasLabel  |  Hal. ${pageIndex + 1}/${chunks.size}",
                margin,
                y,
                subPaint
            )
            y += 14f

            // Table header
            val tableTop = y
            canvas.drawRect(margin, tableTop, pageWidth - margin, tableTop + 22f, headerBgPaint)
            val textY = tableTop + 15f
            canvas.drawText("No", margin + 6f, textY, headerTextPaint)
            canvas.drawText("Nama Siswa", margin + 32f, textY, headerTextPaint)
            canvas.drawText("Kls", margin + 210f, textY, headerTextPaint)
            canvas.drawText("Benar", margin + 245f, textY, headerTextPaint)
            canvas.drawText("Salah", margin + 290f, textY, headerTextPaint)
            canvas.drawText("Nilai", margin + 338f, textY, headerTextPaint)
            canvas.drawText("Kejujuran / Status", margin + 382f, textY, headerTextPaint)
            y = tableTop + 22f

            if (chunk.isEmpty()) {
                y += 24f
                canvas.drawText("Belum ada data nilai siswa.", margin + 10f, y, cellPaint)
            } else {
                chunk.forEachIndexed { rowIdx, item ->
                    val globalNo = pageIndex * rowsPerPage + rowIdx + 1
                    val rowY = y + 17f
                    canvas.drawText("$globalNo", margin + 6f, rowY, cellPaint)
                    canvas.drawText(item.studentName.take(28), margin + 32f, rowY, cellPaint)
                    canvas.drawText("${item.grade}", margin + 214f, rowY, cellPaint)
                    canvas.drawText("${item.correctCount}", margin + 252f, rowY, cellPaint)
                    canvas.drawText("${item.wrongCount}", margin + 296f, rowY, cellPaint)
                    canvas.drawText("${item.score}", margin + 342f, rowY, cellPaint)
                    if (item.cheatCount > 0L) {
                        canvas.drawText("Curang (${item.cheatCount}x)", margin + 382f, rowY, warnPaint)
                    } else {
                        val lockTag = if (item.autoLocked) "Jujur (Auto)" else "Jujur"
                        canvas.drawText(lockTag, margin + 382f, rowY, cellPaint)
                    }
                    y += 24f
                    canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
                }
            }

            // Footer signature on last page
            if (pageIndex == chunks.lastIndex) {
                val sigY = (pageHeight - 75f).coerceAtLeast(y + 36f)
                canvas.drawText("Mengetahui,", pageWidth - margin - 140f, sigY, subPaint)
                canvas.drawText("Guru PJOK", pageWidth - margin - 140f, sigY + 14f, subPaint)
                canvas.drawText(config.teacherName, pageWidth - margin - 140f, sigY + 46f, titlePaint.apply { textSize = 11f })
            }

            doc.finishPage(page)
        }

        FileOutputStream(file).use { out -> doc.writeTo(out) }
        doc.close()
    }

    private fun writeWordHtml(
        file: File,
        config: SchoolConfig,
        results: List<ExamResult>,
        gradeFilter: Int?
    ) {
        val kelasLabel = gradeFilter?.let { "Kelas $it" } ?: "Semua Kelas (1-6)"
        val rows = results.mapIndexed { idx, r ->
            val status = if (r.cheatCount > 0L) {
                "<span style='color:#b91c1c;font-weight:bold'>Terindikasi Curang (${r.cheatCount}x)</span>"
            } else {
                if (r.autoLocked) "Jujur (Kunci Otomatis)" else "Jujur"
            }
            """
            <tr>
              <td style='text-align:center'>${idx + 1}</td>
              <td>${escapeHtml(r.studentName)}</td>
              <td style='text-align:center'>Kelas ${r.grade}</td>
              <td style='text-align:center'>${r.correctCount}</td>
              <td style='text-align:center'>${r.wrongCount}</td>
              <td style='text-align:center'><b>${r.score}</b></td>
              <td>$status</td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")

        val html = """
            <html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word'>
            <head><meta charset='utf-8'><title>Daftar Nilai ${escapeHtml(config.examTitle)}</title>
            <style>
              body { font-family: Arial, sans-serif; margin: 24px; }
              h2, h3 { margin: 4px 0; text-align: center; }
              table { width: 100%; border-collapse: collapse; margin-top: 16px; }
              th { background-color: #0F766E; color: white; padding: 8px; border: 1px solid #cbd5e1; }
              td { padding: 7px; border: 1px solid #cbd5e1; font-size: 11pt; }
            </style>
            </head>
            <body>
              <h2>${escapeHtml(config.schoolName)}</h2>
              <h3>DAFTAR NILAI ${escapeHtml(config.examTitle)}</h3>
              <p style='text-align:center'>Guru PJOK: ${escapeHtml(config.teacherName)} &bull; $kelasLabel</p>
              <table>
                <tr>
                  <th>No</th><th>Nama Siswa</th><th>Kelas</th><th>Benar</th><th>Salah</th><th>Nilai</th><th>Status Kejujuran</th>
                </tr>
                $rows
              </table>
              <br><br>
              <table style='border:none;width:100%'>
                <tr>
                  <td style='border:none;width:65%'></td>
                  <td style='border:none;text-align:left'>
                    Guru PJOK,<br><br><br>
                    <b>${escapeHtml(config.teacherName)}</b>
                  </td>
                </tr>
              </table>
            </body>
            </html>
        """.trimIndent()
        file.writeText(html, Charsets.UTF_8)
    }

    private fun writeExcelXml(
        file: File,
        config: SchoolConfig,
        results: List<ExamResult>,
        gradeFilter: Int?
    ) {
        val kelasLabel = gradeFilter?.let { "Kelas $it" } ?: "Semua Kelas (1-6)"
        val dataRows = results.mapIndexed { idx, r ->
            val status = if (r.cheatCount > 0L) "Terindikasi Curang (${r.cheatCount}x)" else "Jujur"
            """
            <Row>
              <Cell><Data ss:Type="Number">${idx + 1}</Data></Cell>
              <Cell><Data ss:Type="String">${escapeXml(r.studentName)}</Data></Cell>
              <Cell><Data ss:Type="Number">${r.grade}</Data></Cell>
              <Cell><Data ss:Type="Number">${r.correctCount}</Data></Cell>
              <Cell><Data ss:Type="Number">${r.wrongCount}</Data></Cell>
              <Cell><Data ss:Type="Number">${r.unansweredCount}</Data></Cell>
              <Cell><Data ss:Type="Number">${r.score}</Data></Cell>
              <Cell><Data ss:Type="String">${escapeXml(status)}</Data></Cell>
              <Cell><Data ss:Type="String">${if (r.autoLocked) "Otomatis" else "Manual"}</Data></Cell>
            </Row>
            """.trimIndent()
        }.joinToString("\n")

        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <?mso-application progid="Excel.Sheet"?>
            <Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
             xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">
             <Worksheet ss:Name="Daftar Nilai">
              <Table>
               <Row><Cell><Data ss:Type="String">${escapeXml(config.schoolName)} - ${escapeXml(config.examTitle)}</Data></Cell></Row>
               <Row><Cell><Data ss:Type="String">Guru: ${escapeXml(config.teacherName)} | $kelasLabel</Data></Cell></Row>
               <Row/>
               <Row>
                <Cell><Data ss:Type="String">No</Data></Cell>
                <Cell><Data ss:Type="String">Nama Siswa</Data></Cell>
                <Cell><Data ss:Type="String">Kelas</Data></Cell>
                <Cell><Data ss:Type="String">Benar</Data></Cell>
                <Cell><Data ss:Type="String">Salah</Data></Cell>
                <Cell><Data ss:Type="String">Kosong</Data></Cell>
                <Cell><Data ss:Type="String">Nilai</Data></Cell>
                <Cell><Data ss:Type="String">Kejujuran</Data></Cell>
                <Cell><Data ss:Type="String">Penguncian</Data></Cell>
               </Row>
               $dataRows
              </Table>
             </Worksheet>
            </Workbook>
        """.trimIndent()
        file.writeText(xml, Charsets.UTF_8)
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun escapeXml(s: String): String =
        escapeHtml(s).replace("\"", "&quot;").replace("'", "&apos;")
}
