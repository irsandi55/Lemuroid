package com.swordfish.lemuroid.app.mobile.feature.gamemenu.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.app.shared.game.RunningGameMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class MemoryRegion(
    val coreIndex: Int,
    val startAddress: Long,
    val length: Long,
    val label: String,
)

private data class MemoryHit(
    val offset: Int,
    val address: String,
    val value: String,
)

private enum class ValueWidth(val bytes: Int, val label: String) {
    BYTE(1, "8-bit"),
    HALF_WORD(2, "16-bit"),
    WORD(4, "32-bit"),
}

private enum class SearchMode(val label: String) {
    EXACT("Nilai tepat"),
    UNKNOWN("Nilai belum diketahui"),
    INCREASED("Meningkat"),
    DECREASED("Menurun"),
    UNCHANGED("Tidak berubah"),
    CHANGED("Berubah"),
}

@Composable
fun MemorySearchScreen(visible: Boolean, onMinimize: () -> Unit) {
    val view = RunningGameMemory.currentView()
    val scope = rememberCoroutineScope()

    var regions by remember(view) { mutableStateOf(emptyList<MemoryRegion>()) }
    var selectedRegion by remember(view) { mutableStateOf<MemoryRegion?>(null) }
    var loadingRegions by remember(view) { mutableStateOf(true) }
    var regionMenuExpanded by remember { mutableStateOf(false) }
    var widthMenuExpanded by remember { mutableStateOf(false) }
    var modeMenuExpanded by remember { mutableStateOf(false) }
    var width by remember { mutableStateOf(ValueWidth.BYTE) }
    var mode by remember { mutableStateOf(SearchMode.EXACT) }
    var inputValue by remember { mutableStateOf("") }
    var hasScanned by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf(IntArray(0)) }
    var previousSnapshot by remember { mutableStateOf<ByteArray?>(null) }
    var currentSnapshot by remember { mutableStateOf<ByteArray?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Pilih jenis pencarian, lalu mulai scan.") }

    LaunchedEffect(view) {
        loadingRegions = true
        regions = emptyList()
        selectedRegion = null
        if (view != null) {
            val packed = withContext(Dispatchers.IO) {
                runCatching { view.getMemoryRegions(useEmulationThread = false) }.getOrDefault(longArrayOf())
            }
            val foundRegions = mutableListOf<MemoryRegion>()
            for (index in 0 until packed.size / 3) {
                val start = packed[index * 3]
                val length = packed[index * 3 + 1]
                val label = when (start) {
                    0x02000000L -> "EWRAM (RAM kerja)"
                    0x03000000L -> "IWRAM (RAM internal)"
                    0x0E000000L -> "Save RAM"
                    else -> null
                }
                if (label != null && length > 0 && length <= Int.MAX_VALUE) {
                    foundRegions.add(MemoryRegion(index, start, length, label))
                }
            }
            regions = foundRegions
            selectedRegion = foundRegions.firstOrNull()
        }
        loadingRegions = false
    }

    val parsedValue = parseSearchValue(inputValue)
    val maxValue = maxValueFor(width)
    val valueIsValid = parsedValue != null && parsedValue in 0..maxValue
    val availableModes =
        if (hasScanned) {
            listOf(SearchMode.EXACT, SearchMode.INCREASED, SearchMode.DECREASED, SearchMode.UNCHANGED, SearchMode.CHANGED)
        } else {
            listOf(SearchMode.EXACT, SearchMode.UNKNOWN)
        }

    val visibleHits =
        remember(candidates, currentSnapshot, selectedRegion, width) {
            val snapshot = currentSnapshot
            val region = selectedRegion
            if (snapshot == null || region == null) {
                emptyList()
            } else {
                candidates.take(100).map { offset ->
                    val value = readUnsigned(snapshot, offset, width.bytes)
                    MemoryHit(
                        offset = offset,
                        address = String.format(Locale.ROOT, "0x%08X", region.startAddress + offset),
                        value = String.format(Locale.ROOT, "0x%0" + (width.bytes * 2) + "X  (%d)", value, value),
                    )
                }
            }
        }

    if (!visible) return

    Box(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.86f)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Pencarian Memori", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onMinimize) { Text("Sembunyikan") }
                }
                Text(
                    "Cari nilai di RAM GBA. Sembunyikan panel untuk bermain, lalu buka lagi untuk menyaring hasil scan.",
                    style = MaterialTheme.typography.bodySmall,
                )

        when {
            view == null -> {
                Text("Sesi game aktif tidak ditemukan. Tutup menu dan buka kembali dari game yang sedang berjalan.")
            }
            loadingRegions -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("Membaca peta memori...")
                }
            }
            regions.isEmpty() -> {
                Text("Core ini tidak menyediakan peta RAM yang bisa dibaca. Untuk game GBA, gunakan core mGBA.")
            }
            else -> {
                Box {
                    OutlinedButton(onClick = { regionMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedRegion?.let { it.label + "  (" + it.length + " byte)" } ?: "Pilih region memori")
                    }
                    DropdownMenu(expanded = regionMenuExpanded, onDismissRequest = { regionMenuExpanded = false }) {
                        regions.forEach { region ->
                            DropdownMenuItem(
                                text = { Text(region.label + "  (" + region.length + " byte)") },
                                onClick = {
                                    selectedRegion = region
                                    regionMenuExpanded = false
                                    candidates = IntArray(0)
                                    previousSnapshot = null
                                    currentSnapshot = null
                                    hasScanned = false
                                    message = "Region berubah. Mulai scan baru."
                                },
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { widthMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(width.label)
                        }
                        DropdownMenu(expanded = widthMenuExpanded, onDismissRequest = { widthMenuExpanded = false }) {
                            ValueWidth.values().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        width = option
                                        widthMenuExpanded = false
                                        candidates = IntArray(0)
                                        previousSnapshot = null
                                        currentSnapshot = null
                                        hasScanned = false
                                        mode = SearchMode.EXACT
                                        message = "Ukuran nilai berubah. Mulai scan baru."
                                    },
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { modeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(mode.label)
                        }
                        DropdownMenu(expanded = modeMenuExpanded, onDismissRequest = { modeMenuExpanded = false }) {
                            availableModes.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        mode = option
                                        modeMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                if (mode == SearchMode.EXACT) {
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nilai (desimal atau 0x heksadesimal)") },
                        singleLine = true,
                        isError = inputValue.isNotBlank() && !valueIsValid,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !scanning && selectedRegion != null && (mode != SearchMode.EXACT || valueIsValid),
                        onClick = {
                            val activeView = view
                            val region = selectedRegion
                            val target = if (mode == SearchMode.EXACT) parsedValue else null
                            if (activeView != null && region != null) {
                                scope.launch {
                                    scanning = true
                                    try {
                                        val snapshot = withContext(Dispatchers.IO) {
                                            activeView.readMemoryRegion(
                                                region.coreIndex,
                                                0,
                                                region.length,
                                                useEmulationThread = false,
                                            )
                                        }
                                        if (snapshot.size.toLong() != region.length) {
                                            throw IllegalStateException("Tidak dapat membaca region memori.")
                                        }

                                        val result = withContext(Dispatchers.Default) {
                                            if (!hasScanned) {
                                                initialScan(snapshot, width.bytes, mode, target)
                                            } else {
                                                val previous = previousSnapshot
                                                    ?: throw IllegalStateException("Data scan sebelumnya tidak tersedia.")
                                                refineScan(candidates, previous, snapshot, width.bytes, mode, target)
                                            }
                                        }

                                        candidates = result
                                        previousSnapshot = snapshot
                                        currentSnapshot = snapshot
                                        hasScanned = true
                                        if (mode == SearchMode.UNKNOWN) mode = SearchMode.UNCHANGED
                                        message = "Scan selesai. Ubah nilai di dalam game, lalu saring hasil."
                                    } catch (error: Exception) {
                                        message = error.message ?: "Scan gagal."
                                    } finally {
                                        scanning = false
                                    }
                                }
                            }
                        },
                    ) {
                        Text(if (scanning) "Memindai..." else if (hasScanned) "Saring hasil" else "Scan pertama")
                    }

                    TextButton(
                        enabled = !scanning,
                        onClick = {
                            candidates = IntArray(0)
                            previousSnapshot = null
                            currentSnapshot = null
                            hasScanned = false
                            mode = SearchMode.EXACT
                            inputValue = ""
                            message = "Scan direset."
                        },
                    ) {
                        Text("Reset")
                    }
                }

                Text(message, style = MaterialTheme.typography.bodySmall)
                if (hasScanned) {
                    Text("Hasil ditemukan: " + candidates.size + "  •  Menampilkan maksimal 100")
                    Divider()
                    if (visibleHits.isEmpty()) {
                        Text("Tidak ada hasil yang cocok. Coba mode lain atau mulai scan baru.")
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(visibleHits, key = { it.offset }) { hit ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(hit.address, style = MaterialTheme.typography.bodyMedium)
                                        Text(hit.value, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Area yang dipindai: EWRAM, IWRAM, dan Save RAM. ROM, BIOS, dan register perangkat tidak dipindai.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            }
        }
    }
}
}

private fun parseSearchValue(input: String): Long? {
    val value = input.trim()
    if (value.isEmpty()) return null
    return runCatching {
        if (value.startsWith("0x", ignoreCase = true)) value.substring(2).toLong(16) else value.toLong()
    }.getOrNull()
}

private fun maxValueFor(width: ValueWidth): Long {
    return when (width) {
        ValueWidth.BYTE -> 0xFFL
        ValueWidth.HALF_WORD -> 0xFFFFL
        ValueWidth.WORD -> 0xFFFFFFFFL
    }
}

private fun readUnsigned(data: ByteArray, offset: Int, bytes: Int): Long {
    var value = 0L
    for (index in 0 until bytes) {
        value = value or ((data[offset + index].toLong() and 0xFFL) shl (index * 8))
    }
    return value
}

private fun initialScan(
    snapshot: ByteArray,
    bytes: Int,
    mode: SearchMode,
    target: Long?,
): IntArray {
    val lastOffset = snapshot.size - bytes
    if (lastOffset < 0) return IntArray(0)

    val matches = IntArray((lastOffset / bytes) + 1)
    var count = 0
    var offset = 0
    while (offset <= lastOffset) {
        val value = readUnsigned(snapshot, offset, bytes)
        if (mode == SearchMode.UNKNOWN || (mode == SearchMode.EXACT && value == target)) {
            matches[count++] = offset
        }
        offset += bytes
    }
    return matches.copyOf(count)
}

private fun refineScan(
    offsets: IntArray,
    previous: ByteArray,
    current: ByteArray,
    bytes: Int,
    mode: SearchMode,
    target: Long?,
): IntArray {
    val matches = IntArray(offsets.size)
    var count = 0
    for (offset in offsets) {
        if (offset < 0 || offset + bytes > current.size || offset + bytes > previous.size) continue
        val oldValue = readUnsigned(previous, offset, bytes)
        val newValue = readUnsigned(current, offset, bytes)
        val keep = when (mode) {
            SearchMode.EXACT -> newValue == target
            SearchMode.INCREASED -> newValue > oldValue
            SearchMode.DECREASED -> newValue < oldValue
            SearchMode.UNCHANGED -> newValue == oldValue
            SearchMode.CHANGED -> newValue != oldValue
            SearchMode.UNKNOWN -> false
        }
        if (keep) matches[count++] = offset
    }
    return matches.copyOf(count)
}
