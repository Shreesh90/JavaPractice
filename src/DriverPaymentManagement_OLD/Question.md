# Driver Delivery Cost Tracker

## Problem

A food delivery company has tens of thousands of drivers. Each driver has an hourly pay rate in US dollars and completes hundreds of deliveries per week. You are building the backend for an accounting dashboard that tracks what the company owes its drivers.

A driver is paid for the time spent on each delivery. For example, a driver paid **$10.00/hour** who completes a **1 hour 30 minute** delivery earns **$15.00** for it. Drivers can be paid for overlapping deliveries, so each delivery is costed on its own and never merged with another.

Implement a class that supports the operations below.

---

## Assumptions

- A driver must be added with `addDriver` before any delivery is recorded for them. Drivers are never added twice, and every `driverId` passed to other methods exists.
- Time is given as **Unix epoch seconds** (UTC). Precision of one second is required.
- A delivery covers the half-open interval `[startTime, endTime)`. `startTime <= endTime`.
- No single delivery lasts longer than **3 hours**.
- Deliveries are recorded **immediately after they are completed**, so you can assume `recordDelivery` is called in order of increasing (non-decreasing) `endTime`.
- Hourly rates are in US dollars and may include cents, for example `100.25`.
- Money values returned by the methods must be correct to the **nearest cent**.
- You do **not** need to make the class thread-safe.
- Memory is large enough to hold every delivery ever recorded.

---

## Part 1: Cost Calculation

```
addDriver(driverId, usdHourlyRate)
recordDelivery(driverId, startTime, endTime)
getTotalCost()
```

| Method | Description |
|---|---|
| `addDriver` | Registers a driver with the given hourly rate (USD per hour). |
| `recordDelivery` | Records one completed delivery for the driver. |
| `getTotalCost` | Returns the total cost, in USD, of **all** deliveries recorded so far for **all** drivers. |

`getTotalCost` is called by a live dashboard, so it must be fast. It should not loop over every delivery.

Before you write any code, explain:

1. How you will represent **time**, and why.
2. How you will represent **money**, and why.
3. Where in your solution rounding happens, and which rounding rule you use.

---

## Part 2: Payments

```
payUpTo(payTime)
getTotalCostUnpaid()
```

| Method | Description |
|---|---|
| `payUpTo` | Pays every delivery, for every driver, that **ended at or before** `payTime`. A delivery is never paid twice. |
| `getTotalCostUnpaid` | Returns the total cost, in USD, of all deliveries that have **not** yet been paid. |

`getTotalCost` still returns the cost of **all** deliveries, paid or not.

---

## Part 3: Analytics

```
getActiveDriversInLast24Hours(currentTime)
```

Returns the number of **distinct drivers** who have at least one delivery that ended within the last 24 hours, i.e. in the window `(currentTime - 24 hours, currentTime]`.

A driver with several deliveries in the window counts once.

---

## Example

All examples use a rate of **$36.00/hour**, which is exactly **1 cent per second**.

### Costs and payments

| Call | Result |
|---|---|
| `addDriver(1, 36.00)` | |
| `recordDelivery(1, 0, 100)` | |
| `getTotalCost()` | `1.00` |
| `recordDelivery(1, 50, 150)` | overlaps the first delivery, both are paid in full |
| `getTotalCost()` | `2.00` |
| `getTotalCostUnpaid()` | `2.00` |
| `payUpTo(100)` | pays the first delivery (ends at 100) |
| `getTotalCostUnpaid()` | `1.00` |
| `getTotalCost()` | `2.00` |
| `payUpTo(100)` | nothing new to pay |
| `getTotalCostUnpaid()` | `1.00` |
| `payUpTo(150)` | pays the second delivery |
| `getTotalCostUnpaid()` | `0.00` |

### Rounding

| Call | Result |
|---|---|
| `addDriver(1, 100.25)` | |
| `recordDelivery(1, 0, 2833)` | 47 minutes 13 seconds |
| `getTotalCost()` | `78.89` |

The exact cost is `100.25 x 2833 / 3600 = 78.8911...` dollars, which rounds to `78.89`.

### Active drivers

| Call | Result |
|---|---|
| `addDriver(1, 36.00)`, `addDriver(2, 36.00)`, `addDriver(3, 36.00)` | |
| `recordDelivery(1, 50, 100)` | |
| `recordDelivery(2, 150, 200)` | |
| `recordDelivery(1, 89950, 90000)` | |
| `recordDelivery(3, 99950, 100000)` | |
| `getActiveDriversInLast24Hours(100000)` | `2` |

The window is `(13600, 100000]`. Only the deliveries ending at 90,000 (driver 1) and 100,000 (driver 3) fall inside it.

---

## Edge Cases to Consider

- No deliveries recorded: all totals are `0.00`.
- A delivery with `startTime == endTime` costs `0.00`.
- A delivery shorter than a cent, for example 1 second at $1.00/hour, rounds to `0.00`.
- Two deliveries ending at exactly the same time.
- `payUpTo` called with a time earlier than every unpaid delivery.
- `payUpTo` called repeatedly with the same or an earlier time.
- A delivery ending exactly at the start of the 24-hour window is **not** in the window.
- Very large totals: millions of deliveries must not lose or drift by a cent.

---

## Follow-Up Questions

### 1. Out-of-order recording

What if deliveries are **not** guaranteed to be recorded in order of `endTime`? Which parts of your solution break, and what would you change?

### 2. Peak concurrency

Change Part 3: return the **maximum number of drivers who were active at the same instant** during the last 24 hours. A driver is active at time `t` if some delivery of theirs satisfies `start <= t < end`. How does the approach and the data you store change?

### 3. Rate changes

Add:

```
updateRate(driverId, effectiveTime, newUsdHourlyRate)
```

From `effectiveTime` onward, the driver earns the new rate. A delivery is priced at the rate that was in effect when it **started**.

- How do you store a driver's rate history and look up the rate at a given time?
- What if a rate change is recorded **after** deliveries it affects were already costed?
- What if some of those deliveries have **already been paid**?

### 4. Bonuses and surge pricing

Product wants some drivers to receive a flat bonus per delivery, and others to receive a surge multiplier during peak hours. New pay rules will keep appearing.

- How would you design the code so that adding a new rule does not require editing existing logic?
- A surge multiplier like `1.5x` is a decimal. How do you apply it without introducing floating point error?

### 5. Scale

- What breaks first if the number of deliveries grows into the billions?
- How would you support queries over arbitrary time windows, not just 24 hours?

---

## What Interviewers Look For

- Clarifying questions about units, precision, and boundaries before coding.
- A deliberate choice for money (never `float` or `double`) and a clear rounding strategy.
- `getTotalCost` and `getTotalCostUnpaid` in constant time.
- A correct and efficient `payUpTo`, with no delivery paid twice.
- Clean class boundaries and extensibility (OOP design).
- Testing: concrete cases and edge cases, with expected values.