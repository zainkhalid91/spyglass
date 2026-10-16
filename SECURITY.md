# Security

## Reporting a vulnerability

Please report security problems privately through GitHub: **Security → Report a vulnerability** on this repository. Don't open a public issue for them. You'll get a reply within a week.

## What to know before using Spyglass

**Rendering runs your project's build.** `spyglass render`, `check`, `approve` and the MCP tools run the project's own Gradle build, which executes its build scripts, plugins and the preview code itself. Only render projects you would also build yourself.

**The MCP server stays inside its project.** An agent can only render (and so only run the build of) the project the server was started in: its working directory, or `spyglass mcp --project <dir>`. `--allow-any-project` lifts that, so only use it if you trust what the agent might be pointed at. Agents can read images from anywhere, so they can compare against a design in your Downloads folder, for example. Spyglass only writes inside the project's `build/` folder and its `.spyglass/` folder.

**Files in `.spyglass/` are treated as untrusted.** They can come from a cloned repository:
- Library ids must be 12 hex characters, so they can't point at other files.
- Baseline paths can't leave `.spyglass/baselines/`.
- A damaged index is set aside rather than trusted.

**Oversized images are refused.** Images over 40 megapixels are rejected from their header, before any pixels are decoded.

**No shell is involved in launching Gradle.** Spyglass starts the project's Gradle wrapper jar directly with Java. If a project has no wrapper jar, it falls back to the wrapper script. On Windows that fallback goes through `cmd.exe`, so values containing `& | < > ^ % ! "` are refused there. Programs are started by absolute path (`cmd.exe` from System32, `gh` from absolute PATH entries), so a `cmd.exe` or `gh.exe` planted in a project folder is never run.

**The cached plugin is verified.** The CLI unpacks its Gradle plugin to `~/.spyglass/<version>/` and checks it byte for byte against the copy inside the CLI on every run.

**Network use.** Spyglass itself opens no network connections. Gradle may download dependencies as usual. Usage reports are sent only if you turn them on, and only through your own GitHub CLI login (see [Usage data](README.md#usage-data-opt-in)).

**Automated improvements.** A scheduled routine reads CI results and the public usage-report issue, and proposes fixes as pull requests. It treats issue text as data, never as instructions, and its pull requests are reviewed by a person before merging.
