package com.example.caninspector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.caninspector.AppViewModel
import com.example.caninspector.model.PalletSpec

/**
 * First screen: pallet cycle setup.
 *
 * Collects the four mandatory fields needed before inspection can start:
 * Pallet ID, Order Number, Starting Quantity, and Starting Layer (1-14).
 *
 * When continuing the same physical pallet after a "Complete Sorted
 * Pallet" event, Pallet ID / Order Number stay the same and Starting
 * Quantity / Starting Layer are pre-filled with the recalculated leftover
 * values — still editable in case a manual correction is needed.
 */
@Composable
fun PalletSetupScreen(
    viewModel: AppViewModel,
    onStartInspection: () -> Unit,
    onViewReport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Unsorted Pallet Setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Enter the details of the unsorted pallet before inspecting its layers. All fields are required.",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = viewModel.palletId,
            onValueChange = { viewModel.updatePalletId(it) },
            label = { Text("Pallet ID *") },
            isError = viewModel.palletId.isBlank(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = viewModel.orderNumber,
            onValueChange = { viewModel.updateOrderNumber(it) },
            label = { Text("Order Number *") },
            isError = viewModel.orderNumber.isBlank(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = viewModel.startingQuantityText,
            onValueChange = { viewModel.updateStartingQuantityText(it) },
            label = { Text("Starting Quantity *") },
            isError = viewModel.startingQuantityText.toIntOrNull()?.let { it <= 0 } ?: true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = viewModel.startingLayerText,
            onValueChange = { viewModel.updateStartingLayerText(it) },
            label = { Text("Starting Layer (1-${PalletSpec.LAYERS_PER_PALLET}) *") },
            isError = viewModel.startingLayerText.toIntOrNull()?.let { it !in 1..PalletSpec.LAYERS_PER_PALLET } ?: true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        if (!viewModel.isSetupValid()) {
            Text(
                "Please fill in all fields correctly (Starting Layer must be 1-${PalletSpec.LAYERS_PER_PALLET}).",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                viewModel.beginInspectionCycle()
                onStartInspection()
            },
            enabled = viewModel.isSetupValid(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue to Defect Table")
        }

        OutlinedButton(onClick = onViewReport, modifier = Modifier.fillMaxWidth()) {
            Text("View Sorted Pallets Report")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
