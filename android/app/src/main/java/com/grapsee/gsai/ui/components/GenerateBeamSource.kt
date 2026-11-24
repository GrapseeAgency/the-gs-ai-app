package com.grapsee.gsai.ui.components

// Copyright (c) 2026 Jakub Antalik, MIT (see THIRD_PARTY_NOTICES.md).
// Source: Libraries.dev border-beam native port, 14ae9d2dad18fcee251f4b06333b492a2a41d2ed.
// Shader copied from rotateShader.ts with the fixed array sizes expanded.
// Preset data generated from upstream beam-spec.json, md/colorful.
internal object GenerateBeamSource {
    val stroke = floatArrayOf(70f, 40f, 0.33f, -0.074f, 1f, 0.19607843f, 0.39215686f, 1f, 60f, 35f, 0.12f, -0.05f, 0.15686275f, 0.54901961f, 1f, 1f, 40f, 70f, 0.021f, 0.683f, 0.19607843f, 0.78431373f, 0.31372549f, 1f, 20f, 35f, 0.021f, 0.683f, 0.11764706f, 0.7254902f, 0.66666667f, 1f, 180f, 32f, 0.744f, 1f, 0.39215686f, 0.2745098f, 1f, 1f, 85f, 26f, 0.55f, 1f, 0.15686275f, 0.54901961f, 1f, 1f, 74f, 32f, 0.939f, 0f, 1f, 0.47058824f, 0.15686275f, 1f, 26f, 42f, 1f, 0.271f, 0.94117647f, 0.19607843f, 0.70588235f, 1f, 52f, 48f, 1f, 0.271f, 0.70588235f, 0.15686275f, 0.94117647f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    val inner = floatArrayOf(63f, 36f, 0.33f, -0.074f, 1f, 0.19607843f, 0.39215686f, 0.45f, 54f, 32f, 0.12f, -0.05f, 0.15686275f, 0.54901961f, 1f, 0.45f, 36f, 63f, 0.021f, 0.683f, 0.19607843f, 0.78431373f, 0.31372549f, 0.45f, 18f, 32f, 0.021f, 0.683f, 0.11764706f, 0.7254902f, 0.66666667f, 0.45f, 162f, 29f, 0.744f, 1f, 0.39215686f, 0.2745098f, 1f, 0.45f, 77f, 23f, 0.55f, 1f, 0.15686275f, 0.54901961f, 1f, 0.45f, 67f, 29f, 0.939f, 0f, 1f, 0.47058824f, 0.15686275f, 0.45f, 23f, 38f, 1f, 0.271f, 0.94117647f, 0.19607843f, 0.70588235f, 0.45f, 47f, 43f, 1f, 0.271f, 0.70588235f, 0.15686275f, 0.94117647f, 0.45f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    const val blobCount = 9f
    val mask = floatArrayOf(0f, 0f, 0.3f, 0f, 0.36f, 0.1f, 0.44f, 0.35f, 0.52f, 1f, 0.8f, 1f, 0.86f, 0.35f, 0.92f, 0.1f, 0.95f, 0f, 1f, 0f, 0f, 0f, 0f, 0f)
    const val maskCount = 20f
    val lightStroke = floatArrayOf(0f, 0f, 0.54f, 0f, 0.57f, 0.08f, 0.6f, 0.2f, 0.63f, 0.4f, 0.66f, 0.55f, 0.69f, 0.4f, 0.72f, 0.2f, 0.75f, 0.08f, 0.78f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    val darkStroke = floatArrayOf(0f, 0f, 0.54f, 0f, 0.57f, 0.1f, 0.6f, 0.3f, 0.63f, 0.6f, 0.66f, 0.75f, 0.69f, 0.6f, 0.72f, 0.3f, 0.75f, 0.1f, 0.78f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    const val strokeCount = 22f
    val lightBloom = floatArrayOf(0f, 0f, 0.58f, 0f, 0.62f, 0.02f, 0.65f, 0.08f, 0.67f, 0.2f, 0.69f, 0.4f, 0.7f, 0.6f, 0.705f, 0.6f, 0.715f, 0.4f, 0.73f, 0.2f, 0.75f, 0.08f, 0.78f, 0.02f, 0.82f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    val darkBloom = floatArrayOf(0f, 0f, 0.58f, 0f, 0.62f, 0.03f, 0.65f, 0.08f, 0.67f, 0.2f, 0.69f, 0.45f, 0.7f, 0.85f, 0.705f, 0.85f, 0.715f, 0.45f, 0.73f, 0.2f, 0.75f, 0.08f, 0.78f, 0.03f, 0.82f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    const val bloomCount = 26f
    val emptyBlobs = FloatArray(96)
    val emptyBg = FloatArray(32)
    val emptyMask = FloatArray(24)
    const val duration = 1.96f
    const val strength = 0.7f
    const val shader = """
const float PI = 3.14159265358979;

uniform float2 uSize;
uniform float uAngle;        // beam angle, fraction of a full turn 0..1
uniform float uRadius;
uniform float uBorderWidth;
uniform float uKind;         // 0 stroke ring, 1 inner glow, 2 bloom ring
uniform float uEdgeMaskPx;   // inner-layer edge fade; 0 = none
uniform float uBlobs[96];
uniform float uBlobCount;
uniform float uBg[32];
uniform float uBgCount;      // float count (pairs * 2)
uniform float uBgIsBlack;
uniform float uMask[24];
uniform float uMaskCount;    // float count (pairs * 2)
uniform float uCM[9];        // row-major 3x3 color matrix
uniform float uOpacity;
uniform float4 uShadowColor;
uniform float uShadowBlur;

float roundedRectSDF(float2 p, float2 halfSize, float radius) {
  float2 q = abs(p) - halfSize + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float conicFraction(float2 p, float2 center) {
  float2 d = p - center;
  float theta = atan(d.x, -d.y);   // 0 at top, +clockwise (y-down coords)
  return fract(theta / (2.0 * PI) + 1.0);
}

float bgStopsAlpha(float t) {
  int n = int(uBgCount) * 1 / 2;
  if (n == 0) { return 1.0; }
  float result = uBg[1];
  for (int i = 1; i < 16; i++) {
    if (i < n) {
      float p0 = uBg[2 * (i - 1)];
      float a0 = uBg[2 * (i - 1) + 1];
      float p1 = uBg[2 * i];
      float a1 = uBg[2 * i + 1];
      if (t > p0) {
        float f = p1 > p0 ? clamp((t - p0) / (p1 - p0), 0.0, 1.0) : 1.0;
        result = mix(a0, a1, f);
      }
    }
  }
  return result;
}

float maskStopsAlpha(float t) {
  int n = int(uMaskCount) * 1 / 2;
  if (n == 0) { return 1.0; }
  float result = uMask[1];
  for (int i = 1; i < 12; i++) {
    if (i < n) {
      float p0 = uMask[2 * (i - 1)];
      float a0 = uMask[2 * (i - 1) + 1];
      float p1 = uMask[2 * i];
      float a1 = uMask[2 * i + 1];
      if (t > p0) {
        float f = p1 > p0 ? clamp((t - p0) / (p1 - p0), 0.0, 1.0) : 1.0;
        result = mix(a0, a1, f);
      }
    }
  }
  return result;
}

float4 srcOver(float4 src, float4 dst) {
  return src + dst * (1.0 - src.a);
}

half4 main(float2 position) {
  float2 center = uSize * 0.5;
  float2 rel = position - center;
  int kind = int(uKind);

  // Geometry: ring band for stroke/bloom, full rounded rect for inner.
  float outerSDF = roundedRectSDF(rel, center, uRadius);
  float aa = 1.0;
  float outerCov = 1.0 - smoothstep(-aa, 0.0, outerSDF);
  float geom;
  if (kind == 1) {
    geom = outerCov;
  } else {
    float innerRadius = max(uRadius - uBorderWidth, 0.0);
    float innerSDF = roundedRectSDF(rel, center - uBorderWidth, innerRadius);
    float innerCov = 1.0 - smoothstep(-aa, 0.0, innerSDF);
    geom = outerCov - innerCov;
  }
  if (geom <= 0.0) { return half4(0.0); }

  // Rotating conic beam-window mask.
  float mask = 1.0;
  if (uMaskCount > 0.5) {
    float t = fract(conicFraction(position, center) - uAngle);
    mask = maskStopsAlpha(t);
  }

  // Inner layer: edge fade (union of vertical + horizontal edge gradients).
  if (kind == 1 && uEdgeMaskPx > 0.0) {
    float ev = max(1.0 - position.y / uEdgeMaskPx,
                   1.0 - (uSize.y - position.y) / uEdgeMaskPx);
    float eh = max(1.0 - position.x / uEdgeMaskPx,
                   1.0 - (uSize.x - position.x) / uEdgeMaskPx);
    float edge = clamp(max(ev, 0.0) + max(eh, 0.0), 0.0, 1.0);
    mask *= edge;
  }
  if (mask <= 0.001) { return half4(0.0); }

  // Blob stack: premultiplied source-over, FIRST blob on top.
  float4 acc = float4(0.0);
  int nBlobs = int(uBlobCount);
  for (int i = 11; i >= 0; i--) {
    if (i < nBlobs) {
      float rx = max(uBlobs[i * 8 + 0], 0.001);
      float ry = max(uBlobs[i * 8 + 1], 0.001);
      float2 c = float2(uBlobs[i * 8 + 2], uBlobs[i * 8 + 3]) * uSize;
      float d = length((position - c) / float2(rx, ry));
      float alpha = uBlobs[i * 8 + 7] * clamp(1.0 - d, 0.0, 1.0);
      if (alpha > 0.0) {
        float3 rgb = float3(uBlobs[i * 8 + 4], uBlobs[i * 8 + 5], uBlobs[i * 8 + 6]);
        acc = srcOver(float4(rgb * alpha, alpha), acc);
      }
    }
  }

  // Conic gradient on top (white beam highlight / bloom ring pattern).
  if (uBgCount > 0.5) {
    float t = fract(conicFraction(position, center) - uAngle);
    float a = bgStopsAlpha(t);
    float3 rgb = uBgIsBlack > 0.5 ? float3(0.0) : float3(1.0);
    acc = srcOver(float4(rgb * a, a), acc);
  }

  // Inner inset shadow approximation.
  if (kind == 1 && uShadowColor.a > 0.0 && uShadowBlur > 0.0) {
    float depth = -outerSDF;
    float fall = clamp(1.0 - depth / (uShadowBlur + 1.0), 0.0, 1.0);
    float a = uShadowColor.a * fall * fall;
    acc = srcOver(float4(uShadowColor.rgb * a, a), acc);
  }

  // CSS filter chain (3x3 matrix on unpremultiplied RGB).
  if (acc.a > 0.0001) {
    float3 rgb = acc.rgb / acc.a;
    float3 outRGB = float3(
      dot(float3(uCM[0], uCM[1], uCM[2]), rgb),
      dot(float3(uCM[3], uCM[4], uCM[5]), rgb),
      dot(float3(uCM[6], uCM[7], uCM[8]), rgb)
    );
    acc.rgb = clamp(outRGB, 0.0, 1.0) * acc.a;
  }

  // CSS clamps the `opacity` property to [0,1]; several presets exceed 1
  // (e.g. line/dark stroke 1.14). Without this the premultiplied alpha goes
  // above 1 and compositing breaks down.
  float finalA = acc.a * geom * mask * clamp(uOpacity, 0.0, 1.0);
  if (acc.a > 0.0001) {
    return half4(half3(acc.rgb / acc.a * finalA), half(finalA));
  }
  return half4(0.0);
}
"""
}
