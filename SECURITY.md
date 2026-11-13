# Security Policy

MySecurePass protects people's most sensitive data, so security reports are taken seriously and handled quickly.

## Reporting a vulnerability

**Please do not open a public issue for security problems.**

Report privately through GitHub: open the repository's **Security** tab and choose **Report a vulnerability**. Include:

- what you found and why it matters
- steps to reproduce (a proof of concept helps)
- affected version and Android version
- any suggested fix

You can expect an acknowledgement within 72 hours and a status update within 7 days. Once a fix is released, you'll be credited in the release notes unless you prefer otherwise.

## Scope

In scope:

- The vault format and cryptography (`core/src/main/kotlin/com/mysecurepass/core/vault`)
- Key handling, the Android Keystore and biometric unlock
- The Autofill service: any way to fill credentials into the wrong app or site
- Data leaks through the clipboard, screenshots, logs, backups or memory
- Anything that could make the app send data off the device

Out of scope:

- Attacks that need a rooted or already-compromised device
- Physical attacks on someone who knows the master password
- Brute-forcing a weak master password the user chose despite the strength check

## Supported versions

Only the latest release receives security fixes.
