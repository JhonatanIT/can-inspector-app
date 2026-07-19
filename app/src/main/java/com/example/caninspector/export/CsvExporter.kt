package com.example.caninspector.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.caninspector.AppViewModel
import com.example.caninspector.model.DefectCatalog
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports the Sorted Pallets Report, plus the Defect Inspection Table data
 * behind every logged row, as two plain CSV files (no external library
 * needed, works on any Android version):
 *
 *  - "sorted_pallet_report_*.csv": one row per pallet-cycle event, grouped
 *    visually by sorted pallet number (matches the on-screen report).
 *  - "defect_inspection_details_*.csv": one row per layer per pallet-cycle
 *    event, with a column for every defect type plus that layer's total.
 */
object CsvExporter {

    private fun csvEscape(value: String): String =
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value

    private fun writeRow(writer: FileWriter, values: List<String>) {
        writer.append(values.joinToString(",") { csvEscape(it) }).append("\n")
    }

    fun export(context: Context, viewModel: AppViewModel): List<File>? {
        if (viewModel.palletCycleLog.isEmpty()) return null

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = System.currentTimeMillis()

        // ---- Sorted Pallet Report ----
        val reportFile = File(exportDir, "sorted_pallet_report_$timestamp.csv")
        FileWriter(reportFile).use { writer ->
            val grouped = viewModel.palletCycleLog.groupBy { it.sortedPalletNumber }.toSortedMap()
            grouped.forEach { (sortedPalletNumber, records) ->
                writer.append("Sorted Pallet $sortedPalletNumber\n")
                writeRow(
                    writer,
                    listOf(
                        "Date", "Pallet ID", "Order Number", "Starting Quantity",
                        "Rejected Cans", "Finish Quantity", "Cans Used to Complete Sorted Pallet"
                    )
                )
                records.sortedBy { it.timestampMillis }.forEach { r ->
                    writeRow(
                        writer,
                        listOf(
                            dateFormat.format(Date(r.timestampMillis)),
                            r.palletId,
                            r.orderNumber,
                            r.startingQuantity.toString(),
                            r.rejectedCans.toString(),
                            r.finishQuantity?.toString() ?: "",
                            r.cansUsedToCompleteSortedPallet?.toString() ?: ""
                        )
                    )
                }
                writer.append("\n")
            }
        }

        // ---- Defect Inspection Details ----
        val detailFile = File(exportDir, "defect_inspection_details_$timestamp.csv")
        FileWriter(detailFile).use { writer ->
            val headers = mutableListOf("Sorted Pallet #", "Date", "Pallet ID", "Order Number", "Event Type", "Layer")
            headers.addAll(DefectCatalog.defectNames)
            headers.add("Total")
            writeRow(writer, headers)

            viewModel.palletCycleLog.sortedBy { it.timestampMillis }.forEach { r ->
                val eventLabel = if (r.eventType == "COMPLETE") "Complete Sorted Pallet" else "Finish Unsorted Pallet"
                r.defectSnapshot.sortedByDescending { it.layerNumber }.forEach { layerSnap ->
                    val row = mutableListOf(
                        r.sortedPalletNumber.toString(),
                        dateFormat.format(Date(r.timestampMillis)),
                        r.palletId,
                        r.orderNumber,
                        eventLabel,
                        "Layer ${layerSnap.layerNumber}"
                    )
                    DefectCatalog.defectNames.forEach { name -> row.add((layerSnap.defectCounts[name] ?: 0).toString()) }
                    row.add(layerSnap.total.toString())
                    writeRow(writer, row)
                }
            }
        }

        return listOf(reportFile, detailFile)
    }

    fun shareFiles(context: Context, files: List<File>) {
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Sorted Pallet Report (CSV)"))
    }
}
