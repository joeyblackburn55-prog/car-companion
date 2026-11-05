# Car Companion

A CarPlay-style companion app for my Android head unit
(TS18 platform, Android 15, no root) in a 2012 Subaru WRX.

**Download:** [car-companion.apk](https://github.com/joeyblackburn55-prog/car-companion/releases/download/latest/car-companion.apk)
(rebuilt automatically on every push to `main`; installs over the previous version).

## V1
- **Sound** – a simple equalizer for everything the head unit plays (ZLink/CarPlay, Bluetooth):
  four one-tap modes (Normal, More bass, Voices, Highway) and three sliders (Bass, Voices, Treble).
  Uses `DynamicsProcessing` on the global output with a limiter, so it never distorts.
- **Maintenance** – counts kilometres with the head unit's GPS (enter the odometer once),
  reminds you before each service and keeps a history. Starting intervals for a 2012 WRX 5-speed:
  engine oil, transmission oil (also feeds the front diff), rear diff, coolant, brake & clutch fluid,
  power steering fluid, tire rotation, engine and cabin air filters. Every value is editable.

## V2 (needs an OBD-II Bluetooth adapter)
- **Diagnostics** – read and clear Check Engine codes, explained in plain English.
- **Gauges** – live speed, RPM, boost, temperatures, battery voltage.

## Build
Android Studio (JDK 17+) or `./gradlew assembleDebug`. CI builds every push (see `.github/workflows/build.yml`).
