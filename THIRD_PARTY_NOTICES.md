# Third-party notices

## Libraries.dev — `metal-fx`

Copyright (c) 2026 Jakub Antalik. Licensed under the MIT License; the full text is in [`third_party/licenses/MIT-Libraries.dev-metal-fx.txt`](third_party/licenses/MIT-Libraries.dev-metal-fx.txt).

Source: <https://github.com/Jakubantalik/Libraries.dev/tree/main/packages/metal-fx>

The iOS app links the upstream `MetalFxKit` Swift package. The Android Compose renderer in `GsMetallicControls.kt` adapts the upstream React Native Skia `LIQUID_METAL_SKSL` and the Swift `MetalMaterial.sample` equations to AGSL and a CPU bitmap fallback; it is not an upstream Compose package.

### Paper Shaders

The upstream `metal-fx` implementation includes the `liquidMetal` fragment shader from Paper Design, Inc. (`@paper-design/shaders`, version 0.0.80), licensed under Apache License 2.0. The Android adaptation ports its equations to AGSL and the CPU bitmap path; it is not an unmodified copy. The upstream attribution is reproduced in [`third_party/licenses/NOTICE-Libraries.dev-metal-fx.txt`](third_party/licenses/NOTICE-Libraries.dev-metal-fx.txt), and the Apache license text is in [`third_party/licenses/Apache-2.0.txt`](third_party/licenses/Apache-2.0.txt).

Source: <https://github.com/paper-design/shaders>

## Libraries.dev — `distortion-waves`

Copyright (c) 2026 Jakub Antalik. Licensed under the MIT License; the full text is in [`third_party/licenses/MIT-Libraries.dev-distortion-waves.txt`](third_party/licenses/MIT-Libraries.dev-distortion-waves.txt).

Source: <https://github.com/Jakubantalik/Libraries.dev/tree/main/packages/distortion-waves>

The iOS app links upstream `DistortionWavesKit`. The Android Compose component in `GsDistortionWave.kt` is a native Kotlin adaptation of the upstream `WaveFront`, `WaveParticles`, `WaveRim`, timing/easing, and the field, lens, and color-band behavior in `DistortionWaveShaders.metal`. No Android Compose implementation is provided by the upstream repository.

## Libraries.dev — `voice-ring` / `VoicePositionKit`

Copyright (c) 2026 Jakub Antalik. The iOS app links the upstream `VoicePositionKit` and `VoiceRingKit` products from <https://github.com/Jakubantalik/Libraries.dev/tree/main/packages/voice-ring>, licensed under MIT; the license text is in [`third_party/licenses/MIT-Libraries.dev-voice-position.txt`](third_party/licenses/MIT-Libraries.dev-voice-position.txt).

The Android code in `android/app/src/main/java/com/grapsee/gsai/ui/voice/position/` is a native Kotlin adaptation of the public MIT-licensed VoiceCore/VoicePositionKit feature extraction, MFXL model reader, inference, gates, and calibration. There is no upstream Android `VoicePositionKit` product. The separate `VoicePosition.mfxl` model and its weights are under the Libraries.dev Model License, not MIT. It is not included in this repository; see [`docs/VOICE_POSITION.md`](docs/VOICE_POSITION.md) for local app-target paths and the model-license terms.
