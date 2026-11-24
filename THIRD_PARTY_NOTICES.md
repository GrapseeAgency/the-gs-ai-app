# Third-party notices

## Libraries.dev — `metal-fx`

Copyright (c) 2026 Jakub Antalik. Licensed under the MIT License; the full text is in [`third_party/licenses/MIT-Libraries.dev-metal-fx.txt`](third_party/licenses/MIT-Libraries.dev-metal-fx.txt).

Source: <https://github.com/Jakubantalik/Libraries.dev/tree/main/packages/metal-fx>

The iOS app links the upstream `MetalFxKit` Swift package. The Android Compose renderer in `GsMetallicControls.kt` adapts the upstream React Native Skia `LIQUID_METAL_SKSL` and the Swift `MetalMaterial.sample` equations to AGSL and a CPU bitmap fallback; it is not an upstream Compose package. `GsMetalSendRing.kt` also translates the original circular band geometry, chromatic material preset and gravity-driven bend simulation from the React Native port (`material.ts`, `geometry.ts`, `bend.ts`, upstream revision `14ae9d2dad18fcee251f4b06333b492a2a41d2ed`). It does not implement the separate halo or reflection effects.

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

## Libraries.dev — `border-beam`

Copyright (c) 2026 Jakub Antalik, MIT. License: [MIT-Libraries.dev-border-beam.txt](third_party/licenses/MIT-Libraries.dev-border-beam.txt). Source: <https://github.com/Jakubantalik/Libraries.dev/tree/14ae9d2dad18fcee251f4b06333b492a2a41d2ed/packages/border-beam>.

iOS uses upstream BorderBeamKit. Android uses the upstream React Native SkSL rotate-layer shader and md/colorful preset data adapted to native Android Canvas/AGSL; the native software fallback implements the same rotating mask and colored gradient blob layers.

The Android neutral mono Voice Ring renderer in `GsVoiceRing.kt` translates the upstream Voice Ring level follower, two-loop geometry, depth-sorted tube painter and direction lean field (revision `14ae9d2dad18fcee251f4b06333b492a2a41d2ed`). It is a native port, not an official Android package. iOS Talk and Voice Position use upstream VoiceRingKit directly.

## ArgentUI — component rims

Copyright (c) 2026 Sean Geng. MIT, see [MIT-ArgentUI.txt](third_party/licenses/MIT-ArgentUI.txt). Source: <https://github.com/seangeng/argent/tree/6d5d4f4dbcfbd6fe6d7a04762d7bb2da47856f7e> (v0.4.1). The native apps port ArgentUI's own optional `engine="native"` shader in `src/engine.ts`, not the separately licensed default Paper engine or React component. Android uses AGSL with a static scalar implementation of the same field for older/software devices; iOS uses a SwiftUI Metal color effect. The original simplex noise and reflection field are retained, with a deliberately calm fixed phase, broad bands (1.2 repetitions, softness 0.48, distortion 0.05, scale 1.6), zero chromatic dispersion, neutral palettes, aspect cap 2.5 and a directional opacity envelope (4.5–24% light, 7–38% dark). It is a 0.75dp/pt component-only stroke. No idle animation, halo, reveal or new fill. The normal glass rim is replaced rather than stacked on eligible glass controls; excluded surfaces retain their original rim. Elevated chat header buttons explicitly opt in; outer docks, sheets, oversized panels and special send/Generate effects remain excluded. High contrast retains the original border. Custom borders are migrated only at identified component call sites; other bespoke borders remain.

The shader's simplex noise is MIT licensed by Ashima Arts and Stefan Gustavson; see [MIT-Ashima-WebGL-Noise.txt](third_party/licenses/MIT-Ashima-WebGL-Noise.txt).
