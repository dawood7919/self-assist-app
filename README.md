# Orbit — Personal Assistant Hub

A modular personal-productivity Android app: **one application, many tools, one design system.**

This repository currently contains the **UI foundation** — the design system, the app shell,
the navigation model, the tool architecture and four UI-only demo tools. No tool business
logic is implemented yet; that is deliberate. Everything here exists so that the fiftieth
tool looks and behaves exactly like the first.

## Stack

| | |
|---|---|
| Language | Kotlin 2.1.20 |
| UI | Jetpack Compose (Compose BOM 2025.01.00) |
| Navigation | Navigation Compose |
| Build | AGP 8.9.2 / Gradle 8.13 / JDK 17 |
| Min / target SDK | 26 / 35 |
| CI | GitHub Actions → debug + release APK artifacts |

Material 3 is intentionally **not** a dependency. The visual language is original and lives
entirely in `core/designsystem`; only the Material *icon* set is used, and it is funnelled
through a single registry so it can be swapped wholesale later.

## Architecture

```
app/src/main/java/com/dawood/orbit/
├── core/
│   ├── designsystem/
│   │   ├── token/        Colour, Type, Dimens, Elevation, Motion  ← the only literals
│   │   ├── theme/        OrbitTheme + CompositionLocals
│   │   ├── foundation/   Surface, shadows, focus rings, indication
│   │   ├── icon/         OrbitIcons — every icon in the product, named once
│   │   └── component/    Button, Input, Card, Badge, Tabs, Overlay, Toast, State…
│   └── layout/           Width classes, responsive gutters, grid
├── navigation/           Destinations, routes, the graph and its transitions
├── feature/
│   ├── shell/            Top bar, sidebar, rail, bottom navigation
│   ├── command/          ⌘/Ctrl+K command palette
│   ├── home/ tools/ projects/ notes/ settings/
│   └── designsystem/     The internal component reference screen
├── tools/
│   ├── model/            Tool metadata
│   ├── registry/         ToolRegistry — the catalogue
│   ├── shell/            ToolShell, ToolHeader, ToolWorkspace, ToolActions…
│   ├── file/             FileDropZone, FileList, FileItem, FileProgress, FileResult…
│   ├── component/        ToolCard, ToolRow, ToolTile
│   └── demo/             Notebook · PDF Merger · Course Roadmap · Video Downloader
├── data/                 Sample content (placeholder, isolated from the UI)
└── app/                  Preferences and cross-screen state
```

### Adding a tool

1. Add an entry to `ToolRegistry.tools`.
2. Add a branch in `ToolWorkspaceHost` (`navigation/OrbitNavHost.kt`).
3. Build the workspace out of `ToolShell` + existing components.

Nothing else changes. Home, the launcher, search and the command palette all render from the
registry, so a new tool appears everywhere the moment it is registered. A tool with no
workspace yet still opens and shows an honest "on the way" state rather than a dead link.

## Responsive behaviour

Each width class is a different design, not a scaled-down one:

| Class | Width | Navigation | Tool panels |
|---|---|---|---|
| Compact | < 600dp | Bottom bar, hidden inside tools | Drawer / bottom sheet |
| Medium | 600–904dp | Icon rail | Docked |
| Expanded | 905–1239dp | Full sidebar | Docked |
| Large | ≥ 1240dp | Wide sidebar, capped content width | Docked + inspector |

## Theming

Light and dark are both first-class, and the accent colour is a single token with seven
presets. Changing either in Settings cross-fades the entire app because every component
resolves its colours through `OrbitTheme.colors`. Motion collapses to zero when the system
reports that animations are disabled.

## Building

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/
./gradlew assembleRelease   # signed with the debug key so CI output is installable
```

CI runs on every push and uploads both APKs as workflow artifacts.

> The release build is signed with the debug keystore so that CI can produce an installable
> APK without secrets. Replace `signingConfig` in `app/build.gradle.kts` with a real config
> before distributing.
