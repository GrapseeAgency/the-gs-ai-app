# Random v2 — coding-sql-join-duplicates

**Question (user, flash mode):** Why does my SQL query return duplicate rows when I join orders with order_items, and what is the cleanest fix?


**Model answer:**

The duplicate rows happen because the join is many‑to‑many: one order can have multiple order items, and the join repeats the order data for each matching item. When you select columns from the **orders** table without aggregating them, each order item creates a separate row, so the same order appears multiple times.

**Cleanest fix**

Wrap the query in a **subquery** (or CTE) that groups the orders first, then join to the items. This collapses the order rows before the many‑to‑many relationship is introduced.

```sql
WITH order_summary AS (
  SELECT
    order_id,
    customer_id,
    order_date,
    total_amount,
    -- any other order columns you need
  FROM orders
  GROUP BY order_id, customer_id, order_date, total_amount
)
SELECT
  os.order_id,
  os.customer_id,
  os.order_date,
  os.total_amount,
  oi.item_id,
  oi.product_name,
  oi.quantity,
  oi.unit_price
FROM order_summary os
JOIN order_items oi
  ON os.order_id = oi.order_id
ORDER BY os.order_date, os.order_id;
```

**Why this works**

1. **`order_summary`** aggregates the orders table, producing one row per `order_id`.  
2. The **join** now pairs each distinct order with its items, so you get one order row per item but no duplicate order rows.  
3. If you need totals per order (e.g., sum of `quantity * unit_price`), add an aggregate in the CTE or a final `GROUP BY` after the join.

**Optional: keep the original order rows as a separate column**

If you still want the original order row repeated for each item (e.g., for reporting), use a **window function** instead of a subquery:

```sql
SELECT
  order_id,
  customer_id,
  order_date,
  total_amount,
  item_id,
  product_name,
  quantity,
  unit_price
FROM orders o
JOIN order_items oi
  ON o.order_id = oi.order_id
ORDER BY order_date, order_id;
```

Here the order columns are taken directly from `orders`, but because the join is many‑to‑many you still see duplicates. To avoid that while preserving the original order rows, you can **`DISTINCT`** on the order columns plus the item columns if you only need unique combinations:

```sql
SELECT DISTINCT
  o.order_id,
  o.customer_id,
  o.order_date,
  o.total_amount,
  oi.item_id,
  oi.product_name,
  oi.quantity,
  oi.unit_price
FROM orders o
JOIN order_items oi
  ON o.order_id = oi.order_id
ORDER BY o.order_date, o.order_id;
```

**Bottom line**

- Use a **CTE/grouped subquery** when you need one order row per item without repeating the order data.  
- Use **`DISTINCT`** only if you truly need unique combinations and don’t need aggregation.  
- Choose the approach that matches whether you need aggregated totals or just de‑duplicated rows.

---

- mode: flash (reasoning disabled at the model layer; planner/capability routing unaffected)
- planner decision: answer directly
- conversation: cmvlaokf3000gpohl189it2ah
