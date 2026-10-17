# smooth-check run 20261017-214544

- date: 2026-10-17T21:55:15Z
- package: com.grapsee.gsai  activity: com.grapsee.gsai/.MainActivity
- device: sdk_gphone64_x86_64 (android 14, sdk 34), screen 1080x2400, refresh 60Hz, budget 16.67ms
- perfetto captured: 1 (1 = traces sm-<id>.pftrace in this folder, open at ui.perfetto.dev)

```
INTERACTION                            FRAMES  JANKY   JANK%    P50    P95    P99  VERDICT
01-cold-start-first-frame                   0      0   0.00%      -      -      -  FAIL
02-warm-start-first-frame                   0      0   0.00%      -      -      -  FAIL
03-resume-from-background                   0      0   0.00%      -      -      -  FAIL
04-empty-chat-render                       20     18  90.00%    125    200    200  FAIL
05-first-message-send                      74     65  87.84%    117    250   1250  FAIL
06-streaming-reply-5s                      91     83  91.21%    109    250    900  FAIL
07-short-chat-scroll-20-swipe               0      0   0.00%      -      -      -  FAIL
08-long-chat-scroll-50-message            110    104  94.55%     97    200    400  FAIL
09-long-chat-fling-top-to-bottom            0      0   0.00%      -      -      -  FAIL
10-rich-reply-render-table                  6      4  66.67%    150    150    150  FAIL
11-rich-reply-render-code                  90     84  93.33%     97    150    350  FAIL
12-rich-reply-render-image                 84     72  85.71%     93    150    400  FAIL
13-citation-tap-response                    0      0   0.00%      -      -      -  FAIL
14-composer-open                           17     12  70.59%     69    121    121  FAIL
15-composer-type-50-chars                  78     72  92.31%    117    150    200  FAIL
16-composer-expand-attach                  23     23 100.00%    200    650    750  FAIL
17-composer-collapse                       27     20  74.07%     81    113    129  FAIL
18-composer-send-button-press              50     43  86.00%     73    150    250  FAIL
19-image-picker-open                       18     18 100.00%    150    450    450  FAIL
20-image-preview-render                     9      8  88.89%     77    150    150  FAIL
21-image-upload-progress                    0      0   0.00%      -      -      -  FAIL
22-image-upload-complete                    0      0   0.00%      -      -      -  FAIL
23-drawer-open                             10     10 100.00%     93    150    150  FAIL
24-drawer-scroll                            1      1 100.00%    150    150    150  FAIL
25-drawer-close                             1      1 100.00%     53     53     53  FAIL
26-drawer-tap-conversation                  1      1 100.00%    150    150    150  FAIL
27-conversation-switch                      0      0   0.00%      -      -      -  FAIL
28-mode-picker-open                         2      1  50.00%    200    200    200  FAIL
29-mode-picker-scroll                       0      0   0.00%      -      -      -  FAIL
30-mode-picker-select                       9      6  66.67%     89    150    150  FAIL
31-mode-picker-close                        1      1 100.00%     61     61     61  FAIL
32-search-open                              1      1 100.00%     73     73     73  FAIL
33-search-type                              1      1 100.00%     16     16     16  FAIL
34-search-results-render                    0      0   0.00%      -      -      -  FAIL
35-settings-open                            0      0   0.00%      -      -      -  FAIL
36-settings-scroll                          0      0   0.00%      -      -      -  FAIL
37-settings-toggle-response                 0      0   0.00%      -      -      -  FAIL
38-settings-submenu-open                    0      0   0.00%      -      -      -  FAIL
39-settings-back                            0      0   0.00%      -      -      -  FAIL
40-onboarding-first-slide                   0      0   0.00%      -      -      -  FAIL
41-onboarding-advance                       1      1 100.00%     81     81     81  FAIL
42-onboarding-complete                      0      0   0.00%      -      -      -  FAIL
43-network-error-state                      2      2 100.00%     53     53     53  FAIL
44-retry-after-error                        1      1 100.00%     97     97     97  FAIL
45-model-download-progress                  0      0   0.00%      -      -      -  FAIL
46-model-download-complete                  0      0   0.00%      -      -      -  FAIL
47-model-load-first-use                     2      2 100.00%     18     18     18  FAIL
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
