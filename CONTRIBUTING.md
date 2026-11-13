# Contributing

Thanks for helping improve MySecurePass. A few principles keep the project trustworthy.

## Ground rules

1. **No network access, ever.** Pull requests that add the `INTERNET` permission, analytics, crash reporting or any dependency that phones home will not be merged.
2. **Security changes need tests.** Anything touching `core/vault`, `core/generator` or `app/security` must come with unit tests.
3. **Never weaken defaults.** New options must default to the safer behaviour.
4. **Keep dependencies minimal.** Every library is attack surface. Explain why a new one is needed.

## Development setup

- JDK 17
- Android SDK 35 (Android Studio Ladybug or newer)

```bash
./gradlew :core:test :app:testDebugUnitTest   # all tests
./gradlew :app:assembleDebug                  # installable debug build
```

The `core` module is plain Kotlin/JVM. Most logic (generators, crypto, vault format, import/export) lives there and can be tested without an emulator.

## Pull requests

- Keep changes focused; one feature or fix per PR.
- Run the full test suite before opening a PR. CI runs it too.
- Describe *what* changed and *why*, and note any security implications.
- UI changes must keep the WCAG AA contrast test (`ContrastTest`) passing in both themes.

## Reporting security issues

Please follow [SECURITY.md](SECURITY.md) and don't open public issues for vulnerabilities.
