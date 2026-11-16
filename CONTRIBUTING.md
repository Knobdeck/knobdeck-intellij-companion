# Developing the KnobDeck Companion for JetBrains IDEs

One of the [KnobDeck companions](https://github.com/Knobdeck/knobdeck-companion). The HTTP interface
the KnobDeck `intellij` plugin calls is in that repo's
[PROTOCOL.md](https://github.com/Knobdeck/knobdeck-companion/blob/master/PROTOCOL.md). Ids are a
contract: add, never rename. The plugin id `com.harshalkudale.knobdeck.companion` never changes.

## Build and test

JDK 21 (Gradle downloads the IDEs):

```sh
./gradlew test          # headless IDE tests
./gradlew runIde        # sandbox IDE with the plugin
./gradlew buildPlugin   # build/distributions/*.zip
./gradlew verifyPlugin  # Plugin Verifier: 2024.3, 2025.3, 2026.3
```

Point `KNOBDECK_INTELLIJ_PLUGIN` / `KNOBDECK_INTELLIJ_SETUP` at a built `intellij` /
`intellij-setup` from [knobdeck-plugins](https://github.com/Knobdeck/knobdeck-plugins)
(`cargo build -p intellij`) and `test` drives the real plugin too (needs Node). CI does this.

`test-support/compat/` is a copy of `compat/` in knobdeck-companion; keep them in step.

## Releasing

1. Bump `pluginVersion` in `gradle.properties` and add an entry at the top of `CHANGELOG.html`
   (it becomes the Marketplace change notes and the GitHub release notes). Merge to `master`.
2. **Actions → Release → Run workflow** on `master`.

The workflow runs CI, creates the tag `v<version>` and a GitHub release with the plugin zip, then
signs the plugin and publishes it to JetBrains Marketplace. Without the secrets below the
Marketplace step is skipped with a warning; re-running for a released version only retries it.

### Marketplace setup

JetBrains Marketplace needs the **first** version uploaded by hand (plugins.jetbrains.com → your
profile → *Upload plugin*, the zip from a GitHub release). Then set these secrets in
**Settings → Environments → `release`**:

| Secret | |
|---|---|
| `JETBRAINS_MARKETPLACE_TOKEN` | A permanent token from plugins.jetbrains.com → *My Tokens* |
| `JETBRAINS_CERTIFICATE_CHAIN`, `JETBRAINS_PRIVATE_KEY`, `JETBRAINS_PRIVATE_KEY_PASSWORD` | [Plugin signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) certificate and key (PEM contents) |
