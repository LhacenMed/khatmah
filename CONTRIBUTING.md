# Contributing to Khatmah

Thank you for taking the time to contribute! This document explains how to report bugs, suggest improvements, and submit code changes.

---

## Table of Contents

- [Contributing to Khatmah](#contributing-to-khatmah)
  - [Table of Contents](#table-of-contents)
  - [Code of Conduct](#code-of-conduct)
  - [How to Report a Bug](#how-to-report-a-bug)
  - [How to Request a Feature](#how-to-request-a-feature)
  - [Development Setup](#development-setup)
  - [Branching Strategy](#branching-strategy)
  - [Submitting a Pull Request](#submitting-a-pull-request)
  - [Code Style](#code-style)
  - [Adding a New Screen](#adding-a-new-screen)
    - [Full-screen page](#full-screen-page)
    - [Bottom navigation tab](#bottom-navigation-tab)
  - [Translations](#translations)
  - [Commit Message Format](#commit-message-format)

---

## Code of Conduct

Be respectful and constructive. We welcome contributors of all experience levels. Harassment, discrimination, or hostile behaviour will not be tolerated.

---

## How to Report a Bug

1. Search [existing issues](../../issues) to avoid duplicates.
2. Open a new issue using the **Bug Report** template.
3. Include:
   - Device model and Android version
   - App version (from Settings → About)
   - Steps to reproduce
   - Expected vs. actual behaviour
   - Logcat output or screenshot if relevant

---

## How to Request a Feature

1. Search [existing issues](../../issues) first.
2. Open a new issue using the **Feature Request** template.
3. Describe the problem you want solved and your proposed solution. Keep the scope focused — one feature per issue.

---

## Development Setup

```bash
# 1. Fork the repository on GitHub, then clone your fork
git clone https://github.com/<your-username>/khatmah.git
cd khatmah

# 2. Add the upstream remote
git remote add upstream https://github.com/your-org/khatmah.git

# 3. Open in Android Studio (Hedgehog or newer) and let Gradle sync
# 4. Run the debug build variant on a device or emulator (Android 8.0+)
```

**Required tools:**

| Tool           | Version                         |
| -------------- | ------------------------------- |
| Android Studio | Hedgehog (2023.1.1)+            |
| JDK            | 11                              |
| CMake          | 3.22.1                          |
| Android SDK    | API 36 compile / API 24 minimum |

---

## Branching Strategy

| Branch           | Purpose                                  |
| ---------------- | ---------------------------------------- |
| `main`           | Stable, release-ready code               |
| `develop`        | Integration branch for upcoming releases |
| `feature/<name>` | New feature work                         |
| `fix/<name>`     | Bug fixes                                |
| `chore/<name>`   | Refactoring, build, or CI changes        |

Always branch off `develop`, not `main`.

```bash
git checkout develop
git pull upstream develop
git checkout -b feature/your-feature-name
```

---

## Submitting a Pull Request

1. **Keep PRs focused** — one logical change per PR.
2. **Write a clear description** explaining _what_ changed and _why_.
3. **Reference issues** — include `Closes #123` if the PR resolves an issue.
4. **Pass all checks** — the CI pipeline must be green before review.
5. **Respond to review comments** promptly; mark resolved threads once addressed.
6. Maintainers may squash commits before merging.

---

## Code Style

The project follows the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) with these additional rules:

**General**
- 4-space indentation, no tabs.
- Maximum line length: 120 characters.
- Prefer `val` over `var`; avoid mutable state unless strictly necessary.
- Use named arguments for readability when a function has more than two parameters of the same type.

**Jetpack Compose**
- One composable per file (exceptions: small private helpers in the same file).
- Composable function names are `PascalCase`.
- Pass state down and events up — no direct ViewModel references below the screen level.
- Avoid side-effects in composition; use `LaunchedEffect`, `DisposableEffect`, or `SideEffect`.

**Navigation**
- All new screens must be `AppPage` or `AppTab` subclasses registered in `AppRegistry`.
- Do **not** add manual `animatedComposable` blocks in `AppEntry` for new destinations.
- Route strings are auto-derived from the class name; only override when the auto-derived name conflicts or you need path arguments.

**Architecture**
- UI state lives in a `StateFlow` exposed from a `ViewModel`.
- ViewModels should not reference `Context` beyond creation (use repositories or application context).
- Business logic belongs in the `data/` layer; UI logic belongs in the `ui/` layer.

---

## Adding a New Screen

### Full-screen page

```kotlin
// 1. Create the object in the feature's ui/ package
object YourPage : AppPage() {
    // Route is auto-derived as "your" from "YourPage" — override only if needed
    @Composable override fun Content(back: NavBackStackEntry) = YourScreen()
}

// 2. Add to AppRegistry.pages
val pages: List<AppPage> = listOf(
    // ... existing pages ...
    YourPage,
)
```

### Bottom navigation tab

```kotlin
// 1. Create the object in the feature's ui/ package
object YourTab : AppTab(
    iconRes  = R.drawable.ic_your_icon,
    labelRes = R.string.your_label,
    order    = 5,   // position in the tab bar
) {
    @Composable override fun Content(padding: PaddingValues) = YourScreen(padding)
}

// 2. Add to AppRegistry.tabs
val tabs: List<AppTab> = listOf(
    // ... existing tabs ...
    YourTab,
)
```

That's all — no changes to `AppEntry` are needed.

---

## Translations

String resources live in:

- `res/values/strings.xml` — English (source of truth)
- `res/values-ar/strings.xml` — Arabic

To add or update a translation:

1. Copy any missing keys from the English file.
2. Translate the string values (not the `name` attributes).
3. For a new language, create `res/values-<lang-code>/strings.xml` and add the locale to `res/xml/locales_config.xml`.
4. Submit a PR with only translation changes — do not mix translation PRs with code changes.

---

## Commit Message Format

Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <short summary>

[optional body]

[optional footer — e.g. Closes #123]
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `ci`

**Examples:**

```
feat(qibla): add haptic feedback on cardinal alignment
fix(prayer): correct DST offset applied twice in manual mode
docs: update README with split APK instructions
chore(deps): upgrade Compose BOM to 2024.05.00
```

Summary line: imperative mood, ≤72 characters, no trailing period.