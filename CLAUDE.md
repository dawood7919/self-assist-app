# Working in this repository

Orbit is one application made of many tools. The single most important rule is that a new
tool must never introduce a new visual style.

## When asked to build a new tool

Do **not** design a new screen. Assemble it:

1. Add the tool's metadata to `ToolRegistry.tools` (`tools/registry/ToolRegistry.kt`).
2. Add a branch to `ToolWorkspaceHost` in `navigation/OrbitNavHost.kt`.
3. Wrap the workspace in `ToolShell` — it supplies the top bar, back navigation, the
   overflow menu, the settings sheet, the side panel and the sticky action bar.
4. Build the body from existing components: `ToolWorkspace`, `OrbitCard`, `OrbitListItem`,
   `OrbitButton`, `OrbitTextField`, `OrbitBadge`, the `File*` components, and the shared
   empty / loading / error / success states.
5. Only write genuinely tool-specific composables — the shape of the workspace, nothing else.

## Hard rules

- No hard-coded colours, sizes, radii, durations or font sizes. Everything comes from
  `OrbitTheme.colors / typography / spacing / radius / sizes / elevation / motion`.
- No new icons outside `OrbitIcons`. Add a named entry there instead.
- No business logic inside `core/designsystem` — components take values and callbacks.
- Anything reusable belongs in the design system and in the showcase screen
  (`feature/designsystem/DesignSystemScreen.kt`) before it is used in a tool.
- Respect the width classes in `core/layout/WindowSize.kt`; do not branch on raw dp.
- Every interactive element needs a content description or an `onClickLabel`.

## Verifying

There is no Android SDK in the web sandbox, so builds run in GitHub Actions
(`.github/workflows/android.yml`). Push the branch and read the workflow logs.
