package com.grapsee.gsai.ui.components

// ArgentUI v0.4.1 (MIT), 6d5d4f4dbcfbd6fe6d7a04762d7bb2da47856f7e.
// src/engine.ts native shader, ported from GLSL to AGSL. Coordinates are
// flipped to preserve WebGL's y-up convention. No synthetic gradient.
// simplex noise: Ashima Arts / Stefan Gustavson (MIT).
internal const val ARGENT_RIM_SHADER = """uniform float2 u_res;
uniform float u_time;
uniform float3 u_light;
uniform float3 u_dark;
uniform float u_rep;
uniform float u_angle;
uniform float u_soft;
uniform float u_disp;
uniform float u_dist;
uniform float u_scale;
// 2D simplex noise — Ashima Arts / Stefan Gustavson (webgl-noise, MIT).
float3 mod289(float3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
float2 mod289(float2 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
float3 permute(float3 x) { return mod289(((x * 34.0) + 1.0) * x); }
float snoise(float2 v) {
  const float4 C = float4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);
  float2 i = floor(v + dot(v, C.yy));
  float2 x0 = v - i + dot(i, C.xx);
  float2 i1 = (x0.x > x0.y) ? float2(1.0, 0.0) : float2(0.0, 1.0);
  float4 x12 = x0.xyxy + C.xxzz;
  x12.xy -= i1;
  i = mod289(i);
  float3 p = permute(permute(i.y + float3(0.0, i1.y, 1.0)) + i.x + float3(0.0, i1.x, 1.0));
  float3 m = max(0.5 - float3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
  m = m * m;
  m = m * m;
  float3 x = 2.0 * fract(p * C.www) - 1.0;
  float3 h = abs(x) - 0.5;
  float3 ox = floor(x + 0.5);
  float3 a0 = x - ox;
  m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
  float3 g;
  g.x = a0.x * x0.x + h.x * x0.y;
  g.yz = a0.yz * x12.xz + h.yz * x12.yw;
  return 130.0 * dot(m, g);
}

// One reflection band: a soft triangle wave over the warped coordinate.
float stripe(float coord) {
  float f = fract(coord);
  float tri = abs(f * 2.0 - 1.0);
  return smoothstep(0.5 - u_soft, 0.5 + u_soft, tri);
}

half4 main(float2 position) {
  float2 uv = (float2(position.x, u_res.y - position.y) / u_res - 0.5);
  uv.x *= u_res.x / u_res.y;
  uv /= max(u_scale, 0.001);

  float t = u_time;

  // curvature: bands bend as if wrapping a bulged surface
  float d = length(uv);
  float bulge = 1.0 - 0.45 * d * d;

  // flowing warp — two noise octaves drifting at different rates
  float n = snoise(uv * 0.9 + float2(0.0, -t * 0.28));
  n += 0.35 * snoise(uv * 1.8 + float2(t * 0.1, -t * 0.45));

  // rotate so bands run along u_angle
  float c = cos(u_angle), s = sin(u_angle);
  float2 ruv = float2x2(c, -s, s, c) * uv;

  float coord = ruv.y * bulge * u_rep + n * u_dist * 2.2 - t * 0.22;

  // per-channel dispersion — the chrome fringing
  float r = stripe(coord + u_disp);
  float g = stripe(coord);
  float b = stripe(coord - u_disp);

  float3 col = float3(
    mix(u_dark.r, u_light.r, r),
    mix(u_dark.g, u_light.g, g),
    mix(u_dark.b, u_light.b, b)
  );

  // rim lift toward the edges
  col *= 0.94 + 0.14 * smoothstep(0.25, 0.85, d);

  return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}"""
