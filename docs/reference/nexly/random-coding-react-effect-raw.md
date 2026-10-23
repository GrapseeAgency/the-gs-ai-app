React 18 enables **Strict Mode** in development, which intentionally mounts → unmounts → remounts every component to help you find side-effect bugs. The double fetch you see is that remount running your effect a second time.

The fix is not to disable Strict Mode. The fix is to make the effect **idempotent** so a second run is harmless:

1. **Abort the in-flight request on cleanup**  
   Return a cleanup function that calls `abort()` on an `AbortController`. When React remounts, the first request is cancelled before the second one starts.

2. **Guard against stale responses**  
   Keep a local `let ignore = false` flag; set it `true` in cleanup and check it before `setState`.

```jsx
useEffect(() => {
  const controller = new AbortController();
  let ignore = false;

  fetch('/api/data', { signal: controller.signal })
    .then(r => r.json())
    .then(data => { if (!ignore) setData(data); })
    .catch(e => { if (e.name !== 'AbortError') console.error(e); });

  return () => { ignore = true; controller.abort(); };
}, []);
```

If you use a data-fetching library (TanStack Query, SWR, RTK Query) this is handled for you automatically — prefer those for anything beyond a trivial fetch.
