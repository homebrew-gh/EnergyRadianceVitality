package com.erv.app.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.erv.app.data.GoalTemplateOption
import com.erv.app.data.GoalTemplateOptions
import com.erv.app.data.UserPreferences
import com.erv.app.data.UserGoalDefinition
import com.erv.app.data.createGoalFromTemplate
import com.erv.app.data.metricSummaryLabel
import kotlinx.coroutines.launch

private data class GoalEditorState(
    val goalId: String?,
    val template: GoalTemplateOption,
    val title: String,
    val target: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsEditScreen(
    userPreferences: UserPreferences,
    onBack: () -> Unit,
) {
    val savedGoals by userPreferences.goals.collectAsState(initial = emptyList())
    var draft by remember { mutableStateOf<List<UserGoalDefinition>?>(null) }
    var editorState by remember { mutableStateOf<GoalEditorState?>(null) }
    val goals = draft ?: savedGoals
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit goals") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                userPreferences.setGoals(goals)
                                onBack()
                            }
                        },
                    ) {
                        Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Text(
                    text = "Create a few abstract weekly goals. Progress is tracked automatically from your logs and active program instead of from per-exercise checklists.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
            if (goals.isEmpty()) {
                item {
                    Text(
                        text = "No goals yet. Add one from the templates below.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            } else {
                item {
                    Text(
                        text = "Your goals",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
                items(goals, key = { it.id }) { goal ->
                    GoalDefinitionRow(
                        goal = goal,
                        onEdit = {
                            val template = templateForGoal(goal)
                            if (template != null) {
                                editorState = GoalEditorState(
                                    goalId = goal.id,
                                    template = template,
                                    title = goal.title,
                                    target = goal.target.toString(),
                                )
                            }
                        },
                        onDelete = {
                            draft = goals.filterNot { it.id == goal.id }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            item {
                Text(
                    text = "Add goal",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            items(GoalTemplateOptions, key = { it.id }) { template ->
                GoalTemplateRow(
                    template = template,
                    onAdd = {
                        editorState = GoalEditorState(
                            goalId = null,
                            template = template,
                            title = template.title,
                            target = template.defaultTarget.toString(),
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    val currentEditor = editorState
    if (currentEditor != null) {
        GoalEditorDialog(
            state = currentEditor,
            onDismiss = { editorState = null },
            onSave = { title, target ->
                val parsedTarget = target.toIntOrNull()?.coerceAtLeast(1) ?: return@GoalEditorDialog
                val updatedGoal = createGoalFromTemplate(
                    template = currentEditor.template,
                    title = title,
                    target = parsedTarget,
                    goalId = currentEditor.goalId ?: java.util.UUID.randomUUID().toString(),
                )
                draft = if (currentEditor.goalId == null) {
                    goals + updatedGoal
                } else {
                    goals.map { existing -> if (existing.id == currentEditor.goalId) updatedGoal else existing }
                }
                editorState = null
            },
        )
    }
}

@Composable
private fun GoalDefinitionRow(
    goal: UserGoalDefinition,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(goal.title, style = MaterialTheme.typography.titleSmall)
        Text(
            "${goal.target} ${goal.metricSummaryLabel()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit) {
                Text("Edit")
            }
            TextButton(onClick = onDelete) {
                Text("Delete")
            }
        }
    }
}

@Composable
private fun GoalTemplateRow(
    template: GoalTemplateOption,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(template.title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onAdd) {
            Text("Add")
        }
    }
}

@Composable
private fun GoalEditorDialog(
    state: GoalEditorState,
    onDismiss: () -> Unit,
    onSave: (title: String, target: String) -> Unit,
) {
    var title by remember(state) { mutableStateOf(state.title) }
    var target by remember(state) { mutableStateOf(state.target) }
    val targetError = target.isNotBlank() && (target.toIntOrNull() == null || target.toIntOrNull()!! <= 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (state.goalId == null) "Add goal" else "Edit goal")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(state.template.title, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.template.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Goal title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth(),
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it.filter(Char::isDigit) },
                    label = { Text("Weekly target") },
                    singleLine = true,
                    isError = targetError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, target) },
                enabled = title.isNotBlank() && !targetError && target.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun templateForGoal(goal: UserGoalDefinition): GoalTemplateOption? =
    GoalTemplateOptions.firstOrNull { option ->
        option.metricType == goal.metricType && option.cardioFilter == goal.cardioFilter
    }
