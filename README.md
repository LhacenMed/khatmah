# Khatmah — خَتْمَة

**Khatmah** is a free, open-source Android app for Muslims who want to complete the Quran in a structured, consistent way. It schedules a personalised reading plan (khatmah), tracks Qadaa prayers and fasts, displays accurate prayer times, provides Adhkar collections, and shows a Qibla compass — all in one place.

> **Minimum Android version:** 8.0 (API 24+, some features require API 26)  
> **Language:** Kotlin · Jetpack Compose  
> **License:** MIT

---

## Features

| Category            | Highlights                                                                                                                      |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| **Khatmah planner** | Custom start Juz, duration, and daily reading amount; session-by-session breakdown                                              |
| **Quran reader**    | Text (Hafs & Warsh), scanned Mushaf images, SVG vector pages, QCF4 font pages; aya-level audio playback                         |
| **Prayer times**    | Accurate calculation engine; automatic or manual location; 10+ calculation methods; DST & manual correction; Hijri date display |
| **Qibla compass**   | Live sensor-based needle with haptic alignment feedback; gesture tilt and locked-bearing mode                                   |
| **Adhkar**          | Built-in morning, evening, sleep, and after-prayer Adhkar; custom Adhkar cards with full CRUD                                   |
| **Qadaa tracker**   | Track missed prayers and fasts; daily goal with streak; mark-as-done with history log                                           |
| **Reminders**       | Per-prayer Adhan notifications (custom sounds, silent, or device alert); morning / evening Adhkar alarms                        |
| **Widget**          | Glance-based home-screen widget with live countdown / elapsed Chronometer and full prayer list                                  |
| **Themes**          | Light / dark / system; high-contrast dark mode; dynamic colour (Android 12+); 5 built-in palettes                               |
| **Languages**       | English and Arabic (RTL), with system-default fallback                                                                          |

---

## Screenshots

> _Add screenshots here once available._

---

## Getting Started

### Prerequisites

| Tool           | Version                            |
| -------------- | ---------------------------------- |
| Android Studio | Hedgehog (2023.1.1) or newer       |
| JDK            | 11                                 |
| Android SDK    | API 36 (compile), API 24 (minimum) |
| CMake          | 3.22.1 (for native library)        |

### Clone and build

```bash
git clone https://github.com/lhacenmed/khatmah.git
cd khatmah
```

Open the project in Android Studio and let Gradle sync.  
Run the **debug** build variant on a device or emulator running Android 8.0+.

### Signing (release builds)

Create a `keystore.properties` file in the **project root** (not committed to version control):

```properties
storeFile=path/to/your.keystore
storePassword=yourStorePassword
keyAlias=yourKeyAlias
keyPassword=yourKeyPassword
```

Without this file the debug build type still compiles; the release build will fail signing.

### Split APKs (optional)

By default, every build produces a single universal APK.  
Pass `-Psplits` to generate per-ABI APKs + a universal APK for store distribution:

```bash
./gradlew assembleRelease -Psplits
```

---

## Project Structure

```
app/src/main/
├── assets/
│   ├── adhan/          # Bundled Adhan audio files (.opus)
│   ├── fonts/          # Noto Kufi (UI font) for widget canvas
│   └── quran.7z        # Compressed Quran data (Hafs + Warsh JSON)
├── cpp/                # JNI native library (CMake)
├── java/com/lhacenmed/khatmah/
│   ├── core/
│   │   ├── motion/     # Shared-axis and other Compose transitions
│   │   ├── nav/        # AppTab / AppPage registry; NavHost wiring
│   │   └── ui/         # Design system: components, theme, typography
│   ├── feature/
│   │   ├── adhkar/     # Adhkar data + UI (tab, detail, editor)
│   │   ├── audio/      # Aya-level audio playback engine
│   │   ├── debug/      # DB browser, file browser (debug only)
│   │   ├── khatmah/    # Khatmah planner, daily alarm
│   │   ├── more/       # "More" tab (settings hub)
│   │   ├── mushaf/     # Mushaf print management + download
│   │   ├── prayer/     # Prayer engine, settings, Qibla, reminders
│   │   ├── qadaa/      # Qadaa tracker (prayers + fasts)
│   │   ├── quran/      # Quran reader (text, image, SVG, QCF4)
│   │   ├── settings/   # Theme, language, about pages
│   │   ├── today/      # Today tab (session card, quick index)
│   │   └── trips/      # Trip requests feature
│   ├── onboarding/     # Language → notifications → location flow
│   ├── shared/         # Cross-feature utilities and services
│   └── widget/         # Glance home-screen widget
└── res/
    ├── values/         # English strings, themes, colors
    └── values-ar/      # Arabic strings
```

### Navigation pattern

All navigation destinations are self-registering objects:

- **`AppTab`** subclasses declare their icon, label, and order; added to `AppRegistry.tabs`.
- **`AppPage`** subclasses declare their route (auto-derived from class name) and argument list; added to `AppRegistry.pages`.
- `AppEntry` wires both lists into a single `NavHost` — no manual `composable { }` blocks needed for registered destinations.

---

## Tech Stack

| Layer                | Library                               |
| -------------------- | ------------------------------------- |
| UI                   | Jetpack Compose + Material 3          |
| Navigation           | Compose Navigation                    |
| Architecture         | ViewModel + StateFlow (MVI-lite)      |
| Local database       | Room                                  |
| Background work      | WorkManager                           |
| Widget               | Glance AppWidget                      |
| Image loading        | Coil + SVG decoder                    |
| Notifications        | NotificationManager (custom channels) |
| Dependency injection | Manual (factory pattern)              |
| Build                | Gradle Kotlin DSL + KSP               |
| Native               | CMake / JNI                           |

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on reporting bugs, suggesting features, and submitting pull requests.

---

## License

This project is licensed under the **MIT License** — see [LICENSE](LICENSE) for the full text.