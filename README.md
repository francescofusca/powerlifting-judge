# PL Judge ⚡

<div align="center">

<img src="images/logo.png" alt="PL Judge Logo" width="200"/>

**Automatic Powerlifting Movement Judging System Using Device Sensors**

*A university project for Mobile Applications course in Kotlin*

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26-orange.svg)](https://developer.android.com/about/versions/oreo)
[![License](https://img.shields.io/badge/License-Educational-red.svg)](LICENSE)

</div>

---

## 📋 Table of Contents

- [Overview](#overview)
- [Project Origins](#project-origins)
- [Key Features](#key-features)
- [How It Works](#how-it-works)
- [Technical Architecture](#technical-architecture)
- [Installation](#installation)
- [Usage Guide](#usage-guide)
- [Configuration](#configuration)
- [Database Schema](#database-schema)
- [Sensor Implementation](#sensor-implementation)
- [Future Development](#future-development)
- [Building from Source](#building-from-source)
- [Contributing](#contributing)
- [Author](#author)

---

## 🎯 Overview

**PL Judge** is an Android application that leverages device sensors to automatically validate powerlifting movements in real-time. By utilizing the accelerometer, magnetometer, and rotation vector sensors, the app can accurately measure angular displacement during lifts and provide instant feedback through visual, audio, and haptic signals.

The application supports four major powerlifting movements:
- **Squat** - Validates depth by measuring hip angle
- **Bench Press** - Tracks bar path and elbow angle
- **Deadlift (Conventional)** - Measures back angle and lockout position
- **Deadlift (Sumo)** - Adapted for sumo stance positioning

### Why This Matters

In powerlifting competitions, human judges determine whether a lift meets specific criteria. This app aims to:
- Provide **objective measurements** for training purposes
- Help athletes **develop consistency** in their movement patterns
- Offer **real-time feedback** without needing a coach present
- Track **progression over time** with detailed statistics

---

## 🎓 Project Origins

This project was developed as part of the **Mobile Applications** university course, focusing on:

- **Sensor Integration**: Implementing sensor fusion techniques for accurate orientation tracking
- **Real-time Processing**: Handling continuous data streams with performance optimization
- **User Experience**: Creating intuitive interfaces for athletic performance tracking
- **Database Management**: Using Room for persistent local storage
- **Kotlin Development**: Applying modern Android development practices

The app demonstrates practical applications of mobile sensors beyond typical use cases, showcasing how smartphones can serve as affordable training tools for athletes.

---

## ✨ Key Features

### Core Functionality
- ✅ **4 Lift Types Supported** - Squat, Bench Press, Conventional Deadlift, Sumo Deadlift
- ✅ **Auto-Calibration System** - Automatically records starting position for accurate measurements
- ✅ **Configurable Countdown Timer** - 1-10 second delay before lift starts (default: 3 seconds)
- ✅ **Real-time Rep Counter** - Automatically increments for each valid repetition
- ✅ **Customizable Thresholds** - Adjust angle requirements per lift type
- ✅ **Multi-language Support** - English, Italian, Spanish, Portuguese, French, German, Russian, Japanese

### Feedback Systems
- 🎨 **Visual Feedback**
  - Circular progress bar showing current angle vs target
  - Color-coded status indicators (red/yellow/neon green)
  - Large, readable text for distance viewing
  - Rep counter display

- 🔊 **Audio Feedback**
  - Countdown completion sound
  - Success beep for each valid rep
  - Configurable sound selection

- 📳 **Haptic Feedback**
  - Vibration on countdown completion (200ms)
  - Vibration on valid rep (300ms)
  - Compatible with Android 12+ vibrator APIs

### Data Management
- 💾 **Local Database** - Room-based SQLite storage
- 📊 **Detailed Statistics** - Time tracking, rep timing, session summaries
- 🗂️ **Session History** - View past workouts with full details
- ✏️ **Editable Records** - Modify rep counts and add notes
- 🗑️ **Data Management** - Clear history, delete individual sessions

### User Interface
- 🌑 **Modern Dark Theme** - Professional design with neon accents
- 🎴 **Card-based Layout** - Clean, intuitive navigation
- 🎬 **Smooth Animations** - Polished user experience
- 📱 **Responsive Design** - Optimized for various screen sizes

---

## 🔧 How It Works

### Complete Workflow

#### 1. Pre-Lift Setup
```
User selects lift type (e.g., Squat)
   ↓
User positions phone securely (pocket, arm strap, etc.)
   ↓
User presses START button
```

#### 2. Countdown Phase
```
Countdown begins (configurable 1-10 seconds)
   ↓
Visual countdown display updates
   ↓
Status shows "PREPARATI..." (Get Ready)
   ↓
User assumes starting position
```

#### 3. Calibration (Automatic)
```
Countdown reaches 0
   ↓
App plays start sound (start.wav)
   ↓
Device vibrates (200ms)
   ↓
Current pitch angle recorded as baseline (startPitch)
   ↓
Status changes to "VAI!" (Go!)
```

#### 4. Rep Execution
```
User performs movement
   ↓
App continuously calculates: deltaAngle = currentPitch - startPitch
   ↓
Progress bar updates in real-time (20 Hz)
   ↓
When deltaAngle ≥ targetAngle:
   ├─ Visual: Screen turns neon green
   ├─ Audio: Plays bip.wav sound
   ├─ Haptic: 300ms vibration
   ├─ Counter: Increments rep count
   └─ Database: Saves rep with timestamp
```

#### 5. Rep Reset (Hysteresis)
```
User returns to starting position
   ↓
When deltaAngle < (targetAngle × 0.7):
   ├─ isLiftGood flag resets
   ├─ Status shows "PRONTO PER PROSSIMA REP"
   └─ Ready for next rep
```

#### 6. Session Completion
```
User presses FINISCHI button
   ↓
Dialog appears with options:
   ├─ SALVA (Save) - Saves session to database
   ├─ MODIFICA (Edit) - Adjust rep count
   ├─ ELIMINA (Delete) - Discard session
   └─ CONTINUA (Continue) - Keep training
```

### Movement-Specific Logic

#### Squat
- **Starting Position**: Standing upright (0° pitch)
- **Target Position**: Squatting down (90° pitch by default)
- **Detection**: Measures hip angle descent
- **Phone Placement**: Tight front pocket or leg strap

#### Bench Press
- **Starting Position**: Arms extended (0° pitch)
- **Target Position**: Bar to chest (90° pitch by default)
- **Detection**: Measures arm angle
- **Phone Placement**: Arm strap on upper arm

#### Conventional Deadlift
- **Starting Position**: Bent over position (~60° pitch)
- **Target Position**: Standing upright (0° pitch)
- **Detection**: Measures 60° range of motion
- **Phone Placement**: Tight front pocket or leg strap

#### Sumo Deadlift
- **Starting Position**: Sumo stance bent position (~60° pitch)
- **Target Position**: Standing upright (0° pitch)
- **Detection**: Measures 60° range of motion
- **Phone Placement**: Tight front pocket or leg strap

---

## 🏗️ Technical Architecture

### Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Language** | Kotlin | Modern Android development |
| **Min SDK** | 26 (Android 8.0 Oreo) | Wide device compatibility |
| **Target SDK** | 35 | Latest Android features |
| **Database** | Room (SQLite) | Local data persistence |
| **Architecture** | MVVM pattern | Separation of concerns |
| **Sensors** | Rotation Vector, Accelerometer, Magnetometer | Motion tracking |
| **UI Framework** | Material Design 3 | Modern UI components |
| **Storage** | SharedPreferences | Settings persistence |
| **Async** | Kotlin Coroutines | Background operations |

### Project Structure

```
app/src/main/
├── java/com/ff9/poweliftjudge/
│   ├── MainActivity.kt              # Entry point, lift selection
│   ├── JudgeActivity.kt            # Core judging logic, sensor processing
│   ├── HistoryActivity.kt          # Session history display
│   ├── SettingsActivity.kt         # User configuration
│   ├── SetSummaryActivity.kt       # Session overview
│   ├── SetDetailActivity.kt        # Individual session details
│   ├── LiftAdapter.kt              # RecyclerView adapter for history
│   ├── RepStatsAdapter.kt          # Adapter for rep statistics
│   ├── LocaleHelper.kt             # Multi-language support
│   └── database/
│       ├── LiftDatabase.kt         # Room database singleton
│       ├── Lift.kt                 # Lift entity
│       └── LiftDao.kt              # Data access object
├── res/
│   ├── layout/                     # XML layouts
│   ├── values/                     # Strings, colors, themes
│   ├── values-{lang}/              # Localized strings
│   ├── drawable/                   # Icons and graphics
│   ├── raw/                        # Audio files (bip.wav, start.wav)
│   ├── anim/                       # Animations
│   └── xml/                        # Backup rules
└── AndroidManifest.xml             # App configuration
```

### Key Classes

#### JudgeActivity.kt
**Responsibilities:**
- Sensor registration and management
- Sensor fusion (rotation vector with accelerometer/magnetometer fallback)
- Pitch angle calculation using rotation matrices
- UI throttling (50ms minimum between updates)
- Rep validation with hysteresis (70% reset threshold)
- Audio playback with error handling
- Vibration control (legacy and modern APIs)
- Time tracking and statistics collection

**Key Variables:**
```kotlin
private var startPitch: Float? = null          // Calibration baseline
private var targetAngle: Float = 90f           // Required angle
private var currentDelta: Float = 0f           // Current vs baseline
private var isLiftGood: Boolean = false        // Rep state flag
private var repCount: Int = 0                  // Valid reps counter
private var lastUiUpdate: Long = 0             // UI throttling
```

#### LiftDatabase.kt
**Room Configuration:**
```kotlin
@Database(entities = [Lift::class], version = 2, exportSchema = false)
abstract class LiftDatabase : RoomDatabase() {
    abstract fun liftDao(): LiftDao

    companion object {
        @Volatile
        private var INSTANCE: LiftDatabase? = null

        fun getDatabase(context: Context): LiftDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    LiftDatabase::class.java,
                    "lift_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
```

---

## 📥 Installation

### Method 1: Download APK (Easiest)

1. Go to [Releases](https://github.com/francescofusca/powerlifting-judge/releases)
2. Download the latest `app-debug.apk` file
3. On your Android device:
   - Enable **Install from Unknown Sources** in Settings
   - Open the downloaded APK file
   - Follow installation prompts

### Method 2: Build from Source

See [Building from Source](#building-from-source) section below.

---

## 📖 Usage Guide

### First Time Setup

1. **Launch the app** - You'll see the main menu with four lift cards
2. **Go to Settings** (gear icon)
   - Adjust angle thresholds for each lift type
   - Set countdown timer duration (1-10 seconds)
   - Choose preferred language
   - Select audio feedback sounds

### Performing a Lift Session

#### For Squat Example:

1. **Select "SQUAT"** card from main menu
2. **Position your phone**:
   - Place phone in tight front pocket (just below hips)
   - OR use leg strap to secure phone vertically
   - **CRITICAL**: Phone must NOT move during reps
3. **Get ready**: Stand in your starting position
4. **Press START button**
5. **During countdown** (3 seconds default):
   - Assume your standing position
   - Make sure you're stable
6. **When countdown ends**:
   - You'll hear a sound and feel vibration
   - Status shows "VAI!" (Go!)
   - The app has recorded your starting position
7. **Perform squats**:
   - Descend until you hit the target depth (90° default)
   - When valid, you'll get:
     - Green visual feedback
     - Beep sound
     - Vibration
     - Rep counter increment
8. **Return to starting position**
   - Wait for "PRONTO PER PROSSIMA REP" status
   - Perform next rep
9. **Finish session**:
   - Press "FINISCHI" button
   - Choose SALVA to save your session

### Viewing History

1. **Tap "HISTORY"** from main menu
2. **See all sessions** sorted by date (newest first)
3. **Tap any session** to view details:
   - Total reps
   - Total time
   - Average time per rep
   - Individual rep times
   - Session notes
4. **Edit or delete** sessions as needed

---

## ⚙️ Configuration

### Threshold Settings

Access via **Settings** button on main menu:

| Lift Type | Default Threshold | Description |
|-----------|------------------|-------------|
| Squat | 90° | Depth angle from standing to bottom |
| Bench Press | 90° | Arm angle from extended to chest |
| Deadlift | 60° | Range of motion from bent to standing |
| Sumo Deadlift | 60° | Range of motion from sumo to standing |

**Customization Tips:**
- **Increase angle** for deeper squats (e.g., 95° for below parallel)
- **Decrease angle** if phone placement is higher/lower
- **Adjust per your mobility** and training goals

### Countdown Timer

- **Range**: 1-10 seconds
- **Default**: 3 seconds
- **Purpose**: Allows you to position yourself correctly before calibration

### Language Selection

Supported languages:
- 🇬🇧 English
- 🇮🇹 Italian
- 🇪🇸 Spanish
- 🇵🇹 Portuguese
- 🇫🇷 French
- 🇩🇪 German
- 🇷🇺 Russian
- 🇯🇵 Japanese

### Audio Settings

- **Start Sound**: Played when countdown finishes (default: start.wav)
- **Rep Sound**: Played for each valid rep (default: bip.wav)
- Sounds can be changed in Settings

---

## 🗄️ Database Schema

### Lift Entity

```kotlin
@Entity(tableName = "lifts")
data class Lift(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val type: String,              // "Squat", "Bench Press", etc.
    val date: Long,                // Unix timestamp
    val valid: Boolean,            // Always true for saved lifts
    val reps: Int,                 // Number of repetitions
    val totalTime: Long,           // Total session duration (ms)
    val repTimes: String,          // JSON array of individual rep times
    val notes: String = ""         // User notes
)
```

### DAO Operations

```kotlin
interface LiftDao {
    @Insert
    suspend fun insertLift(lift: Lift)

    @Query("SELECT * FROM lifts ORDER BY date DESC")
    suspend fun getAll(): List<Lift>

    @Query("DELETE FROM lifts")
    suspend fun deleteAll()

    @Update
    suspend fun updateLift(lift: Lift)

    @Delete
    suspend fun deleteLift(lift: Lift)
}
```

### Database Version History

- **Version 1**: Initial schema (id, type, date, valid)
- **Version 2**: Added reps, totalTime, repTimes, notes fields
  - Uses `.fallbackToDestructiveMigration()` - upgrades will wipe data

---

## 📡 Sensor Implementation

### Sensor Fusion Strategy

The app uses a **hierarchical sensor approach** for maximum device compatibility:

#### Primary: Rotation Vector Sensor
```kotlin
TYPE_ROTATION_VECTOR
```
- Most accurate orientation data
- Already fused by device's sensor HAL
- Low computational overhead
- **Preferred when available**

#### Fallback: Accelerometer + Magnetometer
```kotlin
TYPE_ACCELEROMETER + TYPE_MAGNETIC_FIELD
```
- Manual sensor fusion using `SensorManager.getRotationMatrix()`
- Used when rotation vector unavailable
- Slightly more CPU intensive
- Adequate accuracy for most use cases

### Pitch Calculation

```kotlin
// Get rotation matrix from sensor data
SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)

// Calculate orientation angles
SensorManager.getOrientation(rotationMatrix, orientation)

// Extract pitch (forward/backward tilt)
val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
```

### Performance Optimizations

#### UI Throttling
```kotlin
private val UI_UPDATE_INTERVAL = 50L // milliseconds (20 Hz)

override fun onSensorChanged(event: SensorEvent) {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastUiUpdate < UI_UPDATE_INTERVAL) {
        return // Skip this update
    }
    lastUiUpdate = currentTime
    // Process sensor data...
}
```

**Benefits:**
- Reduces CPU usage
- Prevents UI lag
- Maintains smooth 60fps experience
- Sensors still sampled at full rate internally

#### Hysteresis for Rep Detection

```kotlin
val RESET_THRESHOLD = 0.7f // 70% of target

if (currentDelta >= targetAngle && !isLiftGood) {
    // Rep completed
    isLiftGood = true
    registerRep()
}

if (currentDelta < (targetAngle * RESET_THRESHOLD) && isLiftGood) {
    // Reset for next rep
    isLiftGood = false
}
```

**Prevents:**
- Multiple counts from sensor noise near threshold
- Accidental triggers from small movements
- Need for completely returning to start position

### Sensor Lifecycle Management

```kotlin
override fun onResume() {
    super.onResume()
    // Register sensors
    sensorManager.registerListener(
        this,
        rotationSensor,
        SensorManager.SENSOR_DELAY_GAME
    )
}

override fun onPause() {
    super.onPause()
    // Unregister to save battery
    sensorManager.unregisterListener(this)
}

override fun onDestroy() {
    super.onDestroy()
    // Release media players
    mediaPlayerBip?.release()
    mediaPlayerStart?.release()
}
```

---

## 🚀 Future Development

This project is currently at v1.0 for academic purposes. Potential future enhancements:

### High Priority
- [ ] **Export Functionality**
  - CSV export for analysis in Excel/Sheets
  - PDF workout summaries
  - Share sessions via social media

- [ ] **Progress Tracking**
  - Charts showing rep volume over time
  - Personal records tracking
  - Monthly/weekly statistics

- [ ] **Additional Lifts**
  - Overhead Press
  - Romanian Deadlift
  - Front Squat
  - Custom lift creation

### Medium Priority
- [ ] **Wearable Integration**
  - Smartwatch support (Wear OS)
  - Direct sensor data from watch
  - Watch face complications

- [ ] **Competition Mode**
  - Multi-athlete sessions
  - Live leaderboards
  - Meet simulation mode

- [ ] **Form Analysis**
  - Machine learning for bar path detection
  - Video recording with angle overlay
  - Form tips and corrections

### Low Priority
- [ ] **Cloud Sync**
  - Cross-device synchronization
  - Backup and restore
  - Coach/athlete sharing

- [ ] **Social Features**
  - Training groups
  - Challenges
  - Global leaderboards

---

## 🔨 Building from Source

### Prerequisites

- **Android Studio** Arctic Fox (2020.3.1) or newer
- **JDK** 11 or higher
- **Android SDK** with API 26-35
- **Git** for cloning the repository

### Clone Repository

```bash
git clone https://github.com/francescofusca/powerlifting-judge.git
cd powerlifting-judge
```

### Open in Android Studio

1. Launch Android Studio
2. Select **File > Open**
3. Navigate to cloned repository folder
4. Click **OK**
5. Wait for Gradle sync to complete

### Build Commands

#### Using Android Studio
- **Debug Build**: `Build > Build Bundle(s) / APK(s) > Build APK(s)`
- **Run on Device**: Click ▶️ Run button or `Shift+F10`

#### Using Command Line
```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease

# Install directly to connected device
./gradlew installDebug

# Run tests
./gradlew test
./gradlew connectedAndroidTest

# Clean build artifacts
./gradlew clean
```

### Output Locations

- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `app/build/outputs/apk/release/app-release.apk`

### Required Files

Ensure these audio files exist in `app/src/main/res/raw/`:
- `bip.wav` - Rep validation sound
- `start.wav` - Countdown completion sound

---

## 🤝 Contributing

While this is primarily an educational project, suggestions and feedback are welcome!

### Reporting Issues

If you encounter bugs or have feature requests:

1. Check [existing issues](https://github.com/francescofusca/powerlifting-judge/issues)
2. Create a new issue with:
   - Clear description
   - Steps to reproduce (for bugs)
   - Device model and Android version
   - Screenshots if applicable

### Pull Requests

For code contributions:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable names
- Comment complex logic
- Keep functions focused and small

---

## 📄 License

This project is developed for educational purposes as part of a university Mobile Applications course.

**Usage Terms:**
- Free to use for personal, educational, and training purposes
- Attribution appreciated but not required
- Not intended for commercial competition judging
- No warranty provided - use at your own risk

---

## 👤 Author

**by ff9**

---

## 🙏 Acknowledgments

- **University Course**: Mobile Applications in Kotlin
- **Powerlifting Community**: For inspiration and testing feedback
- **Android Documentation**: Comprehensive sensor and Room database guides
- **Material Design**: For modern UI components and guidelines

---

## 📞 Support

For questions or issues:

1. Check the [documentation](#table-of-contents) above
2. Search [existing issues](https://github.com/francescofusca/powerlifting-judge/issues)
3. Create a new issue if needed

---

<div align="center">

**Made with 💪 and ☕ for powerlifters everywhere**

*Keep lifting heavy, judge fairly*

</div>
