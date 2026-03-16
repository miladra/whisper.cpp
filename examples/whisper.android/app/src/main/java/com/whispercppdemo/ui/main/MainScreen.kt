package com.whispercppdemo.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.whispercppdemo.R

@Composable
fun MainScreen(viewModel: MainScreenViewModel) {
    MainScreen(
        canTranscribe = viewModel.canTranscribe,
        isRecording = viewModel.isRecording,
        isStreaming = viewModel.isStreaming,
        models = viewModel.models,
        selectedModel = viewModel.selectedModel,
        messageLog = viewModel.dataLog,
        onRecordTapped = viewModel::toggleRecord,
        onStreamTapped = viewModel::toggleStream,
        onModelSelected = viewModel::onModelSelected
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    canTranscribe: Boolean,
    isRecording: Boolean,
    isStreaming: Boolean,
    models: List<String>,
    selectedModel: String,
    messageLog: String,
    onRecordTapped: () -> Unit,
    onStreamTapped: () -> Unit,
    onModelSelected: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            ModelSelector(
                enabled = !isRecording && !isStreaming,
                models = models,
                selectedModel = selectedModel,
                onModelSelected = onModelSelected
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RecordButton(
                    modifier = Modifier.weight(1f),
                    enabled = canTranscribe && !isStreaming,
                    isRecording = isRecording,
                    onClick = onRecordTapped
                )
                StreamButton(
                    modifier = Modifier.weight(1f),
                    enabled = canTranscribe && !isRecording,
                    isStreaming = isStreaming,
                    onClick = onStreamTapped
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            MessageLog(messageLog)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelector(
    enabled: Boolean,
    models: List<String>,
    selectedModel: String,
    onModelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = if (selectedModel.isEmpty()) "No model found" else selectedModel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Model") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            enabled = enabled
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onModelSelected(model)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MessageLog(log: String) {
    SelectionContainer {
        Text(modifier = Modifier.verticalScroll(rememberScrollState()), text = log)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RecordButton(modifier: Modifier = Modifier, enabled: Boolean, isRecording: Boolean, onClick: () -> Unit) {
    val micPermissionState = rememberPermissionState(
        permission = android.Manifest.permission.RECORD_AUDIO,
        onPermissionResult = { granted ->
            if (granted) {
                onClick()
            }
        }
    )
    Button(modifier = modifier, onClick = {
        if (micPermissionState.status.isGranted) {
            onClick()
        } else {
            micPermissionState.launchPermissionRequest()
        }
     }, enabled = enabled) {
        Text(
            if (isRecording) {
                "Stop recording"
            } else {
                "Start recording"
            }
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun StreamButton(modifier: Modifier = Modifier, enabled: Boolean, isStreaming: Boolean, onClick: () -> Unit) {
    val micPermissionState = rememberPermissionState(
        permission = android.Manifest.permission.RECORD_AUDIO,
        onPermissionResult = { granted ->
            if (granted) {
                onClick()
            }
        }
    )
    Button(modifier = modifier, onClick = {
        if (micPermissionState.status.isGranted) {
            onClick()
        } else {
            micPermissionState.launchPermissionRequest()
        }
    }, enabled = enabled) {
        Text(
            if (isStreaming) {
                "Stop stream"
            } else {
                "Start stream"
            }
        )
    }
}
