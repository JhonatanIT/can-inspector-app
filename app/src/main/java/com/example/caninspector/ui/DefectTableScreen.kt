package com.example.caninspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.caninspector.AppViewModel
import com.example.caninspector.model.PalletSpec

/**
 * Defect annotation table.
 *
 * Rows are the physical can layers, shown from [AppViewModel.startingLayer]
 * down to 1. Only layer [AppViewModel.unlockedFloor] up to
 * [AppViewModel.startingLayer] are editable; editing any column of the
 * lowest-unlocked row unlocks the next row down automatically. Every cell
 * is restricted to 0-90 (a layer never holds more than 90 cans).
 *
 * Two actions close out a cycle:
 *  - "Finish Unsorted Pallet" (enabled once layer 1 is reached): the whole
 *    remaining pallet has been inspected.
 *  - "Complete Sorted Pallet" (enabled only once at most one layer's worth
 *    of cans — 90 — is still needed to reach 1260): enter how many cans are
 *    LEFT OVER (unused) in the current layer.
 */
@Composable
fun DefectTableScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onCycleClosed: () -> Unit
) {
    val defectNames = viewModel.defectNames
    val cellWidth = 92.dp
    val layerColWidth = 90.dp
    val totalColWidth = 90.dp

    var showCompleteDialog by remember { mutableStateOf(false) }
    var completeDialogText by remember { mutableStateOf("") }

    val layers = (viewModel.startingLayer downTo 1).toList()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            "Defect Inspection Table",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Pallet ${viewModel.palletId} · Order ${viewModel.orderNumber} · Starting Qty ${viewModel.startingQuantity} · Starting Layer ${viewModel.startingLayer}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Editable layer: ${viewModel.unlockedFloor}  (layers ${viewModel.unlockedFloor}-${viewModel.startingLayer} unlocked)",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))

        val horizontalScroll = rememberScrollState()

        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.horizontalScroll(horizontalScroll)) {
                Column {
                    Row {
                        HeaderCell("Layer", layerColWidth)
                        defectNames.forEach { name -> HeaderCell(name, cellWidth) }
                        HeaderCell("Total", totalColWidth, highlight = true)
                    }

                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        layers.forEach { layer ->
                            val active = viewModel.isLayerActive(layer)
                            Row {
                                LayerLabelCell("Layer $layer", layerColWidth, active)
                                defectNames.indices.forEach { col ->
                                    EditableCell(
                                        width = cellWidth,
                                        value = viewModel.getCellValue(layer, col),
                                        enabled = active,
                                        onValueChange = { newValue ->
                                            viewModel.setCellValue(layer, col, newValue)
                                        }
                                    )
                                }
                                TotalCell(width = totalColWidth, value = viewModel.rowTotal(layer), active = active)
                            }
                        }

                        // Grand total footer: sum of every visible row's total.
                        Row {
                            LayerLabelCell("Grand Total", layerColWidth, active = true)
                            repeat(defectNames.size) {
                                Box(
                                    modifier = Modifier
                                        .width(cellWidth)
                                        .height(56.dp)
                                        .border(0.5.dp, Color.Gray)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                            TotalCell(width = totalColWidth, value = viewModel.grandTotal(), active = true)
                        }
                    }
                }
            }
        }

        HorizontalDivider(Modifier, thickness = 2.dp, color = DividerDefaults.color)

        Spacer(modifier = Modifier.height(8.dp))

        val canComplete = viewModel.canCompleteSortedPallet()
        if (!canComplete) {
            Text(
                "Still ${viewModel.remainingCansNeeded()} OK cans needed — more than one layer's worth (90). Keep inspecting layers to unlock \"Complete Sorted Pallet\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        Button(
            onClick = { showCompleteDialog = true },
            enabled = canComplete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Complete Sorted Pallet")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                viewModel.finishUnsortedPallet()
                onCycleClosed()
            },
            enabled = viewModel.canFinishUnsortedPallet(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Finish Unsorted Pallet")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Pallet Setup")
        }
    }

    if (showCompleteDialog) {
        val remainingEntered = completeDialogText.toIntOrNull()
        val isValid = remainingEntered != null && remainingEntered in 0..PalletSpec.CANS_PER_LAYER

        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text("Complete Sorted Pallet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This sorted pallet reaches 1260 OK cans partway through Layer ${viewModel.unlockedFloor}.")
                    Text("Enter how many cans are REMAINING (unused) in Layer ${viewModel.unlockedFloor} — a number between 0 and 90.")
                    OutlinedTextField(
                        value = completeDialogText,
                        onValueChange = { newText ->
                            val sanitized = newText.filter { it.isDigit() }
                            val intVal = sanitized.toIntOrNull()
                            completeDialogText = if (intVal != null && intVal > PalletSpec.CANS_PER_LAYER) {
                                PalletSpec.CANS_PER_LAYER.toString()
                            } else sanitized
                        },
                        label = { Text("Remaining cans in Layer ${viewModel.unlockedFloor} (0-90)") },
                        isError = completeDialogText.isNotEmpty() && !isValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (completeDialogText.isNotEmpty() && !isValid) {
                        Text(
                            "Please enter a number between 0 and 90.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.completeSortedPallet(remainingEntered ?: 0)
                        completeDialogText = ""
                        showCompleteDialog = false
                        onCycleClosed()
                    },
                    enabled = isValid
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    completeDialogText = ""
                    showCompleteDialog = false
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp, highlight: Boolean = false) {
    Box(
        modifier = Modifier
            .width(width)
            .height(56.dp)
            .border(0.5.dp, Color.Gray)
            .background(if (highlight) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun LayerLabelCell(text: String, width: androidx.compose.ui.unit.Dp, active: Boolean) {
    Box(
        modifier = Modifier
            .width(width)
            .height(56.dp)
            .border(0.5.dp, Color.Gray)
            .background(if (active) MaterialTheme.colorScheme.surfaceVariant else Color.LightGray.copy(alpha = 0.4f))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TotalCell(width: androidx.compose.ui.unit.Dp, value: Int, active: Boolean) {
    Box(
        modifier = Modifier
            .width(width)
            .height(56.dp)
            .border(0.5.dp, Color.Gray)
            .background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.LightGray.copy(alpha = 0.4f))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(if (active) "$value" else "-", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EditableCell(
    width: androidx.compose.ui.unit.Dp,
    value: Int?,
    enabled: Boolean,
    onValueChange: (Int?) -> Unit
) {
    var text by remember(value, enabled) { mutableStateOf(value?.toString() ?: "") }
    var showError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(width)
            .height(56.dp)
            .border(0.5.dp, if (showError) MaterialTheme.colorScheme.error else Color.Gray)
            .background(if (enabled) Color.Transparent else Color.LightGray.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        if (enabled) {
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = { newText ->
                    val sanitized = newText.filter { it.isDigit() }
                    val intVal = sanitized.toIntOrNull()
                    if (intVal != null && intVal > PalletSpec.CANS_PER_LAYER) {
                        // Clamp to 90 and briefly flag the error border.
                        text = PalletSpec.CANS_PER_LAYER.toString()
                        showError = true
                        onValueChange(PalletSpec.CANS_PER_LAYER)
                    } else {
                        text = sanitized
                        showError = false
                        onValueChange(sanitized.toIntOrNull())
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = androidx.compose.ui.text.TextStyle(
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                ),
                modifier = Modifier.fillMaxSize().padding(8.dp),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        if (text.isEmpty()) Text("0", color = Color.LightGray)
                        innerTextField()
                    }
                }
            )
        } else {
            Text("locked", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
    }
}
