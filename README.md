<div align="center">

<img src="images/logo.png" alt="PL Judge logo" width="120"/>

# PL Judge

**An Android app that judges your powerlifting reps in real time.**

Turn your phone into an automatic judge for squat, bench press and deadlift —
either by strapping it to your body (motion sensors) or by pointing a camera at
yourself (on-device pose estimation). No cloud, no account, everything runs on
the device.

</div>

---

## Overview

PL Judge is a Kotlin / Jetpack Compose app built for lifters who train alone and
want objective, consistent feedback on rep depth and lockout — the same things a
powerlifting referee looks for. It counts valid reps, calls **GOOD LIFT / NO
LIFT**, tracks time under tension and keeps a full history of every set so you
can see how you progress over time.

<div align="center">
<img src="images/1.jpg" alt="Judge screen" width="240"/>
&nbsp;&nbsp;
<img src="images/2.jpg" alt="Live judging" width="240"/>
&nbsp;&nbsp;
<img src="images/3.jpg" alt="Set summary" width="240"/>
</div>

## Features

- **Two judging modes**
  - **Sensor mode** — wear the phone on the moving limb (quad, triceps, …). It
    uses the device rotation-vector / accelerometer to measure joint angle and
    detect when you hit depth and lock out. A 3D "world-up" reference vector is
    used instead of a single Euler angle to avoid gimbal lock in any orientation.
  - **Visual mode** — point the camera at yourself and let on-device pose
    estimation ([MediaPipe Pose Landmarker](https://developers.google.com/mediapipe)
    + ML Kit) track your body joints and judge the rep, with a live skeleton
    overlay.
- **All the main lifts** — Squat, Bench Press, Deadlift and Sumo Deadlift, each
  with its own depth/angle threshold.
- **Custom exercises & hold points** — add your own movements and configure
  multiple "hold" checkpoints (e.g. the press command in bench).
- **Per-lift calibration** — tune the depth threshold to your body and setup.
- **Live feedback** — audio cues, on-screen GOOD LIFT / NO LIFT, rep counter and
  target reps.
- **History & statistics** — every set is saved locally; browse it, drill into a
  single set, and view per-lift trends.
- **Powerlifting total** — combine your best squat / bench / deadlift into a
  competition total.
- **Screen recording** — capture the visual-judge session with the overlay.
- **Backup & restore** — export/import your data so nothing is lost.
- **Localized** — English, Italian, Spanish, French, German, Portuguese,
  Russian and Japanese.
- **100% offline** — no network permission, no telemetry, no sign-in.

## Tech stack

| Area              | Technology |
|-------------------|------------|
| Language          | Kotlin |
| UI                | Jetpack Compose + Material 3 |
| Architecture      | MVVM (ViewModel + Kotlin Flow) |
| Navigation        | Navigation Compose |
| Persistence       | Room |
| Camera            | CameraX |
| Pose estimation   | MediaPipe Tasks Vision (Pose Landmarker) + ML Kit Pose Detection |
| Sensors           | Rotation Vector / Accelerometer / Magnetometer |
| Build             | Gradle (Kotlin DSL) + version catalog, KSP |

## Requirements

- Android Studio (Ladybug or newer recommended)
- Android SDK 35 (compile/target), **minimum Android 8.0 (API 26)**
- A physical device is strongly recommended — sensor and camera judging need real
  hardware.

## Getting started

```bash
git clone https://github.com/francescofusca/powerlifting-judge.git
cd powerlifting-judge

# Build a debug APK
./gradlew assembleDebug

# …or install straight onto a connected device
./gradlew installDebug
```

Or just open the project in Android Studio and hit **Run**.

> The MediaPipe pose model (`app/src/main/assets/pose_landmarker_heavy.task`) is
> bundled in the repository, so visual mode works out of the box.

## How to use

1. **Pick a lift** from the home screen.
2. **Choose a mode** — sensor (wear the phone) or visual (camera).
3. **Calibrate** the depth threshold if needed.
4. **Press START**, do your set, and let the app call each rep.
5. Review the **set summary**, then find it later in **History** and **Stats**.

## Project structure

```
app/src/main/java/com/ff9/poweliftjudge/
├── data/          # Room repository, DataStore preferences, sensors, backup
├── database/      # Room entities & DAO
├── model/         # LiftType, HoldPoint, RepStats, CustomExercise
├── navigation/    # Compose navigation graph
└── ui/
    ├── home/ judge/ visual/ calibrate/   # capture & judging flows
    ├── history/ detail/ stats/ summary/  # results & analytics
    ├── total/ settings/                  # powerlifting total & settings
    └── theme/                            # Compose theming
```

## License

Released under the [MIT License](LICENSE).

## Author

Made by **Francesco Fusca** — feedback, issues and pull requests are welcome.
