# Delivery Driver Cost and Payroll (Rippling SDE2)

> Rippling's most frequently reported LLD question. It shows up in phone screens (with or without AI), onsite coding, and Senior+ "two levels" rounds. Level 2 (pay-outs) is usually **not announced in advance**, so the Part 1 model must survive change.

---

## Problem framing

> "We run a food-delivery business and pay drivers by the hour for the time they spend on deliveries. Build an in-memory service that backs a live finance dashboard. We'll start simple, and I'll add requirements as we go. I care about how you represent **time** and **money**, how you keep the dashboard numbers **cheap and exact**, and how your model holds up when the rules change."

Cost of one delivery = duration in hours × the driver's hourly rate. For example, $10.00/hr for 1 h 30 m = $15.00.

**What makes or breaks this round**
- **Agreeing on how time and money are represented before writing code.** `double` for money fails the round on its own.
- **Getters that are O(1),** because totals are kept up to date on write.
- **Pay-outs that are idempotent and only move forward,** without rescanning work that's already settled.
- **Surviving the requirement changes** in Parts 2 and 5 without a rewrite.

### Clarifying questions to ask before you code

| Question | Rule for this run |
|---|---|
| Time format? | `long` Unix epoch **seconds**, UTC. Time zones only matter at display time. |
| Interval semantics? | Half-open `[start, end)`. Adjacent deliveries (`end == next start`) don't overlap. |
| Rate input? | `long` **cents per hour** (`1250` = $12.50/hr). |
| Money accumulation? | Exact. Accumulate **cent-seconds** (`rateCents × seconds`) as `long`, and convert **once** at output. |
| Output format? | `BigDecimal` with exactly 2 decimals (`21.25`), rounded **HALF_UP** at the cent. |
| Overlapping deliveries for one driver? | Allowed. Each delivery is paid independently. |
| Invalid input? | `IllegalArgumentException` covers an unknown driver, a duplicate `addDriver`, a rate ≤ 0, and `start >= end`. |
| Delivery length? | The problem says at most ~3 h. This run does **not** enforce it, but Part 4 relies on it as a bound. |
| Single-threaded? | Yes, until the discussion questions. |

### Overview of the parts

| Part | Adds | Core skill tested |
|---|---|---|
| 1 | Drivers, deliveries, O(1) total cost | Representing time and money, totals kept up to date on write |
| 2 | Effective-dated rates, delivery ids | Historical lookup, idempotency, reworking the model |
| 3 | Pay-outs and the unpaid total | Monotonic and idempotent settlement, no rescans |
| 4 | Most drivers on a delivery at once in the past 24 h | Sweep line, boundary tie-break, distinct counting |
| 5 | Piecewise billing; per-driver pay-outs in whole cents | Range queries on the rate history, no lost fractions of a cent |

---

## Part 1: Drivers, deliveries and an O(1) total (~15 min)

```java
void addDriver(String driverId, long rateCentsPerHour);
void recordDelivery(String driverId, long startTime, long endTime);
BigDecimal getTotalCost();              // across all drivers and deliveries, e.g. 21.25
```

**Rules**
- Cost of one delivery = (duration in seconds × hourly rate) / 3600.
- `getTotalCost()` backs a live dashboard and may be called inside loops, so it **must be O(1)**.
- The total includes every recorded delivery, whether paid or not.

**Test cases**
```
addDriver("d1", 1000); addDriver("d2", 1250)
recordDelivery("d1", 0, 5400)       -> 1.5 h × $10.00 = $15.00
recordDelivery("d2", 3600, 5400)    -> 0.5 h × $12.50 = $6.25
getTotalCost()                      -> 21.25

Precision trap (new tracker):
addDriver("d1", 1000)
3600 deliveries of 1 second each    -> each one is 0.277… cents
getTotalCost()                      -> 10.00   (rounding each delivery to cents gives 0.00)

addDriver("d1", 500)                -> IllegalArgumentException   duplicate driver
addDriver("d4", -100)               -> IllegalArgumentException   rate ≤ 0
recordDelivery("dX", 0, 60)         -> IllegalArgumentException   unknown driver
recordDelivery("d1", 100, 100)      -> IllegalArgumentException   start >= end
```

**What the interviewer checks**
- **The time and money decisions come before the code,** stated out loud with reasons (see "Design decisions" below).
- **The model:** `Driver` (an entity), `Delivery` (a `record`), and the system or tracker.
- **A running total updated on write.** No stream over the deliveries in the getter.
- **Rounding happens once,** when the total is formatted.

---

## Part 2: Effective-dated rates and delivery ids (~15 min)

The API changes. Adding a delivery id is the change that forces candidates to rework their class model:

```java
void updateDriverRate(String driverId, long effectiveTime, long rateCentsPerHour);
long getRateAt(long time);                                                       // on Driver
void recordDelivery(String deliveryId, String driverId, long startTime, long endTime);
```

**Rules**
- The rate from `addDriver` applies from the beginning of time.
- A rate takes effect **at** `effectiveTime`. A query exactly at that timestamp returns the **new** rate.
- `effectiveTime` must be **strictly later** than the driver's latest effective time. If it isn't, throw `IllegalArgumentException`.
- **Start-time pricing:** a delivery is billed **entirely at the rate in effect at its `start`**, even if it runs past a change. Part 5a replaces this rule.
- A rate is looked up when the delivery is **recorded**, and recorded deliveries are **never repriced**. A rate update that arrives while a delivery is in progress therefore applies to it if the effective time is at or before the delivery's start.
- `deliveryId` is **globally** unique. A duplicate submission is **ignored**: no exception, and nothing is added. A delivery that fails validation doesn't use up its id.

**Test cases** (the reported worked example)
```
addDriver("d1", 2000)
recordDelivery("a", "d1", 0, 5400)        -> 30.00
updateDriverRate("d1", 7200, 3000)
recordDelivery("b", "d1", 3600, 9000)     -> starts before 7200, old rate -> 30.00
recordDelivery("c", "d1", 7200, 8100)     -> 7.50
recordDelivery("a", "d1", 0, 5400)        -> duplicate, ignored
getTotalCost()                            -> 67.50   (72.50 under Part 5a piecewise pricing)

getRateAt(7199) -> 2000
getRateAt(7200) -> 3000            exactly at the effective time
getRateAt(-5)   -> 2000            before any update
updateDriverRate("d1", 7200, 2500)        -> IllegalArgumentException   not strictly later
updateDriverRate("d1", 100, 2500)         -> IllegalArgumentException   backdated
updateDriverRate("d1", 9000, 0)           -> IllegalArgumentException   rate ≤ 0
updateDriverRate("dX", 9000, 2500)        -> IllegalArgumentException   unknown driver
recordDelivery("dup", "dX", 0, 60)        -> IllegalArgumentException
recordDelivery("dup", "d1", 10000, 10060) -> accepted; "dup" was never used up
```

**What the interviewer checks**
- **The rate history structure** and its O(log k) "rate at time t" lookup, with the boundary at the effective timestamp exactly right.
- **The rate is resolved once, at record time,** so `getTotalCost()` stays O(1).
- **Idempotency by `deliveryId`:** the check comes first, and the id is added to the seen set only after validation succeeds.
- **Rework cost:** how much of Part 1 had to change?
- **Proactively listing differences from production:** persistence, transactions, idempotency keys with a TTL, locking, an audit trail and the rounding policy.

**The edge case to raise:** a rate update with an effective time inside a delivery that's **already recorded**. That delivery keeps the old rate, while a later-recorded delivery covering the same period gets the new rate. Accept and document it, reject such updates, or reprice using adjustment entries (see discussion question 1).

---

## Part 3: Pay-outs (~15 min)

```java
void payUpTo(long payTime);
BigDecimal getTotalCostUnpaid();
```

**Rules (whole-delivery semantics, as chosen in this run)**
- `payUpTo(t)` settles every unpaid delivery with **`end <= t`**. A delivery still in progress at `t` is paid in full on a later call.
- **Nothing is ever paid twice.** Calling with the same time or an earlier one pays nothing new.
- **Late deliveries:** a delivery recorded after a pay-out time it falls before stays unpaid until the **next** `payUpTo` call, which then pays it.
- `getTotalCost()` is unchanged: it counts paid and unpaid work. `getTotalCostUnpaid()` must also be **O(1)**.

> The other common semantics is **prorated**: pay `[start, min(end, payTime))` and leave the remainder unpaid. State which one you're implementing before you code. Prorated pay-outs need a different structure, ordered by start time, with the paid seconds tracked per delivery.

**Test cases**
```
addDriver("d1", 1000); addDriver("d2", 2000)
record("p1", "d1", 0, 3600)        -> 10.00
record("p2", "d2", 1800, 5400)     -> 20.00
getTotalCostUnpaid()  -> 30.00

payUpTo(3600)         -> p1 ends exactly at 3600, so it's paid
getTotalCostUnpaid()  -> 20.00
payUpTo(3600)         -> 20.00      idempotent
payUpTo(1000)         -> 20.00      an earlier time pays nothing

record("p3", "d1", 7200, 9000)     -> 5.00
payUpTo(8100)         -> p2 paid; p3 straddles 8100, so it isn't paid
getTotalCostUnpaid()  -> 5.00

record("p4", "d2", 2000, 2600)     -> late; 600 s × $20 = $3.333…
getTotalCostUnpaid()  -> 8.33
payUpTo(8100)         -> pays p4 (same time, but new work)
getTotalCostUnpaid()  -> 5.00
getTotalCost()        -> 38.33      unaffected by pay-outs
```

**What the interviewer checks**
- **The semantics, stated out loud,** including the late-delivery rule and the `end == payTime` boundary.
- **A running unpaid total in cent-seconds,** so both getters are O(1) and unpaid + paid always equals the total exactly.
- **No rescanning settled work:** which structure lets `payUpTo` visit only deliveries that are now payable? Give the per-call and amortized cost.
- **Why a plain "paid until" watermark isn't enough** (the late delivery `p4`).

---

## Part 4: Most drivers on a delivery at once (~10 min)

```java
int maxSimultaneousDriversInPast24Hours(long now);
```

**Rules**
- The window is `(now − 86400, now]`. A delivery overlaps it if `end > now − 86400` and `start <= now`. Clip its start to the window.
- Count **drivers**, not deliveries. A driver with two overlapping deliveries counts **once**.
- Intervals are half-open, so a delivery ending at `T` and another starting at `T` **don't** overlap.
- Deliveries are **not** guaranteed to arrive in start-time or end-time order.

**Test cases**
```
d1: 0→3600, d2: 3600→7200                        -> 1   adjacent, not overlapping
d1: 0→3600, d1: 1800→5400, d2: 1800→2000         -> 2   not 3; d1 counts once
d1: 0→100,  d2: 500→600       (now = 1000)       -> 1   both in the window, never at the same time
now = 100000 (window starts at 13600):
  d1: 0→3600 (outside), d2: 10000→20000, d3: 15000→16000   -> 2
  d1: 0→13700, d2: 0→13600     overlap only before the window -> 1
d2 starts at 1001 (now = 1000)                   -> not counted
no deliveries in the window                       -> 0
```

**What the interviewer checks**
- **A sweep line,** not "count the deliveries in the window", which answers a different question.
- **The tie-break:** at the same timestamp, end events are processed before start events, because of the half-open rule.
- **Distinct drivers:** active deliveries are counted per driver, and the driver count changes only on 0→1 and 1→0.
- **Clipping to the window,** so overlaps entirely before the window aren't counted.
- **Cost:** O(n) to filter plus O(m log m) to sort per call. Be ready to discuss how to reduce it (discussion question 4).

---

## Part 5 (the locked extension): Piecewise billing and per-driver pay-outs in whole cents

### 5a. Piecewise billing

Finance replaces start-time pricing with **splitting at rate-change boundaries**.
- A delivery's cost is the sum over each rate segment it covers.
- It's still priced once at record time and never repriced.

**Test cases** (rates: $20 from the start, $30 from 7200)
```
6300→8100   -> 900 s × $20 + 900 s × $30 = 12.50   straddles one change
0→3600      -> 20.00                               no change inside
7200→8100   -> 7.50                                change exactly at start
3600→7200   -> 20.00                               change exactly at end (not applied)
add $40 from 7800:
7000→8400   -> 200×20 + 600×30 + 600×40 = 46,000 dollar-seconds / 3600 = 12.78
Part 2 worked example total -> 72.50   (b is now split at 7200)
```

**What the interviewer checks**
- The rate history goes from a **point lookup** to a **range query** (every change strictly inside `(start, end)`). Does the structure support it directly?
- The cost is summed in cent-seconds with **no per-segment rounding**.
- The pricing logic lives with the rate data (`Driver` or `RateHistory`), not in the system class.
- **If both pricing modes must coexist,** that's the point where a pricing-policy interface is justified, and not before.

### 5b. Per-driver pay-outs in whole cents

`payUpTo` now returns `Map<String, Long>`: the **whole cents** paid to each driver in that pay-out.
- Fractions of a cent must never be lost.
- Over time, a driver's paid cents must equal the floor of their total settled cent-seconds / 3600.

```
addDriver("d1", 1001)              -> $10.01/hr
record("q1", "d1", 0, 1800)        -> 500.5 cents
payUpTo(1800)                      -> {d1=500}   0.5 cents carried over
record("q2", "d1", 1800, 3600)     -> 500.5 cents
payUpTo(3600)                      -> {d1=501}   the 0.5 carried + 500.5
                                      total paid 1001 cents = $10.01, exact
```

**What the interviewer checks**
- Each driver's paid cents are derived from **their cumulative settled cent-seconds**, so rounding never drifts from one pay-out to the next.
- `Delivery` (or the heap entry) now needs a `driverId`.

---

## Design decisions (and why)

### 1. Time: `long` epoch seconds

**What the number represents.** A Unix timestamp is the number of **seconds since 1970-01-01T00:00:00Z** (the epoch), measured in **UTC**. It identifies an **instant**: the same moment everywhere on Earth. It isn't a wall-clock time in any particular zone. POSIX time ignores leap seconds, so every day is exactly 86,400 seconds, which is what the 24-hour window relies on.

**Why an epoch number and not ISO-8601 strings or local times**
- **No time-zone or DST ambiguity.** "2026-03-08 02:30" in New York doesn't exist (clocks jump forward), and "2026-11-01 01:30" happens twice. Epoch seconds can't express either problem.
- **Arithmetic is trivial and exact:** `duration = end − start`, comparisons are `<` and `>`, and the window start is `now − 86400`.
- **Cheap to store and compare:** 8 bytes, with no parsing on the hot path. Time zones are applied only when **displaying** (`Instant.ofEpochSecond(t).atZone(zone)`).

**Why seconds, not milliseconds**
- The spec asks for **at least 1-second precision**, so seconds are enough.
- The unit multiplies directly into the money accumulator. Cent-*seconds* leave room for about **$256 billion** (see decision 3). Cent-*milliseconds* would cut that to about $256 million, which a large business could plausibly reach.

**Why `long`, not `int`**
- A signed 32-bit `int` overflows at **2038-01-19T03:14:07Z** (the "Y2038" problem). `long` seconds last for about 292 billion years.

### 2. Driver rate: `long` cents, not `double` or `float`

**The problem with `double` and `float`.** They're **binary** floating-point numbers. Most decimal fractions, such as 0.1 or 0.01, have no exact binary representation, so they're stored as the nearest representable value. In Java:

| Expression | Result |
|---|---|
| `0.1 + 0.2` | `0.30000000000000004` |
| Adding `0.1` ten times | `0.9999999999999999` |
| `1.005 * 100`, then `Math.round` | `100.49999999999999` → **100**, so $1.005 rounds to $1.00 instead of $1.01 |
| `double`: adding `0.01` a million times | `10000.000000171856` |
| `float`: adding `0.01f` a million times | **`9865.224`**, about $135 lost |
| `float` stores `167772.17f` as | `167772.171875` |
| `16777217f == 16777216f` | `true`: above 2²⁴, `float` can't even represent every integer |

**Specific risks in this system**
- **Accumulated drift:** `getTotalCost` adds thousands or millions of values, and every addition can add error. The float example above loses $135.
- **Rounding at a half cent goes the wrong way** (the `1.005` example). "Just round to 2 decimals at the end" isn't enough, because the value being rounded is already wrong.
- **Equality and comparison break:** `total == 0.3` is false, and tests become flaky or need tolerances.
- **The result depends on the order of operations:** float addition isn't associative, so adding the same deliveries in a different order can give a different total.

**How `long` cents protect you**
- **Integers are exact.** 1250 cents is exactly $12.50, and adding integers never introduces error.
- **Addition is associative,** so the order of deliveries doesn't matter.
- **Validation happens at the boundary.** If a caller sends a `double` or a string, convert it **once** on the way in (`new BigDecimal("12.50").movePointRight(2).longValueExact()`). Reject anything with more than 2 decimals, so imprecision never enters the system.

### 3. Total cost: `long` cent-seconds

**Why cent-seconds and not cents.** One delivery's cost in cents is `rateCents × seconds / 3600`, and that division usually leaves a fraction. A 1-second delivery at $10/hr is 0.277… cents. Rounding each delivery to cents loses that fraction every time: 3600 of those deliveries would add up to $0.00 instead of $10.00 (the precision trap in Part 1). Storing `rateCents × seconds` keeps every delivery's cost as an **exact integer**. The division by 3600 happens **once**, when the total is displayed.

**Will it overflow?**
- `Long.MAX_VALUE` is about 9.22 × 10¹⁸ cent-seconds. Divided by 360,000 (3600 seconds × 100 cents per dollar), that's about **$256 billion** of accumulated cost.
- One 3-hour delivery at $100/hr is 10,000 × 10,800 = 1.08 × 10⁸ cent-seconds. You'd need about **85 billion** of those deliveries to overflow.
- So it won't overflow for a delivery business's lifetime totals. It *could* overflow if the unit became milliseconds, or if the total were never reset across a very large organization. To **fail loudly instead of silently wrapping to a negative number**, use `Math.addExact` and `Math.multiplyExact`, which throw `ArithmeticException` on overflow.

**Why not keep the total as `BigDecimal`?**
- **Every operation allocates a new object.** `BigDecimal` is immutable, so `total = total.add(x)` creates a new instance on every delivery. `long` addition is a single CPU instruction with no garbage.
- **It's noticeably slower,** often 10–100× for simple addition, which matters for a hot write path.
- **There's no exactness to gain:** cent-seconds are integers, and `long` is already exact for integers.
- **It's easier to misuse** (see decision 5).
- Using `long` internally and `BigDecimal` only at the boundary gives exact values, fast arithmetic, and a typed money value for callers.

### 4. When to use `BigDecimal`

Use `BigDecimal` when the domain **can't** be expressed as an exact integer of a fixed smallest unit, or at the **edges** of the system:

| Scenario | Why `BigDecimal` |
|---|---|
| **Formatting output** (our `getTotalCost`) | Converting cent-seconds to dollars divides by 360,000, and the rounding rule must be explicit: `divide(…, 2, RoundingMode.HALF_UP)`. |
| **Parsing input** such as `"12.50"` | Read exactly, validate the scale (≤ 2 decimals), then convert to `long` cents. |
| **Percentages, tax, interest, FX conversion** | For example, 7.25% tax or a 1.0837 EUR/USD rate. The results aren't whole numbers of any fixed unit, and the rounding mode is a legal or business decision. |
| **Division with a variable denominator** | For example, splitting a bill among *n* people or prorating by days in a month. The denominator isn't known in advance, so no single integer unit works. |
| **Mixed or unusual precision** | Currencies with 3 decimals (KWD, BHD), crypto with 8–18 decimals, or rates such as $12.345/hr. A single fixed `long` unit doesn't fit them all. |
| **Values beyond `long` range** | `BigDecimal` and `BigInteger` have no fixed limit. |

**Rule of thumb:** if every amount is a whole number of some known smallest unit and only gets added or subtracted, use `long`. Once you divide or multiply by a non-integer, or need a specific rounding rule, use `BigDecimal` for that step.

### 5. Problems you will and won't face with `BigDecimal`

**Problems it removes**
- Binary representation error: 0.1 is exactly 0.1.
- Accumulated drift from repeated addition.
- Overflow: there's no fixed upper limit.

**Problems it brings**

| Pitfall | Example | Fix |
|---|---|---|
| **`equals` compares scale** | `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` is **false**, but `compareTo` returns 0. As `HashMap` or `HashSet` keys, they count as different. | Compare with `compareTo`. Normalize the scale (`setScale(2)`) or use `stripTrailingZeros()` before hashing. |
| **Constructing from a `double`** | `new BigDecimal(0.1)` gives `0.1000000000000000055511151231257827021181583404541015625`. | Use `BigDecimal.valueOf(0.1)` or `new BigDecimal("0.1")`. |
| **Non-terminating division throws** | `BigDecimal.ONE.divide(BigDecimal.valueOf(3))` throws `ArithmeticException: Non-terminating decimal expansion`. | Always give a scale and `RoundingMode`, or a `MathContext`. |
| **Forgetting it's immutable** | `total.add(x);` does nothing: the result is discarded. | `total = total.add(x);` |
| **The rounding policy is still your decision** | HALF_UP vs. HALF_EVEN (banker's rounding) gives different totals over millions of rows. | Agree on it with finance, and round once, at a defined point. |
| **Performance and allocation** | Every operation creates an object, which is slow in hot loops. | Use it at the boundaries, and `long` internally. |

`BigDecimal` makes the arithmetic exact. It doesn't make the *rounding decisions* for you: precision, *when* to round, and *which* rounding rule to use are three separate decisions.

### 6. Why `+ 1800` before dividing (when returning `long` cents)

The earlier version returned `(totalCost + 1800) / 3600`, where `totalCost` is in cent-seconds and the result is in cents.

**Java integer division truncates toward zero.** It simply drops the fraction:
```
one delivery at $10.01/hr for 1800 s = 1001 × 1800 = 1,801,800 cent-seconds = 500.5 cents
1,801,800 / 3600            = 500    (the .5 is dropped, so this rounds down)
(1,801,800 + 1800) / 3600   = 1,803,600 / 3600 = 501   (rounds half up)
```

**Why 1800:** it's **half the divisor** (3600 / 2). Adding half of the divisor before an integer division turns truncation into **round half up**: any fraction of 0.5 or more crosses the next whole number before it's cut off. In general, `(x + d/2) / d` rounds `x/d` to the nearest integer, with halves going up.

**Its limits**
- **It's only correct for non-negative values.** For −500.5, `(−1,801,800 + 1800) / 3600 = −500`, but HALF_UP gives **−501**. This would matter once refunds or negative adjustments exist.
- **The rounding rule is hidden in a magic number.** Changing to HALF_EVEN means rewriting the arithmetic.
- **It returns cents,** so the caller still has to format it as dollars.

**What the current code does instead**
```java
BigDecimal.valueOf(totalCost).divide(BigDecimal.valueOf(360_000), 2, RoundingMode.HALF_UP)
```
- **360,000** = 3600 (seconds → hours) × 100 (cents → dollars), so the result is **in dollars**.
- **Scale 2** gives exactly two decimal places.
- **`RoundingMode.HALF_UP`** names the rule explicitly, and it's correct for negative values too.

The arithmetic stays in `long`, and this one `BigDecimal` division at the edge is the only place rounding happens.

---

## Follow-up discussion questions (with answers)

> How to answer: state **the specific issue**, then **the fix**, then **the trade-off**.

### 1. Repricing recorded deliveries
**Q:** Finance wants a rate change to reprice already-recorded deliveries that started after its effective time. What changes, and what do the update and the getter cost now?

**A:**
- `updateDriverRate` must find the driver's recorded deliveries with `start >= effectiveTime`. Index them per driver by start time (`TreeMap<Long, List<Delivery>>`), then use `tailMap(effectiveTime)`. The cost is O(log n + r) for the r affected deliveries.
- **Don't change recorded deliveries in place.** Append **adjustment entries** (`deliveryId`, delta in cent-seconds, reason, timestamp). Add the delta to `totalCost`. If the delivery was already paid, the delta also goes to `totalUnpaid` as a correction to pay or claw back.
- `getTotalCost()` stays **O(1)**, because the cost moves to the write path.
- **Trade-off:** rate updates go from O(log k) to O(log n + r), and the history becomes append-only, which is what auditors want anyway.

### 2. Exactly-once pay-outs in a distributed system
**Q:** `payUpTo` is retried after a timeout, and two servers might record the same delivery at the same time. How do you keep it correct?

**A:**
- **Pay-outs:** give each pay-out an **idempotency key** (for example the pay-out batch id). Store the key and its result in the same transaction as the settlement. A retry finds the key and returns the stored result instead of paying again. Monotonic `payUpTo` (only pay `end <= t` for unpaid deliveries) already prevents double payment *within* one process.
- **Duplicate deliveries:** put a **unique constraint on `deliveryId`** in the database, and do the insert and the total update in one transaction (or `INSERT … ON CONFLICT DO NOTHING`, then update the total only if a row was inserted). The in-memory `Set` is the single-process version of the same idea.
- **Trade-off:** you pay for a transactional store and key retention (with a TTL), but money is only moved once.

### 3. Thread safety
**Q:** Make the dashboard safe for concurrent writers and readers. Which costs stay non-constant even with an O(1) `getTotalCost`?

**A:**
- **Simplest:** one lock (or `synchronized`) around every mutating method, and `volatile` or `AtomicLong` totals so readers see a consistent value without locking. Contention is low because writes are short.
- **Atomicity:** `recordDelivery` updates the id set, the heap, the driver's list and two totals, and all of that must happen together. A per-field `AtomicLong` alone isn't enough: a reader could see `totalCost` updated but not `totalUnpaid`. Use a single lock, or keep both totals in an immutable snapshot that's swapped atomically.
- **Still not constant:** `payUpTo` (O(p log n)), the Part 4 sweep (O(n + m log m)), and rate lookups (O(log k)). Under one global lock, a long sweep blocks writers. Copy the needed deliveries under the lock and sweep outside it, or keep a read-write lock.
- **Trade-off:** a single lock is easy to reason about. Finer-grained locking (per driver) scales better but makes multi-field invariants harder to keep.

### 4. Making the 24-hour concurrency query cheaper
**Q:** The dashboard calls `maxSimultaneousDriversInPast24Hours` constantly. Each call scans every delivery and sorts. How do you reduce that?

**A:**
- **Index by start time:** keep a `TreeMap<Long, List<Delivery>>`. Because deliveries last at most 3 h, only those starting in `(now − 86400 − 10800, now]` can overlap the window. A `subMap` returns them in O(log n + m), which removes the full scan.
- **Pre-sorted events:** merge each driver's overlapping deliveries into busy intervals on write (Merge Intervals), and keep their ±1 events in a global `TreeMap<Long, Integer>`. The query then walks the window with no sort.
- **Segment tree over time buckets:** each busy interval is a range add of +1, and the query is a range max. Both are O(log T).
- **Cache the last answer** if `now` is unchanged and nothing new was recorded.
- **Trade-off:** each step speeds up reads by adding work and complexity to writes. Pick based on the read/write ratio.

### 5. Scale: millions of deliveries between pay-outs
**Q:** What does each operation cost, and what breaks first?

**A:**
- `recordDelivery`: O(log n) for the heap insert plus O(log k) for the rate lookup (O(log k + s) under piecewise pricing).
- `getTotalCost` / `getTotalCostUnpaid`: O(1).
- `payUpTo`: O(p log n) for the p deliveries settled. A pay-out after millions of deliveries is one big O(n log n) burst. Spread it out with more frequent pay-outs, or process it in batches.
- **Memory breaks first.** Every `Delivery` stays in `Driver.deliveries` forever. Move settled deliveries to cold storage (they're only needed for audit). Bound the id set with a TTL, since deliveries are recorded within about 3 h of finishing, or move ids to a database with a unique index.
- **Overflow:** `long` cent-seconds allow about $256B. Use `Math.addExact` to fail loudly long before that.

---

## Rubric

| Signal | Hire | Strong hire |
|---|---|---|
| Time and money | Integer cents, epoch seconds | Cent-seconds, a single rounding step, decisions stated *before* coding with reasons (Y2038, float drift, overflow range) |
| Modeling | `Driver`, `Delivery`, a tracker | Rate history as a structure of its own, a `Delivery` record, validation in one place, Part 2 lands with little rework |
| Getters | O(1) total | O(1) total and unpaid, and pay-outs never rescan settled work |
| Correctness | Basic pay-outs work | Idempotent pay-outs, late deliveries, the `end == payTime` boundary, the effective-time boundary, duplicate ids, distinct drivers in the sweep |
| Communication | Answers what's asked | Proactively lists every difference from production, and gives complexity for each operation |
