# Can Inspector

A Kotlin + Jetpack Compose Android app for can-sorting quality inspection.

## Features

1. **Pallet Setup screen** (`ui/PalletSetupScreen.kt`)
   - Mandatory fields: **Pallet ID**, **Order Number**, **Starting Quantity**,
     **Starting Layer (1-14)**. "Continue to Defect Table" stays disabled
     until all four are valid.
   - **"View Sorted Pallets Report"** button to jump to the report screen.

2. **Defect Inspection Table** (`ui/DefectTableScreen.kt`)
   - Shows layers from the current **Starting Layer down to 1**.
   - Inspection starts at the top row (Starting Layer) — every other row is
     locked (greyed out, not editable).
   - **Editing any column in the lowest-unlocked row unlocks the next row
     down.** E.g. starting at Layer 14: typing any number in Layer 14
     unlocks Layer 13; editing Layer 13 unlocks Layer 12; and so on.
   - Every cell (and the Complete Sorted Pallet pop-up) is restricted to
     **0-90** — a layer can never hold more than 90 cans. Typing above 90
     clamps back to 90 and flashes the cell's border red.
   - A **Grand Total** footer row sums every visible row's Total.
   - **"Finish Unsorted Pallet"** — enabled once Layer 1 is unlocked (the
     whole remaining pallet has been reached). Registers the pallet and
     resets Setup to a brand-new fresh pallet (1260 cans / Layer 14, blank
     ID and order).
   - **"Complete Sorted Pallet"** — only enabled once the OK cans still
     needed to reach 1260 are **90 or fewer** (i.e. achievable within the
     current layer); otherwise it's disabled with a hint showing how many
     cans are still needed. Opens a pop-up asking how many cans are
     **remaining (unused)** in the current layer — not how many were used.
     Recalculates the Starting Quantity / Starting Layer for the
     *continuation* of the same physical pallet and returns to Setup with
     those values pre-filled.

3. **Sorted Pallets Report** (`ui/SortedPalletReportScreen.kt`)
   - Every registered row grouped under the sorted-pallet number it belongs
     to, with columns: Date, Pallet ID, Order Number, Starting Quantity,
     Rejected Cans, Finish Quantity, Cans Used to Complete Sorted Pallet.
   - For a "Complete Sorted Pallet" row, **Finish Quantity holds the newly
     recalculated Starting Quantity** for the pallet's continuation (per
     the latest spec), not left blank.
   - Live card showing the pallet currently in progress and its OK-can
     progress toward 1260.
   - **"Export to CSV"** button — writes two CSV files (the report, grouped
     by sorted pallet; and the full per-layer defect breakdown behind every
     row) and opens the share sheet so you can save or send them.

4. **Session persistence** (`AppViewModel.kt`)
   - Every field change, cell edit, and Finish/Complete action is saved to
     `SharedPreferences` as JSON (via `org.json`, already built into
     Android — no extra dependency). `MainActivity.onStop()` also forces a
     flush as a safety net.
   - On next launch, the setup fields, the in-progress defect table, the
     unlock state, the running sorted-pallet progress, and the full report
     log are all restored automatically — closing and reopening the app
     resumes exactly where you left off.

## Business logic (`AppViewModel.kt`)

Physical facts: 1 unsorted pallet = 14 layers × 90 cans = 1260 cans. A
sorted pallet is complete once it holds 1260 OK cans, usually built up
across **multiple** unsorted pallets/cycles.

**Finish Unsorted Pallet** (all layers, Starting Layer down to 1, inspected):
```
rejectedCans = sum of defects entered across layers (StartingLayer .. 1)
finishQuantity = startingQuantity - rejectedCans
```
`finishQuantity` (the OK cans from this pallet) is added to the running
sorted-pallet total. If that pushes the total to ≥ 1260, the sorted pallet
is marked complete right there, with `cansUsedToCompleteSortedPallet =
finishQuantity - overflow`. Either way, this action always consumes the
whole physical pallet, so Setup resets to a brand-new one (1260 / Layer 14,
blank ID/order).

**Complete Sorted Pallet** (invoked mid-layer, at the current lowest-unlocked
layer `R`, with layers `StartingLayer .. R+1` already fully entered above it).
The pop-up now asks for **cans remaining (unused) in layer R**, not cans used:
```
capacity(layer) = 90                                      // any layer except the cycle's very first
capacity(StartingLayer) = startingQuantity - 90*(StartingLayer - 1)  // may be a partial layer

okAboveR   = sum(capacity(layer) - defects(layer)) for layer in (StartingLayer downTo R+1)
usedFromR  = capacity(R) - <remaining cans entered in the pop-up>
totalOk    = okAboveR + usedFromR

# added to the running sorted-pallet total; any excess over 1260 carries
# forward as the starting balance for the next sorted pallet
cansUsedToCompleteSortedPallet = totalOk - overflow

# the SAME physical pallet continues with its leftover cans:
newStartingQuantity = startingQuantity - sum(capacity(layer) for layer above R) - usedFromR
newStartingLayer = R
```
`newStartingQuantity` is also written into this row's **Finish Quantity**
column in the report. Pallet ID / Order Number stay the same for this
continuation. If `newStartingQuantity` comes out ≤ 0, the physical pallet is
fully consumed too, so Setup resets to a brand-new pallet instead.

**"Complete Sorted Pallet" button gating**: since a single layer holds at
most 90 cans, the button only enables once the OK cans still needed to
reach 1260 (`1260 - runningProgress - okAboveR`) is 90 or fewer — otherwise
more full layers must be finished first.

This matches the worked examples: a fresh pallet (1260 / Layer 14) with 10
rejected cans per layer, fully inspected via "Finish Unsorted Pallet", nets
`rejectedCans = 140`, `finishQuantity = 1120`. A second fresh pallet (1260 /
Layer 14) inspected through Layer 14 (10 rejected → 80 OK) and then closed
mid-Layer-13 via "Complete Sorted Pallet" with **30 remaining cans** entered
(i.e. 60 used) nets `rejectedCans = 10` (Layer 14 only),
`cansUsedToComplete = 140` (80 + 60), and recalculates the continuation to
Starting Quantity `1110`, Starting Layer `13` — with `1110` also shown in
that row's Finish Quantity column.

## Project structure

```
app/src/main/java/com/example/caninspector/
├── MainActivity.kt              # Nav host: Setup -> Defects -> Report; flushes state on stop
├── AppViewModel.kt               # All pallet-cycle state, business logic, and JSON persistence
├── model/Models.kt                # DefectCatalog, PalletSpec, PalletCycleRecord, LayerDefectSnapshot
├── export/CsvExporter.kt            # Writes + shares the two CSV files (no external library)
└── ui/
    ├── PalletSetupScreen.kt         # Mandatory Pallet ID / Order # / Qty / Layer
    ├── DefectTableScreen.kt          # Row-locking table, grand total, the two closing actions
    └── SortedPalletReportScreen.kt    # Grouped report + Export to CSV
```

## Requirements

- Android Studio Koala (2024.1) or newer
- Android SDK 34, minSdk 24
- Internet access on first Gradle sync (downloads Compose + Navigation
  Compose dependencies — no other third-party libraries are used)

## Setup

1. Open this folder in Android Studio ("Open an existing project").
2. Let Gradle sync.
3. Run on a device or emulator.

## Customizing

- **Defect columns**: `DefectCatalog.defectNames` in `model/Models.kt`.
- **Layers/pallet size**: `PalletSpec` in `model/Models.kt` (currently 14
  layers × 90 cans = 1260).
- **Row-unlock direction / rule**: `AppViewModel.setCellValue()`.
- **Cell/pop-up valid range (currently 0-90)**: search for
  `PalletSpec.CANS_PER_LAYER` in `AppViewModel.kt` and `DefectTableScreen.kt`.
