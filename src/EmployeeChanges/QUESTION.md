# Scheduled and Backdated Employee Changes (Rippling SDE2, LLD / AI-coding onsite)

## Interviewer framing

> "HR admins edit employee fields like title, salary, level, manager and department, and today every edit takes effect immediately. Add the ability to **schedule a change for a future date**: a promotion next month, a raise on the 1st. Until that date the app shows the current values. From that date it shows the new values **without anyone editing again**. The history must stay readable. I'll keep asking *how the effective date is handled*. That is the round."

**How it's adapted for this repo:** it's in-memory Java with no database, HTTP or frontend.
- Endpoints become public methods.
- The `EmployeeHistory` table becomes an in-memory change log.
- The frontend is covered in the discussion questions.

**What makes or breaks this round**
- **Real candidates failed here.** One proposed a table plus a worker that applies due changes, and the interviewer rejected it. Another compared resolve-on-read vs apply-on-write but ran out of time to review the AI's code. **Decide quickly, state the rule, and code it.**
- **One explicit rule for which value is in effect on any date.** Every query must use that one rule.
- **A change never overwrites another change.** Changes are per field, not whole-record snapshots, and cancellations and corrections are new events.
- **Pin the clock.** Pass "today" in as a parameter; never call `LocalDate.now()` inside the logic. Test both sides of the boundary date.

### Clarifying questions to ask before you code

| Question | Rule for this run |
|---|---|
| Which fields can be scheduled? | `enum Field { TITLE, SALARY, LEVEL, MANAGER, DEPARTMENT }` |
| Per field or whole snapshot? | **Per field.** Each change sets one field. |
| Date or timestamp? Whose time zone? | `LocalDate`, in the **company's** time zone. A change is in effect from the start of its date. |
| Field values? | `String`. Salary is a string of whole cents, such as `"10000000"`. |
| Absolute or relative ("+10%")? | **Absolute only.** |
| Two changes to the same field on the same date? | The **higher server-assigned revision** wins, **for that field only**. The client clock is never used. |
| An immediate edit? | A change effective **today**. Same path and same validation, no special case. |
| Retries? | Every create carries a `requestId`. The same id with the same payload returns the original revision; the same id with a different payload throws `IllegalArgumentException`. |
| Effective date before the hire date? | `IllegalArgumentException`. |
| An employee who joins later? | The caller supplies the id. `hireDate` may be in the future. |
| Past dates? | Rejected in Part 1, and allowed as backdated changes in Part 3. |
| Invalid input? | `IllegalArgumentException` with a message. |

Implement this in its own package, `src/EmployeeChanges/`.

---

## Part 1: The effective-dated model and as-of reads (~25 min)

```java
enum Field { TITLE, SALARY, LEVEL, MANAGER, DEPARTMENT }

void addEmployee(String employeeId, LocalDate hireDate, Map<Field, String> initialValues);
long scheduleChange(String requestId, String employeeId, Field field, String value,
                    LocalDate effectiveDate, LocalDate today);          // returns the revision
Map<Field, String> getSnapshot(String employeeId, LocalDate asOf);
List<ChangeEvent> pendingChanges(String employeeId, LocalDate today);
```

**Rules**
- **The one rule:** the value of field F on date D comes from the change to F with the **latest effective date ≤ D**. If several share that date, the **highest revision** wins.
- The initial values are recorded as changes effective on the hire date, and they use up revisions like any other change.
- Revisions come from a single counter on the system, starting at 1 and increasing across all employees.
- `effectiveDate < today` → `IllegalArgumentException` (until Part 3). `effectiveDate == today` is an immediate edit.
- `effectiveDate < hireDate`, an unknown employee, a duplicate employee id, a null or blank value → `IllegalArgumentException`.
- `getSnapshot` for a date before the hire date → `IllegalArgumentException("not employed on …")`. A field that was never set is left out of the result.
- `pendingChanges` returns the **winning** future changes (`effectiveDate > today`), ordered by date and then field. A change that another same-day change has replaced isn't listed.
- A retry with the same `requestId` and the same payload returns the original revision, even after the effective date has passed.

**Test cases**
```
addEmployee("E1", 2026-01-01, {TITLE: Engineer, SALARY: 10000000, DEPARTMENT: Platform})

today = 2026-01-10
q1  scheduleChange("q1", E1, TITLE,      "Senior Engineer", 2026-03-01)
q2  scheduleChange("q2", E1, DEPARTMENT, "Payments",        2026-02-15)
q3  scheduleChange("q3", E1, SALARY,     "12000000",        2026-03-01)
q4  scheduleChange("q4", E1, SALARY,     "12500000",        2026-03-01)   // same day, later revision

getSnapshot(E1, 2026-02-14) -> Engineer, 10000000, Platform
getSnapshot(E1, 2026-02-15) -> Engineer, 10000000, Payments        (boundary: in effect from that date)
getSnapshot(E1, 2026-03-01) -> Senior Engineer, 12500000, Payments  (q4 overrides SALARY only)
getSnapshot(E1, 2025-12-31) -> IllegalArgumentException

scheduleChange("q1", E1, TITLE, "Senior Engineer", 2026-03-01)  -> same revision as before, no new entry
scheduleChange("q1", E1, TITLE, "Staff",           2026-03-01)  -> IllegalArgumentException
scheduleChange("q1", E1, TITLE, "Senior Engineer", 2026-04-01)  -> IllegalArgumentException (date differs)
scheduleChange("q5", E1, TITLE, "X", 2026-01-05)                -> IllegalArgumentException (past)
scheduleChange("q6", EX, TITLE, "X", 2026-03-01)                -> IllegalArgumentException (unknown employee)
scheduleChange("q7", E1, TITLE, null, 2026-03-01)               -> IllegalArgumentException

pendingChanges(E1, today 2026-02-20) -> [TITLE Senior Engineer @03-01, SALARY 12500000 @03-01]

addEmployee("E2", 2026-03-01, {TITLE: Engineer})  // joins in the future
getSnapshot(E2, 2026-02-20) -> IllegalArgumentException (not employed)
scheduleChange("q8", E2, TITLE, "Staff", 2026-02-15) -> IllegalArgumentException (before hire)
```

**What the interviewer checks**
- **A per-employee, per-field timeline** instead of one overwritten row. Which structure gives an O(log n) as-of lookup? How do you store two entries for the same date without losing one?
- **A `ChangeEvent` record** with `requestId`, employee id, `field`, `value`, `effectiveDate`, `createdDate` and the server revision. History is only ever appended to, never changed.
- **The immediate edit reuses the scheduling path** and its validation.
- **Idempotency is checked before the revision is assigned.** A rejected request doesn't use up a revision number.
- **The rule is written as a one-line comment** in the code, and every read uses it.

---

## Part 2: When does a change take effect? (~15 min)

Pick **resolve-on-read** or **apply-via-job**, **justify it in 3 sentences**, then implement the following on top of your choice:

```java
List<String> employeesWithTitle(String title, LocalDate asOf);       // the list page filter
List<ChangeEvent> drainDueNotifications(LocalDate today);            // payroll side effects
```

**Rules**
- `employeesWithTitle` returns sorted employee ids and is correct on the date a change takes effect, **with no job running**.
- Only **SALARY** changes notify payroll.
- **Each winning SALARY change is notified exactly once,** after its effective date has arrived:
  - A drain on a later day catches up on everything that came due since the last drain.
  - A second drain on the same day returns `[]`.
  - A change that another same-day change replaced is **never** notified.
  - The initial salary at hire is notified too, on the hire date.
- **Employee data never depends on the drain.** `getSnapshot` is correct even if `drainDueNotifications` never runs.

**Test cases** (a small example)
```
today = 2026-01-10
E1 hired 01-01: TITLE Engineer, SALARY 100
E2 hired 01-01: TITLE Engineer, SALARY 200

scheduleChange E1 TITLE  "Senior" @03-01
scheduleChange E1 SALARY 120      @03-01
scheduleChange E1 SALARY 125      @03-01     // same day, replaces 120
scheduleChange E2 SALARY 210      @02-15

employeesWithTitle("Engineer", 02-28) -> [E1, E2]
employeesWithTitle("Senior",   02-28) -> []
employeesWithTitle("Senior",   03-01) -> [E1]        (flips on the effective date, no job)
employeesWithTitle("Engineer", 03-05) -> [E2]

drainDueNotifications(01-10) -> [E1 100, E2 200]     (the initial values at hire)
drainDueNotifications(01-10) -> []                   (same day again)
drainDueNotifications(02-20) -> [E2 210]             (catches up on 02-15)
drainDueNotifications(03-05) -> [E1 125]             (120 was replaced, never sent)
drainDueNotifications(03-05) -> []
```

**What the interviewer checks**
- **The trade-off, stated crisply:**
  - Resolve-on-read needs no job and is always correct, but each read costs more, and filtering a list page means resolving every employee.
  - Apply-via-job runs into late, double or missed runs, races with newer edits, and a slow worker overwriting a newer revision.
- **The outbox is separate from the data.** Why is "the last drained date" alone wrong once Part 3 allows backdated changes, or after an immediate edit made after that day's drain?
- **No second copy of the data.** An index like `date → employee → title` can drift from the timeline. Resolving on read uses the same rule as `getSnapshot`.
- **Cost of the list page:** O(employees × log changes) per query. Say how you'd make it faster without making it wrong (see discussion question 3).

---

## Part 3: Cancellation, backdating and bitemporal reads (~20 min)

```java
long cancelChange(long revision, LocalDate today);                   // returns the cancellation's revision
long scheduleChange(...);                                            // now accepts effectiveDate < today
Map<Field, String> getSnapshot(String employeeId, LocalDate asOf, long knownAsOfRevision);
```

**Rules**
- **Cancellation is recorded as its own event with a new revision.** Nothing is deleted, and the history shows both entries.
  - Only a change with `effectiveDate > today` can be cancelled. Anything else throws `IllegalArgumentException("already effective; record a correction")`. The hire-date values are never cancellable.
  - Cancelling the same change twice returns the first cancellation's revision.
  - After a cancellation, the **next remaining change** wins again. That's an earlier same-day revision, or, if none is left, the previous date.
- **A backdated change** (`effectiveDate < today`) is inserted into the middle of the timeline. Every as-of read for dates on or after its effective date reflects it, until the next change.
- **Bitemporal read:** `getSnapshot(id, asOf, knownAsOfRevision)` answers "what did the system show for `asOf`, using only revisions ≤ `knownAsOfRevision`?" A cancellation counts only if its own revision is ≤ `knownAsOfRevision`.
- A backdated SALARY change is sent on the next drain and flagged retroactive (effective before it was recorded), so payroll can back-pay.
- Cancelling a SALARY change that hasn't been drained yet must also fix the outbox: put back the same-day winner, or remove the entry if none is left.

**Test cases** (a small example)
```
r1  hire     E1 SALARY 100 @01-01
r2  today 01-10: SALARY 120 @03-01
r3  today 01-10: SALARY 125 @03-01      (same day, r3 wins)

drainDueNotifications(01-10)            -> [E1 100]          (before the changes below)

today = 02-20
r4  cancelChange(r3)                    -> SALARY @03-01 resolves to 120 again
    cancelChange(r3) again              -> returns r4 again
    cancelChange(r1)                    -> IllegalArgumentException (hire value)
r5  scheduleChange("q5", E1, SALARY, "110", 2026-02-01, today)   // backdated correction

getSnapshot(E1, 02-10)                  -> 110     (as known now)
getSnapshot(E1, 02-10, known = 4)       -> 100     (what the system showed before the correction)
getSnapshot(E1, 03-01, known = 3)       -> 125     (before the cancellation was known)
getSnapshot(E1, 03-01, known = 4)       -> 120     (after the cancellation)
getSnapshot(E1, 03-01, known = 1)       -> 100     (right after hire)

drainDueNotifications(02-20)            -> [E1 110 @02-01, retroactive]    (120 @03-01 is not due yet)

r6  cancelChange(r2)                    -> SALARY @03-01 falls back to the previous date (02-01 → 110)
drainDueNotifications(03-05)            -> []                (the cancelled 120 is never sent)
```

**What the interviewer checks**
- **Two kinds of time are kept apart:** the effective date (when it's true) and the revision (when the system learned it). One extra filter, `revision <= known`, gives you audit queries.
- **"Never overwrite"** holds for cancellations and corrections too. Every action is a new row, and a cancellation is stored as its own revision, not as a flag on an immutable record.
- **One resolve helper** used by every read. If `getSnapshot`, `pendingChanges` and `employeesWithTitle` each call `getLast()` themselves, they will disagree about cancelled changes.
- **The outbox handles backdating.** It's driven by revisions not yet sent, not by dates.
- **Permissions:** say out loud who can backdate and how far back. You don't need to code it.

---

## Part 4 (the locked extension): Termination and whole-org views

```java
List<Long> terminate(String requestId, String employeeId, LocalDate terminationDate, LocalDate today);
Map<String, Map<Field, String>> orgSnapshot(LocalDate asOf);
```

- After termination, any change effective **after** `terminationDate` is rejected.
- Already-scheduled changes after `terminationDate` are **auto-cancelled** with recorded cancellation events. `terminate` returns their revisions. A scheduled raise followed by an earlier termination is the classic trap.
- `orgSnapshot(asOf)` includes only employees with `hireDate ≤ asOf ≤ terminationDate`. It works for past **and** future dates.

```
E1 hired 01-01, SALARY 100; raise to 120 scheduled @03-01 (revision r2)
terminate(E1, 2026-02-15) on 01-10   -> [r2's cancellation]
getSnapshot(E1, 03-05)               -> IllegalArgumentException (not employed)
scheduleChange(E1, SALARY, 130, 03-01) -> IllegalArgumentException (after termination)
orgSnapshot(2026-02-15)              -> includes E1
orgSnapshot(2026-02-16)              -> does not include E1
drainDueNotifications(03-05)         -> never includes the 120
```

---

## Design decisions (and why)

### 1. Why resolve-on-read, not a job that applies changes
- Correctness can't depend on a job running on time. Resolving on read is correct for any date (past, today, future) with no job at all.
- A job can be late (the app shows old values at 00:30), missed (the change never applies), run twice (a side effect fires twice), or race with an admin edit (a slow worker writes an older value over a newer one).
- The cost is read work: O(log k) per field per employee. A cached "current values" table can come later as an optimization, with the timeline staying the source of truth.

### 2. Why a per-field timeline, not snapshots
Three candidate structures:

| Structure | Write | Read as-of D | Problem |
|---|---|---|---|
| `TreeMap<date, Map<emp, Employee>>` (org snapshot per date) | O(later dates × employees) | O(log dates) | A change inserted at an earlier date must be copied into every later snapshot, or later reads are stale |
| `Map<emp, TreeMap<date, Employee>>` (employee snapshot per date) | O(later dates) | O(log dates) | Same stale-snapshot cascade |
| `Map<emp, Map<Field, TreeMap<date, List<ChangeEvent>>>>` | **O(log k)** | O(fields × log k) | None. Changes can't overwrite each other, and history is kept. |

Example of the cascade: a TITLE change on 03-01 is stored in a snapshot that holds `DEPT = Platform`. A later-recorded `DEPT = Payments` change on 02-15 doesn't reach the 03-01 snapshot, so a read on 03-05 shows Platform. A per-field timeline has no such copy.

### 3. Why a `List<ChangeEvent>` per date
Same-day changes are appended. The last active one wins, and nothing is overwritten. That keeps history readable, and it's what makes cancellation work: if the winner is cancelled, the previous same-day entry wins again.

### 4. Why the revision comes from the server, not the client
Client clocks can be wrong, skewed or forged. A single counter on the server gives a total order for "which change was made later", and it doubles as the "knowledge time" for bitemporal reads. The `requestId` is the client's key for deduplication; the revision is the server's order. They do different jobs.

### 5. Why `scheduleChange` returns the revision
- A retry returns the **same** revision, so the caller can tell its request was recorded once.
- `cancelChange(revision)` needs a handle to the change.
- Bitemporal reads use revisions as the point in time.
- Tests can assert ordering directly.

### 6. Why the idempotency check comes first
A valid request retried after its effective date has passed must still succeed with the original result. Checking the date first would reject the retry. A rejected request must not use up a revision number, so validation happens before `++counter`.

### 7. Why `LocalDate`, and what "in effect from the start of the date" means
The granularity is a day, in the company's time zone. A change effective 03-01 applies from the start of 03-01, so `getSnapshot(03-01)` already shows it (`floorEntry` is inclusive). A timestamp would need a time zone and an instant, which the spec doesn't ask for. For an employee in another zone, the company-day is still the rule.

### 8. Why the outbox is `TreeMap<LocalDate, Map<EmployeeId, ChangeEvent>>`
- `put` replaces an employee's earlier entry for that date, so a replaced same-day change is never sent. There's no explicit eviction step.
- `headMap(today, true)` returns every due date in one call, which gives the catch-up.
- Removing the drained dates is what marks them as sent. A change recorded later for a date that was already drained creates a new entry and goes out on the next drain. That covers an immediate edit after that day's drain, and a backdated change.
- A `lastDrainedDate` alone would miss both of those cases.
- Consequence to state out loud: payroll may get two messages for the same day, where the second is a correction.

### 9. Why employee data never depends on the outbox
The outbox only tells payroll what changed. The snapshot is computed from the timeline. If the drain never runs, reads are still right, and the drain can be replayed or delayed without corrupting anything.

### 10. How cancellation is stored
A separate `Map<Long, Long>` from the cancelled revision to the cancellation's own revision. The change record is immutable and stays in the timeline, so history shows both. The cancellation's own revision matters: a bitemporal query with `known` below it must still see the change as active.

### 11. Why bitemporal reads are cheap here
Effective dates are never overwritten and revisions only go up. So both time axes already exist, and the audit query is the normal resolve walk with one extra filter: `revision <= known`, and a cancellation counts only if its revision is ≤ known.

### 12. Why `refreshOutbox` exists
The outbox keeps only one entry per employee per date. If that entry's change is cancelled before it is sent, the outbox must be given whatever wins now (the previous same-day change, or nothing). Otherwise payroll is told about a salary that never took effect.

### 13. Complexity summary
| Method | Time |
|---|---|
| `addEmployee` | O(F) |
| `scheduleChange` | O(log k) |
| `getSnapshot` | O(F × (log k + c)), c = cancelled or too-new entries skipped |
| `pendingChanges` | O(F × log k + p log p) |
| `employeesWithTitle` | O(N × (log k + c) + r log r) |
| `cancelChange` | O(1) + O(s) for the outbox fix, s = changes on that date |
| `drainDueNotifications` | O(d + m + m log m) |

---

## Follow-up discussion questions (with answers)

**1. Crashed worker.** Suppose you had chosen a job. It updates the employee row, then crashes before marking the change complete. What happens on the next run?

The job runs again and applies the same change a second time. The fix is to make each step idempotent: *claim* the change (atomically mark it in progress with a lease), *apply* it as "set field = value for revision R" (re-applying the same revision is harmless, and it must not overwrite a **newer** revision), then *mark complete*. A claim whose lease expired can be taken over. "Cancel after a worker claimed it" has to check the claim state in the same transaction. This whole class of problems doesn't exist with resolve-on-read, which is the reason to pick it.

**2. Two admins schedule different salaries for the same date.** Is "highest revision wins" acceptable?

It's deterministic and nothing is lost, because both changes are kept in history. For HR, a silent overwrite may be wrong. Options: return a conflict response naming the existing change so a person reviews it, or accept both and show a warning in the History tab. The API could return the winning revision plus a list of superseded revisions.

**3. A fast list page.** How do you keep "all Senior Engineers" fast without being wrong when a change falls due?

Keep a materialized "current values" table, refreshed at midnight in the company's time zone and on every write effective ≤ today. If a refresh is late, fall back to resolving on read for any employee that has a change dated in the gap (a small, indexed set). The timeline stays the source of truth, and the cache is only an optimization.

**4. Time zones and relative changes.**
- An employee in IST, a company in PST: the change dated 03-01 takes effect at the start of 03-01 in the **company's** time zone, which is the same instant for everyone. State that rule, and show the local time in the UI.
- "+10%" breaks idempotent retries (a retry could apply it twice, depending on what the base was) and breaks backdating (the base differs depending on what is already on file). Store the **absolute resulting value** at scheduling time, plus the rule if you want to show it.

**5. The frontend and working with AI.**
- The profile page shows pending changes in a separate "Scheduled" section with a badge, not mixed into the current values. The History tab merges applied, pending, cancelled and corrected entries.
- In AI-written code, check line by line: date comparisons (`isBefore` vs `!isAfter`), the boundary day, a hidden `LocalDate.now()`, migrations that drop history, and any read that calls `getLast()` and ignores cancellations.

**6. Cancel a promotion one day before.** What's stored?

A cancellation event with a new revision, pointing at the promotion. The promotion stays in history. Reads on or after its effective date skip it, and an older read with a `known` below the cancellation's revision still sees it.

**7. A wrong scheduled salary is discovered after it took effect.** How do you correct it without losing history?

You can't cancel it (it's already in effect). Record a backdated correction with the same effective date or an earlier one. The old value stays in history, current reads show the correction, and a bitemporal read at an older revision still reproduces what the system said. Payroll gets the correction flagged as retroactive.

**8. Termination before an already-scheduled raise.** What should happen?

Reject new changes after the termination date, and auto-cancel the scheduled ones with recorded cancellation events, so the history says why the raise never happened. Also make sure the outbox never sends it.

**9. An auditor asks what the system showed before a backdated correction.**

Run the bitemporal read with `known` set to the last revision before the correction. Revisions are the system's own order, so the answer can't be changed afterwards. If the auditor thinks in dates, you need a date-to-revision lookup (record when each revision was created).

**10. Notify downstream exactly once.**

Track what's been sent, not which days have been processed. Removing an entry from the outbox when it's sent is the "mark as sent". For a real system, write the outbox row in the same transaction as the change, and have the sender mark it done after a successful delivery. Delivery is then at-least-once, so downstream needs its own dedup key (the revision).

---

## Rubric

| Signal | Hire | Strong hire |
|---|---|---|
| Model | A pending-changes table | A per-field timeline that's only appended to, with revisions and one written resolution rule |
| Effective date | Works in the middle of a range | Boundary day tested, clock passed in, the date-vs-timestamp choice stated |
| Read vs write | Picks one | Decides in under 3 minutes with a justified trade-off, and side effects kept in a separate idempotent outbox |
| Same-day and idempotency | Last write wins | Merged per field by server revision, retry-safe `requestId`, a rejected request doesn't use up a revision |
| History and audit | Keeps old values | Bitemporal reads, cancellations and corrections recorded as events, backdated changes notified as retroactive |
| Consistency | Works for the main path | One resolve helper used by every read, and the outbox fixed up when a pending change is cancelled |
| Pace and AI use | Long debate | Every rule pinned by a test, the AI's date logic reviewed, 3 of 4 parts done |
