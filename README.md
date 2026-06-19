<div align="center">

# SG Carpark Android

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Google Maps](https://img.shields.io/badge/Google%20Maps-Compose-34A853?logo=googlemaps&logoColor=white)](https://developers.google.com/maps/documentation/android-sdk)

**Native Android car park availability map for Singapore drivers.**

[Report Bug](https://github.com/alfredang/sgcarparkapp_android/issues) · [Request Feature](https://github.com/alfredang/sgcarparkapp_android/issues)

</div>

## Screenshot

![Screenshot](screenshot.png)

## About

SG Carpark Android is a native Kotlin app for checking Singapore car park availability on a Google Maps interface. It mirrors the iOS app experience from `alfredang/sgcarparkapp` while using Android-first UI, location, and map APIs.

### Features

- Live car park availability pins from LTA DataMall `CarParkAvailabilityv2`.
- Google Maps interface with compass, marker callouts, and current-location support.
- Search by postal code, mall, street, place, car park name, area, or agency.
- Nearest car park selection with runtime location permission.
- Availability overview with total available lots and visible car park count.
- Bottom detail panel with lot count, agency, lot type, distance, and Google Maps directions.
- Clear setup fallback messages when API keys have not been configured.

## Tech Stack

| Layer | Technology |
| --- | --- |
| App | Kotlin, Android Gradle Plugin |
| UI | Jetpack Compose, Material 3 |
| Maps & Location | Google Maps Compose, Fused Location Provider, Android Geocoder |
| Networking | OkHttp |
| Serialization | Kotlin serialization |
| Data Source | LTA DataMall `CarParkAvailabilityv2` |
| Platform | Android 8.0+ |

## Architecture

```text
┌─────────────────────────────────────────────────────────────┐
│                    MainActivity / Compose UI                 │
│  Search panel · map markers · overview strip · detail panel  │
└──────────────────────────────┬──────────────────────────────┘
                               │ StateFlow<CarparkMapUiState>
┌──────────────────────────────▼──────────────────────────────┐
│                     CarparkMapViewModel                      │
│  refresh · search · selection · nearest car park coordination │
└───────────────┬───────────────────────┬─────────────────────┘
                │                       │
┌───────────────▼──────────────┐ ┌──────▼─────────────────────┐
│       LTADataMallClient       │ │ SearchService / Location    │
│  availability API + parsing   │ │ Geocoder + fused location   │
└───────────────┬──────────────┘ └──────┬─────────────────────┘
                │                       │
┌───────────────▼───────────────────────▼─────────────────────┐
│        LTA DataMall · Google Maps · Android location APIs     │
└─────────────────────────────────────────────────────────────┘
```

## Project Structure

```text
sgcarparkapp/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/alfredang/sgcarpark/
│       │   ├── CarparkMapViewModel.kt
│       │   ├── CarparkModels.kt
│       │   ├── LTADataMallClient.kt
│       │   ├── LocationService.kt
│       │   ├── MainActivity.kt
│       │   └── SearchService.kt
│       └── res/
├── gradle/
├── local.properties.example
├── screenshot.png
└── settings.gradle.kts
```

## Getting Started

### Prerequisites

- Android Studio with JDK 17.
- Android SDK with API 36.
- LTA DataMall account key.
- Google Maps Android API key.

### Setup

1. Clone the repository:

   ```bash
   git clone https://github.com/alfredang/sgcarparkapp_android.git
   cd sgcarparkapp_android
   ```

2. Copy the local configuration template:

   ```bash
   cp local.properties.example local.properties
   ```

3. Set the Android SDK path and API keys:

   ```properties
   sdk.dir=/Users/yourname/Library/Android/sdk
   ltaAccountKey=your_lta_datamall_account_key
   googleMapsApiKey=your_google_maps_android_api_key
   ```

4. Build the debug APK:

   ```bash
   JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
   ```

5. Run from Android Studio, or install the APK on a connected device:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

`local.properties` is intentionally ignored by Git because it contains machine-specific SDK paths and API keys.

## Configuration

The app reads `ltaAccountKey` into `BuildConfig.LTA_ACCOUNT_KEY` and injects `googleMapsApiKey` into the Android manifest. Without these keys, the app still opens and shows setup messages, but live map tiles and car park availability will not be available.

## Contributing

1. Fork the repository.
2. Create a feature branch.
3. Run `./gradlew :app:assembleDebug`.
4. Open a pull request with a clear description and screenshots for UI changes.

## Developed By

Developed by [Tertiary Infotech Academy Pte. Ltd.](https://www.tertiarycourses.com.sg/).

## Acknowledgements

- [LTA DataMall](https://datamall.lta.gov.sg/) for car park availability data.
- [Google Maps Platform](https://developers.google.com/maps) for Android map services.
- Android Jetpack and Kotlin open-source contributors.
