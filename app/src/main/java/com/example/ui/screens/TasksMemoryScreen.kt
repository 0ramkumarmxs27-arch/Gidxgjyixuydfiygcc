package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MyraViewModel
import com.example.ui.components.GlassCard
import com.example.ui.theme.CyanSoft
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassCardBg
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.RoyalPurpleDeep
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TasksMemoryScreen(
    viewModel: MyraViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val memories by viewModel.memoryFacts.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var showMemoryTab by remember { mutableStateOf(false) }
    var taskTitle by remember { mutableStateOf("") }
    var taskNotes by remember { mutableStateOf("") }
    var selectedDelayMin by remember { mutableStateOf<Int?>(null) }
    var newMemoryText by remember { mutableStateOf("") }

    val dateFormatter = remember { SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 92.dp)
    ) {
        item {
            Text("Tasks, Reminders & Memory", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showMemoryTab,
                    onClick = { showMemoryTab = false },
                    label = { Text("Tasks & Reminders (${tasks.size})") }
                )
                FilterChip(
                    selected = showMemoryTab,
                    onClick = { showMemoryTab = true },
                    label = { Text("AI Memory (${memories.size})") }
                )
            }
        }

        if (!showMemoryTab) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Create Task / Smart Reminder", style = MaterialTheme.typography.titleMedium, color = CyanSoft)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = taskTitle,
                        onValueChange = { taskTitle = it },
                        placeholder = { Text("Task or reminder title...", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LuminousCyan,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("task_title_input")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = taskNotes,
                        onValueChange = { taskNotes = it },
                        placeholder = { Text("Notes (optional)...", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricViolet,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Notify me in:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(null to "No Alert", 1 to "1m", 10 to "10m", 30 to "30m", 60 to "1h").forEach { (mins, label) ->
                            FilterChip(
                                selected = selectedDelayMin == mins,
                                onClick = { selectedDelayMin = mins },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.addTaskOrReminder(taskTitle, taskNotes, selectedDelayMin)
                            taskTitle = ""
                            taskNotes = ""
                            selectedDelayMin = null
                        },
                        enabled = taskTitle.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = LuminousCyan, contentColor = DeepNavyBg),
                        modifier = Modifier.fillMaxWidth().testTag("add_task_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Task / Reminder")
                    }
                }
            }

            if (tasks.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text("No tasks or reminders yet.", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                        Text("Add one above or ask MYRA to remind you.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                }
            } else {
                items(tasks, key = { it.id }) { task ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = GlassCardBg,
                        border = BorderStroke(1.dp, if (task.isReminder) ElectricViolet.copy(alpha = 0.5f) else GlassBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { viewModel.toggleTaskComplete(task) },
                                colors = CheckboxDefaults.colors(checkedColor = EmeraldPulse)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (task.isCompleted) TextMuted else TextPrimary,
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                                )
                                if (task.notes.isNotBlank()) {
                                    Text(task.notes, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                                if (task.dueTimeMillis != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Alarm, contentDescription = null, tint = ElectricViolet, modifier = Modifier.height(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = dateFormatter.format(Date(task.dueTimeMillis)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricViolet
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Task", tint = RoseAlert)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Conversation Memory Consent", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Allow MYRA to remember facts you share", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.memoryConsentEnabled,
                            onCheckedChange = { viewModel.setMemoryConsent(it) }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newMemoryText,
                            onValueChange = { newMemoryText = it },
                            placeholder = { Text("Add a memory fact manually...", color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                viewModel.addMemoryFact(newMemoryText)
                                newMemoryText = ""
                            },
                            enabled = newMemoryText.isNotBlank() && settings.memoryConsentEnabled
                        ) { Text("Save") }
                    }
                }
            }

            items(memories, key = { it.id }) { mem ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = RoyalPurpleDeep.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = LuminousCyan)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(mem.fact, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.deleteMemoryFact(mem.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Memory", tint = RoseAlert)
                        }
                    }
                }
            }
        }
    }
}
