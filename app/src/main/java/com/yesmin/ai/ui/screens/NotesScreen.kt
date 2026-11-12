package com.yesmin.ai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yesmin.ai.ui.MainViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun NotesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    SettingsScaffold("Notes & reminders", onBack) {
        InfoCard("Voice se bolo: “note: doodh lana hai” ya “10 minute baad yaad dilao chai”. Notes sirf is phone me save hote hain. Reminders Android alarm se aate hain (phone restart ke baad dobara set karne padte hain).")
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("New note") })
        Button(onClick = { vm.addNote(text); text = "" }, enabled = text.isNotBlank()) { Text("Save note") }
        if (notes.isEmpty()) Text("Abhi koi note nahi hai.")
        notes.reversed().forEach { n ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(n.text)
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(n.time)), style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { vm.deleteNote(n.id) }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            }
        }
    }
}
