# SCARY

Horror-themed home screen (launcher) for Android TV. Built first for Xiaomi Mi devices.
It brings together a curated library of horror and psychological thrillers, Stremio, Google Play and your installed TV apps.

The launcher does not stream or host video. It opens apps that are already installed.

- Plan and spec (Hebrew): [docs/PLAN.md](docs/PLAN.md)
- Xiaomi install and "set as home" guide (Hebrew): [docs/XIAOMI_SETUP.md](docs/XIAOMI_SETUP.md)
- Interactive mockup: [mockup/launcher-mockup.html](mockup/launcher-mockup.html)

## Structure

| Module | What it is |
|--------|-----------|
| `core/` | Pure Kotlin/JVM: catalog model and validation, tonight's pick, similar movies, app ordering, deep links. Unit tested. |
| `app/` | Android app: Jetpack Compose UI, HOME intent filter, installed apps, launching. |
| `app/src/main/assets/catalog.json` | The curated movie library. Validated by `core` tests on every build. |

## Build

Requires JDK 17 and the Android SDK (platform 35).

```bash
./gradlew :core:test          # unit tests
./gradlew :app:assembleDebug  # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:lintDebug
```

Without the Android SDK you can still run the core tests:

```bash
./gradlew -Pscary.coreOnly :core:test
```

CI (GitHub Actions) runs all three on every push and uploads the debug APK as the `scary-debug-apk` artifact.

## Privacy

Phase 1 has no internet permission, no accounts and no analytics. Preferences and the "recently opened" list stay on the device.

## Fonts

Karantina, Assistant and Rubik Wet Paint, from Google Fonts, under the SIL Open Font License 1.1.
