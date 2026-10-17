# smooth-check run 20261017-222809

- date: 2026-10-17T22:38:40Z
- package: com.grapsee.gsai  activity: com.grapsee.gsai/.MainActivity
- device: sdk_gphone64_x86_64 (android 14, sdk 34), screen 1080x2400, refresh 60Hz, budget 16.67ms
- perfetto captured: 1 (1 = traces sm-<id>.pftrace in this folder, open at ui.perfetto.dev)

```
INTERACTION                            FRAMES  JANKY   JANK%    P50    P95    P99  VERDICT
01-cold-start-first-frame                   3      3 100.00%    300    350    350  FAIL
02-warm-start-first-frame                   0      0   0.00%      -      -      -  FAIL
03-resume-from-background                   0      0   0.00%      -      -      -  FAIL
04-empty-chat-render                       16     14  87.50%    150    300    300  FAIL
05-first-message-send                      71     66  92.96%    150    300    500  FAIL
06-streaming-reply-5s                     102     97  95.10%    150    250    350  FAIL
07-short-chat-scroll-20-swipe               0      0   0.00%      -      -      -  FAIL
08-long-chat-scroll-50-message             79     75  94.94%    117    300    450  FAIL
09-long-chat-fling-top-to-bottom           41     38  92.68%     89    200    300  FAIL
10-rich-reply-render-table                107    101  94.39%    109    200    350  FAIL
11-rich-reply-render-code                  94     84  89.36%    105    200    600  FAIL
12-rich-reply-render-image                110    103  93.64%    109    150    300  FAIL
13-citation-tap-response                    0      0   0.00%      -      -      -  FAIL
14-composer-open                           17     13  76.47%     81    133    133  FAIL
15-composer-type-50-chars                  77     76  98.70%    150    200    200  FAIL
16-composer-expand-attach                  21     20  95.24%    250    800    950  FAIL
17-composer-collapse                       26     18  69.23%     85    150    150  FAIL
18-composer-send-button-press              49     43  87.76%     85    200    350  FAIL
19-image-picker-open                       25     24  96.00%     73    950   1000  FAIL
20-image-preview-render                     9      8  88.89%     73    133    133  FAIL
21-image-upload-progress                    0      0   0.00%      -      -      -  FAIL
22-image-upload-complete                    0      0   0.00%      -      -      -  FAIL
23-drawer-open                              1      1 100.00%     85     85     85  FAIL
24-drawer-scroll                            0      0   0.00%      -      -      -  FAIL
25-drawer-close                             1      1 100.00%    133    133    133  FAIL
26-drawer-tap-conversation                  0      0   0.00%      -      -      -  FAIL
27-conversation-switch                      1      1 100.00%    200    200    200  FAIL
28-mode-picker-open                         3      2  66.67%    200   1550   1550  FAIL
29-mode-picker-scroll                       0      0   0.00%      -      -      -  FAIL
30-mode-picker-select                       8      8 100.00%    113    200    200  FAIL
31-mode-picker-close                        1      1 100.00%     65     65     65  FAIL
32-search-open                              0      0   0.00%      -      -      -  FAIL
33-search-type                              2      1  50.00%     38     38     38  FAIL
34-search-results-render                    0      0   0.00%      -      -      -  FAIL
35-settings-open                            1      0   0.00%      9      9      9  FAIL
36-settings-scroll                          0      0   0.00%      -      -      -  FAIL
37-settings-toggle-response                 0      0   0.00%      -      -      -  FAIL
38-settings-submenu-open                    0      0   0.00%      -      -      -  FAIL
39-settings-back                            1      1 100.00%     65     65     65  FAIL
40-onboarding-first-slide                   0      0   0.00%      -      -      -  FAIL
41-onboarding-advance                       1      1 100.00%     69     69     69  FAIL
42-onboarding-complete                      0      0   0.00%      -      -      -  FAIL
43-network-error-state                     60     53  88.33%    105    250    350  FAIL
44-retry-after-error                        0      0   0.00%      -      -      -  FAIL
45-model-download-progress                  9      9 100.00%    105    200    200  FAIL
46-model-download-complete                  0      0   0.00%      -      -      -  FAIL
47-model-load-first-use                    55     50  90.91%    121    350   1600  FAIL
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
