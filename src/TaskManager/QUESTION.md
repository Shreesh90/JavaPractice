# Task Manager: Filter, Sort, Hierarchy and Dependencies (Rippling SDE2, LLD)

## Interviewer framing

> "We're building a small task manager for an internal tool. Each task has a due date, a
> priority flag, an assignee and a status, and can belong to a parent task. Start by adding tasks
> and printing the ones that still need an owner, in a very specific order. I'll extend it step
> by step. I care about **getting every detail of the spec right**, about **validation**, and about
> **not rewriting Part 1** when Part 2 arrives."

**What makes or breaks this round**
- **The spec is long and detailed.** Candidates fail by dropping a rule (`-1` sorts last, the
  final tie-break, the parent format), not because of the algorithm.
- **One comparator, one validator, one filter.** Every later part reuses them.
- **The filtered-parent case in Part 3** is the classic trap: a naive filter prints children of
  a hidden parent.
- **Pace:** finishing 2 of 3 parts well is expected. Write assumptions as one-line comments and
  keep moving.

---

## Clarifying questions (and the answers for this run)

| Question | Rule |
|---|---|
| What does "no assignee" mean? | `assignee` is `null`, empty or blank. Store it as `""`. |
| What does "done" mean? | `boolean done`. |
| No parent? | `parentTaskId == 0`. Ids are `> 0`. |
| No due date? | `dueDate == -1`. Otherwise it must be `> 0`. |
| Must a parent be added before its child? | **No.** Records arrive in any order. |
| Invalid input? | `IllegalArgumentException` with a clear message. |
| Duplicate id? | Identical record: **ignored** (idempotent). Same id with different data: `IllegalArgumentException`. |
| Description spacing? | Trim it. Blank after trimming: `IllegalArgumentException`. |
| Output? | Print methods **return `List<String>`** so they can be tested. A demo prints them. |
| Is there a numeric priority? | No, only `isPriorityTask`, where `true` comes first. |

---

## Part 1: addTask and printTasks (~20 min)

```java
record Task(int id, String description, int dueDate, boolean isPriorityTask,
            int createdDate, String assignee, boolean done, int parentTaskId) {}

void addTask(Task task);
List<String> printTasks();
```

**Filter:** print only tasks that are **unassigned AND not done**.

**Sort order (exactly this sequence)**
1. `dueDate` ascending, with **`-1` after every real date**
2. Priority tasks first
3. `createdDate` ascending
4. `id` ascending, as a deterministic final tie-break

**Format**
- No parent: `{id}: {description}`
- With a parent: `{id}: {description}: {parentId}: {parentDescription}`
- A parent that was never added: print **without** the suffix (state this assumption).

**Validation:** one `validateTask` method that returns the normalized record.

| Input | Handling |
|---|---|
| `null` task | `IllegalArgumentException` |
| `id <= 0` | `IllegalArgumentException` |
| `null` or blank description | `IllegalArgumentException` |
| Description with leading or trailing spaces | Trimmed |
| `dueDate < -1` or `dueDate == 0` | `IllegalArgumentException` (`-1` is valid) |
| `createdDate <= 0` | `IllegalArgumentException` |
| `parentTaskId < 0` or `parentTaskId == id` | `IllegalArgumentException` |
| `null` assignee | Normalized to `""` |
| Duplicate id | Identical record: ignored. Different data: `IllegalArgumentException`. |

**Test cases**
```
T1 "Write report"   due 5,  prio false, created 1, unassigned, open
T2 "Fix bug"        due 5,  prio true,  created 2, unassigned, open
T3 "Plan"           due -1, prio true,  created 1, unassigned, open
T4 "Review"         due 3,  prio false, created 3, assignee "bob"     -> filtered
T5 "Deploy"         due 3,  prio false, created 4, done               -> filtered
T6 "Test fix"       due 2,  prio false, created 5, unassigned, parent 2
T7 "Docs"           due 5,  prio false, created 1, unassigned, open   (ties T1 except for id)

printTasks() ->
  6: Test fix: 2: Fix bug
  2: Fix bug
  1: Write report
  7: Docs
  3: Plan

addTask(T1 again, identical)          -> ignored
addTask(id 1, "Other", ...)           -> IllegalArgumentException
addTask(id 8, "  Spaces  ", ...)      -> stored and printed as "8: Spaces"
addTask(id 9, createdDate 0)          -> IllegalArgumentException
addTask(id 10, dueDate -5)            -> IllegalArgumentException
addTask(id 11, parent 99)             -> accepted; prints "11: ..." with no suffix
```

**What the interviewer checks**
- `Task` is a **record**. Tasks are looked up by id, ideally in a `Map<Integer, Task>`.
- The `-1` rule lives **inside the comparator**, not in mutated data.
- One `validateTask`, with a stated policy for each bad input.
- Exact formatting, checked against the expected strings.
- Edge cases added proactively after the main test passes.

---

## Part 2: Child tasks and printTaskTree (~15 min)

A task can have **any number of children**. Each child has exactly one parent, given by
`parentTaskId`.

```java
List<String> printTaskTree();
```

**Rules**
- **`parentTaskId` is the single source of truth.** Do not add a `children` list to the record.
  The manager keeps a parent → children index as tasks are added, in any order.
- **Roots** are tasks with no parent, or whose parent was never added. They are filtered and sorted
  as in Part 1.
- Under each printed task, print its children **indented 2 spaces per level**, filtered and sorted
  with the **same** comparator and filter, recursively to any depth.
- Child lines use the short format `{id}: {description}`.
- A child appears **only under its parent**, not again at the top level.
- If a task is filtered out, nothing below it is printed.

**Test cases**
```
T1 "Launch"     due 10, root
T2 "Design"     due 4,  parent 1
T3 "Build"      due 2,  parent 1
T4 "Mockups"    due 3,  parent 2
T5 "Hire"       due 5,  root
T6 "Old spike"  due 1,  parent 1, assignee "bob"   -> filtered
T7 "Spike note" due 1,  parent 6                    -> its parent is filtered

printTaskTree() ->
  5: Hire
  1: Launch
    3: Build
    2: Design
      4: Mockups

Same records added in reverse order (T7 ... T1) -> same output
```

**What the interviewer checks**
- Part 1 is **not rewritten**: the filter, the comparator and the formatting are reused.
- A **children index** (`Map<Integer, List<...>>`) is kept up to date in `addTask`, instead of
  scanning every task for each parent.
- **Roots include orphans** (the parent was never added), not just `parentTaskId == 0`.
- **Recursion safety:** what happens with a cycle in parent links? (Part 3 rejects them.)

---

## Part 3: Filtering propagates to descendants (~15 min)

This rule changes the **flat** `printTasks()` from Part 1.

**Rules**
- A task is shown only if it passes the filter **and every ancestor up the chain passes too**.
- A missing parent ends the chain. The task is treated as a root.
- **Cycles in parent links are rejected in `addTask`** with an `IllegalArgumentException` naming
  the cycle. This must work when records arrive out of order.
- The visibility check must be **O(n) per `printTasks` call**, not O(n × depth).

**Test cases**
```
T1 "Grandparent"  assignee "user123"   -> filtered
T2 "Parent"       parent 1, unassigned
T3 "Child"        parent 2, unassigned
T4 "Unrelated"    unassigned

printTasks() -> 4: Unrelated
  (a naive filter also prints "2: Parent: 1: Grandparent" and "3: Child: 2: Parent")

Same records added in order T3, T2, T4, T1 -> 4: Unrelated

addTask(T8, parent 9); addTask(T9, parent 8)   -> IllegalArgumentException: cycle [9, 8, 9]
addTask(T10, parent 10)                        -> IllegalArgumentException (rejected in Part 1)
```

**What the interviewer checks**
- **Memoization:** each task's visibility is computed once per call, and descendants reuse it.
- **Visibility is decided at print time,** because a child can arrive before its parent.
- **Cycle detection at add time:** walk up from the new task's parent. If the walk reaches the new
  task's id, reject it.
- **The insight to say out loud:** `printTaskTree()` already behaves this way, because it never
  descends below a filtered task. Only the flat view needs the explicit rule.

---

## Part 4: Scheduling with dependencies (~15 min)

Tasks can depend on other tasks: they can't start until those tasks are finished.

```java
record Task(..., List<Integer> dependencyIds) {}   // new field; empty if none
List<Integer> scheduleOrder();                      // the order of task ids to work on
```

**Rules**
- Schedule only tasks that pass the Part 1 filter (unassigned and not done).
- Each task appears **after all its dependencies**.
- Among tasks that are **ready** (all dependencies satisfied), always pick next by the comparator.
- A dependency on a **done** task is already satisfied.
- A dependency on a task that's **filtered out but not done** (for example, assigned to someone else)
  **blocks** the task. The blocked task is removed, and so is everything that depends on it,
  transitively.
- An **unknown dependency id** or a **cycle**: `IllegalArgumentException` naming the ids involved.

**Test cases**
```
A1 "Setup DB"  due 5,  prio false, created 1
A2 "API"       due 3,  prio true,  created 2, depends on [1]
A3 "UI"        due 2,  prio false, created 3, depends on [2]
A4 "Docs"      due 1,  prio false, created 4
A5 "Old"       done
A6 "Cleanup"   due 4,  prio false, created 6, depends on [5]   (done, so satisfied)
A7 "Blocked"   due 1,  prio false, created 7, depends on [8]
A8 "Infra"     assignee "bob", open                             (blocks A7)

scheduleOrder() -> [4, 6, 1, 2, 3]
  (sorting by the comparator alone gives [4, 7, 3, 2, 6, 1], which breaks the dependencies)

B1 depends on [2], B2 depends on [1]   -> IllegalArgumentException: cycle among [1, 2]
C1 depends on [99]                      -> IllegalArgumentException: unknown dependency 99
```

**What the interviewer checks**
- **Kahn's algorithm** with a `PriorityQueue<Task>` of ready tasks ordered by the comparator:
  O((V + E) log V).
- **Priority inversion:** A2 is high priority with an earlier due date, but it waits for
  low-priority A1. Discuss whether a blocking dependency should inherit its dependent's urgency.
- **Cycle detection:** if fewer tasks are output than were eligible, the rest are in or behind a
  cycle.
- **Blocked vs. cycle:** both leave tasks unscheduled. The candidate must tell them apart, or a
  blocked task gets reported as a cycle.
- **Adding a field to the record** without breaking every existing constructor call.

---

## Part 5 (extension): Configurable filters and parallel stages

**5a. Composable filters**
```java
List<String> printTasks(Predicate<Task> filter);   // the default is unassigned AND not done
```
- Filters combine with AND, OR and NOT, for example "priority OR due before day 3, AND not done".
- Part 3's ancestor propagation and Part 4's blocking policy must use **whatever filter is
  passed in**.

**5b. Parallel stages**
```java
List<List<Integer>> scheduleStages();
```
- Stage *k* contains every task whose dependencies are all in earlier stages. Within a stage,
  tasks are ordered by the comparator.

```
Part 4 data -> [[4, 6, 1], [2], [3]]
```

**What the interviewer checks**
- **5a:** `Predicate<Task>` with `and()`, `or()` and `negate()`, with no custom filter class
  hierarchy. The hard-coded Part 1 filter becomes the default argument.
- **5b:** Kahn's algorithm processed level by level (drain the whole ready set, then advance).

---

## Design decisions (and why)

### 1. Why `Task` is a record
- A task is a value: its identity is its id, and its fields describe it. A record gives
  immutability, `equals`, `hashCode` and `toString` for free.
- **Immutability protects the indexes.** No caller holding a reference can change `parentTaskId`
  or `done` behind the manager's back and leave the children index or a sorted structure stale.
- **`equals` makes the duplicate rule a one-liner:** an identical record is ignored and a different
  one is rejected. Compare the **normalized** record, or `"  Plan "` and `"Plan"` will count as
  different.

### 2. Updating a record (for example, marking a task done)
- Build a new `Task` with the changed field and **replace** the old one wherever it is stored.
- If the same `Task` object lives in two structures (a task list and a children index of `Task`
  objects), both must be replaced, or the flat and tree views disagree.
- The cleaner design: **one** `Map<Integer, Task>` as the single source of truth, and every other
  structure (the children index, dependents) stores **ids**. An update is then a single `put`.
- Changing `parentTaskId` also means moving the id in the children index and running the cycle
  check again.

### 3. Why no `children` list inside `Task`
- Records arrive in any order, so a child can come in before its parent exists.
- The parent record is immutable, so adding a child would mean rebuilding the parent, then its
  parent, and so on.
- Two sources of truth (`parentTaskId` and `children`) can disagree.
- A children list would also become part of `equals`, which breaks the duplicate check.

### 4. Why the `-1` rule lives in the comparator
- Mapping `-1` to `Integer.MAX_VALUE` **inside the key extractor** keeps the stored data true to
  the input. Rewriting `dueDate` would change what `equals` compares and what a later "has a due
  date?" question returns.
- `Comparator.comparingInt(...).thenComparing(Task::isPriorityTask, reverseOrder())...` keeps each
  rule on its own line, so it is easy to check against the spec.
- The final `id` tie-break makes the comparator **consistent with equals** for distinct ids.
  Without it, a `TreeSet<Task>` would silently drop one of two tasks that tie on every other field.

### 5. Why visibility is memoized per call, not stored
- Visibility depends on ancestors that might be added **later**, so it can't be decided in
  `addTask`.
- A memo created on each call costs O(n), with each task computed once, and can never go stale.
- Storing visibility across calls would mean invalidating every descendant whenever any task
  changes.

### 6. Why walking up the parent chain is enough to reject cycles
- Every task already stored passed the same check, so the existing parent links form a forest.
- A new task adds exactly one edge (to its parent). A new cycle must therefore include that edge,
  which means walking up from the parent eventually reaches the new task's own id.
- The walk stops at `0` or at a parent that hasn't been added yet. A later arrival of that parent
  runs its own check.

### 7. Kahn's algorithm with a priority queue (not DFS topological sort)
- DFS gives *a* valid order but can't honour "among ready tasks, pick by the comparator".
- Kahn's algorithm keeps an explicit **ready set**. Making it a `PriorityQueue` ordered by the
  comparator gives the required order in O((V + E) log V).
- Cycle detection is free: whatever is left unscheduled is in or behind a cycle.

### 8. Blocked tasks: the in-degree trick and the `toBlock` pass
- A dependency that is filtered out but not done **counts toward in-degree and is never
  decremented**. The blocked task never becomes ready, and its dependents never do either, so
  transitive blocking comes for free.
- **The problem:** after Kahn's algorithm, blocked tasks and tasks in a cycle both look the
  same (in-degree > 0), but only a cycle should throw.
- **`toBlock`:** tasks with a directly blocking dependency are seeded into it. A BFS over the
  dependents map marks everything blocked transitively. `stuck = pending - scheduled - blocked`;
  only a non-empty `stuck` is a real cycle.
- Without it, A7 (waiting on bob's task) would be reported as `Dependency cycle involving [7]`.

### 9. Why print methods return `List<String>`
- `System.out.println` can't be asserted on in a JUnit test. Returning lines makes every format
  rule testable, and the demo prints them.

### 10. Complexity summary

| Operation | With `List<Task>` storage | With `Map<Integer, Task>` storage |
|---|---|---|
| `addTask` (duplicate check, cycle walk) | O(n + depth × n) | O(1 + depth) |
| `printTasks` (Part 3) | O(n²) for the parent lookups | O(n log n) |
| `printTaskTree` | O(n²) to find roots, then O(n log n) | O(n log n) |
| `scheduleOrder` | O((V + E) log V + E × n) | O((V + E) log V) |

The `List` version is the quickest to write in an interview. Say out loud that switching to a map
removes every O(n) lookup.

---

## Follow-up discussion questions (with answers)

**1. Immutable records vs. updates: how do you mark a task done or reassign it?**
Create a new record with the changed field and replace it in the single source of truth
(`Map<Integer, Task>`). If other structures hold ids, nothing else changes. If they hold `Task`
objects, each one must be updated. Changing the parent also moves the id in the children index
and repeats the cycle check. Print and schedule results are computed per call, so nothing cached
goes stale.

**2. Read-heavy workload: would you keep tasks pre-sorted, for example in a `TreeSet<Task>`?**
Yes, if `printTasks` dominates. A `TreeSet` ordered by the comparator gives O(log n) inserts and an
O(n) ordered walk. The risks: the comparator must never return 0 for two different tasks (the `id`
tie-break guarantees that), and an update must **remove the old record before inserting the new
one**. If you mutate a field the comparator uses while the object is in the set, the tree is
corrupted. Records make that mistake impossible. Ancestor visibility still needs the per-call pass.

**3. Keeping links consistent: if the input also carries `childTaskIds` and it disagrees with
`parentTaskId`, which wins?**
Pick one source of truth, normally `parentTaskId`, because each child has exactly one parent and
it can be checked locally. Derive the children index from it. Report any disagreement: reject the
record with an `IllegalArgumentException`, or log a warning and ignore `childTaskIds`. State the
policy before coding. Never store both and hope they agree.

**4. A changing schedule: tasks are added, completed or reprioritized while the scheduler runs.
How do you update the ready queue without recomputing everything?**
Keep the in-degree map, the dependents map and the ready `PriorityQueue` as live state.
- **Completing a task:** decrement each dependent's in-degree, and push any that reach 0.
- **Adding a task:** compute its in-degree from dependencies that aren't done yet, and push it if
  it's 0.
- **Reprioritizing:** `PriorityQueue` has no decrease-key, so remove and reinsert (O(n)), or use a
  `TreeSet` (O(log n)), or push a new entry and skip stale ones when they are polled (lazy
  deletion, keyed by a version number).
- **A new dependency that creates a cycle:** check reachability from the new dependency to the task
  before accepting it.

**5. Working with AI: how do you make sure no rule in a long spec is dropped?**
Turn the spec into a numbered checklist before prompting, with one test per rule (the `-1`
ordering, the id tie-break, both formats, a missing parent, each validation row). Ask for one part
at a time, then review the generated code line by line against the checklist. Run the given
examples **plus** an out-of-order variant. The filtered-parent case is exactly the kind of rule an
assistant skips when it only sees the happy path.

---

## Rubric

| Signal | Hire | Strong hire |
|---|---|---|
| Spec fidelity | The main test passes | Every rule has a test: the `-1` ordering, the id tie-break, both formats, a missing parent |
| Modeling | A task class and a list | An immutable `record`, a map by id, a children index of ids, a single comparator and validator |
| Validation | Some null checks | One `validateTask` with a stated policy per bad input, including `createdDate <= 0` and duplicates |
| Hierarchy | A recursive print | Orphans treated as roots, ancestor propagation with memoization, out-of-order arrival, cycles rejected at add time |
| Scheduling | Sorts by priority | Kahn's algorithm with a priority queue, blocked vs. cycle told apart, priority inversion explained |
| Pace and communication | Long discussions | Assumptions as one-line comments, 2 of 3 parts done, edge cases added proactively |
