# Edge cases

A multi-module build for checking how Spyglass handles less common setups. Render it from this folder:

```bash
spyglass render
```

| Module | What it checks | Expected |
|---|---|---|
| `designsystem` | Declares `@ThemePreviews`, a multi-preview annotation used by another module | No previews of its own |
| `app` | JVM target named `jvm`; `@ThemePreviews` from `designsystem`; `@PreviewParameter`; a preview inside a class | 5 images: 2 theme variants, 2 parameter values, 1 class preview |
| `jvmapp` | A plain `kotlin("jvm")` module | 1 image |
| `jsonly` | A Kotlin Multiplatform module with no JVM target | Skipped with a message, not a failure |

The build has no Gradle wrapper of its own; Spyglass uses the repository's.
