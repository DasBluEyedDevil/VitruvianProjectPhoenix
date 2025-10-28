# Android App Conversion Roadmap
## Vitruvian Trainer Local Control Application

**Last Updated:** October 27, 2025  
**Project Purpose:** Convert the existing web-based Vitruvian Trainer control interface into a native Android application to enable local control of Vitruvian Trainer machines after company bankruptcy.

---

## Executive Summary

This project currently exists as a **Web Bluetooth-based Progressive Web App (PWA)** that allows users to control their Vitruvian Trainer workout machines locally via Bluetooth Low Energy (BLE). The application successfully:

- Connects to Vitruvian machines via BLE
- Sends workout programs with customizable parameters
- Monitors real-time metrics (load, position, reps)
- Visualizes workout data with interactive charts
- Supports multiple workout modes (Old School, Pump, TUT, Echo, etc.)
- Tracks workout history

**Goal:** Create a native Android application with the same functionality plus improved stability, offline capability, and native Android UX.

---

## Current Architecture Analysis

### Technology Stack (Web Version)
- **Frontend:** Vanilla JavaScript (ES6+)
- **BLE Communication:** Web Bluetooth API
- **UI:** HTML5/CSS3 with responsive design
- **Charting:** uPlot library
- **Data Storage:** localStorage for preferences and workout history
- **Deployment:** GitHub Pages (static hosting)

### Core Components

#### 1. **app.js** (~1,500+ lines)
- Main application controller
- UI management and event handlers
- Workout session tracking
- Rep counting logic with auto-stop detection
- Unit conversion (kg/lb)
- Workout history management
- Chart integration

#### 2. **device.js** (~400+ lines)
- BLE device discovery and connection
- GATT service/characteristic management
- BLE protocol command queueing
- Real-time monitoring (notifications)
- Connection state management

#### 3. **protocol.js** (~200+ lines)
- Binary protocol frame builders
- Workout parameter encoding
- Mode-specific configuration
- Color scheme packets
- Echo mode parameters

#### 4. **modes.js** (~150+ lines)
- Workout mode definitions (Old School, Pump, TUT, etc.)
- Echo level parameters
- Mode profile encoding

#### 5. **chart.js** (~300+ lines)
- Real-time data visualization
- Multi-series charting (load, position)
- Event markers
- CSV export functionality
- Time range controls

#### 6. **index.html** (~800+ lines)
- Responsive UI layout
- Sidebar controls
- Live metrics display
- Position visualization bars
- Activity log

### Key Features Implemented

✅ **BLE Connection Management**
- Device discovery with filters (`namePrefix: "Vee"`)
- Connection state tracking
- Automatic reconnection handling
- GATT operation queuing to prevent concurrency issues

✅ **Workout Modes**
- Old School
- Pump
- TUT (Time Under Tension)
- TUT Beast
- Eccentric Only
- Echo Mode (with 4 difficulty levels)
- "Just Lift" mode with auto-stop

✅ **Real-time Monitoring**
- Cable load (A & B) in kg or lb
- Cable position tracking
- Rep counting with visual indicators
- Warmup vs working rep differentiation

✅ **Data Visualization**
- Live updating charts
- Multiple time ranges (15s to 2hr)
- CSV export capability
- Event markers for rep transitions

✅ **Workout Tracking**
- History storage
- Previous workout review
- Per-workout statistics

✅ **Advanced Features**
- Automatic rep detection using position tracking
- Stop-at-top option (for squats)
- Progressive/regressive loading
- Custom color schemes
- Unit switching (kg/lb)

---

## Android Conversion Strategy

### Phase 1: Foundation & Planning (Week 1-2)

#### 1.1 Technology Selection

**Recommended Stack:**
- **Language:** Kotlin (modern, Android-native, null-safe)
- **UI Framework:** Jetpack Compose (modern declarative UI)
- **BLE Library:** Android BLE Library by Nordic Semiconductor
- **Architecture:** MVVM with Clean Architecture
- **Dependency Injection:** Hilt/Dagger
- **Charting:** MPAndroidChart or Vico
- **Local Storage:** Room Database + DataStore
- **Async:** Kotlin Coroutines + Flow

**Alternative Stack (if team prefers):**
- **Language:** Java
- **UI:** Traditional XML layouts with ViewBinding
- **BLE:** Standard Android BLE APIs with RxAndroidBle wrapper

#### 1.2 Project Setup

**Tasks:**
1. Create new Android Studio project
2. Set up Git repository structure
3. Configure Gradle dependencies
4. Set up CI/CD pipeline (GitHub Actions)
5. Create development, staging, and production variants
6. Configure ProGuard/R8 for release builds

**Deliverables:**
- Android Studio project with proper structure
- `README.md` with setup instructions
- Development environment documentation

#### 1.3 Permissions & Manifest Configuration

**Required Permissions:**
```xml
<!-- BLE permissions -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Storage for workout history -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<!-- Network for future updates -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Hardware features -->
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

**Runtime Permissions Strategy:**
- Implement runtime permission requests (Android 6.0+)
- Handle Bluetooth 12+ permission model
- Graceful degradation if permissions denied

---

### Phase 2: Core BLE Infrastructure (Week 3-4)

#### 2.1 BLE Service Layer

**Architecture:**
```
Repository Pattern:
├── BleRepository (Interface)
├── VitruvianBleRepository (Implementation)
├── BleConnectionManager
├── GattCommandQueue
└── NotificationHandler
```

**Key Classes to Implement:**

**VitruvianDevice.kt**
```kotlin
class VitruvianDevice(
    private val context: Context,
    private val bleManager: BleManager
) {
    companion object {
        // UUIDs from device.js
        const val NUS_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
        const val NUS_RX_CHAR_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
        const val MONITOR_CHAR_UUID = "90e991a6-c548-44ed-969b-eb541014eae3"
        // ... other UUIDs
    }
    
    fun connect(): Flow<ConnectionState>
    fun disconnect()
    fun sendCommand(command: ByteArray): Flow<CommandResult>
    fun observeMonitorData(): Flow<MonitorData>
    fun observeRepNotifications(): Flow<RepEvent>
}
```

**GattCommandQueue.kt**
```kotlin
class GattCommandQueue {
    private val commandQueue = Channel<GattCommand>(Channel.UNLIMITED)
    
    suspend fun enqueue(command: GattCommand): Result<ByteArray>
    private suspend fun processQueue()
}
```

**Tasks:**
1. Port BLE UUIDs and constants
2. Implement device scanning with name filter ("Vee")
3. Implement connection state machine
4. Create GATT command queue (prevents "operation in progress" errors)
5. Implement characteristic write operations (with/without response)
6. Set up notification handlers
7. Add connection timeout and retry logic
8. Implement error handling and logging

**Testing:**
- Unit tests for command queue
- Integration tests with mock BLE device
- Real device testing

#### 2.2 Protocol Layer

**ProtocolBuilder.kt**
```kotlin
object ProtocolBuilder {
    fun buildInitCommand(): ByteArray
    fun buildInitPreset(): ByteArray
    fun buildProgramParams(params: ProgramParams): ByteArray
    fun buildEchoControl(params: EchoParams): ByteArray
    fun buildColorScheme(brightness: Float, colors: List<RGBColor>): ByteArray
}
```

**Tasks:**
1. Port all protocol builder functions from `protocol.js`
2. Implement binary data writing utilities
3. Create data classes for all parameter types
4. Add validation for parameter ranges
5. Write unit tests for each protocol frame

**Testing:**
- Unit tests comparing byte arrays to known good outputs
- Validation of all parameter combinations

---

### Phase 3: Domain Layer & Business Logic (Week 5-6)

#### 3.1 Domain Models

**Create data classes for:**
```kotlin
data class WorkoutSession(
    val id: String,
    val timestamp: Long,
    val mode: WorkoutMode,
    val parameters: WorkoutParameters,
    val metrics: List<WorkoutMetric>,
    val duration: Long
)

data class WorkoutMetric(
    val timestamp: Long,
    val loadA: Float,
    val loadB: Float,
    val positionA: Int,
    val positionB: Int,
    val ticks: Int
)

sealed class WorkoutMode {
    object OldSchool : WorkoutMode()
    object Pump : WorkoutMode()
    object TUT : WorkoutMode()
    object TUTBeast : WorkoutMode()
    object EccentricOnly : WorkoutMode()
    data class Echo(val level: EchoLevel) : WorkoutMode()
}

data class WorkoutParameters(
    val reps: Int,
    val weightPerCableKg: Float,
    val progressionKg: Float,
    val isJustLift: Boolean,
    val stopAtTop: Boolean
)
```

#### 3.2 Use Cases

**Implement use cases for:**
1. `ConnectToDeviceUseCase`
2. `StartWorkoutUseCase`
3. `StopWorkoutUseCase`
4. `MonitorWorkoutUseCase`
5. `SaveWorkoutHistoryUseCase`
6. `LoadWorkoutHistoryUseCase`
7. `ExportWorkoutDataUseCase`

**Example:**
```kotlin
class StartWorkoutUseCase @Inject constructor(
    private val bleRepository: BleRepository,
    private val protocolBuilder: ProtocolBuilder
) {
    suspend operator fun invoke(params: WorkoutParameters): Flow<WorkoutState> = flow {
        emit(WorkoutState.Initializing)
        
        // Send init sequence
        bleRepository.sendInit()
        
        // Build program frame
        val programFrame = protocolBuilder.buildProgramParams(params)
        
        // Send to device
        bleRepository.sendProgram(programFrame).collect { result ->
            when (result) {
                is Success -> emit(WorkoutState.Active)
                is Error -> emit(WorkoutState.Error(result.message))
            }
        }
    }
}
```

#### 3.3 Rep Detection Logic

**Port from app.js:**
```kotlin
class RepDetectionEngine {
    private val topPositionsA = mutableListOf<Int>()
    private val bottomPositionsA = mutableListOf<Int>()
    // ... similar for cable B
    
    fun processPosition(posA: Int, posB: Int): RepEvent?
    private fun detectRepTransition(): RepType?
    fun reset()
}
```

**Tasks:**
1. Port rep counting logic
2. Implement rolling window position tracking
3. Add warmup vs working rep differentiation
4. Implement auto-stop detection for "Just Lift" mode
5. Add configurable rep detection sensitivity

---

### Phase 4: Data Persistence (Week 7)

#### 4.1 Room Database

**Database Schema:**
```kotlin
@Database(
    entities = [
        WorkoutSessionEntity::class,
        WorkoutMetricEntity::class
    ],
    version = 1
)
abstract class VitruvianDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val mode: String,
    val reps: Int,
    val weightKg: Float,
    val progressionKg: Float,
    val duration: Long,
    val totalReps: Int,
    val warmupReps: Int
)

@Entity(tableName = "workout_metrics")
data class WorkoutMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestamp: Long,
    val loadA: Float,
    val loadB: Float,
    val positionA: Int,
    val positionB: Int,
    val ticks: Int
)
```

#### 4.2 DataStore for Preferences

```kotlin
data class UserPreferences(
    val weightUnit: WeightUnit,
    val lastConnectedDeviceAddress: String?,
    val autoReconnect: Boolean
)
```

**Tasks:**
1. Create Room database with migrations
2. Implement DAOs with Flow-based queries
3. Create DataStore for user preferences
4. Implement repository layer for data access
5. Add data export to CSV functionality
6. Implement backup/restore functionality

---

### Phase 5: UI Layer with Jetpack Compose (Week 8-10)

#### 5.1 Screen Structure

**Navigation Graph:**
```
├── SplashScreen
├── MainScreen (Bottom Navigation)
│   ├── WorkoutControlScreen
│   ├── LiveMetricsScreen
│   ├── HistoryScreen
│   └── SettingsScreen
└── WorkoutDetailScreen
```

#### 5.2 Key Screens

**WorkoutControlScreen**
- Connection status indicator
- Device connection/disconnection buttons
- Workout mode selector
- Parameter inputs (weight, reps, progression)
- "Just Lift" mode toggle
- Start/Stop workout buttons
- Real-time status messages

**LiveMetricsScreen**
- Cable load displays (A, B, Total)
- Position bars with range indicators
- Rep counter (warmup/working)
- Live chart with time range selector
- Event markers for rep transitions

**HistoryScreen**
- List of past workouts
- Filter/sort options
- Tap to view detailed metrics
- Export functionality

**SettingsScreen**
- Unit preference (kg/lb)
- Auto-reconnect toggle
- Color scheme customization
- About/version info
- Clear data option

#### 5.3 ViewModels

**WorkoutControlViewModel**
```kotlin
@HiltViewModel
class WorkoutControlViewModel @Inject constructor(
    private val connectToDeviceUseCase: ConnectToDeviceUseCase,
    private val startWorkoutUseCase: StartWorkoutUseCase,
    private val stopWorkoutUseCase: StopWorkoutUseCase
) : ViewModel() {
    
    private val _connectionState = MutableStateFlow<ConnectionState>(Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _workoutState = MutableStateFlow<WorkoutState>(Idle)
    val workoutState: StateFlow<WorkoutState> = _workoutState.asStateFlow()
    
    fun connectToDevice()
    fun disconnectFromDevice()
    fun startWorkout(params: WorkoutParameters)
    fun stopWorkout()
}
```

**LiveMetricsViewModel**
```kotlin
@HiltViewModel
class LiveMetricsViewModel @Inject constructor(
    private val monitorWorkoutUseCase: MonitorWorkoutUseCase,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {
    
    val currentMetrics: StateFlow<WorkoutMetric?>
    val repCount: StateFlow<RepCount>
    val chartData: StateFlow<List<ChartDataPoint>>
    
    fun setTimeRange(seconds: Int)
    fun exportData()
}
```

#### 5.4 Composables

**Key composables to create:**
```kotlin
@Composable
fun ConnectionStatusCard(state: ConnectionState, onConnect: () -> Unit)

@Composable
fun WorkoutParameterForm(
    params: WorkoutParameters,
    onParamsChange: (WorkoutParameters) -> Unit
)

@Composable
fun LoadDisplayCard(loadA: Float, loadB: Float, unit: WeightUnit)

@Composable
fun PositionBars(posA: Int, posB: Int, maxPos: Int, ranges: RepRanges?)

@Composable
fun LiveChart(
    data: List<ChartDataPoint>,
    timeRange: Int,
    events: List<ChartEvent>
)

@Composable
fun WorkoutHistoryList(
    workouts: List<WorkoutSession>,
    onWorkoutClick: (WorkoutSession) -> Unit
)
```

**Tasks:**
1. Implement all screen composables
2. Create reusable component library
3. Implement navigation
4. Add loading states and error handling
5. Implement pull-to-refresh
6. Add animations and transitions
7. Ensure accessibility (TalkBack, content descriptions)
8. Test on different screen sizes/orientations

---

### Phase 6: Charting & Visualization (Week 11)

#### 6.1 Chart Library Integration

**Recommended: MPAndroidChart or Vico**

**Tasks:**
1. Integrate charting library
2. Create multi-line chart for load data
3. Add position overlay
4. Implement time range selector
5. Add vertical markers for rep events
6. Implement pinch-to-zoom and pan
7. Add chart legend
8. Implement real-time data streaming

#### 6.2 Chart Features

**Port from chart.js:**
- Total load series
- Left/right cable load series
- Left/right position series
- Event markers (rep start/end, warmup complete)
- Time range controls (15s, 30s, 1min, 5min, 30min, 2hr)
- Live vs historical mode
- CSV export

**ChartManager.kt**
```kotlin
class ChartManager(
    private val chart: LineChart,
    private val context: Context
) {
    fun addDataPoint(metric: WorkoutMetric)
    fun setTimeRange(seconds: Int)
    fun addEventMarker(event: ChartEvent)
    fun exportToCSV(context: Context): Uri
    fun clearData()
}
```

---

### Phase 7: Testing & Quality Assurance (Week 12-13)

#### 7.1 Unit Testing

**Coverage targets:**
- Protocol builders: 100%
- Use cases: 90%+
- ViewModels: 90%+
- Rep detection: 95%+

**Testing frameworks:**
- JUnit 5
- MockK for Kotlin mocking
- Turbine for Flow testing
- Truth for assertions

#### 7.2 Integration Testing

**Test scenarios:**
1. BLE connection flow
2. Workout start/stop sequence
3. Real-time monitoring
4. Rep detection accuracy
5. Data persistence
6. Export functionality

#### 7.3 UI Testing

**Using Compose Testing:**
```kotlin
@Test
fun workoutControl_startsWorkoutSuccessfully() {
    composeTestRule.setContent {
        WorkoutControlScreen(viewModel = mockViewModel)
    }
    
    composeTestRule.onNodeWithText("Start Workout").performClick()
    composeTestRule.onNodeWithText("Active").assertExists()
}
```

#### 7.4 Device Testing

**Test on:**
- Multiple Android versions (8.0+)
- Different manufacturers (Samsung, Google, OnePlus, etc.)
- Various screen sizes
- BLE connection stability
- Battery drain
- Memory usage

#### 7.5 BLE Protocol Testing

**Critical tests:**
1. Verify all protocol frames match original web app
2. Test with actual Vitruvian hardware
3. Validate workout execution
4. Test edge cases (disconnection during workout, etc.)

---

### Phase 8: Polish & Optimization (Week 14)

#### 8.1 Performance Optimization

**Tasks:**
1. Profile app with Android Profiler
2. Optimize chart rendering
3. Minimize BLE latency
4. Reduce memory allocations
5. Implement lazy loading for history
6. Add data caching strategies
7. Optimize database queries

#### 8.2 UX Polish

**Tasks:**
1. Add haptic feedback for key actions
2. Implement proper loading states
3. Add empty states
4. Improve error messages
5. Add onboarding flow
6. Create tutorial/help screens
7. Add success animations
8. Implement dark mode support

#### 8.3 Accessibility

**Tasks:**
1. Add content descriptions
2. Test with TalkBack
3. Ensure proper contrast ratios
4. Add text scaling support
5. Test with accessibility scanner

---

### Phase 9: Advanced Features (Week 15-16)

#### 9.1 Features to Add

**Auto-reconnect:**
- Remember last connected device
- Attempt auto-reconnect on app launch
- Background connection monitoring

**Workout Templates:**
- Save favorite workout configurations
- Quick-start from templates
- Share templates (export/import JSON)

**Statistics Dashboard:**
- Total workouts completed
- Total weight lifted
- Personal records
- Progress charts (week/month/year)
- Muscle group tracking

**Advanced Charting:**
- Comparison mode (overlay multiple workouts)
- Velocity tracking
- Power calculation
- Fatigue indicators

**Export Options:**
- CSV export (already planned)
- PDF workout summaries
- Share to social media
- Integration with fitness apps (Google Fit, Strava)

#### 9.2 Optional Features

**Cloud Sync (Optional):**
- Firebase backend for backup
- Multi-device sync
- Cloud storage for workout history

**Wear OS Support:**
- Companion watch app
- Real-time metrics on wrist
- Workout control from watch

**Voice Commands:**
- Start/stop workouts with voice
- Rep counting announcements
- Coaching prompts

---

### Phase 10: Release Preparation (Week 17-18)

#### 10.1 Documentation

**Create:**
1. User guide
2. FAQ
3. Troubleshooting guide
4. Developer documentation
5. API documentation
6. Change log

#### 10.2 Release Checklist

**Pre-release:**
- [ ] All tests passing
- [ ] Code review completed
- [ ] Security audit
- [ ] Privacy policy created
- [ ] Terms of service created
- [ ] ProGuard rules configured
- [ ] Crashlytics integrated
- [ ] Analytics configured (optional, privacy-respecting)
- [ ] App signing configured
- [ ] Version numbering strategy defined

#### 10.3 Play Store Preparation

**Materials needed:**
1. App icon (512x512, adaptive icons)
2. Feature graphic (1024x500)
3. Screenshots (phone & tablet)
4. Promo video (optional)
5. Store listing copy
6. Privacy policy URL
7. Content rating questionnaire

**Store Listing:**
- Title: "Vitruvian Trainer Local Control"
- Short description (80 chars)
- Full description (4000 chars)
- Category: Health & Fitness
- Keywords/tags

#### 10.4 Beta Testing

**Beta distribution:**
1. Google Play Internal Testing (initial team)
2. Google Play Closed Testing (trusted users)
3. Google Play Open Testing (public beta)
4. Collect feedback
5. Iterate based on feedback

#### 10.5 Launch Strategy

**Phased rollout:**
1. Release to 5% of users
2. Monitor crash reports and feedback
3. Gradually increase to 10%, 25%, 50%, 100%
4. Hot-fix critical issues immediately

---

## Technical Challenges & Solutions

### Challenge 1: BLE Connection Stability

**Problem:** Bluetooth connections can be unstable, especially during workouts.

**Solutions:**
- Implement automatic reconnection with exponential backoff
- Queue commands during brief disconnections
- Add connection health monitoring
- Implement graceful degradation
- Cache last known state

### Challenge 2: Real-time Data Streaming

**Problem:** High-frequency BLE notifications can overwhelm UI updates.

**Solutions:**
- Implement data throttling/debouncing
- Use Kotlin Flow with `conflate()` operator
- Batch database writes
- Use background threads for processing
- Optimize chart rendering with data decimation

### Challenge 3: Battery Consumption

**Problem:** Continuous BLE monitoring can drain battery.

**Solutions:**
- Use BLE Low Power modes
- Implement smart polling intervals
- Pause monitoring when app in background
- Use WorkManager for background tasks
- Add battery optimization exclusion request

### Challenge 4: Cross-device Compatibility

**Problem:** Different Android manufacturers implement BLE differently.

**Solutions:**
- Use Nordic BLE Library (handles quirks)
- Test on multiple devices
- Implement manufacturer-specific workarounds
- Add fallback mechanisms
- Comprehensive error logging

### Challenge 5: Data Migration

**Problem:** Users may want to migrate data from web app.

**Solutions:**
- Implement CSV import functionality
- Create data format converter
- Support localStorage export format
- Add migration wizard
- Provide clear migration instructions

---

## Risk Assessment & Mitigation

### High Priority Risks

**Risk 1: BLE Protocol Changes**
- **Impact:** App won't work with devices
- **Likelihood:** Low (hardware is finalized)
- **Mitigation:** Extensive testing with real hardware, protocol documentation

**Risk 2: Android BLE API Changes**
- **Impact:** App breaks on new Android versions
- **Likelihood:** Medium
- **Mitigation:** Use stable BLE libraries, monitor Android updates, maintain compatibility layer

**Risk 3: Resource Constraints**
- **Impact:** Project timeline delays
- **Likelihood:** Medium
- **Mitigation:** Prioritize MVP features, phased rollout, community contributions

### Medium Priority Risks

**Risk 4: Performance Issues**
- **Impact:** Poor user experience
- **Likelihood:** Medium
- **Mitigation:** Early performance testing, profiling, optimization sprints

**Risk 5: Legal/IP Issues**
- **Impact:** Cease and desist
- **Likelihood:** Low (company bankrupt)
- **Mitigation:** Clear licensing, open source, community-driven

---

## Resource Requirements

### Development Team (Recommended)

**Minimum:**
- 1 Android Developer (Kotlin, BLE experience)
- 1 QA Tester (device access)

**Ideal:**
- 1 Senior Android Developer (team lead)
- 1 Mid-level Android Developer (UI/features)
- 1 BLE Protocol Specialist (part-time)
- 1 UX/UI Designer (part-time)
- 1 QA Engineer
- Community contributors

### Hardware Requirements

**Essential:**
- 1+ Vitruvian Trainer machines for testing
- 5+ Android test devices (various manufacturers/versions)
- BLE sniffer/analyzer (nRF Connect dongle) for protocol debugging

### Software/Tools

**Development:**
- Android Studio (latest stable)
- Git for version control
- Figma for UI design (optional)
- nRF Connect app for BLE debugging

**Services:**
- GitHub for code hosting
- GitHub Actions for CI/CD
- Firebase Crashlytics (crash reporting)
- Play Console account

### Budget Estimate

**One-time costs:**
- Play Store developer account: $25
- BLE sniffer hardware: $50-100
- Domain name (optional): $10/year

**Ongoing costs:**
- Firebase (free tier likely sufficient)
- Cloud storage (if implemented): $0-20/month

**Development time value:**
- ~450 hours (18 weeks × 25 hours/week)
- Commercial rate: $45,000-90,000 USD
- Open source/volunteer: $0

---

## MVP Feature List (Prioritized)

### Must-Have (P0)
1. ✅ BLE device connection
2. ✅ Start/stop workout
3. ✅ Basic workout modes (Old School, Pump)
4. ✅ Real-time load monitoring
5. ✅ Position tracking
6. ✅ Rep counting
7. ✅ Basic UI for controls
8. ✅ Workout history storage
9. ✅ Unit conversion (kg/lb)

### Should-Have (P1)
10. ✅ All workout modes (TUT, Echo, etc.)
11. ✅ Live charting
12. ✅ "Just Lift" mode
13. ✅ Auto-stop detection
14. ✅ Color scheme customization
15. ✅ CSV export
16. ✅ Dark mode

### Nice-to-Have (P2)
17. ⭕ Workout templates
18. ⭕ Statistics dashboard
19. ⭕ Auto-reconnect
20. ⭕ Advanced charts (comparison)
21. ⭕ Voice announcements
22. ⭕ Widget support

### Future (P3)
23. ⭕ Cloud sync
24. ⭕ Wear OS app
25. ⭕ Google Fit integration
26. ⭕ Social sharing
27. ⭕ Coaching features
28. ⭕ Exercise library

---

## Success Metrics

### Technical Metrics
- **Crash-free rate:** >99%
- **BLE connection success rate:** >95%
- **App startup time:** <2 seconds
- **Chart FPS:** >30fps during workout
- **Battery drain:** <5% per hour during active workout

### User Metrics
- **Play Store rating:** >4.5 stars
- **User retention (D7):** >60%
- **User retention (D30):** >40%
- **Active users:** 1,000+ (estimate: 10% of machine owners)

### Community Metrics
- **GitHub stars:** 100+
- **Community contributions:** 5+ contributors
- **Issue response time:** <48 hours
- **Documentation completeness:** 100% of public APIs

---

## Migration Path from Web App

### For Users

**Option 1: Fresh Start**
- Install Android app
- Manually reconfigure preferences
- Start new workout history

**Option 2: Data Import**
- Export web app data (localStorage as JSON)
- Use Android app import feature
- Restore preferences and history

**Step-by-step guide:**
1. On web app: Settings → Export Data → Save file
2. Transfer file to Android device
3. In Android app: Settings → Import Data → Select file
4. Confirm import
5. Verify data integrity

### For Developers

**Code reuse strategy:**
1. **Protocol layer:** Direct port (95% reusable)
2. **Business logic:** Port with adaptation (70% reusable)
3. **UI layer:** Complete rewrite (0% reusable)
4. **Testing:** Adapt test cases (50% reusable)

**Shared understanding:**
- Same BLE protocol
- Same workout modes
- Same parameter ranges
- Same data structures (adapted to Kotlin)

---

## Open Source Strategy

### Licensing
**Recommended:** MIT License or Apache 2.0
- Permissive open source
- Commercial use allowed
- Liability protection
- Clear attribution requirements

### Repository Structure
```
vitruvian-android/
├── app/                    # Android app module
├── ble/                    # BLE library module
├── protocol/               # Protocol library module
├── docs/                   # Documentation
├── scripts/                # Build/deployment scripts
├── .github/                # GitHub Actions workflows
├── LICENSE
├── README.md
├── CONTRIBUTING.md
└── CODE_OF_CONDUCT.md
```

### Contribution Guidelines
1. Code of conduct
2. Pull request template
3. Issue templates (bug, feature, question)
4. Development setup guide
5. Coding style guide
6. Commit message conventions

### Community Building
- Create Discord/Slack channel
- Weekly/monthly dev calls
- Transparent roadmap
- Recognize contributors
- Maintain changelog

---

## Maintenance & Long-term Support

### Update Cadence
- **Critical bugs:** Hot-fix within 24-48 hours
- **Security issues:** Immediate patch
- **Feature updates:** Monthly releases
- **Android OS updates:** Test within 1 week of beta

### Sustainability Plan
1. **Funding:** Donations, sponsorships (GitHub Sponsors)
2. **Volunteer base:** Recruit core maintainers
3. **Documentation:** Comprehensive guides to reduce support burden
4. **Automation:** CI/CD, automated testing, release automation
5. **Modular design:** Easy for community to contribute

---

## Conclusion

Converting the Vitruvian web app to Android is a **highly feasible project** with clear technical requirements and a proven foundation. The web app already demonstrates successful BLE protocol reverse-engineering and functional workout control.

### Key Success Factors
1. ✅ **Proven concept:** Web app works with real hardware
2. ✅ **Clear protocol:** BLE communication is well-documented in code
3. ✅ **Modern stack:** Kotlin + Compose provides excellent development experience
4. ✅ **Community need:** Thousands of users with "bricked" machines
5. ✅ **Open source:** Community can contribute and maintain long-term

### Recommended Approach
**Start with an 8-week MVP:**
- Weeks 1-2: Setup + BLE foundation
- Weeks 3-4: Protocol + core features
- Weeks 5-6: Basic UI + testing
- Weeks 7-8: Polish + beta release

**Then iterate with community feedback.**

### Next Steps
1. ✅ **You are here:** Roadmap complete
2. ⬜ Set up Android project
3. ⬜ Recruit development team
4. ⬜ Begin Phase 1 implementation
5. ⬜ Acquire test hardware
6. ⬜ Create GitHub repository
7. ⬜ Build community

**This roadmap provides a complete blueprint for success. The project is ready to move forward!**

---

## Appendix A: Technology Alternatives Comparison

### BLE Libraries

| Library | Pros | Cons | Recommendation |
|---------|------|------|----------------|
| **Nordic BLE Library** | Mature, handles manufacturer quirks, good documentation | Larger dependency | ⭐ Recommended |
| **RxAndroidBle** | Reactive, powerful | RxJava overhead, learning curve | Good alternative |
| **Native Android APIs** | No dependencies, lightweight | More boilerplate, handle quirks manually | For experienced devs |

### Charting Libraries

| Library | Pros | Cons | Recommendation |
|---------|------|------|----------------|
| **MPAndroidChart** | Feature-rich, mature, well-documented | Large size, not Compose-native | ⭐ Recommended |
| **Vico** | Modern, Compose-native, lightweight | Newer, smaller community | Great for Compose-first |
| **Custom Canvas** | Full control, optimized | High development effort | Only if specialized needs |

### Architecture Patterns

| Pattern | Pros | Cons | Recommendation |
|---------|------|------|----------------|
| **MVVM + Clean** | Testable, scalable, industry standard | More boilerplate | ⭐ Recommended |
| **MVI** | Predictable state, good for complex UI | Steeper learning curve | Alternative |
| **MVP** | Simple, clear separation | Not reactive, verbose | Legacy approach |

---

## Appendix B: Key Code Snippets to Port

### BLE Service UUIDs
```javascript
// From device.js - exact UUIDs to use
const NUS_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
const NUS_RX_CHAR_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
const MONITOR_CHAR_UUID = "90e991a6-c548-44ed-969b-eb541014eae3";
const PROPERTY_CHAR_UUID = "5fa538ec-d041-42f6-bbd6-c30d475387b7";
const REP_NOTIFY_CHAR_UUID = "8308f2a6-0875-4a94-a86f-5c5c5e1b068a";
```

### Critical Protocol Frames
```javascript
// Init command (4 bytes)
[0x0a, 0x00, 0x00, 0x00]

// Init preset (34 bytes)
[0x11, 0x00, ...] // See protocol.js for full frame

// Program params (96 bytes)
// Byte 0x04: reps (or 0xFF for Just Lift)
// Byte 0x54-0x57: effective weight (float32 LE)
// Byte 0x58-0x5B: per-cable weight (float32 LE)
// Byte 0x5C-0x5F: progression (float32 LE)
```

### Rep Detection Algorithm
```javascript
// From app.js - critical logic to port
// Uses rolling windows of top/bottom positions
// Detects transitions based on position changes
// Differentiates warmup vs working reps
// Implements auto-stop for "Just Lift" mode
```

---

## Appendix C: Testing Checklist

### BLE Protocol Testing
- [ ] Device discovery works
- [ ] Connection establishes reliably
- [ ] All characteristics are accessible
- [ ] Init sequence succeeds
- [ ] Program start works for each mode
- [ ] Stop command works
- [ ] Notifications are received
- [ ] Disconnection is handled gracefully
- [ ] Reconnection works

### Workout Mode Testing
- [ ] Old School mode
- [ ] Pump mode
- [ ] TUT mode
- [ ] TUT Beast mode
- [ ] Eccentric Only mode
- [ ] Echo mode (all 4 levels)
- [ ] Just Lift mode
- [ ] Custom color schemes

### Data Accuracy Testing
- [ ] Load values match physical display
- [ ] Position tracking is accurate
- [ ] Rep counting is correct
- [ ] Warmup reps tracked separately
- [ ] Auto-stop triggers correctly
- [ ] Charts display accurate data
- [ ] CSV export has correct values

### UI/UX Testing
- [ ] All screens render correctly
- [ ] Navigation works smoothly
- [ ] Loading states display
- [ ] Error messages are clear
- [ ] Dark mode works
- [ ] Rotation handling
- [ ] Accessibility features work
- [ ] Tablets render properly

---

**Document Version:** 1.0  
**Last Updated:** October 27, 2025  
**Author:** Development Team  
**License:** Same as project (MIT/Apache 2.0)

