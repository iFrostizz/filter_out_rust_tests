# Filter Out Rust Tests

IntelliJ Platform plugin that adds a **Find Usages** filter to hide usages that are inside Rust `#[test]` functions.

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
