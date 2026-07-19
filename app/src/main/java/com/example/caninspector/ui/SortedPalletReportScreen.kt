package com.example.caninspector.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.caninspector.AppViewModel
import com.example.caninspector.export.CsvExporter
import com.example.caninspector.model.PalletSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sorted Pallets Report.
 *
 * Every row logged by "Finish Unsorted Pallet" or "Complete Sorted Pallet"
 * is grouped under the sorted-pallet number it belongs to, matching:
 * Date | Pallet ID | Order Number | Starting Quantity | Rejected Cans |
 * Finish Quantity | Cans used to complete sorted pallet
 */
@Composable
fun SortedPalletReportScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val grouped = viewModel.palletCycleLog.groupBy { it.sortedPalletNumber }.toSortedMap()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text("Sorted Pallets Report", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Currently in progress", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Pallet ${viewModel.palletId.ifBlank { "-" }} · Order ${viewModel.orderNumber.ifBlank { "-" }}")
                Text("Starting Quantity: ${viewModel.startingQuantity} · Starting Layer: ${viewModel.startingLayer}")
                LinearProgressIndicator(
                    progress = { (viewModel.sortedPalletProgress.coerceAtMost(PalletSpec.CANS_PER_PALLET)) / PalletSpec.CANS_PER_PALLET.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                Text("${viewModel.sortedPalletProgress} / ${PalletSpec.CANS_PER_PALLET} OK cans toward Sorted Pallet ${viewModel.currentSortedPalletNumber}")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val files = CsvExporter.export(context, viewModel)
                if (files != null) {
                    CsvExporter.shareFiles(context, files)
                } else {
                    Toast.makeText(context, "Nothing to export yet.", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export to CSV")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (grouped.isEmpty()) {
                Text("No pallets registered yet.", style = MaterialTheme.typography.bodySmall)
            }

            grouped.forEach { (sortedPalletNumber, records) ->
                Text(
                    "Sorted Pallet $sortedPalletNumber",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Column {
                        Row {
                            ReportHeaderCell("Date", width = 130.dp)
                            ReportHeaderCell("Pallet ID")
                            ReportHeaderCell("Order #")
                            ReportHeaderCell("Starting\nQuantity", width = 100.dp)
                            ReportHeaderCell("Rejected\nCans", width = 100.dp)
                            ReportHeaderCell("Finish\nQuantity", width = 100.dp)
                            ReportHeaderCell("Cans Used to\nComplete Sorted Pallet", width = 150.dp)
                        }
                        records.sortedBy { it.timestampMillis }.forEach { r ->
                            Row {
                                ReportCell(dateFormat.format(Date(r.timestampMillis)), width = 130.dp)
                                ReportCell(r.palletId.ifBlank { "-" })
                                ReportCell(r.orderNumber.ifBlank { "-" })
                                ReportCell("${r.startingQuantity}", width = 100.dp)
                                ReportCell("${r.rejectedCans}", width = 100.dp)
                                ReportCell(r.finishQuantity?.toString() ?: "", width = 100.dp)
                                ReportCell(r.cansUsedToCompleteSortedPallet?.toString() ?: "", width = 150.dp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Pallet Setup")
        }
    }
}

@Composable
private fun ReportHeaderCell(text: String, width: androidx.compose.ui.unit.Dp = 90.dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(52.dp)
            .border(0.5.dp, Color.Gray)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ReportCell(text: String, width: androidx.compose.ui.unit.Dp = 90.dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(48.dp)
            .border(0.5.dp, Color.Gray)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
