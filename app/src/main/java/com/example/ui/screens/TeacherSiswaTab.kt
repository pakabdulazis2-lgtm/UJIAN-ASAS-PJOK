package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.data.StudentAccount

@Composable
fun TeacherSiswaTab(
    userId: String,
    students: List<StudentAccount>,
    onSaveStudent: (StudentAccount) -> Unit,
    onDeleteStudent: (String) -> Unit
) {
    var gradeFilter by remember { mutableStateOf<Int?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var editingStudent by remember { mutableStateOf<StudentAccount?>(null) }

    val filtered = remember(students, gradeFilter, searchQuery) {
        students.filter { s ->
            (gradeFilter == null || s.grade.toInt() == gradeFilter) &&
                (searchQuery.isBlank() || s.fullName.contains(searchQuery, ignoreCase = true))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = gradeFilter == null,
                onClick = { gradeFilter = null },
                label = { Text("Semua (${students.size})") }
            )
            (1..6).forEach { g ->
                val count = students.count { it.grade.toInt() == g }
                FilterChip(
                    selected = gradeFilter == g,
                    onClick = { gradeFilter = g },
                    label = { Text("Kelas $g ($count)") }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari nama siswa...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    editingStudent = StudentAccount(
                        userId = userId,
                        grade = (gradeFilter ?: 1).toLong()
                    )
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("btn_add_student")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tambah")
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp)
                    )
                    Text("Belum ada data siswa", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Tambahkan nama siswa, kelas (1–6), dan sandi masing-masing siswa. Daftar ini otomatis muncul di Menu Login Mode Siswa.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id.ifBlank { "${it.fullName}_${it.grade}" } }) { student ->
                    StudentCardRow(
                        student = student,
                        onEdit = { editingStudent = student },
                        onDelete = { onDeleteStudent(student.id) }
                    )
                }
            }
        }
    }

    if (editingStudent != null) {
        StudentEditorDialog(
            initial = editingStudent!!,
            onDismiss = { editingStudent = null },
            onSave = { updated ->
                onSaveStudent(updated)
                editingStudent = null
            }
        )
    }
}

@Composable
private fun StudentCardRow(
    student: StudentAccount,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showPass by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "Kls ${student.grade}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(student.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = if (showPass) "Sandi: ${student.password}" else "Sandi: ••••••",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showPass = !showPass }) {
                Icon(
                    imageVector = if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = "Lihat sandi siswa"
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit siswa")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus siswa", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StudentEditorDialog(
    initial: StudentAccount,
    onDismiss: () -> Unit,
    onSave: (StudentAccount) -> Unit
) {
    var fullName by remember { mutableStateOf(initial.fullName) }
    var grade by remember { mutableIntStateOf(initial.grade.toInt().coerceIn(1, 6)) }
    var password by remember { mutableStateOf(initial.password) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Tambah Akun Siswa" else "Edit Akun Siswa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nama Lengkap Siswa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("student_name_input")
                )
                Text("Pilih Kelas:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..6).forEach { g ->
                        FilterChip(
                            selected = grade == g,
                            onClick = { grade = g },
                            label = { Text("Kls $g") }
                        )
                    }
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Sandi Login Siswa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("student_pass_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            fullName = fullName.trim(),
                            grade = grade.toLong(),
                            password = password.trim()
                        )
                    )
                },
                enabled = fullName.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.testTag("student_save_button")
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
