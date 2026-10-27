# Diagnostic — AIME baseline 3.33% (1/30): scaffold bug, NOT capability

**Verdict: the 3.33% is an artifact of the router's `max_tokens` clamp. The true
baseline is unknown and almost certainly higher. The 5-arm delta table is invalid**
**until this is fixed, because all five arms inherit the same truncation.**

## Root cause

`src/lib/gs-provider-router.ts` sets `DEFAULT_MAX_TOKENS = 512` and always sends an
explicit `max_tokens`. I introduced that to dodge Groq's output-tokens-per-minute wall.
AIME 2024 requires long step-by-step reasoning, so completions are cut off mid-argument
before the model can emit the required `ANSWER: N` final line.

The scorer (`inspect_evals/utils/aime_common.py`) reads **only the last substantive
line**. A truncated response therefore has no answer to read, and the grader scrapes an
arbitrary number out of mid-sentence prose.

Across all 30 samples: **1/30 completions ended with a line containing `ANSWER`** — and
that single one is the single correct answer. A model that is merely bad at maths still
*tries* to answer; here 29/30 never got the chance to finish.

## The five samples, raw

### Sample 0

**1. Question (verbatim)**

```
Every morning Aya goes for a $9$-kilometer-long walk and stops at a coffee shop afterwards. When she walks at a constant speed of $s$ kilometers per hour, the walk takes her 4 hours, including $t$ minutes spent in the coffee shop. When she walks $s+2$ kilometers per hour, the walk takes her 2 hours and 24 minutes, including $t$ minutes spent in the coffee shop. Suppose Aya walks at $s+rac{1}{2}$ kilometers per hour. Find the number of minutes the walk takes her, including the $t$ minutes spent in the coffee shop.
```

**2. Raw gs-ai response (verbatim, complete)**

```
## Step 1: Understand the problem and define variables
We are given that Aya goes for a 9-kilometer-long walk and stops at a coffee shop afterwards. The time taken for the walk and the coffee shop visit varies based on her speed. Let's denote her speed as $s$ kilometers per hour and the time spent in the coffee shop as $t$ minutes.

## Step 2: Formulate equations based on given conditions
When Aya walks at a constant speed of $s$ kilometers per hour, the walk takes her 4 hours, including $t$ minutes spent in the coffee shop. This can be expressed as $\frac{9}{s} + \frac{t}{60} = 4$ because $\frac{9}{s}$ represents the time taken for the walk in hours and $\frac{t}{60}$ represents the time spent in the coffee shop in hours.

## Step 3: Formulate the equation for the second condition
When she walks $s+2$ kilometers per hour, the walk takes her 2 hours and 24 minutes, including $t$ minutes spent in the coffee shop. This can be expressed as $\frac{9}{s+2} + \frac{t}{60} = 2.4$ because $\frac{9}{s+2}$ represents the time taken for the walk at the increased speed in hours and $2.4$ hours is equivalent to 2 hours and 24 minutes.

## Step 4: Solve the system of equations
We have two equations:
1. $\frac{9}{s} + \frac{t}{60} = 4$
2. $\frac{9}{s+2} + \frac{t}{60} = 2.4$

We can solve these equations to find the values of $s$ and $t$.

## Step 5: Subtract the second equation from the first to eliminate $t$
$\frac{9}{s} - \frac{9}{s+2} = 4 - 2.4$

## Step 6: Simplify the equation
$\frac{9(s+2) - 9s}{s(s+2)} = 1.6$

## Step 7: Further simplify the equation
$\frac{9s + 18 - 9s}{s^2 + 2s} = 1.6$

## Step 8: Simplify the numerator
$\frac{
```

**3. Reference answer**

```
204
```

**4. What the grader extracted**

- value: `I`
- extracted answer: `''`
- cleaned last line: `'$\\frac{'`
- output tokens: 413  |  chars: 1650

**5. Comparison**

| field | finding |
| --- | --- |
| Reasoning correct? | **Undecidable** — the response is cut off mid-derivation. |
| Final answer correct? | **No** — no final answer was ever produced. |
| Grader extraction right? | **No** — it scraped a number from mid-sentence prose. |
| Failure mode | **Truncation**, not wrong maths and not a bad final answer. |

### Sample 1

**1. Question (verbatim)**

```
Let $ABC$ be a triangle inscribed in circle $\omega$. Let the tangents to $\omega$ at $B$ and $C$ intersect at point $D$, and let $\overline{AD}$ intersect $\omega$ at $P$. If $AB=5$, $BC=9$, and $AC=10$, $AP$ can be written as the form $\frac{m}{n}$, where $m$ and $n$ are relatively prime integers. Find $m + n$.

[asy]...diagram omitted...[/asy]
```

**2. Raw gs-ai response (verbatim, complete)**

```
To solve this problem, we will use the properties of inscribed angles and tangents to circles.

Step 1:  First, let's analyze the given triangle $ABC$ inscribed in circle $\omega$. We're also given the tangents to $\omega$ at points $B$ and $C$, intersecting at point $D$. Additionally, $\overline{AD}$ intersects $\omega$ at point $P$.

Step 2:  We need to find the length of $AP$. To do this, we'll utilize the properties of tangents and inscribed angles in a circle.

Step 3:  Let's consider the tangent lines $BD$ and $CD$ and their relationship with the circle. Since $BD$ and $CD$ are tangents, $BD = BD$ and $CD = CD$.

Step 4:  By applying the Power of a Point theorem at point $D$, we can relate the lengths of line segments intersecting the circle. This gives us the equation $BP \cdot PD = BD^2$.

Step 5:  Similarly, using the Power of a Point theorem at point $D$ again, we get $CP \cdot PD = CD^2$.

Step 6:  We can then use these equations to find a relationship between $BP$, $CP$, $BD$, and $CD$.

Step 7:  Next, we'll utilize the fact that $\angle BDC = 180^{\circ} - \angle BAC$. This allows us to apply the Law of Cosines to triangle $ABC$ and find the value of $\cos \angle BAC$.

Step 8:  With $\cos \angle BAC$ known, we can then use the Law of Cosines on triangle $ABC$ to find the length of $AP$.

Step 9:  After obtaining the value of $AP$, we'll express it in the form $\frac{m}{n}$, where $m$ and $n$ are relatively prime integers.

Step 10:  Finally, we'll calculate $m + n$ to get the final answer.

Now, let's proceed with the calculations.

Using the Power of a Point theorem, we have:
$BP \cdot PD = BD^2$ and $CP \cdot PD = CD^2$.

From the given information, $AB = 5$, $BC = 9$, and $AC = 10$. Applying the Law of Cos
```

**3. Reference answer**

```
113
```

**4. What the grader extracted**

- value: `I`
- extracted answer: `'10'`
- cleaned last line: `'From the given information, $AB = 5$, $BC = 9$, and $AC = 10$. Applying the Law of Cos'`
- output tokens: 438  |  chars: 1752

**5. Comparison**

| field | finding |
| --- | --- |
| Reasoning correct? | **Undecidable** — the response is cut off mid-derivation. |
| Final answer correct? | **No** — no final answer was ever produced. |
| Grader extraction right? | **No** — it scraped a number from mid-sentence prose. |
| Failure mode | **Truncation**, not wrong maths and not a bad final answer. |

### Sample 2

**1. Question (verbatim)**

```
Each vertex of a regular octagon is independently colored either red or blue with equal probability. The probability that the octagon can then be rotated so that all of the blue vertices end up at positions where there were originally red vertices is $\tfrac{m}{n}$, where $m$ and $n$ are relatively prime positive integers. What is $m+n$?
```

**2. Raw gs-ai response (verbatim, complete)**

```
## Step 1: Understanding the Problem
The problem involves a regular octagon with each vertex colored either red or blue with equal probability. We need to find the probability that the octagon can be rotated so that all the blue vertices end up at positions where there were originally red vertices.

## Step 2: Breaking Down the Problem
To tackle this problem, we can consider the possible arrangements of red and blue vertices and determine the probability for each arrangement that satisfies the given condition.

## Step 3: Considering the Number of Blue Vertices
We can start by considering the number of blue vertices. Since there are 8 vertices in total, the number of blue vertices can range from 0 to 8.

## Step 4: Calculating the Probability for Each Number of Blue Vertices
For each possible number of blue vertices, we need to calculate the probability that the octagon can be rotated so that all the blue vertices end up at positions where there were originally red vertices.

## Step 5: Counting the Favorable Outcomes
If there are $n$ blue vertices, then there are $8-n$ red vertices. The octagon can be rotated to satisfy the condition if and only if the $n$ blue vertices are adjacent to $n$ red vertices.

## Step 6: Analyzing the Cases
We can analyze the cases based on the number of blue vertices. For $n = 0$ or $n = 8$, the probability is 1, as there are no blue vertices to rotate or all vertices are blue. For $n = 1$ or $n = 7$, the probability is 0, as a single blue vertex cannot be adjacent to a red vertex after rotation, and similarly for 7 blue vertices. For $n = 2$ or $n = 6$, the probability is $\frac{1}{4}$, as there is one favorable arrangement out of four possible arrangements. For $n = 3$ or $n = 5$, the probability is $\frac{1}{4}$, as there are two favorable arrangements out of eight possible arrangements. For $n = 4$, the probability is $\frac{1}{2}$, as there are two favorable arrangements out of four possible arrangements.

## Step 7: Calculating the Overall Probability
We can calculate the overall probability by summing the probabilities for each number of blue vertices, weighted by the probability of having that many blue vertices.

## Step 8: Applying the Bin
```

**3. Reference answer**

```
371
```

**4. What the grader extracted**

- value: `I`
- extracted answer: `'8'`
- cleaned last line: `'## Step 8: Applying the Bin'`
- output tokens: 555  |  chars: 2217

**5. Comparison**

| field | finding |
| --- | --- |
| Reasoning correct? | **Undecidable** — the response is cut off mid-derivation. |
| Final answer correct? | **No** — no final answer was ever produced. |
| Grader extraction right? | **No** — it scraped a number from mid-sentence prose. |
| Failure mode | **Truncation**, not wrong maths and not a bad final answer. |

### Sample 3

**1. Question (verbatim)**

```
Define $f(x)=|| x|-\tfrac{1}{2}|$ and $g(x)=|| x|-\tfrac{1}{4}|$. Find the number of intersections of the graphs of \[y=4 g(f(\sin (2 \pi x))) \quad\text{ and }\quad x=4 g(f(\cos (3 \pi y))).\]
```

**2. Raw gs-ai response (verbatim, complete)**

```
## Step 1: Define the function compositions
First, we need to understand and possibly simplify the given functions $f(x)$ and $g(x)$ and their compositions. The function $f(x) = ||x| - \tfrac{1}{2}|$ and $g(x) = ||x| - \tfrac{1}{4}|$. We are looking to find the intersections of the graphs of $y = 4g(f(\sin(2\pi x)))$ and $x = 4g(f(\cos(3\pi y)))$.

## Step 2: Analyze f(x) and g(x)
For $f(x)$, when $|x| < \tfrac{1}{2}$, $f(x) = \tfrac{1}{2} - |x|$. When $|x| \geq \tfrac{1}{2}$, $f(x) = |x| - \tfrac{1}{2}$. For $g(x)$, when $|x| < \tfrac{1}{4}$, $g(x) = \tfrac{1}{4} - |x|$. When $|x| \geq \tfrac{1}{4}$, $g(x) = |x| - \tfrac{1}{4}$.

## Step 3: Consider the range of sin(2πx) and cos(3πy)
The range of $\sin(2\pi x)$ and $\cos(3\pi y)$ is $[-1, 1]$. Thus, $f(\sin(2\pi x))$ and $f(\cos(3\pi y))$ will have their ranges determined by the behavior of $f(x)$ over $[-1, 1]$.

## Step 4: Determine the behavior of f(x) over [-1, 1]
For $x \in [-1, 1]$, $f(x)$ will be $|x| - \tfrac{1}{2}$ when $|x| \geq \tfrac{1}{2}$ and $\tfrac{1}{2} - |x|$ when $|x| < \tfrac{1}{2}$. This means $f(x)$ will range from 0 to $\tfrac{1}{2}$ because when $|x| \geq \tfrac{1}{2}$, the
```

**3. Reference answer**

```
385
```

**4. What the grader extracted**

- value: `I`
- extracted answer: `'0'`
- cleaned last line: `'For $x \\in [-1, 1]$, $f(x)$ will be $|x| - \\tfrac{1}{2}$ when $|x| \\geq \\tfrac{1}{2}$ and $\\tfrac{1}{2} - |x|$ when $|x| < \\tfrac{1}{2}$. This means $f(x)$ will range from 0 to $\\tfrac{1}{2}$ because when $|x| \\geq \\tfrac{1}{2}$, the'`
- output tokens: 292  |  chars: 1166

**5. Comparison**

| field | finding |
| --- | --- |
| Reasoning correct? | **Undecidable** — the response is cut off mid-derivation. |
| Final answer correct? | **No** — no final answer was ever produced. |
| Grader extraction right? | **No** — it scraped a number from mid-sentence prose. |
| Failure mode | **Truncation**, not wrong maths and not a bad final answer. |

### Sample 4

**1. Question (verbatim)**

```
Let $p$ be the least prime number for which there exists a positive integer $n$ such that $n^{4}+1$ is divisible by $p^{2}$. Find the least positive integer $m$ such that $m^{4}+1$ is divisible by $p^{2}$.
```

**2. Raw gs-ai response (verbatim, complete)**

```
## Step 1: Understand the problem
We need to find the least prime number $p$ for which there exists a positive integer $n$ such that $n^{4}+1$ is divisible by $p^{2}$, and then find the least positive integer $m$ such that $m^{4}+1$ is divisible by $p^{2}$.

## Step 2: Analyze the divisibility condition
For $n^{4}+1$ to be divisible by $p^{2}$, $p$ must be a factor of $n^{4}+1$. This suggests we should look for prime numbers $p$ that can divide numbers of the form $n^{4}+1$.

## Step 3: Consider properties of $n^{4}+1$
Notice that $n^{4}+1$ can be factored using the difference of squares identity if we manipulate it: $n^{4}+1 = (n^{2})^{2} + 1^{2} = (n^{2} + 1)^{2} - (n \sqrt{2})^{2} = (n^{2} + n \sqrt{2} + 1)(n^{2} - n \sqrt{2} + 1)$. However, this factorization doesn't directly help with finding $p$. Instead, we consider the properties of primes and fourth powers.

## Step 4: Determine potential primes $p$
A prime $p$ that divides $n^{4}+1$ must have certain properties. Notably, if $p$ divides $n^{4}+1$, then $n^{4} \equiv -1 \mod p$. This suggests that the order of $n$ modulo $p$ is $8$, since $n^{8} \equiv 1 \mod p$ but $n^{4} \not\equiv 1 \mod p$.

## Step 5: Apply Euler's theorem
By Euler's theorem, for any $a$ coprime to $n$, it holds that $a^{\phi(n)} \equiv 1 \mod n$, where $\phi(n)$ is Euler's totient function. For a prime $p$, $\phi(p) = p-1$. Thus, the order of $n$ modulo $p$ must divide $p-1$. Since we're looking for the smallest such prime and the order is $
```

**3. Reference answer**

```
110
```

**4. What the grader extracted**

- value: `I`
- extracted answer: `'1'`
- cleaned last line: `"By Euler's theorem, for any $a$ coprime to $n$, it holds that $a^{\\phi(n)} \\equiv 1 \\mod n$, where $\\phi(n)$ is Euler's totient function. For a prime $p$, $\\phi(p) = p-1$. Thus, the order of $n$ modulo $p$ must divide $p-1$. Since we're looking for the smallest such prime and the order is $"`
- output tokens: 374  |  chars: 1496

**5. Comparison**

| field | finding |
| --- | --- |
| Reasoning correct? | **Undecidable** — the response is cut off mid-derivation. |
| Final answer correct? | **No** — no final answer was ever produced. |
| Grader extraction right? | **No** — it scraped a number from mid-sentence prose. |
| Failure mode | **Truncation**, not wrong maths and not a bad final answer. |

## Why each sample scored INCORRECT

| # | out tokens | chars | extracted | reference | how the response ended |
| --- | --- | --- | --- | --- | --- |
| 0 | 413 | 1650 | `''` | `204` | …$\frac{ |
| 1 | 438 | 1752 | `'10'` | `113` | …$AB = 5$, $BC = 9$, and $AC = 10$. Applying the Law of Cos |
| 2 | 555 | 2217 | `'8'` | `371` | …## Step 8: Applying the Bin |
| 3 | 292 | 1166 | `'0'` | `385` | …o $\tfrac{1}{2}$ because when $|x| \geq \tfrac{1}{2}$, the |
| 4 | 374 | 1496 | `'1'` | `110` | …'re looking for the smallest such prime and the order is $ |

Every one of the five ends mid-word or mid-heading. Sample 0 ends on `$\frac{`;
sample 1 on `Applying the Law of Cos`; sample 2 on `## Step 8: Applying the Bin`.

## The general failure across all 30

```
completions whose LAST line contains 'ANSWER': 1 / 30
completion length: min=1126  median=1588  max=2486 chars
```

The one completion that did finish (sample 7) answered `25`, matched reference `25`,
and is the one `CORRECT` — i.e. **1/30 = 3.33% is precisely the rate at which the model
managed to finish before being cut off**, not a measure of AIME ability.

## The deeper defect (not just a number to tune)

The router **silently clamps** `max_tokens` to a per-provider ceiling. That converts
"this provider cannot afford this request" into "the model produced a wrong answer" —
which the scorer records identically to a genuine reasoning error. Silent truncation is
worse than an error here, because it is invisible in the score.

Correct behaviour is one of:

1. skip providers whose ceiling is below the caller's requested budget and fail over, or
2. surface an explicit truncation error instead of returning a short completion, or
3. raise the default budget and make the *provider choice* respect it (Groq's 1000-token
   OTPM ceiling means Groq cannot serve AIME at all and should be passed over for it).

Option 1 is the cleanest: a provider that cannot honour the budget is not eligible.

## Not fixed here

Per instruction, no code was changed for this diagnostic. The 5-arm run is still
executing and is affected by this same clamp.
