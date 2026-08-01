package com.scaso.drclawapp.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ExportScreen(
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Export & Backup",
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedButton(
            onClick = viewModel::exportConversations,
            enabled = !uiState.isExporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Export Conversations")
        }

        OutlinedButton(
            onClick = viewModel::exportBrain,
            enabled = !uiState.isExporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Export Brain")
        }

        Button(
            onClick = viewModel::createBackup,
            enabled = !uiState.isExporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create Backup")
        }

        if (uiState.isExporting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (uiState.lastResult != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Text(
                    text = uiState.lastResult!!,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
