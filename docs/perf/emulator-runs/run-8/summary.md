# smooth-check run 20261015-185857

- date: 2026-10-15T19:10:28Z
- package: com.grapsee.gsai  activity: com.grapsee.gsai/.MainActivity
- device: sdk_gphone64_x86_64 (android 14, sdk 34), screen 1080x2400, refresh 60Hz, budget 16.67ms
- perfetto captured: 1 (1 = traces sm-<id>.pftrace in this folder, open at ui.perfetto.dev)

```
INTERACTION                            FRAMES  JANKY   JANK%    P50    P95    P99  VERDICT
01-cold-start-first-frame                   3      3 100.00%     85    101    101  FAIL
02-warm-start-first-frame                   0      0   0.00%      -      -      -  FAIL
03-resume-from-background                   1      1 100.00%     42     42     42  FAIL
04-empty-chat-render                        1      1 100.00%     18     18     18  FAIL
05-first-message-send                       7      2  28.57%     19     97     97  FAIL
06-streaming-reply-5s                       7      2  28.57%     24    109    109  FAIL
07-short-chat-scroll-20-swipe               0      0   0.00%      -      -      -  FAIL
08-long-chat-scroll-50-message              0      0   0.00%      -      -      -  FAIL
09-long-chat-fling-top-to-bottom            0      0   0.00%      -      -      -  FAIL
10-rich-reply-render-table                  7      4  57.14%     16     42     42  FAIL
11-rich-reply-render-code                   7      2  28.57%     16    117    117  FAIL
12-rich-reply-render-image                  7      4  57.14%     36    117    117  FAIL
13-citation-tap-response                    0      0   0.00%      -      -      -  FAIL
14-composer-open                            1      0   0.00%     10     10     10  FAIL
15-composer-type-50-chars                   4      3  75.00%    113    150    150  FAIL
16-composer-expand-attach                   2      2 100.00%     32     32     32  FAIL
17-composer-collapse                        0      0   0.00%      -      -      -  FAIL
18-composer-send-button-press               6      3  50.00%     17    117    117  FAIL
19-image-picker-open                        0      0   0.00%      -      -      -  FAIL
20-image-preview-render                     0      0   0.00%      -      -      -  FAIL
21-image-upload-progress                    0      0   0.00%      -      -      -  FAIL
22-image-upload-complete                    0      0   0.00%      -      -      -  FAIL
23-drawer-open                              0      0   0.00%      -      -      -  FAIL
24-drawer-scroll                            0      0   0.00%      -      -      -  FAIL
25-drawer-close                             0      0   0.00%      -      -      -  FAIL
26-drawer-tap-conversation                  0      0   0.00%      -      -      -  FAIL
27-conversation-switch                      0      0   0.00%      -      -      -  FAIL
28-mode-picker-open                         1      0   0.00%     11     11     11  FAIL
29-mode-picker-scroll                       0      0   0.00%      -      -      -  FAIL
30-mode-picker-select                       0      0   0.00%      -      -      -  FAIL
31-mode-picker-close                        0      0   0.00%      -      -      -  FAIL
32-search-open                              1      0   0.00%     10     10     10  FAIL
33-search-type                              4      3  75.00%    150    150    150  FAIL
34-search-results-render                    0      0   0.00%      -      -      -  FAIL
35-settings-open                            7      4  57.14%    117    300    300  FAIL
36-settings-scroll                          0      0   0.00%      -      -      -  FAIL
37-settings-toggle-response                 0      0   0.00%      -      -      -  FAIL
38-settings-submenu-open                    0      0   0.00%      -      -      -  FAIL
39-settings-back                            0      0   0.00%      -      -      -  FAIL
40-onboarding-first-slide                   0      0   0.00%      -      -      -  FAIL
41-onboarding-advance                       1      1 100.00%    150    150    150  FAIL
42-onboarding-complete                      5      5 100.00%    300    600    600  FAIL
43-network-error-state                      7      3  42.86%     26    113    113  FAIL
44-retry-after-error                        0      0   0.00%      -      -      -  FAIL
45-model-download-progress                  0      0   0.00%      -      -      -  FAIL
46-model-download-complete                  0      0   0.00%      -      -      -  FAIL
47-model-load-first-use                     7      2  28.57%     14    105    105  FAIL
48-splash-to-first-frame                    0      0   0.00%      -      -      -  FAIL
```

TOTAL: 0 of 48 PASS, 48 FAIL

Stat sources: FRAMES/JANKY/JANK% from dumpsys gfxinfo summary since reset
(device jank definition, full window). P50/P90/P95/P99/WORST from
gfxinfo framestats per-frame durations, FrameCompleted-IntendedVsync,
last ~120 frames (adb limitation). Worst frame source: read the
matching sm-<id>.pftrace at ui.perfetto.dev frame timeline.
Raw command log: commands.log. Raw frame data: <id>-framestats.raw,
<id>-summary.raw, <id>.durs, <id>.sorted. Launch timings: <id>-amstart.raw.
