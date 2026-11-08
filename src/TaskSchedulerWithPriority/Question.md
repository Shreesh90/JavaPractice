# Task Scheduler with Dependencies, Priorities and Filters

**Company:** Rippling | **Role:** SDE2 | **Round:** Technical (coding) | **Difficulty:** Medium | **Time:** ~45 min

---

## 1. Problem Statement

At Rippling, onboarding workflows are sets of tasks such as "create email account", "provision laptop", "enroll in benefits" and "assign payroll". Some tasks depend on others, and some matter more than others.

Build a scheduler that decides **the order in which tasks are output**.

Each task has:

| Field | Type | Notes |
|---|---|---|
| `id` | String | unique |
| `dependencies` | List of ids | tasks that must be output first |
| `priority` | int | larger = more important (to be confirmed) |
| `attributes` | Map<String,String> | e.g. `team=IT`, `type=manual`, `owner=alice` |

### Part 1: Dependencies first
Output every task so that it appears only after all of its dependencies.

### Part 2: Priority
When several tasks are ready at the same time, choose the next one by priority. The exact rule is to be settled with the interviewer.

### Part 3: Filters
The caller can pass filter requirements (e.g. `team == IT`, `NOT type == manual`). Filtered-out tasks must not appear in the output, but the ordering guarantees for the remaining tasks must still hold.

### Example

| id | deps | priority | attributes |
|----|------|----------|------------|
| A | [] | 1 | team=IT |
| B | [A] | 5 | team=IT |
| C | [A] | 3 | team=HR |
| D | [B, C] | 9 | team=HR |
| E | [] | 2 | team=Finance |

**Expected output with the agreed rules: `E, A, B, C, D`**

Trace (heap shown after each poll):

| Step | Poll | Releases | Ready heap after |
|---|---|---|---|
| 1 | E (2) | none | [A] |
| 2 | A (1) | B, C | [B(5), C(3)] |
| 3 | B (5) | D's indegree 2 -> 1 | [C] |
| 4 | C (3) | D's indegree 1 -> 0 | [D] |
| 5 | D (9) | none | [] |

---

## 2. Clarifying Questions and Agreed Rules

A strong candidate asks these up front and writes the answers down as an explicit checklist.

| # | Question | Agreed rule |
|---|---|---|
| 1 | Is a higher number more important? | Yes, higher priority first among **ready** tasks |
| 2 | How are ties broken? | (1) priority desc, (2) original dependency count asc, (3) id asc. This is a **total order**, so output is deterministic |
| 3 | Does a high-priority task pull its dependencies forward? | **Open design question**, see section 9. Base version: no, dependencies are hard constraints and priority only orders ready tasks |
| 4 | What if a filter removes a task that others depend on? | The removed task still gates its dependents (see section 6). Filter changes what is *shown*, not what must happen first |
| 5 | Multiple filters: AND or OR? | Composable: caller chooses with `and` / `or` / `negate` |
| 6 | Filter before or after ordering? | After the graph is built (applied at output) |
| 7 | Cycle? | Throw `IllegalStateException` naming the stuck tasks |
| 8 | Unknown dependency id? | Throw `IllegalArgumentException` |
| 9 | Duplicate task id? | Throw `IllegalArgumentException` |
| 10 | Repeated dependency in one task's list? | Dedupe silently (set semantics) |
| 11 | Flat list or parallel stages? | Flat list. Stages are a follow-up |
| 12 | Empty input? | Empty output |

---

## 3. Approach

**Kahn's algorithm (BFS topological sort) with a priority queue as the ready set.**

1. Validate input and build `byId`.
2. Build the reverse graph (`dependents`) and the `indegree` map, using deduplicated dependency sets.
3. Push every task with `indegree == 0` into a priority queue ordered by the comparator.
4. Repeatedly poll the best ready task, append it to `out`, and decrement the indegree of each dependent. When one reaches 0, push it.
5. If `out.size() != input.size()`, some tasks were never released, so there is a cycle.

### Why sort-then-bucket-by-priority is wrong
Sorting topologically first and then re-ordering by priority lets a high-priority task (D, priority 9) jump ahead of its low-priority dependencies. Priority must only compete among tasks that are **ready**, so the selection structure has to be updated as tasks are released. Hence a single heap inside the Kahn loop, not one queue per priority level.

---

## 4. Data Structures

| Structure | Type | Purpose | Cost |
|---|---|---|---|
| `byId` | `HashMap<String, Task>` | validation, duplicate-id and unknown-dependency detection | O(V) build |
| `deps` | `HashMap<String, Set<String>>` | deduplicated dependencies, original count for the comparator | O(V + E) |
| `dependents` | `HashMap<String, List<Task>>` | reverse edges: who to release when a task is output | O(V + E) |
| `indegree` | `HashMap<String, Integer>` | number of unmet dependencies per task | O(V) |
| `ready` | `PriorityQueue<Task>` | tasks whose dependencies are all done, ordered by the comparator | O(log V) per push/poll |
| `out` | `ArrayList<Task>` | the final order | O(V) |

**Key by id, not by `Task` object.** Keying by object depends on `equals`/`hashCode`, and it hides unknown-id and duplicate-id errors. Keying by id makes both checks trivial.

### Comparator (a total order)

```java
Comparator<Task> cmp = Comparator
        .comparingInt(Task::getPriority).reversed()          // 1. higher priority first
        .thenComparingInt(t -> deps.get(t.getId()).size())   // 2. fewer ORIGINAL dependencies first
        .thenComparing(Task::getId);                         // 3. id: ties can never happen
```

- `.reversed()` must come right after the first level, otherwise it flips the whole chain.
- Level 2 reads the original count from the deduplicated `deps` map. The `indegree` map is decremented as the loop runs, so it cannot be used here (a ready task always has remaining indegree 0).
- Defence for level 2: a task with fewer dependencies is cheaper to schedule and unblocks sooner. Any defensible rule is acceptable if you can argue for it. An alternative is "number of tasks it unblocks".
- Level 3 is what makes output identical on every run. `PriorityQueue` does not guarantee order among equal elements.

---

## 5. Reference Solution (Java)

`Task` here exposes `getId()`, `getPriority()`, `getDependencies()` (list of **ids**) and `getAttributes()`.

```java
import java.util.*;
import java.util.function.Predicate;

public final class TaskScheduler {

    private final List<Task> schedule;   // immutable after construction

    public TaskScheduler(List<Task> input) {
        this.schedule = List.copyOf(computeSchedule(input));   // defensive, immutable copy
    }

    private static List<Task> computeSchedule(List<Task> input) {
        // 1. Validate and index by id
        Map<String, Task> byId = new HashMap<>();
        for (Task t : input) {
            if (byId.putIfAbsent(t.getId(), t) != null) {
                throw new IllegalArgumentException("Duplicate task id: " + t.getId());
            }
        }

        // 2. Build deduplicated deps, reverse graph and indegrees
        Map<String, Set<String>> deps = new HashMap<>();
        Map<String, List<Task>> dependents = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();

        for (Task t : input) {
            Set<String> d = new LinkedHashSet<>(t.getDependencies());   // dedupe
            for (String depId : d) {
                if (!byId.containsKey(depId)) {
                    throw new IllegalArgumentException(
                            "Task " + t.getId() + " depends on unknown task " + depId);
                }
                dependents.computeIfAbsent(depId, k -> new ArrayList<>()).add(t);
            }
            deps.put(t.getId(), d);
            indegree.put(t.getId(), d.size());
        }

        // 3. Ready set ordered by the total-order comparator
        Comparator<Task> cmp = Comparator
                .comparingInt(Task::getPriority).reversed()
                .thenComparingInt(t -> deps.get(t.getId()).size())
                .thenComparing(Task::getId);

        PriorityQueue<Task> ready = new PriorityQueue<>(cmp);
        for (Task t : input) {
            if (indegree.get(t.getId()) == 0) ready.add(t);
        }

        // 4. Kahn's loop
        List<Task> out = new ArrayList<>(input.size());
        while (!ready.isEmpty()) {
            Task t = ready.poll();
            out.add(t);                                   // emit when polled, not when enqueued
            for (Task n : dependents.getOrDefault(t.getId(), List.of())) {
                if (indegree.merge(n.getId(), -1, Integer::sum) == 0) ready.add(n);
            }
        }

        // 5. Cycle detection
        if (out.size() != input.size()) {
            Set<String> emitted = new HashSet<>();
            for (Task t : out) emitted.add(t.getId());
            List<String> stuck = new ArrayList<>();
            for (Task t : input) if (!emitted.contains(t.getId())) stuck.add(t.getId());
            throw new IllegalStateException("Cycle detected among tasks: " + stuck);
        }
        return out;
    }

    /** Filters the precomputed schedule; relative order of kept tasks is preserved. */
    public List<Task> filter(Predicate<Task> filter) {
        return schedule.stream().filter(filter).toList();
    }
}
```

Self-dependency (A depends on A) gives A an indegree of 1 that is never released, so it is reported as a cycle.

---

## 6. Filters

### Design decision
Take A -> B -> C (C depends on B, B depends on A) and a filter that removes B.

| Option | Behaviour | Verdict |
|---|---|---|
| (a) | C is output and still comes after A | **Chosen** |
| (b) | C is dropped too | Surprising: the caller asked to hide B, not C |
| (c) | B is output anyway | Violates the filter |

Output for the example: `A, C`.

### Where the filter applies
Build the **full graph first**, run the full schedule, then filter the result. Equivalently, filter at poll time: always release dependents, but only emit if the predicate passes. Both give the same relative order for kept tasks.

- Chains of removed tasks of any depth work with no special handling, because the graph stays intact.
- Filtering *before* the sort would force you to bridge edges (A -> C) across every removed task, which is error-prone for multi-step chains.
- Trade-off to state out loud: a removed task still occupies its slot in the schedule, so it can delay its dependents relative to other ready tasks. For onboarding, a filter changes what is shown, not what must happen first.

### Cycle check with filters
If you filter at poll time, count **every polled task** (`processed++`), not just emitted ones. Comparing `out.size()` to the total would make every filtered run look like a cycle. With the filter-at-output design above this does not arise.

### Composable representation
`Predicate<Task>` already supports `and`, `or` and `negate`, so new filter kinds never touch the scheduler:

```java
Predicate<Task> it     = t -> "IT".equals(t.getAttributes().get("team"));
Predicate<Task> high   = t -> t.getPriority() >= 5;
Predicate<Task> manual = t -> "manual".equals(t.getAttributes().get("type"));

scheduler.filter(it.and(high.negate()));          // IT tasks, excluding priority >= 5
scheduler.filter(it.or(manual));
scheduler.filter(t -> true);                      // no filtering
```

---

## 7. Complexity

| | Cost |
|---|---|
| Graph build | O(V + E): each task once, each dependency once (not V x E) |
| Kahn edge releases | O(E) |
| Heap operations | O(V log V): each task pushed once and polled once, heap holds at most V entries |
| **Total time** | **O(E + V log V)** |
| Space | O(V + E) |
| `filter(...)` | O(V) per call |

---

## 8. Edge Cases and Tests

| Case | Expected |
|---|---|
| Example above | `E, A, B, C, D` |
| Same example, input order reversed (D first) | Same output (input order must not matter) |
| Empty input | Empty list |
| Single task | That task |
| No dependencies at all | Pure priority order, ties by dep count then id |
| Equal priority, equal dep count (E and F, both priority 2, no deps) | E before F, decided by id (level 3) |
| Dependency shared by several tasks | Released once, enqueues each dependent exactly when its last dependency finishes |
| High priority blocked by low-priority dependency | Dependency first (D never before B and C) |
| Cycle A <-> B | `IllegalStateException` listing A and B |
| Self-dependency | `IllegalStateException` |
| Unknown dependency id | `IllegalArgumentException` |
| Duplicate task id | `IllegalArgumentException` |
| Same dependency listed twice | Treated as one dependency, counts and order correct |
| Filter removes middle of chain (A -> B -> C, remove B) | `A, C` |
| Filter removes two consecutive tasks in a chain | Remaining tasks still ordered correctly |
| Filter removes everything | Empty list, no exception |
| Filter keeps everything | Identical to unfiltered output |
| Determinism | Run 1000 times with shuffled input order, output identical every time |

### Pitfalls found during the mock round
- **Emitting on enqueue instead of on poll.** Works with a FIFO queue by accident, wrong with a heap.
- **Counting each edge twice in `indegree`** (both `merge` and `put`): dependents never reach 0 and nothing downstream is emitted.
- **`graph.get(task)` returns null** for tasks with no dependents, which throws an NPE. Use `getOrDefault`.
- **Global "seen before" dedupe** (`uniqueTasks.contains(dep)`) used to skip counting an edge: wrong, depends on input order and can leave a task with no `indegree` entry. Dedupe the dependency list **per task**.
- **Forgetting `processed++`**, so the cycle check fires on every valid input.
- **Comparator with only priority:** ties come out in heap-internal order, so output is non-deterministic.
- **`return null` / `System.out.println` on cycle:** use an exception that names the stuck tasks.
- **Unknown dependency silently scheduled** as if it were a real task because dependencies were added to the task set while walking edges.
- **Cycle check against `out.size()` after adding filtering** breaks, since `out` is deliberately smaller.

---

## 9. Follow-up: Should a high-priority task pull its dependencies forward?

In the example, D has priority 9 but is output last, and E (priority 2, blocks nothing) runs before A (priority 1, on the critical path to D). This is **priority inversion**.

**Option 1: keep it as is.** Priority only orders ready tasks and dependencies are hard constraints. Simple, easy to reason about, easy to test.

**Option 2: priority inheritance.** A task's *effective priority* is the max of its own priority and the effective priority of anything that depends on it.

- Compute in one pass over the **reverse** of the topological order, propagating each task's effective priority to its dependencies: O(V + E). Requires the cycle check first.
- Example: A, B, C, D all get effective priority 9, E stays 2. Output becomes `A, B, C, D, E`.
- Comparator needs an extra level: effective priority desc, then own priority desc (so B(5) beats C(3)), then dep count, then id.
- Costs: a low-priority task can appear early, which can surprise readers, and there are now two notions of priority to explain and test.

**Recommendation for onboarding:** lean towards Option 2, because priority expresses how much the business cares about the outcome, and a critical task's prerequisites are equally critical. Confirm with the product owner before building it.

### Other follow-ups
1. **Parallel stages.** Run Kahn by level: every task with indegree 0 forms stage 1, remove them, repeat. Within a stage, sort by the comparator. Output is a `List<List<Task>>`.
2. **Dynamic updates.** Tasks added and priorities changed while running (see section 10).
3. **Staying correct with an AI assistant.** Write the rules checklist first (section 2), turn each rule into a test before generating code, and review generated code against the checklist line by line.

---

## 10. Concurrency

### 10.1 The base design is thread-safe by construction
- All graph structures (`byId`, `deps`, `dependents`, `indegree`, `ready`) are **local variables** of `computeSchedule`. Nothing mutable is shared across calls.
- The schedule is built **once in the constructor**, stored in a `final` field as an **immutable** list (`List.copyOf`).
- `filter(...)` only reads that list and builds a new one, so any number of threads can call it concurrently with no locks.
- Safe publication: `final` fields plus constructor-complete construction guarantee other threads see a fully built schedule.
- The input list is defensively copied. Otherwise a caller mutating their list afterwards would make the schedule stale with no signal.
- `Task` must be **immutable** (final fields, unmodifiable list and map copies), otherwise a mutated priority or dependency list silently invalidates the precomputed order.
- Predicates passed by callers must be stateless and side-effect free. The scheduler cannot enforce this, so document it.
- Make the class `final` and `computeSchedule` `private static`, so a subclass cannot override it while the object is half-built.

### 10.2 When tasks and priorities change at runtime
Reads are frequent and updates are rare, so use **copy-on-write with an atomic snapshot swap**.

```java
public final class DynamicTaskScheduler {
    private final AtomicReference<TaskScheduler> current;
    private final Object writeLock = new Object();
    private List<Task> source;                       // guarded by writeLock

    public DynamicTaskScheduler(List<Task> initial) {
        this.source = List.copyOf(initial);
        this.current = new AtomicReference<>(new TaskScheduler(source));
    }

    public List<Task> filter(Predicate<Task> p) {
        return current.get().filter(p);              // lock-free read of a consistent snapshot
    }

    public void update(UnaryOperator<List<Task>> change) {
        synchronized (writeLock) {                   // serialise writers
            List<Task> next = List.copyOf(change.apply(source));
            TaskScheduler rebuilt = new TaskScheduler(next);   // validates; throws before swapping
            source = next;
            current.set(rebuilt);                    // atomic publish
        }
    }
}
```

- Readers always see **one consistent schedule** (old or new), never a half-updated one.
- Writers are serialised, and validation (cycle, unknown id) happens **before** the swap, so a bad update never corrupts the live schedule.
- Cost: a full rebuild O(E + V log V) per update. Fine for modest sizes and rare writes.
- If updates become frequent and V is large, consider **incremental** maintenance (adjust only the affected subgraph, then re-run priority inheritance for ancestors of a changed task). This is much harder to get right, so justify it with measurements first.
- Alternative: `ReentrantReadWriteLock` with a mutable schedule. It works, but readers block during rebuilds, so the snapshot approach is preferred.

### 10.3 When the tasks are actually *executed* in parallel
The scheduler produces an order. If a worker pool executes tasks concurrently, a dependency is only satisfied when the dependency has **finished**, not merely been emitted.

- Keep per-task remaining-dependency counters as `AtomicInteger`, in a `ConcurrentHashMap<String, AtomicInteger>`.
- When a task finishes, each worker does `decrementAndGet()` on each dependent. Exactly one thread sees the value hit 0, and that thread pushes the dependent into a `PriorityBlockingQueue<Task>` using the same comparator.
- Worker threads `take()` from the queue and run the task. Priority is now best-effort among tasks that are ready at that instant, since other workers are already running lower-priority tasks.
- Track completion with a `CountDownLatch(V)` or a `CompletableFuture` per task. For failures, decide a policy (stop all, skip dependents, retry) and document it.
- Alternative using stages: compute stages with Kahn's levels, run each stage with `invokeAll` on an executor, and wait before the next stage. Simpler, but a slow task blocks the whole next stage.
- Pitfalls: dependents released twice (use the atomic decrement as the single gate), a task enqueued before its dependencies are *complete* (decrement on completion, not on start), and `PriorityBlockingQueue` ordering being unstable for equal elements (the id level in the comparator solves it).

### 10.4 Summary

| Scenario | Technique | Why |
|---|---|---|
| Many threads read the schedule | Immutable list, `final` field | No locking needed |
| Schedule changes occasionally | `AtomicReference` snapshot swap + writer lock | Consistent reads, validated writes |
| Tasks run in parallel | `AtomicInteger` counters + `PriorityBlockingQueue` | Exactly-once release of each dependent |
| Callers pass filters | Stateless predicates, documented | Cannot be enforced by the scheduler |

---

## 11. Scoring Rubric (what the interviewer looks for)

| Area | Strong signal |
|---|---|
| Requirements | Clarifies up front, writes rules as a checklist |
| Algorithm | Kahn's with heap as ready set, can explain why sort-then-bucket is wrong |
| Determinism | Total-order comparator ending in id |
| Robustness | Handles cycles, unknown ids, duplicate ids, duplicate deps, empty input explicitly |
| Filters | Clear policy for removed dependencies, composable predicates |
| Complexity | States O(E + V log V) correctly and explains the V vs E split |
| Testing | Covers each rule and the interactions between them |
| Design sense | Takes a position on priority inversion and defends it |
| Concurrency | Immutability first, snapshot swap for updates, atomic counters for parallel execution |
| Communication | Thinks out loud, traces examples, fixes bugs methodically |