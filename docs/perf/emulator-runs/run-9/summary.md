# smooth-check run 20261015-192301

- date: 2026-10-15T19:33:18Z
- package: com.grapsee.gsai  activity: com.grapsee.gsai/.MainActivity
- device: sdk_gphone64_x86_64 (android 14, sdk 34), screen 1080x2400, refresh 60Hz, budget 16.67ms
- perfetto captured: 1 (1 = traces sm-<id>.pftrace in this folder, open at ui.perfetto.dev)

```
INTERACTION                            FRAMES  JANKY   JANK%    P50    P95    P99  VERDICT
01-cold-start-first-frame                   1      1 100.00%    200    200    200  FAIL
02-warm-start-first-frame                   0      0   0.00%      -      -      -  FAIL
03-resume-from-background                   0      0   0.00%      -      -      -  FAIL
04-empty-chat-render                       23     20  86.96%    125    150    150  FAIL
05-first-message-send                      77     61  79.22%     89    250    250  FAIL
06-streaming-reply-5s                      99     85  85.86%     89    150    300  FAIL
07-short-chat-scroll-20-swipe               0      0   0.00%      -      -      -  FAIL
08-long-chat-scroll-50-message            114    100  87.72%     85    150    150  FAIL
09-long-chat-fling-top-to-bottom           26     22  84.62%     81    350    350  FAIL
10-rich-reply-render-table                 12      9  75.00%     65    300    300  FAIL
11-rich-reply-render-code                  88     74  84.09%     77    129    350  FAIL
12-rich-reply-render-image                102     81  79.41%     77    150    200  FAIL
13-citation-tap-response                    0      0   0.00%      -      -      -  FAIL
14-composer-open                           18     10  55.56%     69    117    117  FAIL
15-composer-type-50-chars                  53     43  81.13%     85    150    150  FAIL
16-composer-expand-attach                  30     30 100.00%    117    450    600  FAIL
17-composer-collapse                       28     17  60.71%     61    109    150  FAIL
18-composer-send-button-press              66     47  71.21%     57    129    150  FAIL
19-image-picker-open                       17     17 100.00%    200    550    550  FAIL
20-image-preview-render                    10      8  80.00%     73    109    109  FAIL
21-image-upload-progress                    0      0   0.00%      -      -      -  FAIL
22-image-upload-complete                    0      0   0.00%      -      -      -  FAIL
23-drawer-open                              1      1 100.00%     77     77     77  FAIL
24-drawer-scroll                            0      0   0.00%      -      -      -  FAIL
25-drawer-close                             1      1 100.00%     44     44     44  FAIL
26-drawer-tap-conversation                  0      0   0.00%      -      -      -  FAIL
27-conversation-switch                      0      0   0.00%      -      -      -  FAIL
28-mode-picker-open                         1      0   0.00%      6      6      6  FAIL
29-mode-picker-scroll                       0      0   0.00%      -      -      -  FAIL
30-mode-picker-select                      10      7  70.00%     81    125    125  FAIL
31-mode-picker-close                        1      1 100.00%     93     93     93  FAIL
32-search-open                             13      6  46.15%     53    150    150  FAIL
33-search-type                             16      9  56.25%     36    109    109  FAIL
34-search-results-render                    0      0   0.00%      -      -      -  FAIL
35-settings-open                            1      0   0.00%     11     11     11  FAIL
36-settings-scroll                          0      0   0.00%      -      -      -  FAIL
37-settings-toggle-response                 0      0   0.00%      -      -      -  FAIL
38-settings-submenu-open                    0      0   0.00%      -      -      -  FAIL
39-settings-back                            1      1 100.00%     85     85     85  FAIL
40-onboarding-first-slide                   0      0   0.00%      -      -      -  FAIL
41-onboarding-advance                       2      2 100.00%     77     77     77  FAIL
42-onboarding-complete                      0      0   0.00%      -      -      -  FAIL
43-network-error-state                     58     43  74.14%     73    150    150  FAIL
44-retry-after-error                        0      0   0.00%      -      -      -  FAIL
45-model-download-progress                 12      9  75.00%     69    150    150  FAIL
46-model-download-complete                  0      0   0.00%      -      -      -  FAIL
47-model-load-first-use                    68     58  85.29%     85    200    300  FAIL
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
