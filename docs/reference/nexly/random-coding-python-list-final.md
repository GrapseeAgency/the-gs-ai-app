`list.append()` returns `None` — it mutates the list in place and does not produce a value. A comprehension collects the *result* of its expression, so you end up with a list of `None`s.

**Fix:** build the list directly in the comprehension instead of appending.

```python
# ❌ returns [None, None, None]
result = [my_list.append(x) for x in source]

# ✅ returns the transformed values
result = [x * 2 for x in source]          # or any expression
```

If you need to accumulate into an existing list, use a plain loop or `extend`:

```python
my_list.extend(x * 2 for x in source)
```
