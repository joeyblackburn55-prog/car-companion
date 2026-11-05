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

## Status
**V1.0 – closed (Nov 5, 2026).** Installed on the head unit and working. Now in real-road testing before V2.

### Road-test checklist
- [ ] EQ comes back on by itself after a full head unit restart (car off overnight)
- [ ] EQ stays on during a long drive with ZLink / CarPlay
- [ ] EQ works on Bluetooth music from the phone
- [ ] Km counted by GPS match the dashboard odometer after a few drives (note the difference)
- [ ] Maintenance reminder notification shows up when an item gets close
- [ ] Everything readable and easy to tap while parked, day and night
- [ ] Maintenance intervals checked against the owner's manual

## V2 (needs an OBD-II Bluetooth adapter)
- **Diagnostics** – read and clear Check Engine codes, explained in plain English.
- **Gauges** – live speed, RPM, boost, temperatures, battery voltage.

## Build
Android Studio (JDK 17+) or `./gradlew assembleDebug`. CI builds every push (see `.github/workflows/build.yml`).
