# Web App to Android App - Feature Mapping
## Direct Translation Guide from Existing Web App Code

This document maps every feature in the current web application to its Android equivalent, helping developers understand what to port and how.

---

## Overview

The existing web app is **fully functional** and demonstrates:
- ✅ Successful BLE protocol reverse-engineering
- ✅ Complete workout control
- ✅ Real-time monitoring
- ✅ Data visualization
- ✅ History tracking

**Your job:** Translate proven JavaScript code to Kotlin/Android while maintaining identical behavior.

---

## File-by-File Translation Guide

### 1. device.js → Android BLE Layer

| Web App (device.js) | Android Equivalent | Complexity | Priority |
|---------------------|-------------------|------------|----------|
| `class VitruvianDevice` | `VitruvianBleManager.kt` | Medium | P0 |
| `navigator.bluetooth.requestDevice()` | `BluetoothAdapter.startDiscovery()` + Nordic library | Medium | P0 |
| `device.gatt.connect()` | `BleManager.connect(device)` | Easy | P0 |
| `characteristic.writeValueWithResponse()` | `writeCharacteristic(data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)` | Easy | P0 |
| `characteristic.startNotifications()` | `setNotificationCallback(characteristic)` then `enableNotifications()` | Easy | P0 |
| `gattQueue` system | `GattCommandQueue.kt` with `Channel<T>` | Medium | P0 |
| Device name filter `"Vee"` | `ScanFilter.Builder().setDeviceNamePrefix("Vee")` | Easy | P0 |

**Key UUIDs to preserve (CRITICAL - must be exact):**
```javascript
// JavaScript
const NUS_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
const NUS_RX_CHAR_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
// ... etc

// Kotlin
val NUS_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
val NUS_RX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
// ... etc
```

**Connection Flow (identical in both):**
1. Scan for devices with name prefix "Vee"
2. Connect to GATT server
3. Discover services
4. Find NUS service
5. Get all characteristics
6. Enable notifications on 7 specific characteristics
7. Ready for commands

**Code Comparison:**

**Web (device.js):**
```javascript
async connect() {
  this.device = await navigator.bluetooth.requestDevice({
    filters: [{ namePrefix: "Vee" }],
    optionalServices: [NUS_SERVICE_UUID, GATT_SERVICE_UUID],
  });
  
  this.server = await this.device.gatt.connect();
  const nusService = await this.server.getPrimaryService(NUS_SERVICE_UUID);
  const characteristics = await nusService.getCharacteristics();
  
  for (const char of characteristics) {
    if (char.uuid === NUS_RX_CHAR_UUID) {
      this.rxChar = char;
    }
    // ... etc
  }
}
```

**Android (VitruvianBleManager.kt):**
```kotlin
suspend fun connect(device: BluetoothDevice): Flow<ConnectionState> = flow {
    emit(ConnectionState.Connecting)
    
    bleManager.connect(device)
        .timeout(15000)
        .useAutoConnect(false)
        .retry(3, 100)
        .await()
    
    val services = bleManager.discoverServices().await()
    val nusService = services.find { it.uuid == BleConstants.NUS_SERVICE_UUID }
        ?: throw Exception("NUS service not found")
    
    rxCharacteristic = nusService.characteristics.find { 
        it.uuid == BleConstants.NUS_RX_CHAR_UUID 
    } ?: throw Exception("RX characteristic not found")
    
    emit(ConnectionState.Connected(device.name ?: "Unknown"))
}
```

---

### 2. protocol.js → ProtocolBuilder.kt

| Function | Complexity | Notes |
|----------|------------|-------|
| `buildInitCommand()` | Trivial | Direct byte array port |
| `buildInitPreset()` | Trivial | Direct byte array port |
| `buildProgramParams()` | Medium | Requires ByteBuffer for floats |
| `buildEchoControl()` | Medium | Similar to program params |
| `buildColorScheme()` | Easy | RGB byte packing |
| `bytesToHex()` | Trivial | Use Kotlin extension |

**Critical: Maintain byte-perfect compatibility!**

**Example: buildInitCommand()**

**Web:**
```javascript
function buildInitCommand() {
  return new Uint8Array([0x0a, 0x00, 0x00, 0x00]);
}
```

**Android:**
```kotlin
fun buildInitCommand(): ByteArray {
    return byteArrayOf(0x0A, 0x00, 0x00, 0x00)
}
```

**Example: buildProgramParams() - Float handling**

**Web:**
```javascript
function buildProgramParams(params) {
  const frame = new Uint8Array(96);
  const view = new DataView(frame.buffer);
  
  view.setFloat32(0x54, params.effectiveKg, true); // Little endian
  view.setFloat32(0x58, params.perCableKg, true);
  // ...
  
  return frame;
}
```

**Android:**
```kotlin
fun buildProgramParams(params: ProgramParams): ByteArray {
    val buffer = ByteBuffer.allocate(96).order(ByteOrder.LITTLE_ENDIAN)
    
    // ... earlier bytes
    
    buffer.position(0x54)
    buffer.putFloat(params.effectiveKg)
    buffer.position(0x58)
    buffer.putFloat(params.perCableKg)
    
    return buffer.array()
}
```

**Testing Strategy:**
```kotlin
@Test
fun `protocol frames match web app exactly`() {
    // Use known good values from web app
    val params = ProgramParams(
        mode = WorkoutMode.OLD_SCHOOL,
        reps = 10,
        effectiveKg = 20.0f,
        perCableKg = 10.0f,
        progressionKg = 0.5f,
        isJustLift = false
    )
    
    val result = ProtocolBuilder.buildProgramParams(params)
    
    // Compare with captured web app output (from browser console)
    val expected = byteArrayOf(
        0x04, 0x00, 0x00, 0x00, 0x0D, // reps = 10+3 = 13 (0x0D)
        // ... rest of bytes
    )
    
    assertThat(result).isEqualTo(expected)
}
```

---

### 3. modes.js → WorkoutMode.kt + ModeProfile.kt

**Web structure:**
```javascript
const ProgramMode = {
  OLD_SCHOOL: 0,
  PUMP: 1,
  TUT: 2,
  // ...
};

const EchoLevel = {
  HARD: 0,
  HARDER: 1,
  // ...
};

function getModeProfile(mode) {
  const buffer = new ArrayBuffer(32);
  // Fill buffer based on mode
  return new Uint8Array(buffer);
}
```

**Android equivalent:**
```kotlin
enum class ProgramMode(val value: Int) {
    OLD_SCHOOL(0),
    PUMP(1),
    TUT(2),
    TUT_BEAST(3),
    ECCENTRIC_ONLY(4)
}

enum class EchoLevel(val value: Int) {
    HARD(0),
    HARDER(1),
    HARDEST(2),
    EPIC(3)
}

data class EchoParams(
    val level: EchoLevel,
    val eccentricPct: Int,
    val concentricPct: Int = 50,
    val smoothing: Float = 0.1f,
    val floor: Float = 0.0f,
    val negLimit: Float = -100.0f,
    val gain: Float,
    val cap: Float
)

object ModeProfileBuilder {
    fun getModeProfile(mode: ProgramMode): ByteArray {
        val buffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        
        when (mode) {
            ProgramMode.OLD_SCHOOL -> {
                buffer.putShort(0)          // 0x00
                buffer.putShort(20)         // 0x02
                buffer.putFloat(3.0f)       // 0x04
                buffer.putShort(75)         // 0x08
                buffer.putShort(600)        // 0x0A
                buffer.putFloat(50.0f)      // 0x0C
                buffer.putShort(-1300)      // 0x10
                buffer.putShort(-1200)      // 0x12
                buffer.putFloat(100.0f)     // 0x14
            }
            // ... other modes
        }
        
        return buffer.array()
    }
}
```

---

### 4. app.js → Multiple ViewModels + Use Cases

**app.js is ~1500 lines - needs to be split into:**

| Responsibility | Web (app.js) | Android Equivalent |
|----------------|--------------|-------------------|
| Connection management | Lines 1-100 | `ConnectionViewModel.kt` |
| Workout control | Lines 100-400 | `WorkoutControlViewModel.kt` |
| Live monitoring | Lines 400-700 | `LiveMetricsViewModel.kt` |
| Rep counting | Lines 700-1000 | `RepDetectionEngine.kt` + `MonitorWorkoutUseCase.kt` |
| Unit conversion | Lines 1000-1200 | `UnitConverter.kt` utility |
| Workout history | Lines 1200-1400 | `WorkoutRepository.kt` + `HistoryViewModel.kt` |
| Chart integration | Lines 1400-1500 | `ChartManager.kt` |

**Example: Rep Detection Logic**

**Web (app.js):**
```javascript
class VitruvianApp {
  updateLiveStats(sample) {
    this.currentSample = sample;
    
    // Auto-adjust max position
    const currentMax = Math.max(sample.posA, sample.posB);
    if (currentMax > this.maxPos) {
      this.maxPos = currentMax + 100;
    }
    
    // Check auto-stop for Just Lift mode
    if (this.isJustLiftMode) {
      this.checkAutoStop(sample);
    }
  }
  
  checkAutoStop(sample) {
    // Auto-stop logic when cables at rest
    const threshold = 50; // Low position = at rest
    if (sample.posA < threshold && sample.posB < threshold) {
      if (!this.autoStopStartTime) {
        this.autoStopStartTime = Date.now();
      } else if (Date.now() - this.autoStopStartTime > 3000) {
        this.stopWorkout();
      }
    } else {
      this.autoStopStartTime = null;
    }
  }
}
```

**Android (RepDetectionEngine.kt):**
```kotlin
class RepDetectionEngine {
    private var maxPos = 1000
    private var autoStopStartTime: Long? = null
    
    fun processMetric(sample: WorkoutMetric): RepEvent? {
        // Auto-adjust max position
        val currentMax = max(sample.posA, sample.posB)
        if (currentMax > maxPos) {
            maxPos = currentMax + 100
        }
        
        // Rep detection logic here
        return detectRepTransition(sample)
    }
    
    fun checkAutoStop(sample: WorkoutMetric, isJustLiftMode: Boolean): Boolean {
        if (!isJustLiftMode) return false
        
        val threshold = 50
        if (sample.posA < threshold && sample.posB < threshold) {
            val startTime = autoStopStartTime
            if (startTime == null) {
                autoStopStartTime = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - startTime > 3000) {
                return true // Should auto-stop
            }
        } else {
            autoStopStartTime = null
        }
        
        return false
    }
}
```

**Integration in ViewModel:**
```kotlin
@HiltViewModel
class LiveMetricsViewModel @Inject constructor(
    private val monitorWorkoutUseCase: MonitorWorkoutUseCase,
    private val repDetectionEngine: RepDetectionEngine
) : ViewModel() {
    
    private val _metrics = MutableStateFlow<WorkoutMetric?>(null)
    val metrics: StateFlow<WorkoutMetric?> = _metrics.asStateFlow()
    
    private val _shouldAutoStop = MutableStateFlow(false)
    val shouldAutoStop: StateFlow<Boolean> = _shouldAutoStop.asStateFlow()
    
    fun startMonitoring(isJustLiftMode: Boolean) {
        viewModelScope.launch {
            monitorWorkoutUseCase().collect { metric ->
                _metrics.value = metric
                
                // Check auto-stop
                if (repDetectionEngine.checkAutoStop(metric, isJustLiftMode)) {
                    _shouldAutoStop.value = true
                }
                
                // Detect reps
                repDetectionEngine.processMetric(metric)?.let { repEvent ->
                    handleRepEvent(repEvent)
                }
            }
        }
    }
}
```

---

### 5. chart.js → ChartManager.kt + Composable

**Web (chart.js) uses uPlot:**
```javascript
class ChartManager {
  constructor(containerId) {
    this.chart = null;
    this.loadHistory = [];
    this.maxHistoryPoints = 72000;
  }
  
  addData(sample) {
    const timestamp = Date.now() / 1000;
    this.loadHistory.push({
      time: timestamp,
      totalLoad: sample.loadA + sample.loadB,
      loadA: sample.loadA,
      loadB: sample.loadB,
      posA: sample.posA,
      posB: sample.posB
    });
    
    this.updateChart();
  }
  
  setTimeRange(seconds) {
    this.currentTimeRange = seconds;
    this.updateChart();
  }
}
```

**Android (ChartManager.kt) with MPAndroidChart:**
```kotlin
class ChartManager(
    private val lineChart: LineChart,
    private val maxPoints: Int = 72000
) {
    private val loadHistory = mutableListOf<ChartDataPoint>()
    private var currentTimeRange = 30 // seconds
    
    fun addData(sample: WorkoutMetric) {
        val timestamp = System.currentTimeMillis() / 1000.0
        
        loadHistory.add(ChartDataPoint(
            time = timestamp,
            totalLoad = sample.loadA + sample.loadB,
            loadA = sample.loadA,
            loadB = sample.loadB,
            posA = sample.posA,
            posB = sample.posB
        ))
        
        // Trim old data
        if (loadHistory.size > maxPoints) {
            loadHistory.removeAt(0)
        }
        
        updateChart()
    }
    
    fun setTimeRange(seconds: Int) {
        currentTimeRange = seconds
        updateChart()
    }
    
    private fun updateChart() {
        val now = System.currentTimeMillis() / 1000.0
        val cutoff = now - currentTimeRange
        
        val filteredData = loadHistory.filter { it.time >= cutoff }
        
        val entries = filteredData.map { 
            Entry(it.time.toFloat(), it.totalLoad) 
        }
        
        val dataSet = LineDataSet(entries, "Total Load")
        lineChart.data = LineData(dataSet)
        lineChart.invalidate() // Refresh
    }
}
```

**Compose Integration:**
```kotlin
@Composable
fun WorkoutChart(
    data: List<WorkoutMetric>,
    timeRange: Int,
    modifier: Modifier = Modifier
) {
    val chartManager = remember { ChartManager(/* ... */) }
    
    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                // Configure chart appearance
                description.isEnabled = false
                setTouchEnabled(true)
                setPinchZoom(true)
                // ... more configuration
            }
        },
        update = { chart ->
            // Update with latest data
            data.forEach { chartManager.addData(it) }
            chartManager.setTimeRange(timeRange)
        },
        modifier = modifier
    )
}
```

---

### 6. index.html → Jetpack Compose UI

**Web HTML structure:**
```html
<div class="sidebar">
  <div class="sidebar-header">
    <h1>Vitruvian Control</h1>
  </div>
  
  <div class="section">
    <h2>Connection</h2>
    <div id="status" class="status disconnected">Disconnected</div>
    <button id="connectBtn">Connect to Device</button>
  </div>
  
  <div class="section">
    <h2>Workout Parameters</h2>
    <label>Weight per cable (kg):</label>
    <input type="number" id="weight" min="0" max="100" step="0.5" value="10">
  </div>
</div>

<div class="main-content">
  <div class="live-card">
    <h2>Live Metrics</h2>
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-label">Left Cable</div>
        <div class="stat-value" id="loadB">0 <span class="stat-unit">kg</span></div>
      </div>
    </div>
  </div>
</div>
```

**Android Compose equivalent:**
```kotlin
@Composable
fun MainScreen() {
    Scaffold(
        bottomBar = { BottomNavigationBar() }
    ) { paddingValues ->
        NavHost(
            navController = rememberNavController(),
            startDestination = "connection",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("connection") { ConnectionScreen() }
            composable("workout") { WorkoutControlScreen() }
            composable("metrics") { LiveMetricsScreen() }
            composable("history") { HistoryScreen() }
        }
    }
}

@Composable
fun ConnectionScreen(viewModel: ConnectionViewModel = hiltViewModel()) {
    val connectionState by viewModel.connectionState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Vitruvian Control",
            style = MaterialTheme.typography.headlineLarge
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Connection status
        ConnectionStatusCard(
            state = connectionState,
            onConnect = { viewModel.connect() },
            onDisconnect = { viewModel.disconnect() }
        )
    }
}

@Composable
fun ConnectionStatusCard(
    state: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is ConnectionState.Connected -> Color(0xFFD3F9D8)
                is ConnectionState.Error -> Color(0xFFFFE0E0)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when (state) {
                    is ConnectionState.Disconnected -> "Disconnected"
                    is ConnectionState.Connecting -> "Connecting..."
                    is ConnectionState.Connected -> "Connected to ${state.deviceName}"
                    is ConnectionState.Error -> "Error: ${state.message}"
                },
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = if (state is ConnectionState.Connected) onDisconnect else onConnect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state is ConnectionState.Connected) "Disconnect" else "Connect to Device")
            }
        }
    }
}

@Composable
fun LiveMetricsScreen(viewModel: LiveMetricsViewModel = hiltViewModel()) {
    val metrics by viewModel.metrics.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        metrics?.let { metric ->
            // Load displays
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LoadCard(
                    label = "Left Cable",
                    load = metric.loadB,
                    unit = "kg"
                )
                LoadCard(
                    label = "Right Cable",
                    load = metric.loadA,
                    unit = "kg"
                )
                LoadCard(
                    label = "Total",
                    load = metric.loadA + metric.loadB,
                    unit = "kg"
                )
            }
            
            // Position bars
            PositionBars(
                posA = metric.posA,
                posB = metric.posB,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(16.dp)
            )
            
            // Chart
            WorkoutChart(
                data = viewModel.chartData.collectAsState().value,
                timeRange = viewModel.timeRange.collectAsState().value,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun LoadCard(label: String, load: Float, unit: String) {
    Card(
        modifier = Modifier.padding(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append("%.1f".format(load))
                    }
                    append(" ")
                    withStyle(
                        style = SpanStyle(
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        append(unit)
                    }
                },
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}
```

---

## Data Flow Comparison

### Web App Data Flow
```
User Action (HTML) 
  → Event Handler (app.js)
    → Device Command (device.js)
      → BLE Write (Web Bluetooth API)
        → Machine Response
          → BLE Notification
            → Callback (device.js)
              → Update UI (app.js → DOM)
```

### Android App Data Flow
```
User Action (Compose UI)
  → ViewModel Method
    → Use Case
      → Repository
        → BLE Manager
          → Android BLE API
            → Machine Response
              → BLE Notification Callback
                → Flow Emission (Repository)
                  → StateFlow Update (ViewModel)
                    → UI Recomposition (Compose)
```

**Key Difference:** Android uses reactive streams (Flow/StateFlow) instead of callbacks

---

## Storage Comparison

### Web App Storage
```javascript
// localStorage for preferences
localStorage.setItem("vitruvian.weightUnit", "kg");

// In-memory for workout history
this.workoutHistory = [];

// Export to CSV for persistence
exportCSV() {
  const csv = this.workoutHistory.map(/* ... */).join('\n');
  downloadFile(csv, 'workout.csv');
}
```

### Android Storage
```kotlin
// DataStore for preferences
class PreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { preferences ->
            preferences[WEIGHT_UNIT_KEY] = unit.name
        }
    }
    
    val weightUnit: Flow<WeightUnit> = dataStore.data.map { preferences ->
        WeightUnit.valueOf(preferences[WEIGHT_UNIT_KEY] ?: "KG")
    }
}

// Room database for workout history
@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertWorkout(workout: WorkoutSessionEntity)
    
    @Query("SELECT * FROM workout_sessions ORDER BY timestamp DESC")
    fun getAllWorkouts(): Flow<List<WorkoutSessionEntity>>
}

// Export to CSV
suspend fun exportToCSV(context: Context): Uri {
    val workouts = workoutDao.getAllWorkouts().first()
    val csv = workouts.joinToString("\n") { /* ... */ }
    
    val contentValues = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, "workout_${System.currentTimeMillis()}.csv")
        put(MediaStore.Downloads.MIME_TYPE, "text/csv")
    }
    
    val uri = context.contentResolver.insert(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        contentValues
    )
    
    uri?.let {
        context.contentResolver.openOutputStream(it)?.use { output ->
            output.write(csv.toByteArray())
        }
    }
    
    return uri ?: throw IOException("Failed to create file")
}
```

---

## Feature Parity Checklist

### Core BLE Features
- [ ] Device scanning with name filter
- [ ] GATT connection
- [ ] Service discovery
- [ ] Characteristic discovery
- [ ] Write with response
- [ ] Write without response
- [ ] Enable notifications (7 characteristics)
- [ ] Handle disconnection
- [ ] Command queuing
- [ ] Connection timeout

### Protocol Features
- [ ] Init command (4 bytes)
- [ ] Init preset (34 bytes)
- [ ] Program parameters (96 bytes)
- [ ] Echo control (32 bytes)
- [ ] Color scheme (34 bytes)
- [ ] Stop command
- [ ] All workout modes supported
- [ ] Just Lift mode
- [ ] Byte-perfect protocol compatibility

### Workout Control
- [ ] Start workout
- [ ] Stop workout
- [ ] Mode selection (5 modes)
- [ ] Echo levels (4 levels)
- [ ] Weight input (0-100 kg)
- [ ] Progression input (±3 kg)
- [ ] Reps input
- [ ] Just Lift toggle
- [ ] Stop at top toggle
- [ ] Color customization

### Real-time Monitoring
- [ ] Load monitoring (cable A, B, total)
- [ ] Position tracking
- [ ] Tick counter
- [ ] Rep counting
- [ ] Warmup vs working reps
- [ ] Auto-stop detection
- [ ] Range indicators
- [ ] 100ms polling rate

### Data Visualization
- [ ] Real-time chart
- [ ] Multi-series (load + position)
- [ ] Time range selector (15s to 2hr)
- [ ] Event markers
- [ ] Live mode
- [ ] Historical mode
- [ ] Zoom/pan support
- [ ] 2-hour data retention

### Data Persistence
- [ ] Workout history storage
- [ ] Preferences storage (unit, etc.)
- [ ] CSV export
- [ ] Previous workout review
- [ ] Data import (optional)

### UI Features
- [ ] Connection screen
- [ ] Workout control screen
- [ ] Live metrics screen
- [ ] History screen
- [ ] Settings screen
- [ ] Dark mode
- [ ] Responsive layout
- [ ] Loading states
- [ ] Error handling
- [ ] Accessibility support

---

## Testing Your Port

### Protocol Validation
1. **Capture web app traffic:** Use browser DevTools or BLE sniffer
2. **Capture Android app traffic:** Use Bluetooth HCI snoop log
3. **Compare byte-by-byte:** Every protocol frame must match exactly

**Example test:**
```kotlin
@Test
fun `init sequence matches web app`() {
    // From web app console log
    val expectedInitCmd = "0A 00 00 00"
    val expectedInitPreset = "11 00 00 00 00 00 00 00 00 00 00 00 CD CC CC 3E FF 00 4C FF 23 8C FF 8C 8C FF 00 4C FF 23 8C FF 8C 8C"
    
    // From Android
    val actualInitCmd = ProtocolBuilder.buildInitCommand().toHexString()
    val actualInitPreset = ProtocolBuilder.buildInitPreset().toHexString()
    
    assertThat(actualInitCmd).isEqualTo(expectedInitCmd)
    assertThat(actualInitPreset).isEqualTo(expectedInitPreset)
}
```

### Functional Testing
- [ ] Can discover device
- [ ] Can connect successfully
- [ ] Can start each workout mode
- [ ] Machine responds correctly
- [ ] Weights match physical display
- [ ] Reps count correctly
- [ ] Can stop workout
- [ ] Data persists across app restarts

### Comparison Testing
Run **same workout** on web app and Android app, compare:
- [ ] BLE command sequences (identical)
- [ ] Load values (identical)
- [ ] Position values (identical)
- [ ] Rep counts (identical)
- [ ] Timing (similar)
- [ ] CSV exports (same format)

---

## Migration for Existing Users

### Export from Web App
```javascript
// Add to web app
function exportAllData() {
  const data = {
    version: "1.0",
    preferences: {
      weightUnit: localStorage.getItem("vitruvian.weightUnit") || "kg"
    },
    workoutHistory: app.workoutHistory,
    exportDate: new Date().toISOString()
  };
  
  const json = JSON.stringify(data, null, 2);
  downloadFile(json, 'vitruvian_data.json');
}
```

### Import to Android App
```kotlin
suspend fun importWebAppData(jsonFile: Uri, context: Context) {
    val json = context.contentResolver.openInputStream(jsonFile)
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: throw IOException("Cannot read file")
    
    val data = Json.decodeFromString<WebAppExport>(json)
    
    // Import preferences
    preferencesManager.setWeightUnit(
        WeightUnit.valueOf(data.preferences.weightUnit.uppercase())
    )
    
    // Import workout history
    data.workoutHistory.forEach { workout ->
        workoutRepository.insertWorkout(workout.toEntity())
    }
}

@Serializable
data class WebAppExport(
    val version: String,
    val preferences: Preferences,
    val workoutHistory: List<WorkoutHistoryItem>,
    val exportDate: String
)
```

---

## Common Pitfalls & Solutions

### Pitfall 1: Float Byte Order
**Problem:** JavaScript DataView and Java ByteBuffer use different default byte orders
**Solution:** Always specify `ByteOrder.LITTLE_ENDIAN` in Android

### Pitfall 2: Unsigned Bytes
**Problem:** Kotlin bytes are signed (-128 to 127), JS Uint8Array is unsigned (0-255)
**Solution:** Use `.toByte()` and be careful with values > 127

```kotlin
// Correct
byteArrayOf(0xFF.toByte()) // -1 in Kotlin, but 0xFF when transmitted

// Wrong
byteArrayOf(0xFF) // Compile error, out of byte range
```

### Pitfall 3: BLE Notification Timing
**Problem:** Android may receive notifications at different rates than web
**Solution:** Use Flow operators like `conflate()` or `sample()` to handle backpressure

### Pitfall 4: Connection State Machine
**Problem:** Web Bluetooth handles some edge cases automatically
**Solution:** Implement explicit state machine in Android with timeouts and retries

### Pitfall 5: Permission Changes
**Problem:** Android 12+ requires new Bluetooth permissions
**Solution:** Request both old and new permissions, handle gracefully

---

## Success Criteria

Your Android port is **feature-complete** when:

1. ✅ All BLE commands match web app byte-for-byte
2. ✅ Machine responds identically to both apps
3. ✅ All workout modes work correctly
4. ✅ Real-time monitoring shows same values
5. ✅ Rep counting matches web app behavior
6. ✅ Charts display correctly
7. ✅ Data persists correctly
8. ✅ Export/import works
9. ✅ No crashes or ANRs
10. ✅ Battery drain is acceptable

**Ultimate test:** A user should be able to switch between web and Android apps mid-workout with no difference in experience!

---

## Questions During Development?

**Protocol unclear?**
- Check browser console logs from web app
- Use BLE sniffer to capture real traffic
- Compare web app source code directly

**BLE issues?**
- Test with nRF Connect app first
- Enable HCI snoop logging
- Use Nordic BLE Library documentation

**UI/UX questions?**
- Match web app behavior first
- Improve later based on user feedback

**Performance concerns?**
- Profile early and often
- Web app handles 72,000 data points, Android should too

---

**Remember: The web app proves this is possible. Your job is to translate proven concepts to Android! 🚀**

---

**Document Version:** 1.0  
**Last Updated:** October 27, 2025

