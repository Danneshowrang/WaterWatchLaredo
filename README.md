# WaterWatch Laredo

An Android app for reporting water taste, smell, color, and low-pressure observations in Laredo, Texas. It maps saved reports, highlights nearby same-type report patterns, and displays city-published Utilities updates.

Built with Kotlin, Jetpack Compose, Room, osmdroid/OpenStreetMap, and Firebase Authentication. Resident reports currently save locally on the device. Fictional demo reports are labeled and analyzed separately. The app does not replace official city notices.

## Run

1. Open this folder in Android Studio with Android SDK 37 installed. The Gradle wrapper configures Java toolchain 25; allow its download if prompted.
2. Register `com.example.waterwatchlaredo` in your Firebase project, enable Email/Password and Google authentication, and download its Android configuration to `app/google-services.json`.
3. Sync Gradle and select an emulator or device running Android 8.0 (API 26) or newer.
4. Run the `app` configuration. Internet access is needed for map tiles, city updates, and sign-in. Enable device location for reporting.

Map and Community can be explored without an account. Open **Report → About → Explore demo** to load fictional reports. Saving an observation requires a verified account and a location with reported accuracy of 100 meters or better.

Firebase configuration is not included in this repository. Supply your own `app/google-services.json` before building. Google sign-in also requires registering your signing certificate fingerprints in Firebase.

## Build and test

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

On Windows, use `gradlew.bat`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Map data: © OpenStreetMap contributors. The optional 2022 water-main layer is an unofficial reference and does not establish flow direction or water safety. City updates link to their original sources.
