# Random v2 — coding-js-async-beginner

**Question (user, flash mode):** Explain async/await vs .then() promises in JavaScript to a complete beginner with one tiny example.


**Model answer:**

Both styles do the same thing: wait for a promise to finish, then run the next line.  
`async/await` lets you write it like normal top-to-bottom code; `.then()` chains callbacks.

```js
// tiny example: wait 1 second, then log
function wait(ms) {
  return new Promise(r => setTimeout(r, ms));
}

// async/await — reads like a recipe
async function demoAwait() {
  console.log('start');
  await wait(1000);
  console.log('after 1s (await)');
}

// .then() — chains a callback
function demoThen() {
  console.log('start');
  wait(1000).then(() => console.log('after 1s (then)'));
}

demoAwait();
demoThen();
```

Output (both print the same lines, ~1 s apart):
```
start
after 1s (await)
start
after 1s (then)
```

Key differences for a beginner
- **Reading order**: `await` pauses the function; `.then()` attaches a handler.
- **Errors**: `try/catch` works with `await`; `.then()` needs a second callback or `.catch()`.
- **Debugging**: stack traces stay cleaner with `await`.
- **Parallelism**: `Promise.all([a, b])` + `await` runs things together; with `.then()` you nest or chain.

Rule of thumb: default to `async/await`. Use `.then()` for one-liners or when you’re already inside a callback and can’t make the function `async`.

---

- mode: flash (reasoning disabled at the model layer; planner/capability routing unaffected)
- planner decision: answer directly
- conversation: cmvlaozk6000mpohl80do9pqg
