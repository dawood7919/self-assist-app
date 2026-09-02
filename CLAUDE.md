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

## Separate the engine from the screen

Every tool that computes, parses or converts keeps that logic in a plain object with no
Android or Compose types: `CalculatorEngine`, `PageRanges`, `ImageMath`, `TaskQueries`,
`SectionCalculations`, `TextLayout`. The composable collects input and shows results;
it never does arithmetic.

This is not tidiness. It is the only way anything gets verified — see below — and it has
already caught shipped-quality bugs: a rebar weight formula that disagreed with the
published bar tables, and a take-off line that read `StringBuilder.length` instead of its
own because it was computed inside `buildString`.

Persisted data goes through `EntityRepository` + a `JsonCodec` (`core/storage`), so a new
kind of record is a model, a codec and a one-line repository subclass.

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

There is no Android SDK in the web sandbox, so the full build runs in GitHub Actions
(`.github/workflows/android.yml`): push the branch and read the workflow logs. The
workflow runs `testDebugUnitTest` before it assembles anything, so a failing test stops
the APK rather than shipping with it.

Before pushing, the pure engines can be compiled and their tests run locally with a
downloaded `kotlin-compiler-embeddable` — no Android SDK needed, because those files
import nothing from Android. That loop catches type errors and wrong answers in seconds
instead of a five-minute CI round trip.

Its limitation is worth knowing: it cannot compile anything that touches Compose. Every
compile error that has reached CI so far has been in a composable — a `@Composable`
getter read inside a plain lambda, a trailing lambda binding to `enabled` instead of
`onCheckedChange`. Re-read new composables for those specifically.

## Being honest in the UI

Where a tool cannot do something, the tool says so, in the tool, rather than failing
quietly or implying otherwise:

- Compressing a text-only PDF reports that there was nothing to compress instead of
  writing an identical file and calling it smaller.
- Clipboard History has a Save button because Android does not let an app read the
  clipboard in the background — the empty state says exactly that.
- The AI tools need the user's own API key, hold no bundled one, and say that the key is
  stored unencrypted in app-private storage.
- Load Tables applies no partial safety factor and says so.
- `ToolStatus.Planned` entries stay in the catalogue with a description saying why they
  are not built, rather than being hidden or faked.
