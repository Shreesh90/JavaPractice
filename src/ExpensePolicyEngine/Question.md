# Expense Policy Rules Engine

**Company:** Rippling | **Role:** SDE2 | **Round:** Onsite (design + coding) | **Difficulty:** Medium

---

## Problem Statement

Build an **expense policy rules engine**. Employees submit expenses, and the engine checks every expense against the company's expense policy and reports the violations.

The interview then probes how the engine **scales**, how its results are **stored and delivered**, and how **compound rules** are evaluated.

---

## Data Model (assumed, confirm with the interviewer)

### Expense

| Field | Type | Notes |
|---|---|---|
| `expenseId` | String | Unique |
| `employeeId` | String | Required |
| `tripId` | String | **Nullable**. Not every expense belongs to a trip |
| `category` | Enum | e.g. MEAL, TRAVEL, LODGING, SUBSCRIPTION |
| `vendor` | String | Free text, e.g. "Zomato", "Swiggy" |
| `amountCents` | long | Money stored in the smallest unit, never floats |
| `date` | Date | Needed for monthly rules |

### Rule types

**Basic rule:** a condition on **one expense**. It is stateless.

- A: the expense is a meal
- C: the amount is above a per-expense limit
- D: the vendor is on a disallowed list

**Group rule:** a condition on an **aggregate over a group of expenses**. It needs running totals.

- G1: total meal spend per trip is above a trip limit
- G2: total spend per employee per month is above a monthly limit

**Compound rule:** a combination of other rules.

- B = A AND C (a meal above the per-expense limit)
- X = A AND B

A rule describes a violation: an expense (or group) violates a rule when the rule's condition holds.

### A rule is made of

- a **filter** (which expenses it applies to, e.g. category = MEAL)
- a **group key** (group rules only, e.g. trip, or employee + month)
- an **aggregate** (group rules only, e.g. sum of amount)
- a **threshold** (the limit)

---

## Clarifying Questions to Ask First

- Which fields exist, and which do rules test?
- Is every amount in one currency?
- Is the limit exceeded when amount is **above** it, or **at or above** it?
- Does a violation **block** the expense, or only **flag** it for review?
- For a group rule, is the violation reported on the **group**, on **every expense** in it, or on the **expense that crossed** the limit?
- Can one expense match multiple rules? (Yes. One violation per rule.)

### Assumptions settled in the mock session

- A violation **only flags**. It never blocks. The money was spent, so it always counts toward group totals.
- `tripId` is nullable. Group rules keyed on trip skip expenses with no trip.
- A limit is violated only when the amount is **strictly above** it.
- A basic violation is identified by **rule + expense**. A group violation is identified by **rule + group key**.

---

## Part 1: Evaluate Basic and Group Rules

Implement the engine. Given a list of expenses and a set of basic and group rules, return **every violation**, identified by the rule and by the expense (basic) or the group key (group).

**What this part should cover**

- A rule representation that separates conditions, grouping keys, and thresholds
- Correct results for both rule kinds, with exact money arithmetic and boundary amounts
- The cost of the straightforward evaluation, as the baseline for Part 2

**Hint:** a basic rule looks at one expense at a time. A group rule needs a total first. Evaluate each kind in the way that suits it.

**Edge cases to test**

- Amount exactly equal to the limit
- A flight evaluated against a meal rule (rule must not apply)
- An expense with no trip evaluated against a trip rule
- Two meals in one trip that together cross the limit
- A group with no expenses

---

## Part 2: A Million Expenses and Ten Thousand Rules

There are now about **1,000,000 expenses** and **10,000 rules**. How do you optimize the evaluation? Treat basic rules and group rules separately.

**Hint:** count the pairs. 10^6 x 10^4 = 10^10 checks. Look for structure in the rules, such as the fields they test or the totals they share, that lets most pairs be skipped or shared.

**Clarifying questions**

- Periodic batch over all expenses, or check each expense as it is submitted?
- Does each rule belong to one company? How many rules apply to a typical company?

**What this part should cover**

- Why checking every expense against every rule does not scale
- Narrowing the basic rules an expense must be checked against, and batch or set-based evaluation
- Computing each distinct group aggregate **once** and keeping it up to date as expenses change

---

## Part 3: Storing Results and Notifying the Customer

How do you store the evaluation results, and how do you notify the customer?

**Hint:** results change over time. The same expense is evaluated again when it is edited or when a rule changes. Decide what the stored result looks like so re-running is safe, and the customer hears about **changes**, not about every run.

**Clarifying questions**

- Who is the customer to notify: finance admins, the employee, or both?
- Immediate notifications, or is a periodic digest acceptable?

**What this part should cover**

- A violations store keyed so that repeated evaluation is **idempotent**, recording the rule version behind each result
- Notifications driven by **state changes** (new and resolved violations), delivered reliably and without duplicates
- Querying violations for review by company, rule, employee, and status

---

## Part 4: Compound Rules That Reference Each Other

Optimize the evaluation when compound rules reference other compound rules. For example, compound rule X is `A AND B`, while B is itself `A AND C`.

**Hint:** expand before evaluating. Write out what X requires in terms of basic conditions only, and notice what a straightforward recursive evaluation computes more than once.

**Clarifying questions**

- Can compound rules use OR and NOT as well as AND?
- Can they include group rules?
- What should happen if a rule refers to itself through other rules?

**What this part should cover**

- Compound rules as a **dependency graph**, with cycle detection when a rule is saved
- Evaluating each shared sub-rule **once per expense** (or once per batch)
- Simplifying or merging equivalent compound rules, and ordering conditions so evaluation can stop early

---

## Follow-up Questions

1. A rule's limit is lowered after a month of evaluations. Which stored violations change, and what does the customer hear?
2. An expense is moved from one trip to another. Which group totals and violations must be updated?
3. Rules now support OR and NOT. What changes in sharing sub-results and in stopping early?
4. How would you let finance admins write their own rules without letting them create rules that are expensive to evaluate?

---

## What a Strong Answer Covers

- Settles the fields, rule semantics, and money representation **before coding**
- A clean separation of per-expense conditions, group aggregates, and composition, so each optimization stays local
- **Quantified** reasoning about evaluation cost at a million expenses and ten thousand rules
- Durable, idempotent results and change-driven notifications
- Tests covering boundary amounts, empty groups, edits that resolve a violation, and cyclic definitions

---

## Source

Question page: https://prachub.com/interview-questions/expense-policy-rules-engine-scale-violation-storage-and-shared-compound-rules