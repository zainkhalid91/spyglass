---
name: spyglass
description: See Compose UI without an IDE. Use when writing or changing Jetpack Compose or Compose Multiplatform UI (screens, components, @Preview functions), or when the user shares a design image, mock or screenshot that the UI should match. Renders @Preview functions to PNG and compares them with designs.
---

# Spyglass

Spyglass renders Compose `@Preview` functions to PNG headlessly and compares renders with design images. Use it so you look at the UI you write instead of guessing.

## Tools

Prefer the MCP tools when the `spyglass` MCP server is connected:

- `render_previews` with `filter` set to the preview you changed. The images come back inline.
- `compare_with_design` with `preview` (function name or `@Preview` name) and `designImage` (path). Add `variant` when one function has several `@Preview` annotations.
- `diff_images` to compare any two images.

Without MCP, use the CLI and read the PNG files it reports:

```bash
spyglass render --filter LoginScreen --json
spyglass diff path/to/render.png path/to/design.png --json
```

Run `spyglass render` from the project root, or from a module folder to render only that module. Pass `--module :shared` to pick one explicitly.

## Workflow

1. Make sure the UI you are changing has a `@Preview`: a parameterless `@Composable` function annotated with `androidx.compose.ui.tooling.preview.Preview`, in a module with a JVM (desktop) target. Add one next to the composable if it is missing, with realistic sample data.
2. After each meaningful change, render with a `filter` and look at the image before saying the UI is done. Check spacing, alignment, clipping, text overflow and colours, not only that it compiled.
3. When there is a design image, compare against it. Fix the largest regions first (the `regions` list gives px, dp and a rough position such as `top-left`), then compare again. Stop when the render matches the design by eye and the score stops improving. Pixel-exact targets reach 0.98 or more; mocks drawn in other tools rarely pass 0.9 because fonts and anti-aliasing differ.
4. Report the final similarity and any differences you chose not to fix, honestly.

## Errors

- A Gradle failure returns its last output lines. Usually it is a compile error in the code you just wrote: fix it and render again.
- A preview that throws is reported with the exception and the source line. Other previews still render.
- "No @Preview functions were rendered": check the filter, and that the module has `jvm()` / `jvm("desktop")` in its `kotlin { }` block.

## Limits

- Previews that only exist in Android source sets, or that use Android-only APIs, do not render yet.
- `@PreviewParameter`, dark mode (`uiMode`) and locales are not supported yet.
- `LocalInspectionMode` is `true` during rendering, as in the IDE.
