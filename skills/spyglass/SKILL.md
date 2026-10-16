---
name: spyglass
description: See Compose UI without an IDE. Use when writing or changing Jetpack Compose or Compose Multiplatform UI (screens, components, @Preview functions), or when the user shares a design image, mock or screenshot that the UI should match. Renders @Preview functions to PNG and compares them with designs.
---

# Spyglass

Renders Compose `@Preview` functions to PNG without an IDE and compares renders with design images. Use it to look at the UI you write instead of guessing.

## Tools

With the `spyglass` MCP server connected:

- `render_previews` with `filter` set to what you changed. Images come back inline, scaled to `imageMaxPx` (640). Previews you saw recently that haven't changed come back as `"unchanged"` without an image; trust that rather than asking again (`force: true` resends).
- `compare_with_design` with `preview` (function or `@Preview` name) and `designImage` (a path, or the name of a saved design); add `variant` when one function has several `@Preview`s and `designName` to save a new design for later. Returns similarity, regions as `[x, y, w, h]` in dp, and one image: render | design | heatmap (the design is left out once you've seen it).
- `diff_images` for any two images.
- `references`, the project's UI memory. Before building a new component, `similar` (with an image) or `list` (with a query) shows whether something like it exists, as text. `save` names a design or adds a note such as what was fixed; `show` sends one image; `approve` stores baselines for `spyglass check`.

Without MCP, use the CLI (compact JSON on stdout) and read the PNGs it lists:

```bash
spyglass render --filter LoginScreen --json
spyglass diff render.png design.png --json
```

Run from the project root, or a module folder to render only that module (`--module :shared` picks one explicitly).

## Workflow

1. Make sure the UI has a `@Preview` in a module with a JVM (desktop) target. Add one next to the composable if missing, with realistic sample data.
2. After each meaningful change, render with a `filter` and look at the image: spacing, alignment, clipping, text overflow, colours.
3. With a design, compare and fix the largest regions first, then compare again. Stop when it matches by eye and the score stops improving. Pixel-exact targets reach 0.98+; mocks from other tools rarely pass 0.9 (fonts and anti-aliasing differ). A `warning` means the design's aspect ratio differs: ask for an export at the preview's size.
4. Report the final similarity and any differences left, honestly. When a screen matches its design, `references approve` it so `spyglass check` guards it from now on.

## Supported

`@Preview` (also repeated, in objects or classes), multi-preview annotations including `@PreviewLightDark`, `@PreviewFontScale`, `@PreviewScreenSizes` and your own, `@PreviewParameter` (one image per value), `uiMode` dark, `fontScale`, `widthDp`/`heightDp`, `device = "spec:..."`, `showBackground`, `backgroundColor`.

## Errors

- Gradle failure: the reply holds the compile error with file and line. Fix it and render again.
- A preview that throws is reported with the exception and its source line; other previews still render.
- "No previews rendered": check the filter, and that the module has `jvm()` or `jvm("desktop")`.

## Limits

Previews only in Android source sets or using Android-only APIs; locales; `device = "id:..."`; `@PreviewWrapper`. Text uses the machine's fonts, so compare renders made on the same OS.
