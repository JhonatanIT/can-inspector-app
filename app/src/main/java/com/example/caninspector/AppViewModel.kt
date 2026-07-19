package com.example.caninspector

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import com.example.caninspector.model.DefectCatalog
import com.example.caninspector.model.LayerDefectSnapshot
import com.example.caninspector.model.PalletCycleRecord
import com.example.caninspector.model.PalletSpec
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Extends [AndroidViewModel] (rather than plain [ViewModel]) purely to get
 * access to an [Application] context for persisting state to
 * [android.content.SharedPreferences] — the whole point being that the
 * current pallet/inspection session survives the app being fully closed
 * and reopened, not just configuration changes.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    val defectNames: List<String> = DefectCatalog.defectNames
    val numLayers: Int = DefectCatalog.NUM_LAYERS

    // ---- Pallet setup fields (mandatory, entered on the first screen) ----
    var palletId by mutableStateOf("")
        private set
    var orderNumber by mutableStateOf("")
        private set
    var startingQuantityText by mutableStateOf(PalletSpec.CANS_PER_PALLET.toString())
        private set
    var startingLayerText by mutableStateOf(PalletSpec.LAYERS_PER_PALLET.toString())
        private set

    fun updatePalletId(v: String) { palletId = v; persistState() }
    fun updateOrderNumber(v: String) { orderNumber = v; persistState() }
    fun updateStartingQuantityText(v: String) { startingQuantityText = v.filter { it.isDigit() }; persistState() }
    fun updateStartingLayerText(v: String) { startingLayerText = v.filter { it.isDigit() }; persistState() }

    val startingQuantity: Int get() = startingQuantityText.toIntOrNull() ?: 0
    val startingLayer: Int get() = startingLayerText.toIntOrNull() ?: 0

    /** All four setup fields are mandatory before inspection can begin. */
    fun isSetupValid(): Boolean =
        palletId.isNotBlank() &&
            orderNumber.isNotBlank() &&
            (startingQuantityText.toIntOrNull()?.let { it > 0 } == true) &&
            (startingLayerText.toIntOrNull()?.let { it in 1..PalletSpec.LAYERS_PER_PALLET } == true)

    /**
     * Call when moving from the setup screen into the Defect Table for this
     * cycle. Only resets the table if the unlocked floor is out of sync
     * with the current Starting Layer (a genuinely new/changed cycle) —
     * otherwise it leaves everything as-is, so navigating back and forth,
     * or resuming after the app was fully closed and reopened, doesn't
     * wipe out cells the operator already entered.
     */
    fun beginInspectionCycle() {
        if (unlockedFloor > startingLayer || unlockedFloor < 1) {
            unlockedFloor = startingLayer
            defectCells.clear()
        }
        persistState()
    }

    // ---- Defect table state ----
    // Keyed by (layerNumber, defectColumnIndex) -> count entered by the user.
    val defectCells: SnapshotStateMap<Pair<Int, Int>, Int> = mutableStateMapOf()

    /** Lowest layer number currently unlocked for editing. Rows from this number up to
     * [startingLayer] are editable; rows below are still locked. */
    var unlockedFloor by mutableStateOf(PalletSpec.LAYERS_PER_PALLET)
        private set

    fun isLayerActive(layer: Int): Boolean = layer in unlockedFloor..startingLayer

    /** Every cell must be between 0 and 90 cans (a layer can never exceed 90 cans). */
    fun setCellValue(layer: Int, col: Int, value: Int?) {
        val clamped = value?.coerceIn(0, PalletSpec.CANS_PER_LAYER)
        val key = layer to col
        if (clamped == null) defectCells.remove(key) else defectCells[key] = clamped

        // Editing any column of the currently-lowest-unlocked row unlocks the next row down.
        if (layer == unlockedFloor && unlockedFloor > 1) {
            val rowHasAnyValue = defectNames.indices.any { c -> defectCells.containsKey(layer to c) }
            if (rowHasAnyValue) unlockedFloor = layer - 1
        }
        persistState()
    }

    fun getCellValue(layer: Int, col: Int): Int? = defectCells[layer to col]

    fun rowTotal(layer: Int): Int =
        defectNames.indices.sumOf { col -> defectCells[layer to col] ?: 0 }

    /** Sum of every visible row's total — shown as the table's grand-total footer. */
    fun grandTotal(): Int = (startingLayer downTo 1).sumOf { rowTotal(it) }

    /** Whether the whole remaining pallet (down to layer 1) has been reached and can be finished. */
    fun canFinishUnsortedPallet(): Boolean = unlockedFloor == 1

    /** Cans available in a given layer for the *current* cycle (the first layer of a
     * continuation cycle may hold fewer than 90 cans; every other layer holds 90). */
    private fun capacityOfLayer(layer: Int): Int =
        if (layer == startingLayer) startingQuantity - PalletSpec.CANS_PER_LAYER * (startingLayer - 1)
        else PalletSpec.CANS_PER_LAYER

    /** OK cans already accounted for in the fully-entered rows above the current active layer. */
    private fun okCansAboveCurrentLayer(): Int {
        val r = unlockedFloor
        val layersAbove = (startingLayer downTo (r + 1)).toList()
        val rejectedAbove = layersAbove.sumOf { rowTotal(it) }
        val capacitiesAbove = layersAbove.sumOf { capacityOfLayer(it) }
        return capacitiesAbove - rejectedAbove
    }

    /** How many more OK cans are still needed to reach 1260 for the sorted pallet in progress,
     * assuming everything already entered above the current layer is accounted for. */
    fun remainingCansNeeded(): Int =
        (PalletSpec.CANS_PER_PALLET - sortedPalletProgress - okCansAboveCurrentLayer()).coerceAtLeast(0)

    /** "Complete Sorted Pallet" only makes sense once the gap left to fill is at most one
     * layer's worth of cans (90) — otherwise more full layers must be inspected first. */
    fun canCompleteSortedPallet(): Boolean = remainingCansNeeded() in 0..PalletSpec.CANS_PER_LAYER

    private fun snapshotLayers(layers: List<Int>): List<LayerDefectSnapshot> =
        layers.map { layer ->
            val counts = defectNames.indices.associate { col ->
                defectNames[col] to (defectCells[layer to col] ?: 0)
            }
            LayerDefectSnapshot(layer, counts, rowTotal(layer))
        }

    // ---- Sorted pallet tracking ----
    val palletCycleLog = mutableStateListOf<PalletCycleRecord>()

    /** Running OK-can count accumulated toward the sorted pallet currently being filled. */
    var sortedPalletProgress by mutableStateOf(0)
        private set

    /** 1-based index of the sorted pallet currently being filled. */
    var currentSortedPalletNumber by mutableStateOf(1)
        private set

    /**
     * "Finish Unsorted Pallet" — the operator has inspected every remaining
     * layer of this physical pallet (down to layer 1) without hitting the
     * 1260 OK-can mark. Registers the whole remaining pallet, adds its OK
     * cans to the running sorted-pallet total, and resets the setup screen
     * for a brand-new fresh pallet (1260 cans, 14 layers, blank ID/order).
     */
    fun finishUnsortedPallet(): PalletCycleRecord {
        val layers = (startingLayer downTo 1).toList()
        val rejected = layers.sumOf { rowTotal(it) }
        val finishQty = startingQuantity - rejected
        val now = System.currentTimeMillis()

        sortedPalletProgress += finishQty
        var cansUsedToComplete: Int? = null
        if (sortedPalletProgress >= PalletSpec.CANS_PER_PALLET) {
            val overflow = sortedPalletProgress - PalletSpec.CANS_PER_PALLET
            cansUsedToComplete = finishQty - overflow
        }

        val record = PalletCycleRecord(
            id = UUID.randomUUID().toString(),
            timestampMillis = now,
            palletId = palletId,
            orderNumber = orderNumber,
            startingQuantity = startingQuantity,
            rejectedCans = rejected,
            finishQuantity = finishQty,
            cansUsedToCompleteSortedPallet = cansUsedToComplete,
            sortedPalletNumber = currentSortedPalletNumber,
            defectSnapshot = snapshotLayers(layers),
            eventType = "FINISH"
        )
        palletCycleLog.add(record)

        if (cansUsedToComplete != null) {
            sortedPalletProgress -= PalletSpec.CANS_PER_PALLET
            currentSortedPalletNumber += 1
        }

        // This physical pallet is fully consumed — reset to a brand-new fresh pallet.
        palletId = ""
        orderNumber = ""
        startingQuantityText = PalletSpec.CANS_PER_PALLET.toString()
        startingLayerText = PalletSpec.LAYERS_PER_PALLET.toString()
        defectCells.clear()
        unlockedFloor = PalletSpec.LAYERS_PER_PALLET

        persistState()
        return record
    }

    /**
     * "Complete Sorted Pallet" — invoked when, partway through the current
     * (lowest-unlocked) layer, the sorted pallet reaches 1260 OK cans.
     * [remainingCansInCurrentLayer] is how many cans are LEFT OVER
     * (un-used) in that layer — not how many were used. Cans used from
     * that layer are derived as (that layer's capacity - remaining).
     *
     * Registers this contribution, closes out the sorted pallet, and
     * recalculates the Starting Quantity / Starting Layer for the
     * *continuation* of this same physical pallet (its leftover cans).
     */
    fun completeSortedPallet(remainingCansInCurrentLayer: Int): PalletCycleRecord {
        val remaining = remainingCansInCurrentLayer.coerceIn(0, PalletSpec.CANS_PER_LAYER)
        val r = unlockedFloor
        val layersAbove = (startingLayer downTo (r + 1)).toList()
        val rejectedAbove = layersAbove.sumOf { rowTotal(it) }
        val capacitiesAbove = layersAbove.sumOf { capacityOfLayer(it) }
        val okAbove = capacitiesAbove - rejectedAbove

        val capacityOfR = capacityOfLayer(r)
        val usedFromR = (capacityOfR - remaining).coerceAtLeast(0)

        val totalOk = okAbove + usedFromR
        val now = System.currentTimeMillis()

        sortedPalletProgress += totalOk
        val overflow = (sortedPalletProgress - PalletSpec.CANS_PER_PALLET).coerceAtLeast(0)
        val cansUsedForThisSortedPallet = totalOk - overflow

        // Recalculate the continuation of this same physical pallet.
        val newStartingQuantity = (startingQuantity - capacitiesAbove - usedFromR).coerceAtLeast(0)

        val record = PalletCycleRecord(
            id = UUID.randomUUID().toString(),
            timestampMillis = now,
            palletId = palletId,
            orderNumber = orderNumber,
            startingQuantity = startingQuantity,
            rejectedCans = rejectedAbove,
            // Per spec: populate Finish Quantity with the newly recalculated
            // starting quantity for this Complete-Sorted-Pallet row too.
            finishQuantity = newStartingQuantity,
            cansUsedToCompleteSortedPallet = cansUsedForThisSortedPallet,
            sortedPalletNumber = currentSortedPalletNumber,
            defectSnapshot = snapshotLayers(layersAbove),
            eventType = "COMPLETE"
        )
        palletCycleLog.add(record)

        currentSortedPalletNumber += 1
        sortedPalletProgress = overflow

        defectCells.clear()

        if (newStartingQuantity <= 0) {
            // Nothing left in this physical pallet — start a brand-new one.
            palletId = ""
            orderNumber = ""
            startingQuantityText = PalletSpec.CANS_PER_PALLET.toString()
            startingLayerText = PalletSpec.LAYERS_PER_PALLET.toString()
            unlockedFloor = PalletSpec.LAYERS_PER_PALLET
        } else {
            startingQuantityText = newStartingQuantity.toString()
            startingLayerText = r.toString()
            unlockedFloor = r
            // palletId / orderNumber stay the same — same physical pallet continues.
        }

        persistState()
        return record
    }

    // ---- Persistence: survive the app being fully closed and reopened ----

    companion object {
        private const val PREFS_NAME = "can_inspector_prefs"
        private const val KEY_STATE = "state_json"
    }

    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    init {
        restoreState()
    }

    /** Public so the hosting Activity can force a synchronous-ish flush in onStop(). */
    fun persistState() {
        try {
            prefs.edit().putString(KEY_STATE, buildStateJson().toString()).apply()
        } catch (_: Exception) {
            // Persistence is best-effort; never crash the app over it.
        }
    }

    private fun restoreState() {
        val raw = prefs.getString(KEY_STATE, null) ?: return
        try {
            val json = JSONObject(raw)
            palletId = json.optString("palletId", "")
            orderNumber = json.optString("orderNumber", "")
            startingQuantityText = json.optString("startingQuantityText", PalletSpec.CANS_PER_PALLET.toString())
            startingLayerText = json.optString("startingLayerText", PalletSpec.LAYERS_PER_PALLET.toString())
            unlockedFloor = json.optInt("unlockedFloor", PalletSpec.LAYERS_PER_PALLET)
            sortedPalletProgress = json.optInt("sortedPalletProgress", 0)
            currentSortedPalletNumber = json.optInt("currentSortedPalletNumber", 1)

            defectCells.clear()
            json.optJSONArray("defectCells")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    defectCells[o.getInt("layer") to o.getInt("col")] = o.getInt("value")
                }
            }

            palletCycleLog.clear()
            json.optJSONArray("palletCycleLog")?.let { arr ->
                for (i in 0 until arr.length()) {
                    palletCycleLog.add(recordFromJson(arr.getJSONObject(i)))
                }
            }
        } catch (_: Exception) {
            // Corrupt or incompatible saved state — start fresh rather than crash.
        }
    }

    private fun buildStateJson(): JSONObject {
        val json = JSONObject()
        json.put("palletId", palletId)
        json.put("orderNumber", orderNumber)
        json.put("startingQuantityText", startingQuantityText)
        json.put("startingLayerText", startingLayerText)
        json.put("unlockedFloor", unlockedFloor)
        json.put("sortedPalletProgress", sortedPalletProgress)
        json.put("currentSortedPalletNumber", currentSortedPalletNumber)

        val cellsArr = JSONArray()
        defectCells.forEach { (key, value) ->
            val o = JSONObject()
            o.put("layer", key.first)
            o.put("col", key.second)
            o.put("value", value)
            cellsArr.put(o)
        }
        json.put("defectCells", cellsArr)

        val logArr = JSONArray()
        palletCycleLog.forEach { r -> logArr.put(recordToJson(r)) }
        json.put("palletCycleLog", logArr)

        return json
    }

    private fun recordToJson(r: PalletCycleRecord): JSONObject {
        val o = JSONObject()
        o.put("id", r.id)
        o.put("timestampMillis", r.timestampMillis)
        o.put("palletId", r.palletId)
        o.put("orderNumber", r.orderNumber)
        o.put("startingQuantity", r.startingQuantity)
        o.put("rejectedCans", r.rejectedCans)
        r.finishQuantity?.let { o.put("finishQuantity", it) }
        r.cansUsedToCompleteSortedPallet?.let { o.put("cansUsedToCompleteSortedPallet", it) }
        o.put("sortedPalletNumber", r.sortedPalletNumber)
        o.put("eventType", r.eventType)

        val snapArr = JSONArray()
        r.defectSnapshot.forEach { snap ->
            val so = JSONObject()
            so.put("layerNumber", snap.layerNumber)
            so.put("total", snap.total)
            val countsObj = JSONObject()
            snap.defectCounts.forEach { (name, count) -> countsObj.put(name, count) }
            so.put("defectCounts", countsObj)
            snapArr.put(so)
        }
        o.put("defectSnapshot", snapArr)
        return o
    }

    private fun recordFromJson(o: JSONObject): PalletCycleRecord {
        val snapshot = mutableListOf<LayerDefectSnapshot>()
        o.optJSONArray("defectSnapshot")?.let { arr ->
            for (i in 0 until arr.length()) {
                val so = arr.getJSONObject(i)
                val countsObj = so.optJSONObject("defectCounts")
                val counts = mutableMapOf<String, Int>()
                if (countsObj != null) {
                    val keysIter = countsObj.keys()
                    while (keysIter.hasNext()) {
                        val key = keysIter.next()
                        counts[key] = countsObj.getInt(key)
                    }
                }
                snapshot.add(LayerDefectSnapshot(so.getInt("layerNumber"), counts, so.getInt("total")))
            }
        }
        return PalletCycleRecord(
            id = o.optString("id", UUID.randomUUID().toString()),
            timestampMillis = o.optLong("timestampMillis", System.currentTimeMillis()),
            palletId = o.optString("palletId", ""),
            orderNumber = o.optString("orderNumber", ""),
            startingQuantity = o.optInt("startingQuantity", 0),
            rejectedCans = o.optInt("rejectedCans", 0),
            finishQuantity = if (o.has("finishQuantity")) o.optInt("finishQuantity") else null,
            cansUsedToCompleteSortedPallet = if (o.has("cansUsedToCompleteSortedPallet")) o.optInt("cansUsedToCompleteSortedPallet") else null,
            sortedPalletNumber = o.optInt("sortedPalletNumber", 1),
            defectSnapshot = snapshot,
            eventType = o.optString("eventType", "FINISH")
        )
    }
}
