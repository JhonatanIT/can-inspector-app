package com.example.caninspector.model

/** The fixed list of defect types tracked in the inspection table. */
object DefectCatalog {
    val defectNames: List<String> = listOf(
        "External Scratches",
        "Dents",
        "Internal Scratches",
        "Printing",
        "Missing",
        "BM"
    )

    const val NUM_LAYERS: Int = 14
}

/**
 * Fixed physical dimensions of a pallet.
 * 1 unsorted pallet = 14 layers x 90 cans = 1260 cans, and a sorted
 * (finished) pallet is complete once it holds 1260 OK cans.
 */
object PalletSpec {
    const val CANS_PER_LAYER: Int = 90
    const val LAYERS_PER_PALLET: Int = DefectCatalog.NUM_LAYERS
    const val CANS_PER_PALLET: Int = CANS_PER_LAYER * LAYERS_PER_PALLET // 1260
}

/** A snapshot of one layer's defect counts, taken at the moment a cycle event is recorded. */
data class LayerDefectSnapshot(
    val layerNumber: Int,
    val defectCounts: Map<String, Int>,
    val total: Int
)

/**
 * One row in the Sorted Pallet Report.
 *
 * Produced either by:
 *  - "Finish Unsorted Pallet": the whole remaining physical pallet was
 *    inspected down to layer 1. [finishQuantity] is populated,
 *    [cansUsedToCompleteSortedPallet] is null unless this action also
 *    happened to complete the current sorted pallet.
 *  - "Complete Sorted Pallet": the sorted pallet (1260 OK cans) was reached
 *    partway through a layer. [finishQuantity] is null (the physical
 *    pallet still has cans left), [cansUsedToCompleteSortedPallet] is
 *    populated with the OK cans consumed to close out the sorted pallet.
 */
data class PalletCycleRecord(
    val id: String,
    val timestampMillis: Long,
    val palletId: String,
    val orderNumber: String,
    val startingQuantity: Int,
    val rejectedCans: Int,
    val finishQuantity: Int?,
    val cansUsedToCompleteSortedPallet: Int?,
    val sortedPalletNumber: Int,
    val defectSnapshot: List<LayerDefectSnapshot>,
    // "FINISH" (Finish Unsorted Pallet) or "COMPLETE" (Complete Sorted Pallet).
    // Needed because, per the latest spec, finishQuantity is populated for
    // both event types (for COMPLETE it holds the recalculated leftover
    // starting quantity), so it can no longer be used to infer which
    // action produced the row.
    val eventType: String
)
