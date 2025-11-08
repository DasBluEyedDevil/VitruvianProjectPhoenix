# Flutter Migration Roadmap - Vitruvian Project Phoenix

**Version:** 1.0
**Date:** November 8, 2025
**Status:** Planning Phase
**Target Platforms:** Android & iOS

---

## Executive Summary

This roadmap outlines the complete migration of the Vitruvian Project Phoenix native Android application to Flutter, enabling cross-platform support for both Android and iOS. The migration will preserve all existing functionality while leveraging Flutter's cross-platform capabilities to provide feature parity across both platforms with a single codebase.

**Current State:**
- Native Android app (Kotlin + Jetpack Compose)
- 84 source files, 10 database tables
- MVVM + Clean Architecture
- BLE-based machine control
- Comprehensive workout tracking and analytics

**Target State:**
- Flutter cross-platform app (Dart)
- Single codebase for Android & iOS
- Feature parity with current Android app
- Enhanced iOS support with native performance
- Maintained architecture principles

---

## Phase 1: Project Setup & Foundation (2-3 weeks)

### 1.1 Flutter Project Initialization
**Duration:** 2-3 days

- [ ] Create new Flutter project structure
- [ ] Configure `pubspec.yaml` with initial dependencies
- [ ] Set up build configurations for Android/iOS
- [ ] Configure app signing for both platforms
- [ ] Set up version management
- [ ] Create folder structure matching Clean Architecture

**Deliverables:**
- Working Flutter project skeleton
- Build scripts for both platforms
- CI/CD pipeline configuration

### 1.2 Core Dependencies Setup
**Duration:** 3-4 days

**Package Selection:**

| Android Library | Flutter Equivalent | Purpose |
|-----------------|-------------------|---------|
| Nordic BLE | `flutter_blue_plus` (v1.31+) | BLE communication |
| Room Database | `sqflite` (v2.3+) + `drift` (v2.14+) | Local database |
| DataStore | `shared_preferences` (v2.2+) or `hive` (v2.2+) | Key-value storage |
| Hilt/Dagger | `get_it` (v7.6+) + `injectable` (v2.3+) | Dependency injection |
| Coroutines/Flow | Dart Streams + `async`/`await` | Async programming |
| Jetpack Compose | Flutter Widgets | UI framework |
| Material 3 | `material` (v3.0+) | Design system |
| MPAndroidChart | `fl_chart` (v0.66+) | Charting |
| Coil | `cached_network_image` (v3.3+) | Image loading |
| Timber | `logger` (v2.0+) | Logging |
| Accompanist Permissions | `permission_handler` (v11.0+) | Permissions |

**Additional Flutter Packages:**
- `provider` (v6.1+) or `riverpod` (v2.4+) - State management
- `freezed` (v2.4+) + `json_serializable` (v6.7+) - Code generation
- `go_router` (v13.0+) - Navigation
- `equatable` (v2.0+) - Value equality
- `dartz` (v0.10+) - Functional programming
- `intl` (v0.19+) - Internationalization

**Tasks:**
- [ ] Add all core packages to `pubspec.yaml`
- [ ] Configure code generation tools
- [ ] Set up dependency injection container
- [ ] Create base architecture classes
- [ ] Configure logging framework

**Deliverables:**
- Complete `pubspec.yaml` with all dependencies
- DI container setup
- Base architecture structure

### 1.3 Project Structure Setup
**Duration:** 2-3 days

```
lib/
├── main.dart                           # App entry point
├── app.dart                            # App widget
├── core/
│   ├── constants/
│   │   └── ble_constants.dart          # BLE UUIDs & constants
│   ├── error/
│   │   ├── failures.dart               # Error types
│   │   └── exceptions.dart             # Exception types
│   ├── network/
│   │   └── network_info.dart           # Network status
│   ├── di/
│   │   └── injection_container.dart    # DI setup
│   └── utils/
│       ├── protocol_builder.dart       # BLE protocol frames
│       └── device_info.dart            # Device metadata
├── features/
│   ├── workout/
│   │   ├── data/
│   │   │   ├── datasources/
│   │   │   │   ├── workout_local_datasource.dart
│   │   │   │   └── workout_remote_datasource.dart
│   │   │   ├── models/
│   │   │   │   ├── workout_model.dart
│   │   │   │   └── workout_metric_model.dart
│   │   │   └── repositories/
│   │   │       └── workout_repository_impl.dart
│   │   ├── domain/
│   │   │   ├── entities/
│   │   │   │   ├── workout.dart
│   │   │   │   └── workout_metric.dart
│   │   │   ├── repositories/
│   │   │   │   └── workout_repository.dart
│   │   │   └── usecases/
│   │   │       ├── start_workout.dart
│   │   │       ├── stop_workout.dart
│   │   │       └── get_workout_history.dart
│   │   └── presentation/
│   │       ├── bloc/
│   │       │   ├── workout_bloc.dart
│   │       │   ├── workout_event.dart
│   │       │   └── workout_state.dart
│   │       ├── pages/
│   │       │   ├── home_screen.dart
│   │       │   ├── active_workout_screen.dart
│   │       │   └── workout_history_screen.dart
│   │       └── widgets/
│   │           ├── connection_status_banner.dart
│   │           └── workout_controls.dart
│   ├── ble/
│   │   ├── data/
│   │   │   ├── datasources/
│   │   │   │   └── ble_datasource.dart
│   │   │   ├── models/
│   │   │   │   └── ble_device_model.dart
│   │   │   └── repositories/
│   │   │       └── ble_repository_impl.dart
│   │   ├── domain/
│   │   │   ├── entities/
│   │   │   │   └── ble_device.dart
│   │   │   ├── repositories/
│   │   │   │   └── ble_repository.dart
│   │   │   └── usecases/
│   │   │       ├── scan_devices.dart
│   │   │       ├── connect_device.dart
│   │   │       └── send_command.dart
│   │   └── presentation/
│   │       ├── bloc/
│   │       │   ├── ble_bloc.dart
│   │       │   ├── ble_event.dart
│   │       │   └── ble_state.dart
│   │       ├── pages/
│   │       │   └── device_selection_screen.dart
│   │       └── widgets/
│   │           └── device_list_item.dart
│   ├── exercise/
│   │   └── [similar structure]
│   ├── routine/
│   │   └── [similar structure]
│   ├── analytics/
│   │   └── [similar structure]
│   └── settings/
│       └── [similar structure]
└── shared/
    ├── widgets/
    │   ├── custom_button.dart
    │   ├── custom_text_field.dart
    │   └── loading_indicator.dart
    └── theme/
        ├── app_theme.dart
        ├── app_colors.dart
        └── app_text_styles.dart
```

**Tasks:**
- [ ] Create folder structure
- [ ] Set up base classes for each layer
- [ ] Create utility classes
- [ ] Set up theme system

**Deliverables:**
- Complete project structure
- Base classes and interfaces
- Theme configuration

---

## Phase 2: Data Layer Migration (3-4 weeks)

### 2.1 Database Migration (Room → Sqflite/Drift)
**Duration:** 1 week

**Strategy:** Use Drift for type-safe database access

**Database Schema (10 tables):**
1. `workout_sessions`
2. `workout_metrics`
3. `routines`
4. `routine_exercises`
5. `weekly_programs`
6. `program_days`
7. `exercises`
8. `exercise_videos`
9. `personal_records`
10. `connection_logs`

**Tasks:**
- [ ] Create Drift database class (`AppDatabase`)
- [ ] Define 10 table schemas using Drift annotations
- [ ] Implement DAOs (Data Access Objects)
- [ ] Create migration scripts (v1 → v14)
- [ ] Implement TypeConverters for custom types
- [ ] Write database tests
- [ ] Create seed data for exercise library

**Code Example:**
```dart
// workout_sessions_table.dart
@DataClassName('WorkoutSessionData')
class WorkoutSessions extends Table {
  TextColumn get id => text()();
  DateTimeColumn get timestamp => dateTime()();
  TextColumn get mode => text()();
  IntColumn get reps => integer()();
  RealColumn get weight => real()();
  // ... more columns

  @override
  Set<Column> get primaryKey => {id};
}
```

**Deliverables:**
- Complete Drift database implementation
- All 10 tables migrated
- Migration scripts
- Database tests

### 2.2 BLE Layer Migration (Nordic → flutter_blue_plus)
**Duration:** 1.5 weeks

**Current Implementation:**
- Nordic BLE Library
- Custom `VitruvianBleManager`
- 15 characteristics (NUS, Monitor, Property, Rep Notify, etc.)
- 5 command types (Init, Init Preset, Program Params, Echo Control, Color Scheme)

**Flutter Implementation:**

**Tasks:**
- [ ] Create `BluetoothService` class wrapping `flutter_blue_plus`
- [ ] Implement device scanning with "Vee" prefix filter
- [ ] Implement connection management with timeout (15s)
- [ ] Implement GATT service discovery
- [ ] Create characteristic read/write methods
- [ ] Implement notification subscriptions
- [ ] Port protocol builder (binary frame construction)
- [ ] Implement monitor polling (100ms intervals)
- [ ] Add connection state management
- [ ] Implement retry logic
- [ ] Add comprehensive error handling
- [ ] Write BLE integration tests

**Protocol Implementation:**
```dart
class ProtocolBuilder {
  // Init Command (4 bytes)
  static Uint8List buildInitCommand() {
    final buffer = ByteData(4);
    buffer.setUint8(0, 0x0A);
    // ... remaining bytes
    return buffer.buffer.asUint8List();
  }

  // Init Preset (34 bytes)
  static Uint8List buildInitPreset(List<double> coefficients) {
    final buffer = ByteData(34);
    buffer.setUint8(0, 0x11);
    // ... coefficient table
    return buffer.buffer.asUint8List();
  }

  // Program Params (96 bytes)
  static Uint8List buildProgramParams({
    required WorkoutMode mode,
    required int reps,
    required double weight,
    // ... more params
  }) {
    final buffer = ByteData(96);
    buffer.setUint8(0, 0x04);
    // ... workout configuration
    return buffer.buffer.asUint8List();
  }

  // Echo Control (40 bytes)
  static Uint8List buildEchoControl(/* params */) { /* ... */ }

  // Color Scheme (44 bytes)
  static Uint8List buildColorScheme(/* params */) { /* ... */ }
}
```

**BLE Service UUIDs:**
```dart
class BleConstants {
  static const nusServiceUuid = '6e400001-b5a3-f393-e0a9-e50e24dcca9e';
  static const nusRxCharUuid = '6e400002-b5a3-f393-e0a9-e50e24dcca9e';
  static const monitorCharUuid = '90e991a6-c548-44ed-969b-eb541014eae3';
  static const propertyCharUuid = '5fa538ec-d041-42f6-bbd6-c30d475387b7';
  static const repNotifyCharUuid = '8308f2a6-0875-4a94-a86f-5c5c5e1b068a';
  // ... 8 workout command characteristics
  // ... 7 notification characteristics
}
```

**Deliverables:**
- Complete BLE service implementation
- Protocol builder ported to Dart
- All characteristics accessible
- Connection management working
- BLE tests passing

### 2.3 Local Storage Migration (DataStore → SharedPreferences/Hive)
**Duration:** 2-3 days

**Current Preferences:**
- `weight_unit` (String: KG/LB)
- `autoplay_enabled` (Boolean)
- `stop_at_top` (Boolean)
- Theme preferences

**Tasks:**
- [ ] Create `PreferencesService` class
- [ ] Implement key-value storage with type safety
- [ ] Create preference models
- [ ] Implement Stream-based observation
- [ ] Add default values
- [ ] Write preferences tests

**Code Example:**
```dart
class PreferencesService {
  final SharedPreferences _prefs;
  final _controller = StreamController<UserPreferences>.broadcast();

  Stream<UserPreferences> get preferencesStream => _controller.stream;

  Future<void> setWeightUnit(WeightUnit unit) async {
    await _prefs.setString('weight_unit', unit.name);
    _notifyListeners();
  }

  WeightUnit getWeightUnit() {
    final value = _prefs.getString('weight_unit') ?? 'kg';
    return WeightUnit.values.firstWhere((e) => e.name == value);
  }
}
```

**Deliverables:**
- Preferences service implemented
- All settings migrated
- Observable preferences working

### 2.4 Repository Layer Migration
**Duration:** 1 week

**Repositories to Migrate:**
1. `BleRepository` - BLE operations
2. `WorkoutRepository` - Workout data
3. `ExerciseRepository` - Exercise library
4. `PersonalRecordRepository` - PR tracking
5. `RoutineRepository` - Routine management
6. `ConnectionLogRepository` - Debug logging

**Tasks:**
- [ ] Create repository interfaces (domain layer)
- [ ] Implement repository classes (data layer)
- [ ] Connect to data sources (local + remote)
- [ ] Implement error handling with Either<Failure, Success>
- [ ] Add caching where appropriate
- [ ] Write repository tests with mocks

**Code Example:**
```dart
class WorkoutRepositoryImpl implements WorkoutRepository {
  final WorkoutLocalDataSource localDataSource;
  final NetworkInfo networkInfo;

  @override
  Future<Either<Failure, List<Workout>>> getWorkoutHistory() async {
    try {
      final workouts = await localDataSource.getWorkouts();
      return Right(workouts);
    } on CacheException {
      return Left(CacheFailure());
    }
  }

  @override
  Future<Either<Failure, void>> saveWorkout(Workout workout) async {
    try {
      await localDataSource.saveWorkout(workout.toModel());
      return const Right(null);
    } on CacheException {
      return Left(CacheFailure());
    }
  }
}
```

**Deliverables:**
- All 6 repositories migrated
- Repository tests passing
- Error handling implemented

---

## Phase 3: Domain Layer Migration (2 weeks)

### 3.1 Entity Models Migration
**Duration:** 3-4 days

**Core Entities:**
1. `Workout` & `WorkoutMetric`
2. `Exercise` & `ExerciseVideo`
3. `Routine` & `RoutineExercise`
4. `WeeklyProgram` & `ProgramDay`
5. `PersonalRecord`
6. `ConnectionLog`
7. `BleDevice`
8. `UserPreferences`

**Tasks:**
- [ ] Create entity classes with `freezed` for immutability
- [ ] Define value objects (WeightUnit, WorkoutMode, etc.)
- [ ] Implement equality and toString
- [ ] Add JSON serialization for data models
- [ ] Create entity tests

**Code Example:**
```dart
@freezed
class Workout with _$Workout {
  const factory Workout({
    required String id,
    required DateTime timestamp,
    required WorkoutMode mode,
    required int reps,
    required double weight,
    required double progression,
    required int totalReps,
    required int warmupReps,
    required int workingReps,
    required bool isJustLift,
    required bool stopAtTop,
    double? eccentricLoad,
    int? echoLevel,
  }) = _Workout;
}

enum WorkoutMode {
  oldSchool,
  pump,
  tut,
  tutBeast,
  eccentricOnly,
  echo,
}

enum WeightUnit { kg, lb }
```

**Deliverables:**
- All entities migrated to Dart
- Freezed code generation working
- Entity tests passing

### 3.2 Use Cases Migration
**Duration:** 1 week

**Use Cases to Migrate:**

**Workout Feature:**
- [ ] `StartWorkout`
- [ ] `StopWorkout`
- [ ] `PauseWorkout`
- [ ] `ResumeWorkout`
- [ ] `GetWorkoutHistory`
- [ ] `GetActiveWorkout`
- [ ] `SaveWorkout`

**BLE Feature:**
- [ ] `ScanDevices`
- [ ] `ConnectToDevice`
- [ ] `DisconnectFromDevice`
- [ ] `SendCommand`
- [ ] `SubscribeToNotifications`

**Exercise Feature:**
- [ ] `GetExerciseLibrary`
- [ ] `SearchExercises`
- [ ] `GetExerciseById`
- [ ] `ImportExercises`
- [ ] `UpdateExercise`

**Routine Feature:**
- [ ] `CreateRoutine`
- [ ] `UpdateRoutine`
- [ ] `DeleteRoutine`
- [ ] `GetRoutines`

**Analytics Feature:**
- [ ] `GetWorkoutStatistics`
- [ ] `GetPersonalRecords`
- [ ] `CalculateProgressTrends`

**Code Example:**
```dart
class StartWorkout {
  final WorkoutRepository repository;
  final BleRepository bleRepository;

  StartWorkout(this.repository, this.bleRepository);

  Future<Either<Failure, void>> call(WorkoutParams params) async {
    // 1. Validate parameters
    if (params.reps <= 0) {
      return Left(InvalidParamsFailure());
    }

    // 2. Build BLE command
    final command = ProtocolBuilder.buildProgramParams(
      mode: params.mode,
      reps: params.reps,
      weight: params.weight,
      // ... more params
    );

    // 3. Send to device
    final result = await bleRepository.sendCommand(command);

    return result.fold(
      (failure) => Left(failure),
      (_) async {
        // 4. Create workout session
        final workout = Workout(
          id: Uuid().v4(),
          timestamp: DateTime.now(),
          mode: params.mode,
          reps: params.reps,
          weight: params.weight,
          // ... more fields
        );

        // 5. Save to database
        return await repository.saveWorkout(workout);
      },
    );
  }
}
```

**Deliverables:**
- All use cases migrated
- Business logic preserved
- Use case tests passing

### 3.3 Rep Counting Algorithm Migration
**Duration:** 2-3 days

**Current Implementation:** `RepCounterFromMachine.kt`
- Hardware-based rep counting
- Warmup/working rep separation
- Position-based range detection
- Auto-stop logic

**Tasks:**
- [ ] Port `RepCounterFromMachine` to Dart
- [ ] Implement rep detection state machine
- [ ] Add warmup phase detection
- [ ] Implement position tracking
- [ ] Add auto-stop logic
- [ ] Write comprehensive rep counting tests

**Code Example:**
```dart
class RepCounterFromMachine {
  final _repEventsController = StreamController<RepEvent>.broadcast();

  Stream<RepEvent> get repEvents => _repEventsController.stream;

  int _warmupReps = 0;
  int _workingReps = 0;
  bool _isWarmupComplete = false;

  void onRepCompleted(int hardwareCount) {
    if (!_isWarmupComplete) {
      _warmupReps++;
      if (_warmupReps >= 3) {
        _isWarmupComplete = true;
        _repEventsController.add(RepEvent.warmupCompleted(_warmupReps));
      }
    } else {
      _workingReps++;
      _repEventsController.add(RepEvent.workingRepCompleted(_workingReps));
    }
  }

  RepCount getCurrentCount() {
    return RepCount(
      warmupReps: _warmupReps,
      workingReps: _workingReps,
      totalReps: _warmupReps + _workingReps,
      isWarmupComplete: _isWarmupComplete,
    );
  }
}
```

**Deliverables:**
- Rep counting algorithm ported
- State machine working correctly
- Algorithm tests passing

---

## Phase 4: Presentation Layer Migration (4-5 weeks)

### 4.1 State Management Setup (BLoC Pattern)
**Duration:** 3-4 days

**Strategy:** Use `flutter_bloc` for state management

**BLoCs to Create:**
1. `WorkoutBloc` - Main workout state
2. `BleBloc` - BLE connection state
3. `ExerciseBloc` - Exercise library
4. `RoutineBloc` - Routine management
5. `AnalyticsBloc` - Statistics
6. `SettingsBloc` - User preferences
7. `ThemeBloc` - Theme management

**Tasks:**
- [ ] Set up bloc pattern structure
- [ ] Create events for each bloc
- [ ] Create states for each bloc
- [ ] Implement bloc logic
- [ ] Add bloc tests
- [ ] Set up bloc providers

**Code Example:**
```dart
// Events
abstract class WorkoutEvent extends Equatable {}

class StartWorkoutEvent extends WorkoutEvent {
  final WorkoutParams params;
  StartWorkoutEvent(this.params);
  @override
  List<Object> get props => [params];
}

class StopWorkoutEvent extends WorkoutEvent {
  @override
  List<Object> get props => [];
}

// States
abstract class WorkoutState extends Equatable {}

class WorkoutInitial extends WorkoutState {
  @override
  List<Object> get props => [];
}

class WorkoutLoading extends WorkoutState {
  @override
  List<Object> get props => [];
}

class WorkoutActive extends WorkoutState {
  final Workout workout;
  final RepCount repCount;

  WorkoutActive(this.workout, this.repCount);

  @override
  List<Object> get props => [workout, repCount];
}

class WorkoutError extends WorkoutState {
  final String message;

  WorkoutError(this.message);

  @override
  List<Object> get props => [message];
}

// Bloc
class WorkoutBloc extends Bloc<WorkoutEvent, WorkoutState> {
  final StartWorkout startWorkout;
  final StopWorkout stopWorkout;
  final GetActiveWorkout getActiveWorkout;

  WorkoutBloc({
    required this.startWorkout,
    required this.stopWorkout,
    required this.getActiveWorkout,
  }) : super(WorkoutInitial()) {
    on<StartWorkoutEvent>(_onStartWorkout);
    on<StopWorkoutEvent>(_onStopWorkout);
  }

  Future<void> _onStartWorkout(
    StartWorkoutEvent event,
    Emitter<WorkoutState> emit,
  ) async {
    emit(WorkoutLoading());

    final result = await startWorkout(event.params);

    result.fold(
      (failure) => emit(WorkoutError(failure.message)),
      (_) => emit(WorkoutActive(/* ... */)),
    );
  }

  Future<void> _onStopWorkout(
    StopWorkoutEvent event,
    Emitter<WorkoutState> emit,
  ) async {
    // ... stop logic
  }
}
```

**Deliverables:**
- All 7 BLoCs implemented
- State management working
- BLoC tests passing

### 4.2 Theme System Migration (Material 3)
**Duration:** 2-3 days

**Current Theme:**
- Light mode: Lavender/pink/violet gradient
- Dark mode: Slate/indigo/blue gradient
- Material Design 3
- Custom colors and typography

**Tasks:**
- [ ] Create `AppTheme` class with light/dark themes
- [ ] Define color schemes
- [ ] Set up typography system
- [ ] Create spacing constants
- [ ] Implement theme switching
- [ ] Add theme persistence

**Code Example:**
```dart
class AppTheme {
  static ThemeData lightTheme = ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(
      seedColor: const Color(0xFF7C3AED), // Purple
      primary: const Color(0xFF7C3AED),
      secondary: const Color(0xFFA855F7),
      tertiary: const Color(0xFFD946EF),
    ),
    // ... more theme config
  );

  static ThemeData darkTheme = ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    colorScheme: ColorScheme.fromSeed(
      seedColor: const Color(0xFF7C3AED),
      brightness: Brightness.dark,
      // ... dark colors
    ),
    // ... more theme config
  );
}

class AppColors {
  static const purple = Color(0xFF7C3AED);
  static const purpleLight = Color(0xFFA855F7);
  static const purpleLighter = Color(0xFFD946EF);
  static const error = Color(0xFFEF4444);
  // ... more colors
}

class AppTextStyles {
  static const heading1 = TextStyle(
    fontSize: 32,
    fontWeight: FontWeight.bold,
  );
  // ... more text styles
}
```

**Deliverables:**
- Complete theme system
- Light/dark themes matching Android app
- Theme switching working

### 4.3 Screen Migration (20+ Screens)
**Duration:** 3 weeks

**Screens to Migrate:**

**Priority 1 (Core Functionality):**
1. [ ] **SplashScreen** - Animated splash (1 day)
2. [ ] **HomeScreen** - Main dashboard (2 days)
3. [ ] **DeviceSelectionScreen** - BLE device picker (1 day)
4. [ ] **ActiveWorkoutScreen** - Real-time workout (3 days)
5. [ ] **JustLiftScreen** - Single exercise mode (2 days)

**Priority 2 (Essential Features):**
6. [ ] **WorkoutHistoryScreen** - Past workouts (2 days)
7. [ ] **ExerciseLibraryScreen** - Browse exercises (2 days)
8. [ ] **SingleExerciseScreen** - Exercise config (1 day)
9. [ ] **SettingsScreen** - User preferences (1 day)

**Priority 3 (Advanced Features):**
10. [ ] **AnalyticsScreen** - Statistics dashboard (3 days)
11. [ ] **ProgramBuilderScreen** - Custom routines (3 days)
12. [ ] **DailyRoutinesScreen** - Quick templates (1 day)
13. [ ] **WeeklyProgramsScreen** - Multi-day plans (2 days)
14. [ ] **ConnectionLogsScreen** - BLE debugging (1 day)

**Migration Strategy per Screen:**
1. Review Kotlin/Compose implementation
2. Create Flutter widget structure
3. Integrate with BLoC for state
4. Migrate all UI components
5. Add navigation
6. Test on both platforms
7. Verify iOS-specific behavior

**Code Example (HomeScreen):**
```dart
class HomeScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Vitruvian Phoenix'),
        actions: [
          BlocBuilder<ThemeBloc, ThemeState>(
            builder: (context, state) {
              return IconButton(
                icon: Icon(state.isDark ? Icons.light_mode : Icons.dark_mode),
                onPressed: () => context.read<ThemeBloc>().add(ToggleThemeEvent()),
              );
            },
          ),
        ],
      ),
      body: BlocBuilder<BleBloc, BleState>(
        builder: (context, bleState) {
          if (bleState is BleDisconnected) {
            return _buildDisconnectedView(context);
          } else if (bleState is BleConnected) {
            return _buildConnectedView(context, bleState);
          } else if (bleState is BleConnecting) {
            return _buildConnectingView(context);
          }
          return const SizedBox.shrink();
        },
      ),
    );
  }

  Widget _buildConnectedView(BuildContext context, BleConnected state) {
    return Column(
      children: [
        ConnectionStatusBanner(
          deviceName: state.deviceName,
          deviceAddress: state.deviceAddress,
        ),
        Expanded(
          child: GridView.count(
            crossAxisCount: 2,
            children: [
              WorkoutModeCard(
                title: 'Just Lift',
                icon: Icons.fitness_center,
                onTap: () => Navigator.pushNamed(context, '/just_lift'),
              ),
              WorkoutModeCard(
                title: 'Program Builder',
                icon: Icons.list_alt,
                onTap: () => Navigator.pushNamed(context, '/program_builder'),
              ),
              // ... more cards
            ],
          ),
        ),
      ],
    );
  }
}
```

**Deliverables:**
- All 14 screens migrated
- UI matching Android app
- iOS-specific adaptations
- Navigation working

### 4.4 Reusable Widgets Migration
**Duration:** 1 week

**Widgets to Migrate:**
1. [ ] `ConnectionStatusBanner` - Connection indicator
2. [ ] `ConnectingOverlay` - Loading overlay
3. [ ] `ConnectionErrorDialog` - Error dialogs
4. [ ] `CustomNumberPicker` - Weight/rep picker
5. [ ] `CompactNumberPicker` - Compact variant
6. [ ] `ExercisePickerDialog` - Exercise selector
7. [ ] `StatsCard` - Statistics display
8. [ ] `ThemeToggle` - Theme switcher
9. [ ] `EmptyStateComponent` - Empty states
10. [ ] `CountdownCard` - Countdown timer
11. [ ] `RestTimerCard` - Rest timer
12. [ ] `WorkoutModeCard` - Mode selection card
13. [ ] `LoadingIndicator` - Custom loader

**Tasks:**
- [ ] Migrate each widget to Flutter
- [ ] Ensure Material 3 compliance
- [ ] Add iOS Cupertino variants where needed
- [ ] Test on both platforms
- [ ] Document widget usage

**Deliverables:**
- All 13 widgets migrated
- Platform-specific adaptations
- Widget documentation

### 4.5 Navigation Setup
**Duration:** 2-3 days

**Strategy:** Use `go_router` for type-safe navigation

**Routes:**
```dart
final router = GoRouter(
  routes: [
    GoRoute(
      path: '/',
      builder: (context, state) => const SplashScreen(),
    ),
    GoRoute(
      path: '/home',
      builder: (context, state) => const HomeScreen(),
    ),
    GoRoute(
      path: '/just_lift',
      builder: (context, state) => const JustLiftScreen(),
    ),
    GoRoute(
      path: '/single_exercise',
      builder: (context, state) => const SingleExerciseScreen(),
    ),
    GoRoute(
      path: '/active_workout',
      builder: (context, state) => const ActiveWorkoutScreen(),
    ),
    GoRoute(
      path: '/daily_routines',
      builder: (context, state) => const DailyRoutinesScreen(),
    ),
    GoRoute(
      path: '/weekly_programs',
      builder: (context, state) => const WeeklyProgramsScreen(),
    ),
    GoRoute(
      path: '/program_builder/:programId',
      builder: (context, state) {
        final programId = state.pathParameters['programId'];
        return ProgramBuilderScreen(programId: programId);
      },
    ),
    GoRoute(
      path: '/analytics',
      builder: (context, state) => const AnalyticsScreen(),
    ),
    GoRoute(
      path: '/settings',
      builder: (context, state) => const SettingsScreen(),
    ),
    GoRoute(
      path: '/connection_logs',
      builder: (context, state) => const ConnectionLogsScreen(),
    ),
  ],
);
```

**Tasks:**
- [ ] Set up go_router
- [ ] Define all routes
- [ ] Implement navigation guards
- [ ] Add deep linking support
- [ ] Test navigation flow

**Deliverables:**
- Complete navigation system
- Type-safe routing
- Deep linking working

---

## Phase 5: Platform-Specific Features (2 weeks)

### 5.1 Android-Specific Features
**Duration:** 3-4 days

**Features:**
- [ ] Foreground service for workout tracking
- [ ] Notification channel setup
- [ ] Wake lock implementation
- [ ] Adaptive icons
- [ ] Android 12+ splash screen
- [ ] Material You dynamic colors (optional)

**Tasks:**
- [ ] Create foreground service in native Android code
- [ ] Set up method channel for service communication
- [ ] Implement notification system
- [ ] Configure wake lock
- [ ] Test on multiple Android versions (8.0+)

**Code Example:**
```dart
// Platform channel for foreground service
class WorkoutService {
  static const platform = MethodChannel('com.vitruvian/workout_service');

  static Future<void> startService() async {
    try {
      await platform.invokeMethod('startWorkoutService');
    } on PlatformException catch (e) {
      print('Failed to start service: ${e.message}');
    }
  }

  static Future<void> stopService() async {
    try {
      await platform.invokeMethod('stopWorkoutService');
    } on PlatformException catch (e) {
      print('Failed to stop service: ${e.message}');
    }
  }
}
```

**Deliverables:**
- Foreground service working
- Notifications implemented
- Wake lock configured

### 5.2 iOS-Specific Features
**Duration:** 5-6 days

**Features:**
- [ ] iOS BLE permission handling
- [ ] Background modes for BLE
- [ ] iOS app lifecycle handling
- [ ] Cupertino widgets where appropriate
- [ ] iOS haptic feedback
- [ ] App Store compliance
- [ ] Privacy manifest (Privacy.xcprivacy)

**Tasks:**
- [ ] Configure Info.plist for BLE permissions
- [ ] Add background mode capabilities
- [ ] Implement iOS-specific BLE handling
- [ ] Create Cupertino variants for dialogs/pickers
- [ ] Set up haptic feedback engine
- [ ] Add privacy manifest
- [ ] Test on multiple iOS versions (14+)

**Info.plist Configuration:**
```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app requires Bluetooth to connect to your Vitruvian Trainer machine</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>This app requires Bluetooth to control your workout equipment</string>
<key>UIBackgroundModes</key>
<array>
    <string>bluetooth-central</string>
</array>
```

**Tasks:**
- [ ] Add all required permission descriptions
- [ ] Enable background modes
- [ ] Test BLE in background
- [ ] Verify App Store guidelines compliance
- [ ] Create Privacy.xcprivacy file

**Deliverables:**
- iOS BLE working in background
- Permissions properly requested
- Cupertino widgets implemented
- App Store ready

### 5.3 Haptic Feedback Implementation
**Duration:** 1-2 days

**Events for Haptics:**
- Rep completion
- Warmup completion
- Workout start/end
- Button presses
- Errors

**Tasks:**
- [ ] Use `flutter_vibrate` or native platform channels
- [ ] Implement haptic patterns for each event
- [ ] Add user setting to enable/disable
- [ ] Test on both platforms

**Code Example:**
```dart
class HapticService {
  static Future<void> lightImpact() async {
    await HapticFeedback.lightImpact();
  }

  static Future<void> mediumImpact() async {
    await HapticFeedback.mediumImpact();
  }

  static Future<void> heavyImpact() async {
    await HapticFeedback.heavyImpact();
  }

  static Future<void> selectionClick() async {
    await HapticFeedback.selectionClick();
  }

  static Future<void> repCompleted() async {
    await mediumImpact();
  }

  static Future<void> workoutCompleted() async {
    await heavyImpact();
    await Future.delayed(const Duration(milliseconds: 100));
    await heavyImpact();
  }
}
```

**Deliverables:**
- Haptic feedback working on both platforms
- User preference implemented

---

## Phase 6: Testing & Quality Assurance (2-3 weeks)

### 6.1 Unit Tests Migration
**Duration:** 1 week

**Test Files to Migrate (12 files):**
1. [ ] `protocol_builder_test.dart` - Binary protocol
2. [ ] `workout_bloc_test.dart` - Workout BLoC
3. [ ] `ble_bloc_test.dart` - BLE BLoC
4. [ ] `workout_repository_test.dart` - Repository
5. [ ] `rep_counting_test.dart` - Rep algorithm
6. [ ] `rep_tracking_test.dart` - Rep tracking
7. [ ] `workout_mode_test.dart` - Workout modes
8. [ ] `exercise_repository_test.dart` - Exercise repo
9. [ ] `preferences_service_test.dart` - Preferences
10. [ ] `start_workout_usecase_test.dart` - Use case
11. [ ] `database_test.dart` - Database operations
12. [ ] `bluetooth_service_test.dart` - BLE service

**Testing Framework:**
- `test` package for unit tests
- `mockito` or `mocktail` for mocking
- `bloc_test` for BLoC testing

**Tasks:**
- [ ] Set up test environment
- [ ] Migrate all 12 test files
- [ ] Achieve >80% code coverage
- [ ] Set up code coverage reporting
- [ ] Add CI/CD pipeline for tests

**Deliverables:**
- All unit tests migrated
- >80% code coverage
- CI/CD pipeline

### 6.2 Integration Tests
**Duration:** 3-4 days

**Integration Tests:**
1. [ ] Workout flow end-to-end
2. [ ] BLE connection flow
3. [ ] Database operations
4. [ ] Offline functionality
5. [ ] Navigation flows

**Tasks:**
- [ ] Create integration test suite
- [ ] Test critical user flows
- [ ] Test on both platforms
- [ ] Add to CI/CD pipeline

**Deliverables:**
- Integration tests passing
- Critical flows tested

### 6.3 Widget Tests
**Duration:** 3-4 days

**Widgets to Test:**
- [ ] HomeScreen
- [ ] ActiveWorkoutScreen
- [ ] ConnectionStatusBanner
- [ ] CustomNumberPicker
- [ ] ExercisePickerDialog
- [ ] All custom widgets

**Tasks:**
- [ ] Create widget test suite
- [ ] Test user interactions
- [ ] Test state changes
- [ ] Verify rendering

**Deliverables:**
- Widget tests passing
- UI behavior verified

### 6.4 Manual Testing
**Duration:** 1 week

**Test Checklist:**

**BLE Functionality:**
- [ ] Device scanning
- [ ] Connection establishment
- [ ] Connection stability (15+ min)
- [ ] Reconnection after disconnect
- [ ] Multiple connection attempts
- [ ] Protocol command sending
- [ ] Real-time data reception
- [ ] Characteristic notifications

**Workout Modes:**
- [ ] Old School mode
- [ ] Pump mode
- [ ] TUT mode
- [ ] TUT Beast mode
- [ ] Eccentric Only mode
- [ ] Echo mode (all 4 levels)
- [ ] Just Lift mode

**Core Features:**
- [ ] Rep counting accuracy
- [ ] Auto-stop detection
- [ ] Workout history saving
- [ ] Personal records detection
- [ ] Exercise library browsing
- [ ] Routine creation
- [ ] Weekly program scheduling
- [ ] Analytics calculations

**UI/UX:**
- [ ] Splash screen animation
- [ ] Theme switching
- [ ] Navigation flow
- [ ] Permission requests
- [ ] Error dialogs
- [ ] Loading states
- [ ] Empty states
- [ ] Haptic feedback

**Platform-Specific:**
- [ ] Android foreground service
- [ ] Android notifications
- [ ] iOS background BLE
- [ ] iOS permissions
- [ ] Cupertino widgets on iOS
- [ ] Material widgets on Android

**Performance:**
- [ ] App startup time
- [ ] Screen transition smoothness
- [ ] BLE data processing (100ms polling)
- [ ] Database query speed
- [ ] Memory usage
- [ ] Battery consumption during workout

**Deliverables:**
- Manual test report
- Bug list with priorities
- Performance metrics

---

## Phase 7: iOS-Specific Development & Testing (2-3 weeks)

### 7.1 iOS Build Configuration
**Duration:** 2-3 days

**Tasks:**
- [ ] Configure Xcode project
- [ ] Set up code signing
- [ ] Configure capabilities (Bluetooth, Background Modes)
- [ ] Set bundle identifier
- [ ] Configure app icons
- [ ] Set up launch screen
- [ ] Configure Info.plist
- [ ] Add Privacy.xcprivacy

**Deliverables:**
- iOS build configuration complete
- App signing working
- Capabilities configured

### 7.2 iOS-Specific Testing
**Duration:** 1.5 weeks

**Test Devices:**
- iPhone 12/13/14/15 (iOS 14+)
- iPad (optional)
- Various screen sizes

**Test Scenarios:**
- [ ] BLE connection on iOS
- [ ] Background BLE operation
- [ ] App lifecycle (background/foreground)
- [ ] Permission requests
- [ ] Haptic feedback
- [ ] Cupertino widgets
- [ ] iOS-specific UI patterns
- [ ] Memory management
- [ ] Battery usage

**Known iOS Considerations:**
1. **BLE Background Limitations:**
   - iOS limits background BLE operations
   - Must use background modes
   - Connection may pause when app backgrounded

2. **Permission Handling:**
   - More strict than Android
   - Must provide clear descriptions
   - Users can revoke at any time

3. **UI Guidelines:**
   - Follow Human Interface Guidelines
   - Use Cupertino widgets where appropriate
   - Adapt navigation patterns

**Deliverables:**
- iOS app fully tested
- Platform-specific issues resolved
- iOS compliance verified

### 7.3 App Store Preparation
**Duration:** 2-3 days

**Tasks:**
- [ ] Create App Store Connect listing
- [ ] Prepare screenshots (multiple sizes)
- [ ] Write app description
- [ ] Add keywords for SEO
- [ ] Create promotional graphics
- [ ] Set pricing (free)
- [ ] Configure in-app purchases (if any)
- [ ] Submit for review

**Deliverables:**
- App Store listing complete
- App submitted for review

---

## Phase 8: Optimization & Polish (1-2 weeks)

### 8.1 Performance Optimization
**Duration:** 4-5 days

**Tasks:**
- [ ] Profile app performance
- [ ] Optimize BLE data processing
- [ ] Optimize database queries
- [ ] Reduce app size (code splitting)
- [ ] Optimize image loading
- [ ] Reduce memory usage
- [ ] Optimize build time

**Tools:**
- Flutter DevTools
- Xcode Instruments
- Android Profiler

**Deliverables:**
- Performance improvements documented
- App size optimized
- Memory leaks fixed

### 8.2 UI/UX Polish
**Duration:** 3-4 days

**Tasks:**
- [ ] Refine animations
- [ ] Improve loading states
- [ ] Enhance error messages
- [ ] Add micro-interactions
- [ ] Improve accessibility
- [ ] Add tooltips/help text
- [ ] Refine color scheme
- [ ] Polish typography

**Deliverables:**
- UI/UX refinements complete
- Accessibility improved

### 8.3 Error Handling & Edge Cases
**Duration:** 2-3 days

**Tasks:**
- [ ] Handle all BLE errors gracefully
- [ ] Add retry mechanisms
- [ ] Improve error messages
- [ ] Handle network errors
- [ ] Handle low memory situations
- [ ] Handle permission denials
- [ ] Add crash reporting (Sentry/Firebase Crashlytics)

**Deliverables:**
- Comprehensive error handling
- Crash reporting configured

---

## Phase 9: Documentation & Deployment (1 week)

### 9.1 Code Documentation
**Duration:** 2-3 days

**Tasks:**
- [ ] Add dartdoc comments to all public APIs
- [ ] Document complex algorithms
- [ ] Create architecture documentation
- [ ] Document BLE protocol
- [ ] Create widget catalog
- [ ] Document state management patterns

**Deliverables:**
- Complete code documentation
- Architecture diagrams
- Widget catalog

### 9.2 User Documentation
**Duration:** 1-2 days

**Tasks:**
- [ ] Update README.md
- [ ] Create user guide
- [ ] Create troubleshooting guide
- [ ] Document BLE setup
- [ ] Create video tutorials (optional)

**Deliverables:**
- User documentation complete
- Troubleshooting guide

### 9.3 Deployment
**Duration:** 2-3 days

**Android:**
- [ ] Create signed release APK/AAB
- [ ] Upload to Play Store
- [ ] Configure store listing
- [ ] Submit for review

**iOS:**
- [ ] Create archive
- [ ] Upload to App Store Connect
- [ ] Submit for review
- [ ] Monitor review status

**Deliverables:**
- Apps deployed to both stores
- Release notes published

---

## Phase 10: Post-Launch (Ongoing)

### 10.1 Monitoring & Analytics
**Duration:** Ongoing

**Tasks:**
- [ ] Set up Firebase Analytics
- [ ] Monitor crash reports
- [ ] Track user engagement
- [ ] Monitor BLE connection success rates
- [ ] Track workout completion rates
- [ ] Gather user feedback

**Deliverables:**
- Analytics dashboard
- Monitoring alerts

### 10.2 Bug Fixes & Maintenance
**Duration:** Ongoing

**Tasks:**
- [ ] Address user-reported bugs
- [ ] Fix crash issues
- [ ] Improve BLE stability
- [ ] Performance improvements
- [ ] Security updates

**Deliverables:**
- Regular updates
- Bug fix releases

### 10.3 Feature Enhancements
**Duration:** Ongoing

**Planned Features:**
- [ ] Live charting visualization
- [ ] CSV export functionality
- [ ] Unit conversion (kg/lb)
- [ ] Cloud sync between devices
- [ ] Social features (leaderboards, sharing)
- [ ] Workout templates marketplace
- [ ] Apple Watch integration
- [ ] Android Wear integration

**Deliverables:**
- New feature releases
- User satisfaction improvements

---

## Migration Challenges & Mitigation

### Challenge 1: BLE Protocol Complexity
**Risk:** High
**Impact:** High

**Mitigation:**
- Port protocol builder byte-for-byte
- Create comprehensive tests for all commands
- Test with real hardware early
- Document all protocol details
- Keep reference to Android implementation

### Challenge 2: iOS Background BLE Limitations
**Risk:** Medium
**Impact:** High

**Mitigation:**
- Research iOS background modes thoroughly
- Test on real devices early
- Implement proper state restoration
- Add user notifications for connection status
- Educate users on iOS limitations

### Challenge 3: State Management Complexity
**Risk:** Medium
**Impact:** Medium

**Mitigation:**
- Use proven BLoC pattern
- Create comprehensive state tests
- Document state transitions
- Use freezed for immutable states
- Implement proper error states

### Challenge 4: Platform-Specific UI Differences
**Risk:** Low
**Impact:** Medium

**Mitigation:**
- Design for both platforms from start
- Use platform-aware widgets
- Test on both platforms regularly
- Follow platform guidelines
- Create platform-specific variants where needed

### Challenge 5: Database Migration
**Risk:** Medium
**Impact:** High

**Mitigation:**
- Preserve exact schema structure
- Test migrations thoroughly
- Create migration scripts from Android DB
- Implement data export/import
- Test with production data

### Challenge 6: Testing Coverage
**Risk:** Medium
**Impact:** Medium

**Mitigation:**
- Write tests during development
- Aim for >80% coverage
- Use TDD where appropriate
- Automate tests in CI/CD
- Regular code reviews

---

## Resource Requirements

### Team Composition (Recommended)

**Option 1: Full Team (Faster, 3-4 months)**
- 1 Flutter Senior Developer (Full-time)
- 1 Flutter Mid-level Developer (Full-time)
- 1 iOS Developer (Part-time, for iOS-specific work)
- 1 QA Engineer (Part-time)
- 1 UI/UX Designer (Part-time)

**Option 2: Small Team (Slower, 5-6 months)**
- 1 Flutter Senior Developer (Full-time)
- 1 Flutter Junior Developer (Full-time)
- 1 QA Engineer (Part-time)

**Option 3: Solo Developer (Slowest, 6-8 months)**
- 1 Experienced Flutter Developer (Full-time)
- External QA support (Part-time)

### Hardware Requirements
- Mac with Xcode (for iOS development)
- Physical Android device (API 26+)
- Physical iOS device (iOS 14+)
- Vitruvian Trainer machine for testing
- Multiple test devices (various OS versions)

### Software Requirements
- Flutter SDK (latest stable)
- Android Studio with Flutter plugin
- Xcode 14+
- VS Code (optional)
- Git & GitHub
- Firebase account (for analytics/crashlytics)
- Apple Developer account ($99/year)
- Google Play Developer account ($25 one-time)

---

## Timeline Summary

| Phase | Duration | Key Deliverables |
|-------|----------|------------------|
| Phase 1: Setup | 2-3 weeks | Project structure, dependencies |
| Phase 2: Data Layer | 3-4 weeks | Database, BLE, repositories |
| Phase 3: Domain Layer | 2 weeks | Entities, use cases, business logic |
| Phase 4: Presentation | 4-5 weeks | UI screens, state management |
| Phase 5: Platform Features | 2 weeks | Android/iOS specific features |
| Phase 6: Testing | 2-3 weeks | Unit, integration, widget tests |
| Phase 7: iOS Development | 2-3 weeks | iOS-specific work & testing |
| Phase 8: Optimization | 1-2 weeks | Performance, polish |
| Phase 9: Documentation | 1 week | Docs, deployment |
| Phase 10: Post-Launch | Ongoing | Monitoring, maintenance |

**Total Estimated Duration:**
- **Minimum (Aggressive):** 16-20 weeks (4-5 months)
- **Recommended (Realistic):** 20-26 weeks (5-6.5 months)
- **Maximum (Conservative):** 26-32 weeks (6.5-8 months)

**Factors Affecting Timeline:**
- Team size and experience
- Availability of test hardware
- iOS App Store review time (1-2 weeks)
- Unexpected technical challenges
- Scope changes

---

## Success Criteria

### Technical Success Criteria
- [ ] All 84 source files migrated to Flutter
- [ ] 10 database tables with full schema parity
- [ ] BLE protocol 100% functional
- [ ] All 6 workout modes working correctly
- [ ] Rep counting algorithm accuracy >95%
- [ ] >80% code coverage
- [ ] All screens migrated (14+)
- [ ] Android & iOS builds successful
- [ ] App Store & Play Store approved

### Functional Success Criteria
- [ ] Users can scan & connect to devices
- [ ] All workout modes function correctly
- [ ] Workout history saves properly
- [ ] Personal records tracked accurately
- [ ] Exercise library accessible
- [ ] Routines & programs work correctly
- [ ] Analytics display correctly
- [ ] Theme switching works
- [ ] Permissions handled properly

### Performance Success Criteria
- [ ] BLE polling at 100ms (10Hz)
- [ ] App startup < 3 seconds
- [ ] Screen transitions < 300ms
- [ ] Database queries < 100ms
- [ ] Memory usage < 200MB
- [ ] App size < 50MB
- [ ] Battery drain < 10%/hour during workout

### User Experience Success Criteria
- [ ] UI matches Android app design
- [ ] iOS feels native with Cupertino widgets
- [ ] Haptic feedback works on both platforms
- [ ] Error messages are clear
- [ ] Loading states are smooth
- [ ] Accessibility score > 90%

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation Priority |
|------|------------|--------|-------------------|
| BLE protocol issues | Medium | High | **Critical** |
| iOS background limitations | High | High | **Critical** |
| Database migration errors | Low | High | **High** |
| State management complexity | Medium | Medium | **Medium** |
| UI/UX differences | Low | Medium | **Medium** |
| App Store rejection | Medium | High | **High** |
| Performance issues | Low | Medium | **Medium** |
| Testing coverage gaps | Medium | Medium | **Medium** |
| Timeline delays | Medium | Low | **Low** |

---

## Flutter Package Dependencies (Final List)

```yaml
# pubspec.yaml

name: vitruvian_phoenix
description: Vitruvian Trainer control app for Android & iOS
version: 1.0.0+1

environment:
  sdk: '>=3.0.0 <4.0.0'

dependencies:
  flutter:
    sdk: flutter

  # State Management
  flutter_bloc: ^8.1.3
  equatable: ^2.0.5

  # Dependency Injection
  get_it: ^7.6.0
  injectable: ^2.3.0

  # BLE
  flutter_blue_plus: ^1.31.0

  # Database
  sqflite: ^2.3.0
  drift: ^2.14.0

  # Local Storage
  shared_preferences: ^2.2.0
  hive: ^2.2.3
  hive_flutter: ^1.1.0

  # Navigation
  go_router: ^13.0.0

  # Networking & Serialization
  dio: ^5.3.0
  json_annotation: ^4.8.1
  freezed_annotation: ^2.4.1

  # Functional Programming
  dartz: ^0.10.1

  # UI
  cached_network_image: ^3.3.0
  shimmer: ^3.0.0

  # Charts
  fl_chart: ^0.66.0

  # Permissions
  permission_handler: ^11.0.0

  # Logging
  logger: ^2.0.0

  # Utilities
  intl: ^0.19.0
  uuid: ^4.0.0
  path_provider: ^2.1.0

  # Haptics
  vibration: ^1.8.0

dev_dependencies:
  flutter_test:
    sdk: flutter

  # Testing
  bloc_test: ^9.1.0
  mocktail: ^1.0.0
  fake_async: ^1.3.1

  # Code Generation
  build_runner: ^2.4.6
  json_serializable: ^6.7.1
  freezed: ^2.4.5
  drift_dev: ^2.14.0
  injectable_generator: ^2.4.0

  # Linting
  flutter_lints: ^3.0.0

flutter:
  uses-material-design: true

  assets:
    - assets/exercises/
    - assets/images/

  fonts:
    - family: Roboto
      fonts:
        - asset: fonts/Roboto-Regular.ttf
        - asset: fonts/Roboto-Bold.ttf
          weight: 700
```

---

## Next Steps (Immediate Actions)

### Week 1: Project Kickoff
1. **Day 1-2:** Create Flutter project, set up Git repository
2. **Day 3:** Configure build systems for Android & iOS
3. **Day 4:** Add core dependencies to pubspec.yaml
4. **Day 5:** Set up project structure and base classes

### Week 2: Foundation
1. **Day 1-2:** Set up dependency injection with get_it
2. **Day 3:** Create theme system and color scheme
3. **Day 4-5:** Begin database schema migration with Drift

### Week 3: BLE Implementation
1. **Day 1-2:** Set up flutter_blue_plus and BLE service
2. **Day 3-4:** Port protocol builder to Dart
3. **Day 5:** Begin BLE testing with real hardware

### Week 4-20: Continue with roadmap phases...

---

## Conclusion

This roadmap provides a comprehensive path to migrate the Vitruvian Project Phoenix Android app to Flutter, enabling full Android and iOS support. The migration preserves all existing functionality while leveraging Flutter's cross-platform capabilities.

**Key Success Factors:**
1. **Thorough Planning:** This roadmap covers all aspects
2. **Early Hardware Testing:** Test BLE early and often
3. **Test Coverage:** Maintain >80% test coverage
4. **Platform Awareness:** Respect iOS and Android differences
5. **Incremental Migration:** Complete one feature at a time
6. **User Feedback:** Gather feedback throughout development

**Expected Outcomes:**
- Single codebase supporting Android & iOS
- Feature parity with current Android app
- Native performance on both platforms
- Reduced maintenance burden
- Faster feature development
- Larger user base with iOS support

**Project Success Probability:**
- **Technical Feasibility:** 95% - Flutter fully supports all required features
- **Timeline Accuracy:** 80% - Realistic estimates with buffers
- **Resource Availability:** Variable - Depends on team composition
- **User Adoption:** 90% - Existing Android users + new iOS users

This migration will future-proof the Vitruvian Project Phoenix, enabling it to reach a wider audience and providing a solid foundation for future enhancements.

---

**Document Version:** 1.0
**Last Updated:** November 8, 2025
**Status:** Ready for Implementation
**Next Review:** After Phase 1 completion
