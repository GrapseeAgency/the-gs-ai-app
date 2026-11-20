# Voice Position integration

## iOS

The iOS Voice Position screen uses the `VoicePositionKit` and `VoiceRingKit` products from `https://github.com/Jakubantalik/Libraries.dev` (branch `main`). The app target remains iOS 17+, matching the package and the app's existing deployment floor. Direction is reported only from live stereo input with either a supported model or a saved calibration for the current pose. The UI shows the detected hand/desk pose, microphone loudness, pan and side; distance stays unavailable unless the loaded model contains a distance model for that pose.

The model is **not** part of the Swift package or this repository. Download `VoicePosition.mfxl` from the Model only tab at <https://libraries.dev/voice-position> and add it to the iOS app target as a bundle resource (recommended local path: `ios/App/Resources/VoicePosition.mfxl`). `.gitignore` excludes this exact model name throughout the repository. Never commit or publish the file. Without it, calibrate left, ahead and right on the actual phone; calibration is saved locally and separately for hand and desk poses. The optional model was trained on iPhone 15 Pro, so other iPhone models should be calibrated and model-derived readings must not be treated as universally validated.

## Android

There is no upstream Android `VoicePositionKit` port. The Android implementation is a native, best-effort stereo-cue calibration for Android 10+ (API 29+) and saves separate hand/desk calibrations on-device (or an explicit unknown-pose profile if motion sensing is missing). Android microphone routing and stereo availability vary by manufacturer and route. If the app receives mono or duplicated channels, it reports direction as unavailable rather than guessing. This implementation does **not** load the iPhone-trained `.mfxl` file and does not report distance; the distance row explicitly stays unavailable until a validated Android model exists.

The app's existing minimum Android version is unchanged. On Android versions below 10, the Voice Position screen explains the API limitation while the rest of the app continues to work.

## Speech and privacy

Talk mode, speech-to-text dictation, chat handoff, and local text-to-speech remain available; Voice Position is an additional primary Voice surface, not a deletion of those shared features. The new position capture analyses microphone samples on-device and does not transcribe or upload them. Microphone capture stops when the screen closes.

The Libraries.dev model license permits development, prototyping and non-commercial apps; a commercial release that bundles the model requires Libraries.dev Pro or Business. Check the current license at Libraries.dev before shipping.
