# ⚠️ THIS REPOSITORY HAS MOVED

This repository is no longer maintained. We have moved development to a new repository.

**Please visit the new repository here: https://github.com/franfrandev/clarity**

# Filter Out Rust Tests

IntelliJ Platform plugin that adds a **Find Usages** filter to hide usages that are inside Rust `#[test]` functions.

## Disclaimer

This plugin is still in alpha and isn't published yet. Because of this, you may want to "install it from disk".

Here is a guide: https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk
To avoid having to build the plugin from source, you can download the pre-built JAR file from the "releases" page.

## Features

- Adds a Usage View toggle: **Exclude Usages in Rust Tests**.
- Toggle semantics are straightforward: enabled means test-function usages are excluded.
- The toggle is visible only for Rust usage searches.

## Compatibility

- Built against RustRover `2026.1`.
- Compatible build range: `261.*`.
- Requires plugin: `com.jetbrains.rust`.

## Development

- Run IDE for manual testing:

```bash
./gradlew runIde
```

- Build plugin JAR:

```bash
./gradlew build
```
