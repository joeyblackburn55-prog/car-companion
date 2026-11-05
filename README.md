# Car Companion

Apps for my Android head unit (TS18 platform, Android 15, no root).

## Step 1 – Car EQ Probe (current)

A small test app that checks which ways of changing the sound work on this head unit
before the real equalizer is built.

**Download:** [car-eq-probe.apk](https://github.com/joeyblackburn55-prog/car-companion/releases/download/latest/car-eq-probe.apk)
(rebuilt automatically on every push to `main`).

### How to test
1. Install the APK on the head unit (allow "install unknown apps" for the browser / file manager).
2. In the head unit's own sound settings, set its EQ to **Flat**.
3. Open **Car EQ Probe**, then start music in **ZLink (CarPlay)**.
4. Come back to the probe and turn each test **ON**, one at a time, listening for:
   - **Classic Equalizer** / **DynamicsProcessing EQ** → bass disappears, sounds like a telephone
   - **Bass Boost** → much more bass
   - **Loudness Enhancer** → clearly louder
5. Turn everything off, repeat with **Bluetooth music** from the phone.
6. Tap **Copy / share report** (or take a photo of the screen) and send the results.

## Roadmap
1. Probe – find what works on the TS18 ✅ in progress
2. MVP equalizer – 10+ bands, presets, preamp, background service, auto-start
3. V2 – speed-based volume/EQ, automatic presets per source, spectrum display
4. V3 – auto-EQ with a microphone measurement
