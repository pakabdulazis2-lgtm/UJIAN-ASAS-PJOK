package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.SchoolConfig
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.TealPrimary
import com.example.util.ImageUtils
import com.example.util.SmartImage

@Composable
fun TeacherPengaturanTab(
    config: SchoolConfig,
    onSaveConfig: (SchoolConfig) -> Unit
) {
    val context = LocalContext.current
    var examTitle by remember(config) { mutableStateOf(config.examTitle) }
    var schoolName by remember(config) { mutableStateOf(config.schoolName) }
    var teacherName by remember(config) { mutableStateOf(config.teacherName) }
    var teacherPassword by remember(config) { mutableStateOf(config.teacherPassword) }
    var showPassword by remember { mutableStateOf(false) }
    var durationText by remember(config) { mutableStateOf(config.examDurationMinutes.toString()) }
    var shuffleQuestions by remember(config) { mutableStateOf(config.shuffleQuestions) }
    var themeStyle by remember(config) { mutableStateOf(config.themeStyle) }
    var logoData by remember(config) { mutableStateOf(config.logoData.orEmpty()) }
    var logoUrlInput by remember(config) {
        mutableStateOf(if (config.logoData?.startsWith("http") == true) config.logoData else "")
    }

    LaunchedEffect(config) {
        examTitle = config.examTitle
        schoolName = config.schoolName
        teacherName = config.teacherName
        teacherPassword = config.teacherPassword
        durationText = config.examDurationMinutes.toString()
        shuffleQuestions = config.shuffleQuestions
        themeStyle = config.themeStyle
        logoData = config.logoData.orEmpty()
    }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            ImageUtils.uriToCompressedDataUri(context, uri, maxDimension = 512, maxBytes = 150_000)
                .onSuccess { dataUri ->
                    logoData = dataUri
                    logoUrlInput = ""
                }
                .onFailure { err ->
                    Toast.makeText(context, err.localizedMessage, Toast.LENGTH_SHORT).show()
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Identitas Sekolah & Ujian", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                // Logo Sekolah
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(68.dp)
                    ) {
                        if (logoData.isNotBlank()) {
                            SmartImage(
                                imageData = logoData,
                                contentDescription = "Logo Sekolah",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.School, contentDescription = null, modifier = Modifier.size(34.dp))
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pilih Logo Sekolah")
                        }
                        if (logoData.isNotBlank()) {
                            TextButton(onClick = {
                                logoData = ""
                                logoUrlInput = ""
                            }) {
                                Text("Reset Logo")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = logoUrlInput,
                    onValueChange = {
                        logoUrlInput = it
                        if (it.startsWith("http")) logoData = it
                    },
                    label = { Text("Atau Link URL Logo (https://...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = examTitle,
                    onValueChange = { examTitle = it },
                    label = { Text("Judul Ujian (Default: UJIAN ASAS PJOK)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("setting_exam_title")
                )
                OutlinedTextField(
                    value = schoolName,
                    onValueChange = { schoolName = it },
                    label = { Text("Nama Sekolah") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teacherName,
                    onValueChange = { teacherName = it },
                    label = { Text("Nama Guru PJOK") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teacherPassword,
                    onValueChange = { teacherPassword = it },
                    label = { Text("Sandi Mode Guru") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Tampilkan sandi guru"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tampilan & Aturan Ujian", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Text("Tema Warna Sekolah:", style = MaterialTheme.typography.labelLarge)
                val themes = listOf(
                    Triple("TEAL", "Teal Sporty", TealPrimary),
                    Triple("CRIMSON", "Merah Putih", CrimsonPrimary),
                    Triple("BLUE", "Biru Ceria", BluePrimary),
                    Triple("FOREST", "Hijau Alam", ForestPrimary)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    themes.forEach { (key, label, color) ->
                        val selected = themeStyle == key
                        Surface(
                            onClick = { themeStyle = key },
                            shape = RoundedCornerShape(12.dp),
                            color = color.copy(alpha = if (selected) 0.16f else 0.06f),
                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) color else Color.LightGray),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = durationText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) durationText = it },
                    label = { Text("Waktu Ujian (Menit, default: 90)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Acak Nomor Soal Setiap Siswa", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Urutan soal diacak otomatis agar setiap siswa mendapat urutan berbeda.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = shuffleQuestions,
                        onCheckedChange = { shuffleQuestions = it }
                    )
                }
            }
        }

        Button(
            onClick = {
                val mins = durationText.toLongOrNull()?.coerceIn(5L, 240L) ?: 90L
                onSaveConfig(
                    config.copy(
                        examTitle = examTitle.trim().ifBlank { "UJIAN ASAS PJOK" },
                        schoolName = schoolName.trim().ifBlank { "Sekolah Dasar" },
                        teacherName = teacherName.trim().ifBlank { "Guru PJOK" },
                        teacherPassword = teacherPassword.trim().ifBlank { "Abdulazis19" },
                        logoData = logoData.ifBlank { null },
                        themeStyle = themeStyle,
                        shuffleQuestions = shuffleQuestions,
                        examDurationMinutes = mins
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("btn_save_settings"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Simpan Pengaturan", style = MaterialTheme.typography.titleMedium)
        }
    }
}
