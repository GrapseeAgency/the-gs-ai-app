# Voice Position (beta)

## What is real, and what is not

The iOS screen uses Libraries.dev's actual `VoicePositionKit` and `VoiceRingKit`. The model weights are a separate, licensed download and are not in this repository. Until that file is added, iOS uses the package's real three-position calibration fallback; distance remains unavailable.

There is no upstream Android `VoicePositionKit`. Android now has a native Kotlin reader for the public MFXL format and the MIT-licensed VoiceCore/VoicePositionKit feature extraction, model inference, direction/distance gates, and calibration path. If the user supplies the licensed model in Android assets, it runs those weights on-device. Without the file, it offers local side calibration and explicitly reports no distance model. **This Android port has not been validated on Android phones and must not inherit the iPhone's beta accuracy claims.**

The existing Talk/dictation and read-aloud paths remain separate and intact. Position analysis does not transcribe or upload audio. Distance updates pause while the app's own TTS is speaking.

## Add the separately licensed model (optional, required for model-based output)

Download the optional `VoicePosition.mfxl` v2.0 beta from the **Model only** tab at <https://libraries.dev/voice-position> using the Libraries.dev free account, then add the file to the relevant app target:

- iOS: `ios/App/Resources/VoicePosition.mfxl` (the XcodeGen app target includes `App/Resources`).
- Android: `android/app/src/main/assets/VoicePosition.mfxl`.

The filename is ignored by Git. Do not commit, publish, or otherwise redistribute the model. The Swift package does not contain it. Commercial releases that load or ship the model require Libraries.dev Pro or Business; development and non-commercial use are allowed by the model license. The app code can build without this file.

## iOS

The iOS target uses Swift Package Manager from `https://github.com/Jakubantalik/Libraries.dev`, branch `main`, with `VoicePositionKit` and `VoiceRingKit`. The app targets iOS 17+. `NSMicrophoneUsageDescription` explains local analysis and dictation. The view shares one `VoiceMeter` with `VoicePosition` and the upstream `VoiceRing`; `VoicePositionKit` owns microphone capture, stereo feature extraction, pose sensing, inference, direction/distance decisions, and calibration. It starts only after permission has already been granted or the user taps Start.

Without the model, calibrate left, ahead, and right while holding the phone in the pose where it will be used. The package's calibration replaces the side model for that pose; it does not generate distance. Calibrations are saved locally. The beta model was tested on iPhone 15 Pro only; results and limitations are documented at <https://libraries.dev/voice-position>.

## Android

Android's app minimum remains API 26. The microphone is requested through Android's runtime permission flow. The model reader looks only for `VoicePosition.mfxl` in app assets and reads its version, bands, per-pose weights, and optional per-pose distance weights from the file. It does not embed weights in Kotlin or infer a fixed side/distance from volume. The feature extraction is the port of the upstream 1024-sample Blackman FFT and stereo band/cross-spectrum features. Calibration, when used, follows the upstream level/phase cue path; a complete saved calibration takes precedence for that pose, matching the upstream default. The official iPhone 15 Pro accuracy numbers do **not** apply to Android.

Android stereo validation adds a local, unvalidated input-quality heuristic because `AudioRecord` can expose two channels that contain a duplicated mono signal. It treats a normalized channel-difference RMS above `0.0005` in at least 3 of a rolling 48 voiced-frame window as distinct-channel evidence; a ratio at or below `0.000001` in all 48 frames is treated as duplicated mono, while the middle range remains `UNKNOWN` and produces no position. These values classify input only—they are not learned weights or evidence of direction accuracy. They are not upstream VoicePositionKit thresholds, have not been tuned on Android hardware, and should be calibrated with recordings from supported device/route combinations before claiming hardware parity. Android pose classification is also a heuristic: it estimates gravity from `TYPE_ACCELEROMETER` using a per-event low-pass coefficient of `0.8`, then applies the upstream `0.9 g` flat and `0.03 g` stillness gates with the upstream 1.5-second desk / 0.5-second hand settling times. This is not equivalent to iOS CoreMotion device-motion data; when there is no usable accelerometer, the pose stays unknown rather than selecting a classifier. Capture tries 48 kHz, 44.1 kHz, then 32 kHz to keep the model's 9 kHz band below Nyquist; none of these Android routes have been hardware-validated.

If the model file is absent or invalid, Android reports that state. Without a usable model for the detected pose, the user may calibrate left/ahead/right for side only; distance is unavailable. The currently published v2 model has a desk distance classifier and no hand distance classifier; Android reads the per-pose entries in the supplied file rather than assigning distance from loudness. Mono or indistinguishable duplicated channels produce no direction or distance. The pose sensor may report unknown when it cannot distinguish hand from desk; the UI never silently substitutes a hand/desk model in that state. Android audio routing, stereo mic availability, sample rates, and pose classification vary by device; this is an experimental native port, not upstream-supported or hardware-validated parity.

## Readings and limitations

On iOS, the values come directly from the upstream observable `VoicePosition`: committed `pan`/`side`, pre-commit probabilities, pose, level, model status, distance, distance belief, `soundsFar`, and `readsDistance`. Distance is model-driven and currently desk-only; it is categorical (`small`/`large`), not a metre measurement. The iOS UI sets `readsDistance = false` while this app's read-aloud player is active.

The iPhone beta's model is trained/tested on one speaker and iPhone 15 Pro, upright or flat on a desk. The roughly 70 cm distance boundary is an estimate. Multiple simultaneous speakers, noisy rooms, landscape, far off-axis voices, hand-held distance, other iPhone models, and Bluetooth/mono routes are not covered. Simulator/Mac/Bluetooth mono input should show no side/distance; drive previews with a slider instead of manufacturing readings.

The Android UI is explicit that its model-backed port is experimental and not covered by those results. Its hard-coded values are only signal-processing bands, upstream gate/hysteresis settings, and device/route validity checks—not learned weights or an invented distance mapping. Model weights are read from the optional file.
