# Spyglass

**Let AI agents (and you) see Compose UI without an IDE.**

Spyglass renders your Compose `@Preview` functions to PNG with no IDE, emulator or device, then compares them with a design image and tells you exactly where they differ. It works from the command line, in CI, and as an MCP server, so a coding agent can look at the UI it just wrote, compare it with the mock and fix the gaps on its own.

<table>
  <tr>
    <th>Design target</th>
    <th>First attempt</th>
    <th><code>spyglass diff</code> heatmap</th>
  </tr>
  <tr>
    <td><img src="docs/images/demo-design.png" width="240" alt="The design: a Pro plan card"></td>
    <td><img src="docs/images/demo-first-attempt.png" width="240" alt="A first attempt with small mistakes"></td>
    <td><img src="docs/images/demo-first-attempt-diff.png" width="240" alt="Heatmap with the differences in red"></td>
  </tr>
  <tr>
    <td colspan="3" align="center">89.81% similar. Spyglass found 4 regions: the corners, the price colour, the list spacing and the button.</td>
  </tr>
</table>

> Status: early development (0.1.0). Previews in Compose Multiplatform shared (`commonMain`) and desktop code work today. Android-only previews are on the roadmap.

---

## The missing piece

AI coding agents write a lot of UI code now, and they write it **blind**. An agent can make a Compose screen compile, but it can't see that the padding is off, the text is clipped or the button is the wrong colour. You find out when you open the IDE. In the 2025 Stack Overflow developer survey, AI output that is "almost right, but not quite" was developers' most-cited frustration with AI tools, and UI is where "almost right" is hardest to spot in code.

The ways to see a Compose preview today were built for people, or for regression tests:

| | Needs an IDE running | Changes to your build | Made for agents | Compares with a design mock |
|---|---|---|---|---|
| Android Studio preview | Yes | None | No | Only through Gemini inside the IDE ("Match UI to Target Image") |
| Android CLI `render-compose-preview` (Google, May 2026) | Yes: Android Studio with the project open | None | Yes: returns an image path and UI hierarchy | No |
| Screenshot-test libraries (Paparazzi, Roborazzi, Compose Preview Screenshot Testing) | No | Plugin, dependencies, test sources, recorded baselines | No: they are test frameworks | They check against recorded baselines, not a design |
| **Spyglass** | **No** | **None** (applied at run time) | **Yes: MCP server, images returned inline** | **Yes: score, regions in px/dp, heatmap** |

Screenshot-test libraries are great at what they do: stopping regressions once the UI is right. Spyglass covers the step before that, **getting the UI right in the first place**, in a loop an agent can run by itself:

```mermaid
flowchart LR
    W[Agent writes or edits<br/>Compose code] --> R[render_previews<br/>agent sees the PNG]
    R --> C[compare_with_design<br/>score + regions + heatmap]
    C -->|regions differ| F[Agent fixes the<br/>largest regions]
    F --> R
    C -->|matches| D[Done: reports the<br/>final score honestly]
```

---

## Walkthrough: see, compare, fix

The three versions of the card above are real previews in [`samples/desktop-sample`](samples/desktop-sample/src/commonMain/kotlin/sample/Demo.kt), so you can reproduce every image and number here. They play the parts of a designer's mock, a typical first attempt, and the fixed version.

**1. Render.** `spyglass render` finds every `@Preview` and draws it:

```text
$ spyglass render --filter Demo
spyglass: rendered 3/3 previews
  ok     DemoKt.DemoDesignTarget [Design target]  720x860px  .../build/spyglass/previews/DemoKt.DemoDesignTarget_Design_target.png
  ok     DemoKt.DemoFirstAttempt [First attempt]  720x860px  .../build/spyglass/previews/DemoKt.DemoFirstAttempt_First_attempt.png
  ok     DemoKt.DemoAfterFix [After fix]  720x860px  .../build/spyglass/previews/DemoKt.DemoAfterFix_After_fix.png
```

**2. Compare with the design.** `spyglass diff` scores the render and lists the regions that differ, largest first, with a rough position an agent can act on:

```text
$ spyglass diff DemoKt.DemoFirstAttempt_First_attempt.png DemoKt.DemoDesignTarget_Design_target.png
spyglass diff: 89.81% similar (63095 of 619200 px differ)
  heatmap: .../DemoKt.DemoFirstAttempt_First_attempt.diff.png
  regions that differ, largest first (px, then dp at density 2.0):
    1. center        x=44 y=374 616x308px  (22,187 308x154dp)  31.22% of it changed
    2. top-left      x=110 y=198 88x88px  (55,99 44x44dp)  36.25% of it changed
    3. top-left      x=44 y=44 66x66px  (22,22 33x33dp)  12.10% of it changed
    4. top-right     x=616 y=44 44x66px  (308,22 22x33dp)  18.11% of it changed
```

Read with the heatmap, that is: the feature list and button (tighter spacing pushed them up, and the button lost its gradient), the price (wrong colour), and the top corners (radius 12dp instead of 28dp).

**3. Fix and compare again.** After fixing those, one small region is left:

<table>
  <tr>
    <th>After fix</th>
    <th>Heatmap</th>
  </tr>
  <tr>
    <td><img src="docs/images/demo-after-fix.png" width="240" alt="The fixed card"></td>
    <td><img src="docs/images/demo-after-fix-diff.png" width="240" alt="Heatmap: only one word differs"></td>
  </tr>
</table>

```text
spyglass diff: 99.82% similar (1090 of 619200 px differ)
  regions that differ, largest first (px, then dp at density 2.0):
    1. center        x=242 y=440 132x44px  (121,220 66x22dp)  18.77% of it changed
```

That region is the word "Support": the fixed version says "Priority Support", the design says "Priority support". Small enough to miss by eye, obvious in the heatmap.

Through MCP, an agent gets all of this from one `compare_with_design` call: the JSON summary plus the render, the design and the heatmap as images.

---

## What renders today

Real output from the sample project, cropped to each preview's content:

<table>
  <tr>
    <td><img src="docs/images/gallery-weather.png" width="200" alt="Weather card"></td>
    <td><img src="docs/images/gallery-ring.png" width="140" alt="Progress ring drawn on a Canvas"></td>
    <td><img src="docs/images/gallery-chip.png" width="180" alt="Profile chip"><br><br><img src="docs/images/gallery-chip-large-text.png" width="225" alt="Profile chip at font scale 1.5"></td>
    <td><img src="docs/images/gallery-fixed-size.png" width="240" alt="Fixed 320x120 dp canvas"></td>
  </tr>
  <tr>
    <td>Gradients and text</td>
    <td><code>Canvas</code> drawing, <code>backgroundColor</code></td>
    <td>Two <code>@Preview</code>s on one function, one with <code>fontScale = 1.5f</code></td>
    <td><code>widthDp</code> / <code>heightDp</code></td>
  </tr>
</table>

A preview that throws doesn't stop the run. It is reported with its error and source line:

```text
  error  BrokenKt.BrokenPreview  IllegalStateException: Intentional failure to test error reporting (at sample.BrokenKt.BrokenPreview:10)
```

---

## How it works

```mermaid
flowchart TB
    subgraph You["You, CI, or an AI agent"]
        CLI["spyglass render / diff"]
        MCP["spyglass mcp<br/>(MCP server on stdio)"]
    end
    CLI --> G
    MCP --> G
    G["Your project's own Gradle wrapper<br/>+ init script that applies the Spyglass plugin<br/>(your build files are not changed)"]
    G --> J["Compiles the module's JVM / desktop target"]
    J --> R["Renderer runs on your project's classpath"]
    R --> S["1. ClassGraph finds @Preview methods in the compiled classes"]
    S --> I["2. Each preview is composed in an offscreen ImageComposeScene (Skia)"]
    I --> P["3. PNG cropped to the content + previews.json manifest"]
    P --> D["spyglass diff: perceptual pixel compare<br/>score, regions, heatmap"]
```

**1. No build changes.** The `spyglass` CLI carries the Gradle plugin inside it. When you run `spyglass render`, it starts your project's own `gradlew` with an init script (`-I spyglass.init.gradle`) that applies the plugin to every project for that run only. Nothing in your repository is edited, and your Gradle, Kotlin and Compose versions are the ones used.

**2. A plugin that works anywhere.** The Gradle plugin is written in Java and deliberately avoids Kotlin Gradle Plugin classes, because an init script loads it in a separate class loader. It finds your Kotlin Multiplatform JVM target (`jvm()` or `jvm("desktop")`) by reading the `org.jetbrains.kotlin.platform.type` attribute on Gradle configurations, then registers `spyglassRender`. It works with the configuration cache on.

**3. Rendering on your classpath.** `spyglassRender` runs the renderer in a JVM whose classpath is your module's compiled desktop jar plus its runtime dependencies, so your composables, resources and Compose version are exactly what ship. The renderer itself compiles against Compose only as `compileOnly`.

**4. Finding previews without loading your code.** [ClassGraph](https://github.com/classgraph/classgraph) scans the compiled classes for methods carrying `@Preview`. That annotation has `BINARY` retention, so it isn't visible to normal reflection but is in the class files. Spyglass recognises:
- `androidx.compose.ui.tooling.preview.Preview` (the common annotation since Compose Multiplatform 1.10)
- the deprecated JetBrains and desktop `@Preview`s
- repeated `@Preview`s on one function
- your own multi-preview annotations

**5. Calling a composable by reflection.** A parameterless `@Composable fun Foo()` compiles to a static method `Foo(Composer, int)`. Spyglass composes a host inside an offscreen `ImageComposeScene`, with `LocalInspectionMode` set to `true` as the IDE does, and invokes the preview method with the scene's current `Composer`. It renders two frames, so state set in the first frame's effects shows up, measures the content, and crops the PNG to it, or to `widthDp` / `heightDp` when set. `fontScale`, `showBackground` and `backgroundColor` are applied too. Compose's own error log is captured, so a crashing preview becomes one clean line instead of a stack trace.

**6. A manifest for machines.** Every run writes `build/spyglass/previews/previews.json` with each preview's function, `@Preview` name, status, image path, size and error. `spyglass render --json` prints the same data, and the MCP server returns it with the images.

**7. Comparing images like a person would.** `spyglass diff` flattens both images onto white and scales the design to the render's size if they differ. It then compares every pixel with the YIQ colour distance used by [pixelmatch](https://github.com/mapbox/pixelmatch), so `--threshold 0.1` means what it does there. To turn changed pixels into something an agent can act on:
- the image is split into a grid, and cells with only stray anti-aliasing changes are ignored
- touching cells are joined into regions, and regions that nearly touch are merged, so "27°" is one region rather than two digits
- each region is reported in px and dp with a rough position (`top-left`, `center`...) and the share of its pixels that changed

**8. An MCP server that speaks old and new MCP.** `spyglass mcp` serves the stateless 2026-07-28 revision of the Model Context Protocol (version in each request's `_meta`, plus `server/discover`). It also answers the older `initialize` handshake (2024-11-05 to 2025-11-25), so it works with clients on either. Images are returned inline as base64 PNG. Problems the agent can fix, such as a compile error (with Gradle's last output lines), a crashing preview, or an ambiguous preview name (with the exact choices), come back as tool errors with the details.

---

## Install

From source for now:

```bash
git clone https://github.com/zainkhalid91/spyglass
cd spyglass
./gradlew :cli:installDist
```

The launcher is `cli/build/install/spyglass/bin/spyglass` (`spyglass.bat` on Windows). Copy the `cli/build/install/spyglass` folder somewhere stable and add its `bin` to your `PATH`. Requires JDK 17+.

## Use

```bash
cd /path/to/your/project
spyglass render                       # every module with a JVM target
spyglass render --module :shared      # one module (inferred when run from a module folder)
spyglass render --filter Card --json  # matching previews only, JSON on stdout
spyglass diff render.png design.png   # compare; --fail-under 0.98 for CI
spyglass help
```

Images land in `<module>/build/spyglass/previews/`. Render options: `--density` (default 2), `--max-width-dp` / `--max-height-dp` (canvas limit for previews without a fixed size, default 1024), `--target` (JVM target name, detected by default), `--fail-on-error`.

## Use with AI agents

`spyglass mcp` is an MCP server with three tools:

| Tool | What it does |
|---|---|
| `render_previews` | Renders previews (optionally filtered) and returns a JSON summary plus the images. |
| `compare_with_design` | Renders one preview and compares it with a design image: score, regions, render, design and heatmap in one call. |
| `diff_images` | Compares any two images. |

**Claude Code:**

```bash
claude mcp add --scope user spyglass -- /path/to/spyglass/bin/spyglass mcp
# Windows
claude mcp add --scope user spyglass -- cmd /c C:\path\to\spyglass\bin\spyglass.bat mcp
```

**Other clients** (`.mcp.json` or the client's settings):

```json
{
  "mcpServers": {
    "spyglass": { "command": "/path/to/spyglass/bin/spyglass", "args": ["mcp"] }
  }
}
```

The server renders the project it is started in. Every tool also accepts `projectDir` and `module`.

**Claude Code skill:** copy [`skills/spyglass/`](skills/spyglass/SKILL.md) to `~/.claude/skills/`. It tells the agent when to render, how to work through a design comparison, and what the limits are. It falls back to the CLI when the MCP server isn't connected.

## Use the Gradle plugin directly

```kotlin
plugins {
    id("io.github.zainkhalid91.spyglass")
}
```

Then run `./gradlew spyglassRender`. Options are Gradle properties: `-Pspyglass.filter=<text>`, `-Pspyglass.density=2`, `-Pspyglass.maxWidthDp=1024`, `-Pspyglass.maxHeightDp=1024`, `-Pspyglass.target=<jvm target>`, `-Pspyglass.failOnError=true`. The plugin isn't published yet; [`samples/desktop-sample`](samples/desktop-sample) shows how to use it from this repository.

## Limits

- **Previews:** they must be parameterless and live in code compiled for a JVM (desktop) target. Previews that exist only in Android source sets, or use Android-only APIs, don't render yet.
- **Not supported yet:** `@PreviewParameter`, dark mode (`uiMode`) and locales.
- **Fonts:** text uses the fonts available on the machine, so renders can differ slightly between operating systems. Compare renders made on the same OS, or use a looser `--threshold`.
- **Scores against other tools' mocks:** a mock drawn in another tool will rarely score above 90%, because fonts and anti-aliasing differ. Use the regions and the heatmap, not just the number.

## Roadmap

- Semantics tree output (text, sizes, touch targets) alongside each image
- `@PreviewParameter`, dark mode, locales
- Android-only previews
- Published Gradle plugin, CLI releases, and a Claude Code plugin bundle

## Project layout

| Module | What it is |
|---|---|
| [`renderer/`](renderer) | Kotlin. Finds previews with ClassGraph and renders them with `ImageComposeScene`. Runs on the rendered project's classpath. |
| [`gradle-plugin/`](gradle-plugin) | Java. Registers `spyglassRender` and ships the renderer inside its jar. |
| [`cli/`](cli) | Kotlin. The `spyglass` command: runs your Gradle wrapper with the bundled plugin, reads manifests, diffs images, and serves MCP. |
| [`skills/spyglass/`](skills/spyglass) | The Claude Code skill. |
| [`samples/desktop-sample/`](samples/desktop-sample) | A Compose Multiplatform project used for testing and for every image in this README. |

## Windows note

Gradle fails with `Unable to establish loopback connection` when `TEMP` is an 8.3 short path like `C:\Users\JOHNSM~1\...`, which is common when the user name has spaces. `spyglass render` detects this and points `TEMP` at `<project>/.gradle/spyglass-tmp` for its Gradle run. If you run Gradle yourself, point `TEMP`, `TMP` and `-Djdk.net.unixdomain.tmpdir` at a folder without `~` or spaces.

## License

[MIT](LICENSE)
