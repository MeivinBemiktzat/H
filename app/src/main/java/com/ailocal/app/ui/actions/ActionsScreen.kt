package com.ailocal.app.ui.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ailocal.app.R
import com.ailocal.app.tools.ActionCategory
import com.ailocal.app.tools.ActionDefinition
import com.ailocal.app.tools.ActionRegistry
import com.ailocal.app.tools.RiskLevel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsScreen() {
    val grouped = ActionCategory.values().associateWith { ActionRegistry.byCategory(it) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.actions_title)) })

        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            grouped.forEach { (category, actions) ->
                if (actions.isNotEmpty()) {
                    item {
                        Text(
                            categoryLabel(category),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(actions) { action ->
                        ActionRow(action)
                    }
                }
            }
        }
    }
}

@Composable
private fun categoryLabel(category: ActionCategory): String = when (category) {
    ActionCategory.APPS -> stringResource(R.string.actions_apps_category)
    ActionCategory.SYSTEM -> stringResource(R.string.actions_system_category)
    ActionCategory.MEDIA -> stringResource(R.string.actions_media_category)
    ActionCategory.TIME -> stringResource(R.string.actions_time_category)
    ActionCategory.DEVICE -> stringResource(R.string.actions_device_category)
    ActionCategory.FILES -> stringResource(R.string.actions_files_category)
}

@Composable
private fun ActionRow(action: ActionDefinition) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(action.displayNameHe, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (action.risk == RiskLevel.CONFIRM) "דורש אישור" else "ללא צורך באישור",
                style = MaterialTheme.typography.labelMedium,
                color = if (action.risk == RiskLevel.CONFIRM)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
