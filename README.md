# تطبيق مقر الذكي - منصة إدارة مساحات العمل المشتركة (`maqar-access-coworking-1289812`)
> منظومة مساحات العمل المشتركة الذكية المتكاملة مع بوابات الدخول RFID/NFC 13.56 MHz وإدارة الإشغال الحركي

- **Project ID**: `#1289812`
- **Commercial Archetype**: `coworking`
- **Bound Hardware Peripheral**: `13.56 MHz RFID Turnstile Access + TOTP Pass`
- **Package Name**: `sa.factory.maqaraccesscoworki`
- **Zero-Emoji Invariant**: Strictly Enforced (100% Clean Vector/ASCII)

---

## [Sidekick Autonomous Update]

### 1. Summary of Completed Enhancements
- **Resolved Kotlin Template Hazards**: Extracted fallback peripheral strings in Compose screens to prevent Kotlin single-quote interpolation compiler failures.
- **Modernized Compose Components**: Upgraded deprecated dividers to `HorizontalDivider` and added missing Material3 text formatting imports (`FontWeight`, `sp`).
- **Clean Invariant Enforcement**: Stripped unicode checkmarks and non-standard symbols in compliance with the Zero-Emoji enterprise standard.
- **Relocated Test Suite**: Moved regression and engine tests into `app/src/test/java/sa/factory/maqaraccesscoworki/` for full Gradle JUnit runner integration.
- **Embedded Gradle Wrapper**: Bundled official Gradle wrapper binaries and comprehensive `.gitignore` rules for autonomous CI/CD compatibility.

### 2. Architecture & Domain Engines
- **RfidNfcTurnstileProtocolEngine (`sa.factory.maqaraccesscoworki.engine.RfidNfcTurnstileProtocolEngine`)**:
  - Implements contactless ISO/IEC 14443 Type A and MIFARE Classic protocol handling.
  - Verifies UID format parity, executes cryptographic sector key authentication, and provides Anti-Passback cooldown guards.
  - Generates momentary hardware relay pulses (500ms `ACTIVE_MOMENTARY_OPEN`) with full telemetry audit logging.
- **DynamicSpaceOccupancyEngine (`sa.factory.maqaraccesscoworki.engine.DynamicSpaceOccupancyEngine`)**:
  - Provides dynamic capacity tracking across flex desks, dedicated offices, executive studios, and meeting rooms.
  - Automatically assesses real-time comfort indices and toggles eco-mode energy savings when zones become vacant.
  - Manages desk assignments, check-ins, and check-outs with zero race conditions.
- **HardwareSensorDriver Telemetry Integration**:
  - Emits real-time state frames combining turnstile access decisions and multi-zone occupancy statistics.

### 3. Step-by-Step Build and Test Instructions
```bash
# Run comprehensive unit and regression test suite
./gradlew test

# Build debug APK for cloud test harness deployment
./gradlew assembleDebug
```
