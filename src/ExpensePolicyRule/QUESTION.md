# Expense Policy Rules Engine (Rippling SDE2, AI-assisted phone screen / LLD)

## Interviewer framing

> "Employees submit expenses, and finance defines policy rules. Implement `evaluateRules(rules, expenses)`, which runs every rule against every expense and returns **every** violation; one expense can break several rules. New rule types must be addable **without touching the evaluation loop**. Later, rules will come in through an API."

**Where it shows up:** the AI-assisted phone screen (most common), non-AI onsite coding, and a system-design version.

**What makes or breaks this round**
- **Hardcoding is reported as an instant fail.** An `if (type == ENTERTAINMENT)` inside the loop ends the round. The loop only knows a `Rule` abstraction.
- **Be ready to switch.** One interviewer approved a data-driven design (field + operator + value), then asked for hardcoded rule classes mid-round. Design so both fit behind the same interface.
- **Money is integer cents.** Never `double`.
- **Every violation names both the subject (expense or group) and the rule,** and the output order is deterministic.

### Clarifying questions to ask before you code

| Question | Rule for this run |
|---|---|
| Fields? | `expenseId`, `tripId`, `employeeId`, `type`, `amount`, `sellerType`, `sellerName`. `tripId` and `employeeId` are optional. |
| Expense types? | `enum ExpenseType { FOOD, AIRFARE, HOTEL, SUPPLIES, ENTERTAINMENT }`. "Restaurant" is a **seller** type, not an expense type. |
| Seller type? | A `String` (it comes from merchant data the company doesn't control). |
| Is "above the limit" `>` or `>=`? | **Strictly `>`.** Exactly at the limit is allowed. |
| Currency? | USD only, stored as `long` cents. |
| Input shape? | Typed `Expense` objects. (Variant: string-only maps that must be parsed; see Part 1 notes.) |
| Output? | `List<Violation>`: per-expense violations sorted by expense id, then rule id; then group violations sorted the same way. Ids are compared as text. |
| Does a violation block the expense? | No, it **flags** it for manager review. |
| Group rules report on? | The **group** (trip, or trip + person), listing the member expenses that counted toward the total. |
| A trip shared by several employees? | Allowed. Both a whole-trip cap and a per-person-per-trip cap must be supported. |
| Invalid input? | `IllegalArgumentException` with a short message. |

---

## Part 1: Per-expense rules (~20 min)

```java
record Expense(String expenseId, String tripId, String employeeId, ExpenseType type,
               long amount, String sellerType, String sellerName) {}       // amount in cents
record Violation(String ruleId, String subjectId, String message) {}

interface Rule { String id(); Optional<Violation> evaluate(Expense e); }

List<Violation> evaluateRules(List<Rule> rules, List<Expense> expenses);
```

**Rule set A**
- `R1`: any expense over **$175** is a violation.
- `R2`: a `restaurant` seller with an amount over **$45** is a violation.
- `R3`: the `ENTERTAINMENT` type is prohibited.

**Rules**
- An expense breaking several rules gets one violation per rule.
- `Expense` rejects a blank id, a null type and a negative amount.
- `evaluateRules` rejects null lists, null elements, duplicate expense ids and duplicate rule ids.
- A rule receives one expense and returns `Optional.empty()` or one `Violation`.

**Test cases**
```
E1  FOOD           250.00  restaurant    -> R1, R2
E2  ENTERTAINMENT   30.00  venue         -> R3
E3  FOOD            45.00  restaurant    -> none   (boundary: exactly 45 is allowed)
E4  SUPPLIES       175.00  store         -> none   (boundary)

evaluateRules([R1, R2, R3], [E1..E4]) -> [E1 R1, E1 R2, E2 R3]
evaluateRules([], [E1])               -> []

Rule set B (restaurant > 75, no airfare, no entertainment, any expense > 250)
  E1 FOOD 250 restaurant, E8 AIRFARE 300 airline -> [E1 B1, E8 B2, E8 B4]   (same loop, no change)

new Expense("A", …, FOOD, -5, …)                -> IllegalArgumentException
evaluateRules([R1], [A, A])                     -> IllegalArgumentException (duplicate expense)
evaluateRules([R1, R1], [E1])                   -> IllegalArgumentException (duplicate rule)
```

**Variant: string-only input.** If expenses arrive as `Map<String, String>`, parse them once at the boundary: `"250"` and `"250.00"` → 25000 cents via `BigDecimal`; `"abc"`, `"12.345"` and negatives are rejected. A malformed row becomes one `INVALID_EXPENSE` violation and the batch continues.

**What the interviewer checks**
- **The loop never mentions a rule type.** Adding set B is new `Rule` instances only.
- **Generic rule classes, not one class per sentence:** an amount limit with an optional scope, and a prohibited type, cover sets A, B and C.
- **Boundary tests** at exactly 45.00 and 175.00.
- **`long` cents** throughout.

---

## Part 2: Group rules (trip level) (~20 min)

```java
enum GroupBy { TRIP, TRIP_EMPLOYEE }                    // a named key: how to split expenses into groups
class GroupRule { GroupRule(String id, GroupBy groupBy, Predicate<Expense> counts, long limitCents); }
record Violation(String ruleId, String subjectId, String message, List<String> expenseIds) {}

List<Violation> evaluateRules(List<Rule> rules, List<GroupRule> groupRules, List<Expense> expenses);
```

**Rules**
- `G1`: a trip's total must not exceed **$2000**.
- `G2`: a trip's total **meal** (`FOOD`) spend must not exceed **$200**.
- `G3`: one person's spend on one trip must not exceed **$1200**.
- **`GroupBy` = how to split** ("one total per X"). **`counts` = which expenses count** toward each total ("only Y").
- An expense with no (or a blank) `tripId` is in no trip group. Under `TRIP_EMPLOYEE`, a missing employee also leaves it out.
- Per-expense and group violations come back from **one** call. Rule ids are unique across both kinds.
- A group violation's `subjectId` is the group key (`T1`, `T1/Bob`), and `expenseIds` lists only the expenses that counted.

**Test cases**
```
T1 shared by Alice and Bob
E1 Alice AIRFARE 900  T1
E2 Alice FOOD    150  T1
E3 Bob   AIRFARE 800  T1
E4 Bob   FOOD    100  T1
E5 Bob   HOTEL   350  T1
E6 Bob   FOOD     50  (no trip)

G1 T1        total 2300            -> T1 G1 [E1, E2, E3, E4, E5]
G2 T1        meals 150 + 100 = 250 -> T1 G2 [E2, E4]
G3 T1/Alice  1050                  -> none
G3 T1/Bob    1250                  -> T1/Bob G3 [E3, E4, E5]
E6                                 -> in no group

T2: Carol FOOD 200                 -> G2 none (boundary: exactly 200)
"Food per person per trip ≤ $100"  -> new GroupRule(TRIP_EMPLOYEE, FOOD, 10000): no new code
```

**What the interviewer checks**
- **No `Trip` class with precomputed totals.** Fixed fields (total, per category, per employee) can't express "meals per person per trip", and every new rule kind would change the class.
- **Bucketing once per distinct `GroupBy`,** shared by every rule that uses it: G1 and G2 share the `TRIP` buckets.
- **The filter is applied per rule,** not while bucketing: G1 and G2 share a bucket but count different expenses.
- **Robustness:** blank ids aren't a group; `/` can't appear in trip or employee ids (it separates them in keys); totals use `Math.addExact`; `expenseIds` is copied.

**Reimbursement variant (ask if the interviewer goes there):** E2 is a valid $150 meal on its own, but its trip broke G2. Report it as "rejected because of the group", separate from item-level reasons.

---

## Part 3: Rules as data, added through an API (~15 min)

```java
record Condition(String field, String op, String value) {}        // raw API data
void addRule(String id, Condition scope, Condition check);         // scope may be null
List<Violation> evaluate(List<Expense> expenses);
```

**Rules**
- Operators: `EQ`, `NE`, `GT`, `GE`, `LT`, `LE`.
- **A field registry** lists the only fields a rule may use, how to read each from an `Expense`, and how to parse a value for it: `amount_in_usd` (number, in dollars → cents), `expense_type` (enum), `seller_type`, `seller_name`, `trip_id`, `employee_id` (text).
- **Validate at `addRule`, never at evaluation time:**
  - Unknown field → `IllegalArgumentException`.
  - An ordering operator (`GT`, …) on a text field → `IllegalArgumentException`.
  - A value that can't be parsed (`"lots"`, `CRUISE`, blank) → `IllegalArgumentException`.
  - An unknown operator, a missing check, a duplicate rule id → `IllegalArgumentException`.
- **Never `eval` the received text,** and never use reflection on field names from the API.
- A missing field (null) never matches, for any operator.
- A data rule is just another `Rule`, so the engine loop doesn't change.

**Test cases**
```
addRule("R1", null,                         amount_in_usd GT 175.00)
addRule("R2", seller_type EQ restaurant,    amount_in_usd GT 45)
addRule("R3", null,                         expense_type EQ Entertainment)
evaluate(Part 1 expenses) -> [E1 R1, E1 R2, E2 R3]   (same as Part 1)

addRule("X", null, discount GT 5)              -> IllegalArgumentException (unknown field)
addRule("Y", null, seller_name GT abc)         -> IllegalArgumentException (operator not allowed)
addRule("Z", null, amount_in_usd GT lots)      -> IllegalArgumentException (invalid amount)
addRule("Z", null, expense_type EQ CRUISE)     -> IllegalArgumentException (unknown type)
addRule("Z", null, amount_in_usd BETWEEN 5)    -> IllegalArgumentException (unknown operator)
addRule("R1", …)                               -> IllegalArgumentException (duplicate rule)
After all the rejections, evaluate still returns the same 3 violations.

seller_type NE restaurant on an expense with no seller type -> no violation (missing never matches)
```

**What the interviewer checks**
- **The field registry** is what both validation and evaluation use.
- **Parse once, evaluate many:** a condition is compiled to a predicate when the rule is added.
- **The trade-off out loud:** data rules can be added by finance with no deploy but only express field + operator + value; code rules can do anything but need a deploy.

---

## Part 4: Compound rules (~15 min)

```java
class CompoundRule implements Rule {
    enum Op { AND, OR, NOT }
    CompoundRule(String id, Op op, List<Rule> children);
}
```

**Rules**
- A compound rule combines **other rules**. A child "matches" when it would report a violation.
- AND and OR take one or more children; NOT takes exactly one.
- **Only the top rule is reported.** Building blocks ("seller is a restaurant") are not violations on their own and are not passed to the engine.
- **Short-circuit:** AND stops at the first non-matching child, OR at the first matching one.
- **A sub-rule shared inside one compound tree is evaluated once per expense.**
- Cycles must be impossible.

**Test cases**
```
A  = restaurant over $75                 (building block)
F  = type is FOOD                        (building block)
NF = NOT(F)
X  = AND(A, NF)                          "a pricey non-meal at a restaurant"

E1 FOOD          90.00 restaurant -> A ✓, F ✓ → NF ✗       → no violation
E2 ENTERTAINMENT 120.00 restaurant -> A ✓, NF ✓            → E2 X
E3 ENTERTAINMENT 40.00 restaurant -> A ✗ → NF never evaluated → no violation

Shared sub-rule: B = AND(A, C), X = AND(A, B)  -> A evaluated once, C once (2 leaf evaluations)
Shared compound:  X = AND(B, NOT(NOT(B)))       -> B's leaves evaluated once
OR(true, …) / AND(false, …)                     -> only the first child evaluated

NOT with two children, no children, a null child, a null op, a blank id -> IllegalArgumentException
```

**What the interviewer checks**
- **A compound rule is just another `Rule`,** so the engine doesn't change.
- **Cycle prevention by construction:** children are existing objects, so a rule can't reference itself or a later one. A DFS check is only needed once rules can be edited or referenced by id.
- **Memoization** keyed by rule object, not rule id (ids aren't unique inside a tree).

---

## Part 5 (the locked extension): Expression trees inside one rule

```java
sealed interface Expr permits And, Or, Not, Leaf {}
record EvalResult(boolean matched, String explanation) {}
```

- One rule holds a tree of `And`, `Or`, `Not` and `Leaf(Condition)` nodes, parsed and validated **once**, evaluated against many expenses.
- Leaves use Part 3's typed conditions, so type mismatches are rejected when the rule is built.
- **Missing field ≠ null:** define it once (a leaf on a missing field is false), and note that `NOT(leaf)` is then true.
- **Explainability:** a violation says which sub-expressions decided it, for example `AND(FOOD ✓, amount > 75.00 ✓, NOT(OR(Approved Cafe ✗, Office Canteen ✗)) ✓)`.
- New operators without a growing `switch` in the engine.

```
M = AND(type EQ FOOD, amount GT 75.00, NOT(OR(seller_name EQ Approved Cafe, seller_name EQ Office Canteen)))
E40 FOOD 90.00 "Joe's Diner"    -> violation, every part listed
E41 FOOD 90.00 "Approved Cafe"  -> none (NOT failed)
E42 FOOD 60.00 "Joe's Diner"    -> none; the NOT branch is never evaluated
```

**Compound rules vs expression trees:** a compound rule combines other **named rules** (sharing, cycles and memoization matter); an expression tree combines **conditions inside one rule** (typing and explanations matter). Allowing a tree leaf to be "a reference to rule B" turns one into the other.

---

## Design decisions (and why)

### 1. Why a `Rule` interface, not a `Rule(type, limit)` class
A data class with one fixed meaning can only express "type X over limit Y". "Any expense over $175" has no type, "restaurant over $45" tests the seller type, and "entertainment is prohibited" has no limit. Each new kind would change the class and the loop. An interface with `evaluate(expense)` lets each kind of check be its own small class.

### 2. Why generic rule classes, not one class per rule
`AmountLimitRule(id, limit, scope)` and `ProhibitedRule(id, type)` cover sets A, B and C as **instances** with different arguments. One class per sentence (`MealLimitRule`, `AirfareRule`, …) works, but every new rule needs new code. A new class is only needed for a genuinely new kind of check.

### 3. What the `Predicate` does in `AmountLimitRule`
`Predicate<Expense>` is "a yes/no question about an expense", passed in as a value. It decides **which expenses the limit applies to**: `e -> true` for R1, `e -> "restaurant".equals(e.sellerType())` for R2. The alternative is storing the scope as data (field + value), which is what Part 3 does: a lambda can't come from an API and can't be validated or displayed.

### 4. Why `long` cents and strictly `>`
`double` can't represent most decimal amounts exactly (`0.1 + 0.2 = 0.30000000000000004`), so boundary checks at exactly $45.00 become unreliable. Integer cents compare exactly. "Above the limit" is strictly `>`: the most common reading, and it must be tested at the boundary.

### 5. `ExpenseType` enum, `sellerType` String
Use an enum when **you** own the list of values (policy categories): typos are compile errors and `==` is safe. Use a String (or an enum with `OTHER`) when an outside source owns it (merchant categories from card networks): new values must not be rejected. The cost of the String is that `"Restaurant"` vs `"restaurant"` silently doesn't match; put the constant first (`"restaurant".equals(…)`) so null is safe.

### 6. Why validation lives in the `Expense` constructor and at the start of `evaluateRules`
A negative amount would pass every limit check silently, and a null id would make two violations impossible to sort. Rejecting these once, where the object is created, means no rule has to defend against them. Duplicate rule ids would report the same violation twice.

### 7. Why not a `Trip` class with running totals
A `Trip` with `total`, `perCategory` and `perEmployee` decides in advance which totals exist, so a rule can't say which one it means, and "meals per person per trip" or "per employee per month" need a new field. Running totals inside the engine also double-count if `evaluateRules` is called twice. Incremental totals are the right **optimization** (follow-up 1), keyed by `(ruleId, groupKey)`, not by trip.

### 8. Why a named `GroupBy` key instead of a lambda key
With a lambda key, each group rule must scan every expense and the engine can't tell that G1 and G2 group the same way. A named key lets the engine **bucket once per distinct key** and share the buckets, and expenses with no trip are dropped in that single pass. Named keys can also come from an API. The cost: a new kind of grouping needs one new enum value and one line in `keyOf`.

### 9. `GroupBy` vs `counts`
`GroupBy` decides **how to split** (one total per trip, per trip + person). `counts` decides **which expenses count** toward each total (only food). "Food per person per trip" is `TRIP_EMPLOYEE` + a food predicate, with no new code. A new `GroupBy` is only needed for a separate total for **every value** of something ("each category per trip"), or for groups that aren't trips ("per employee per month", "the whole company").

### 10. How the group total is computed per key
The bucket map already separates the groups: `evaluate(groupKey, members)` is called once per group and only sees that group's members, so the sum it computes **is** that group's total. The key is only written into the violation.

### 11. Why group violations list member ids
"T1 broke G2" isn't actionable without knowing which expenses caused it. Listing only the counted members (the meals for G2) tells a manager exactly what to look at, and supports the reimbursement variant.

### 12. How compound rules evaluate
Two methods call each other: `matches` combines a compound rule's children (AND / OR / NOT), and `childMatches` either recurses into a child compound rule or evaluates a plain rule. `matches` returns a boolean because AND / OR / NOT work on yes/no values; the children's own violations are reduced to yes/no and discarded, and `evaluate` turns the final yes into **one** violation for the top rule.

### 13. Memoization and the map type
A per-expense map caches each sub-rule's answer, so a sub-rule shared inside one tree runs once. It's keyed by **rule object**, not id, because ids aren't checked for uniqueness inside a tree. A plain `HashMap` works because the rule classes keep the default identity-based `equals`/`hashCode`; `IdentityHashMap` would also guard against a future record or custom `equals`. The memo is per top-level rule, so two separate compound rules sharing a block each evaluate it once.

### 14. Complexity summary
| Operation | Time |
|---|---|
| Per-expense rules | O(E × R) |
| Sorting violations | O(V log V) |
| Group bucketing | O(E × K), K = distinct `GroupBy` values in use |
| Group evaluation | O(Σ group sizes × G) |
| `addRule` (data rule) | O(1) after parsing the condition |
| Compound rule, per expense | O(n), n = distinct rules in its tree |

---

## Follow-up discussion questions (with answers)

**1. A million expenses and ten thousand rules: how do you get past O(E × R)?**
Split basic and group rules. For basic rules, **index rules by the field and value they test**: a rule scoped to `FOOD` lives under `FOOD`, so a hotel expense never sees it. Rules with no scope still run on everything. Data rules (Part 3) make this possible because the engine can read a rule's scope; lambdas can't be indexed. For group rules, compute each distinct aggregate once (shared bucketing) and keep it **up to date incrementally**: adding an expense adds its amount to the running totals for each `(ruleId, groupKey)` it belongs to, so a new expense costs O(G), not a rescan.

**2. How do you store results and notify the customer?**
A violations table keyed by `(subject, ruleId, ruleVersion)`, so re-evaluating the same expense is idempotent (an upsert, not a duplicate). Store a status (open, resolved), and notify only on **state changes**: a new violation, or one that was resolved. Write the notification to an outbox in the same transaction, send it at least once, and let the receiver deduplicate by violation id. Index by company, rule, employee and status for the review screens.

**3. Compound rules that share sub-rules: `X = A AND B`, where `B = A AND C`.**
Treat the rules as a dependency graph. Reject cycles when a rule is saved: with immutable rules that can only reference existing ones, a cycle is impossible; once rules can be edited, run a DFS from the edited rule. Evaluate each shared sub-rule once per expense with a memo (or once per batch, for group-level sub-rules). Flatten `X` to `A AND C` when saving. Order children so cheap or selective checks run first, to short-circuit early.

**4. Rules are added while expenses keep arriving.**
Version every rule; a violation records the version that produced it. Distribute the rule set as an immutable snapshot, so a batch never sees half an update. When a rule is added or a limit lowered, re-check existing expenses in the background, idempotently (keyed by rule version). Decide what the customer hears: new violations from a stricter limit are notified; violations that disappear under a looser limit are resolved, not deleted.

**5. "Meals over $75 unless the vendor is on an approved list": how far before the format is a programming language?**
Add an `IN` operator and `NOT` (Part 5), and stop there: no loops, no variables, no arbitrary functions. Anything beyond that (dates, cross-expense lookups) becomes a new **code** rule type that finance can then parameterize. To stop admins from writing expensive rules, cap tree depth and size, and allow only indexed fields in scopes.

**6. One bad API rule naming a nonexistent field must not break everyone.**
Validate at `addRule` against the field registry, so it's rejected before it's stored. At evaluation time, also isolate each rule: a rule that throws is recorded as a rule error and skipped, instead of losing the whole batch. (In this code a throwing predicate still stops the batch; that's the gap to name.)

**7. An expense moves from trip T1 to T2.**
Both trips' totals change: subtract it from T1's running totals and add it to T2's. Re-evaluate the group rules for both, resolve T1 violations that no longer hold, and raise new T2 violations. With batch evaluation (no state), just re-run both trips.

**8. A rule's limit is lowered after a month. Which stored violations change?**
Decide the policy: either the new limit applies only to expenses submitted after the change (store the rule version each expense was checked against), or it is applied retroactively (re-check, raise new violations, mark them clearly). Never silently rewrite old violations; the record of what was enforced at the time must stay.

**9. When does a data-driven spec beat one class per rule?**
When non-engineers add rules often, rules must be stored, listed, edited and audited, or rules must be indexed for performance. One class per kind of check wins when the logic is genuinely new (cross-expense lookups, external calls). The design here supports both behind `Rule`.

**10. Why might the interviewer ask you to switch to hardcoded rule classes mid-round?**
To test whether your loop depends on a specific representation. If the engine only sees `Rule`, switching is adding classes; if the engine parses conditions itself, it's a rewrite. Say this, and show that `AmountLimitRule`, `ConditionRule` and `CompoundRule` all plug into the same loop.

---

## Rubric

| Signal | Hire | Strong hire |
|---|---|---|
| Extensibility | A `Rule` interface | The loop never changes; generic rule classes; data and code rules behind one interface |
| Money and boundaries | Amounts as numbers | `long` cents, strict `>` tested at exactly the limit |
| Output | A list of violations | Names subject and rule, deterministic order, group violations with counted members |
| Group rules | Trip total works | Named grouping key, buckets shared across rules, split vs filter explained, shared trips handled |
| Rules as data | Field + op + value | Validated at `addRule` with a field registry, no `eval`, a bad rule can't break others |
| Compound logic | AND only | AND / OR / NOT, short-circuit, memoized shared sub-rules, cycles impossible |
| Pace | Long design talk | Part 1 with boundary tests in ~20 min, Part 2 done, Part 3 started |
