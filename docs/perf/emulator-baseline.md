# Emulator baseline -- smooth-check 48 interactions

Run: https://github.com/GrapseeAgency/the-gs-ai-app/actions/runs/38777152919
Absolute numbers are emulator garbage; the ordering is the signal.

```
INTERACTION                            FRAMES  JANKY   JANK%    P50    P95    P99  VERDICT
01-cold-start-first-frame                   4      4 100.00%   3050   3150   3150  FAIL
02-warm-start-first-frame                   3      3 100.00%   1800   2100   2100  FAIL
03-resume-from-background                   1      1 100.00%    200    200    200  FAIL
04-empty-chat-render                        0      0   0.00%   4950   4950   4950  FAIL
05-first-message-send                       7      3  42.86%     36    150    150  FAIL
06-streaming-reply-5s                       6      3  50.00%     38     77     77  FAIL
07-short-chat-scroll-20-swipe               0      0   0.00%   4950   4950   4950  FAIL
08-long-chat-scroll-50-message              1      1 100.00%    150    150    150  FAIL
09-long-chat-fling-top-to-bottom            0      0   0.00%   4950   4950   4950  FAIL
10-rich-reply-render-table                  0      0   0.00%   4950   4950   4950  FAIL
11-rich-reply-render-code                   0      0   0.00%   4950   4950   4950  FAIL
12-rich-reply-render-image                  0      0   0.00%   4950   4950   4950  FAIL
13-citation-tap-response                    0      0   0.00%   4950   4950   4950  FAIL
14-composer-open                            0      0   0.00%   4950   4950   4950  FAIL
15-composer-type-50-chars                   0      0   0.00%   4950   4950   4950  FAIL
16-composer-expand-attach                   0      0   0.00%   4950   4950   4950  FAIL
17-composer-collapse                        0      0   0.00%   4950   4950   4950  FAIL
18-composer-send-button-press               0      0   0.00%   4950   4950   4950  FAIL
19-image-picker-open                        0      0   0.00%   4950   4950   4950  FAIL
20-image-preview-render                     0      0   0.00%   4950   4950   4950  FAIL
21-image-upload-progress                    0      0   0.00%   4950   4950   4950  FAIL
22-image-upload-complete                    0      0   0.00%   4950   4950   4950  FAIL
23-drawer-open                              2      2 100.00%   1800   1800   1800  FAIL
24-drawer-scroll                            0      0   0.00%   4950   4950   4950  FAIL
25-drawer-close                             0      0   0.00%   4950   4950   4950  FAIL
26-drawer-tap-conversation                  0      0   0.00%   4950   4950   4950  FAIL
27-conversation-switch                      0      0   0.00%   4950   4950   4950  FAIL
28-mode-picker-open                         3      2  66.67%    400   1300   1300  FAIL
29-mode-picker-scroll                       0      0   0.00%   4950   4950   4950  FAIL
30-mode-picker-select                       0      0   0.00%   4950   4950   4950  FAIL
31-mode-picker-close                        0      0   0.00%   4950   4950   4950  FAIL
32-search-open                              0      0   0.00%   4950   4950   4950  FAIL
33-search-type                              0      0   0.00%   4950   4950   4950  FAIL
34-search-results-render                    0      0   0.00%   4950   4950   4950  FAIL
35-settings-open                            0      0   0.00%   4950   4950   4950  FAIL
36-settings-scroll                          0      0   0.00%   4950   4950   4950  FAIL
37-settings-toggle-response                 0      0   0.00%   4950   4950   4950  FAIL
38-settings-submenu-open                    0      0   0.00%   4950   4950   4950  FAIL
39-settings-back                            0      0   0.00%   4950   4950   4950  FAIL
40-onboarding-first-slide                   0      0   0.00%   4950   4950   4950  FAIL
41-onboarding-advance                       0      0   0.00%   4950   4950   4950  FAIL
42-onboarding-complete                      0      0   0.00%   4950   4950   4950  FAIL
43-network-error-state                      7      2  28.57%     23    121    121  FAIL
44-retry-after-error                        0      0   0.00%   4950   4950   4950  FAIL
45-model-download-progress                  0      0   0.00%   4950   4950   4950  FAIL
46-model-download-complete                  0      0   0.00%   4950   4950   4950  FAIL
47-model-load-first-use                     9      4  44.44%     27   1050   1050  FAIL
48-splash-to-first-frame                    5      4  80.00%    150   1000   1000  FAIL
```

TOTAL: 0 of 48 PASS, 48 FAIL
