# Spreadsheet: Raw and Computed Views (Rippling SDE2, onsite LLD coding)

> A multi-part live-coding round. Each part builds on the previous one. Keep everything in memory and use only the Java standard library.

---

## Problem framing

> "We're building a lightweight spreadsheet for an internal HR tool. Users type numbers or formulas into cells. The UI has two tabs: a **raw view** that shows exactly what the user typed (`=A1+B2`), and a **computed view** that shows the resulting numbers. We'll start with something very simple and keep adding to it. Keep everything in memory. I care about how you model cells and formulas, and how you keep computed values correct when things change."

**What makes or breaks this round**
- **Modeling a cell's content:** a number vs. a formula, and the raw text vs. its computed value. Most candidates mix these together.
- **The dependency graph:** both directions, and **replacing** dependencies when a formula changes.
- **Recomputing correctly:** each affected cell once, in the right order, without stale values.

### Clarifying questions to ask before you code

| Question | Rule for this run |
|---|---|
| Grid size? | 26 columns `A`–`Z`, rows `1..N` (1-based), with N given to the constructor |
| Cell names? | Column letter plus row number: `A1`, `Z1000`. Upper case only. |
| Empty cells? | Value `0`. Raw view shows `""`. |
| Value type? | Integers. Store and compute as `long` to avoid overflow. |
| Integer literals in formulas? | `0`–`100000` |
| Operators? | `+` and `-`, evaluated left to right (no precedence needed). `SUM(...)` from Part 3. |
| Whitespace in formulas? | Ignore it |
| Invalid cell name or bad formula syntax? | `IllegalArgumentException`, and the sheet stays unchanged |
| Cycles? | Promised not to happen until Part 5 |
| Single-threaded? | Yes |

### Overview of the parts

| Part | Adds | Core skill tested |
|---|---|---|
| 1 | Set integer cells, evaluate a one-off expression | Cell addressing, tokenizing |
| 2 | Stored formulas, raw and computed views | Modeling a cell's content |
| 3 | `SUM` with cells, ranges and literals | Extending the parser without rewriting it |
| 4 | Cache computed values, recompute on write | Dependency graph, topological order |
| 5 | Reject cycles and report the path | Graph search, no partial state |
| 6 | Atomic batches and undo | Command/Memento, validate then commit |

---

## Part 1: Cells and one-off expressions (~15 min)

```java
Spreadsheet(int rows);
void setCell(String cell, int value);
void resetCell(String cell);              // back to empty (0)
long getValue(String expression);         // e.g. "=A1+B2", "=A1+7", "=5+6"
```

**Rules**
- `getValue` evaluates an expression **once** and returns the result. **Nothing is stored.**
- The expression must start with `=`. Each operand is a cell reference or an integer from 0 to 100000.
- Empty cells count as 0.
- A cell id is one column letter followed by a 1-based row number. The column is the first character, and the rest is the row.

**Test cases**
```
Spreadsheet s = new Spreadsheet(1000);
s.setCell("A1", 10); s.setCell("B2", 5);
s.getValue("=A1+B2")   -> 15
s.getValue("=A1+7")    -> 17
s.getValue("=5+6")     -> 11
s.getValue("=C3+1")    -> 1      C3 is empty
s.getValue("=A1-B2-1") -> 4      left to right
s.resetCell("A1");
s.getValue("=A1+B2")   -> 5

s.setCell("AA1", 1)            -> IllegalArgumentException   column out of range
s.setCell("A0", 1)             -> IllegalArgumentException   rows are 1-based
s.setCell("A1001", 1)          -> IllegalArgumentException   row out of range
s.getValue("A1+B2")            -> IllegalArgumentException   missing '='
s.getValue("=A1+100001")       -> IllegalArgumentException   literal too large
s.getValue("=A1++B2")          -> IllegalArgumentException   bad syntax
```

**What the interviewer checks**
- **A `CellRef` value object** (`record CellRef(int row, int col)`) with one `parse("A12")` method. Cell-id parsing shouldn't be scattered across the code.
- **Storage choice, with the trade-off stated:** a sparse `Map` or a pre-allocated 2D array. Compare memory for a mostly empty sheet with O(1) indexing.
- **Tokenizing:** `+` and `-` are handled together, left to right. Think ahead: will the approach survive `SUM(...)` in Part 3?

---

## Part 2: Stored formulas, plus raw and computed views (~15 min)

Cells can now hold **formulas** that stay attached to the cell.

```java
void setCell(String cell, String input);   // "42" or "=A1+B1"
String getRaw(String cell);                // exactly what was typed; "" if empty
long getComputed(String cell);             // the current value
```

**Rules**
- An input starting with `=` is a formula. Anything else must be an integer.
- Setting a number on a formula cell **replaces the formula**.
- Formulas can reference other formula cells (chains).
- **Compute on read** is fine for this part: evaluate recursively when `getComputed` is called.
- Resetting a cell makes its raw value `""` and its computed value `0`.

**Test cases**
```
s.setCell("A1", "10"); s.setCell("B1", "5");
s.setCell("C1", "=A1+B1");
s.getRaw("C1")       -> "=A1+B1"
s.getComputed("C1")  -> 15
s.setCell("A1", "20");
s.getComputed("C1")  -> 25        the formula persists
s.setCell("D1", "=C1+1");
s.getComputed("D1")  -> 26        chain through a formula cell
s.setCell("C1", "7");
s.getRaw("C1")       -> "7"       the formula was replaced
s.getComputed("D1")  -> 8
s.getRaw("Z9")       -> ""
s.getComputed("Z9")  -> 0
```

**What the interviewer checks**
- **Raw vs. computed are separate concepts.** The raw view must return exactly what was typed, even after the values it depends on change.
- **Where parsing happens:** on write (once) or on read (every time). State the cost of each.
- **Invalid input is rejected on write,** so the sheet never holds content that can't be evaluated.
- **Discuss:** compute on read costs O(size of the dependency chain) per read. When does that become a problem? (Part 4 answers it.)

---

## Part 3: SUM with ranges (~10 min)

```
=SUM(A1, A1:B2, 5)
=SUM(A1:A2)-B1+5
```

**Rules**
- `SUM(arg, arg, ...)`: each argument is a cell, a rectangular range `X:Y`, or an integer.
- **References count with multiplicity.** If `A1` appears twice, it's added twice.
- A range covers the full rectangle from the min row/column to the max row/column: `A1:B2` means A1, B1, A2, B2.
- A reversed range like `B2:A1` means the same rectangle.
- `SUM(...)` can appear as a term inside `+` and `-`.
- A cell inside a range can itself hold a formula, and it must be evaluated.
- The sum logic lives in **its own class** that takes a list of arguments.
- Nested `SUM` is **out of scope** for this part.

**Test cases**
```
A1=1, A2=2, B1=3, B2=4
s.setCell("C1", "=SUM(A1, A1:B2)")
s.getComputed("C1") -> 11      1 + (1+2+3+4)
s.setCell("A1", "10")
s.getComputed("C1") -> 29      10 + (10+2+3+4)
s.setCell("C2", "=SUM(A2:A2)-B1+5")
s.getComputed("C2") -> 4       2 - 3 + 5
s.setCell("C3", "=SUM(B2:A1)")
s.getComputed("C3") -> 19      the same rectangle as A1:B2
s.setCell("B2", "=A2+1")
s.getComputed("C3") -> 18      a formula cell inside the range
```

**What the interviewer checks**
- **`SUM` slots in without rewriting** the `+`/`-` handling. Splitting on `+`/`-` inside `SUM(...)` brackets is the classic bug.
- **The sum class is decoupled from storage.** It receives a way to read a cell's value, not the whole spreadsheet.
- **Ranges:** stored as two corners vs. expanded into every cell. Expanding `A1:Z100000` costs a lot of memory, but Part 4 needs the individual cells for the dependency graph. Discuss when each is worth it.

---

## Part 4: Cache values and recompute on write (~20 min)

Reads now happen **far more often** than writes. **`getComputed` must be O(1)**, or at least must not recompute the same cell again and again.

**Rules**
- Each cell caches its computed value.
- When a cell changes, every cell that depends on it, **directly or transitively**, must reflect the change.
- Each affected cell is recomputed **exactly once**, and only **after** the cells it depends on.
- Changing a cell's formula **replaces** its dependencies. The old ones must no longer trigger recomputes.
- Resetting a cell is just another change, and its dependents must update too.

**Test cases** (the diamond)
```
A1 = 1
B1 = "=A1+1"          -> 2
C1 = "=A1+B1"         -> 3
D1 = "=B1+C1"         -> 5

set A1 = 5   -> B1=6, C1=11, D1=17
             recompute order: B1, then C1, then D1 (each once)

set C1 = "100"         C1 no longer depends on A1 or B1
set A1 = 7   -> B1=8, C1=100 (not recomputed), D1=108

resetCell("A1") -> B1=1, D1=101
```

Performance check: build a chain of 25 cells where each is `=SUM(prev, prev)`. Reading the last cell must not take 2²⁴ evaluations.

Add a test hook, for example `recomputeCount()`, so a test can show that each cell was recomputed exactly once.

**What the interviewer checks**
- **Two maps:** `dependsOn` (cell → cells it references) and `dependents` (cell → cells that reference it). Explain why you need both.
- **Replacing a formula:** the cell is removed from each **old** dependency's `dependents` set before being added to the new ones. Forgetting this is the most common bug.
- **Eager vs. lazy:**
  - *Eager:* recompute all affected cells on write, in topological order. Reads are O(1).
  - *Lazy:* on write, walk `dependents` and mark each cached value as not computed (`null`). On read, compute it if needed and store it.
  - Know which one you picked, and why it handles the diamond.
- **Why a plain BFS gives a stale value** in the diamond under eager recompute.
- **`null` vs. `0`:** a formula can legitimately evaluate to 0, so 0 can't mean "not computed".
- **Multiplicity:** `SUM(A1, A1)` depends on A1 once in the graph, but A1 counts twice in the value. Keep the **graph edge set** separate from the **evaluation**.
- **Cost:** `setCell` is O(affected cells + edges).

---

## Part 5: Cycle detection and reporting (~10 min)

The promise of no cycles is removed.

```java
void setCell(String cell, String input);   // throws CycleException when the formula would create a cycle
```

**Rules**
- If a new formula would create a cycle, **reject it**: throw `CycleException`, which includes the cycle path, and leave the sheet **completely unchanged**.
- A self-reference is a cycle. A range that includes the cell itself is a cycle.

**Test cases**
```
C1 = "5"
B1 = "=C1+1"       -> 6
A1 = "=B1+1"       -> 7
set C1 = "=A1+1"   -> CycleException: C1 -> A1 -> B1 -> C1
getRaw("C1") -> "5", getComputed("A1") -> 7     unchanged

set D1 = "=D1+1"         -> CycleException: D1 -> D1
set E5 = "=SUM(A1:F9)"   -> CycleException: E5 is inside its own range
```

**What the interviewer checks**
- **When the check happens:** before anything is committed, not by updating and then rolling back half the state.
- **Which graph to search,** from where, and how the reported path comes out of that search.
- **Cost:** O(cells reachable from the new references).
- **The invalidation walk** from Part 4 can't loop forever even if the check has a bug: it keeps a visited set.
- **Discuss the alternative:** Excel accepts the cycle and shows an error value in the affected cells. What changes in the cache and the views?

---

## Part 6 (the locked Rippling extension): Atomic edits and undo

```java
void applyBatch(List<Edit> edits);   // record Edit(String cell, String input); all or nothing
void undo();                          // reverts the last successful setCell or batch
```

**Rules**
- A batch is **atomic**. If any edit is invalid or creates a cycle, **nothing** is applied, and the exception is thrown.
- Edits apply in order, and a later edit in the batch can overwrite an earlier one.
- Recompute **once at the end**, over everything the batch affected, not once per edit.
- `undo()` reverts one operation: a single `setCell` or a whole batch. It restores the **raw contents**, and computed values follow from them.
- Failed operations aren't added to the undo history.
- `undo()` with no history: does nothing, or throws. Decide which and say so.

**Test cases**
```
setCell("A1", "1")
setCell("B1", "=A1+1")                                     -> B1=2
applyBatch([("A1","10"), ("C1","=B1+A1")])                 -> A1=10, B1=11, C1=21
applyBatch([("A1","50"), ("B1","=B1+1")])                  -> CycleException
                                                              A1=10, B1=11, C1=21 (unchanged)
undo()   -> reverts the first batch: A1=1, B1=2, getRaw("C1")="", getComputed("C1")=0
undo()   -> reverts setCell B1: getRaw("B1")="", getComputed("B1")=0
```

**What the interviewer checks**
- **What goes into the undo history:** raw contents (before-images) vs. computed values. Computed values are derived data.
- **Undo reuses the normal write path,** so the graph and the cache stay consistent without special code.
- **Atomicity:** how the whole batch is validated before anything becomes visible.
- **The question to answer:** can a batch create a cycle midway and then break it with a later edit, for example setting `A1 = "=B1"` and then `B1 = "5"`? Checking only the final state allows it, and checking after each edit rejects it. Pick one and justify it.

---

## Follow-up discussion questions (with answers)

> How to answer: state **the specific issue**, then **the fix**, then **the trade-off**.

### 1. Compute on read vs. cache on write
**Q:** How does the read/write ratio decide between computing on read and caching on write? Is there a hybrid?

**A:**
- **Compute on read:** writes are O(1), and each read costs the size of the cell's dependency tree. Without memoization this is exponential on diamonds. It's fine when writes dominate and reads are rare.
- **Eager cache on write:** reads are O(1), and each write costs O(affected cells + edges) plus a topological sort. It's the right choice when reads dominate, as in a UI that redraws the computed view constantly.
- **Hybrid (lazy invalidation):** on write, mark the dependents dirty. On read, recompute a dirty cell once and cache it. Writes touch only the affected cells and do no evaluation, and reads are O(1) once warm. It handles diamonds without a topological sort, because a cell is computed the first time it's read after all its inputs are clean.
- **Trade-off:** lazy invalidation moves latency to the first read after a write, and a write that dirties many cells nobody views costs almost nothing. Eager recompute gives predictable reads but wastes work on cells nobody looks at.

### 2. Error values
**Q:** Add `#REF!` (a reference outside the grid), `#DIV/0!` (once `/` is added) and `#CYCLE!`. How do errors propagate to dependents, how does the computed view show them, and what type does a cell's value become?

**A:**
- A cell's value is no longer a `long`. It becomes a small sealed type: `Value = Number(long) | Error(ErrorKind)`.
- **Propagation:** any operator or `SUM` that receives an `Error` operand returns that error, using the first error left to right. A dependent of a `#CYCLE!` cell shows `#CYCLE!`.
- **The computed view** renders `Number` as digits and `Error` as its code. The raw view is unchanged.
- **Cycles:** instead of rejecting the formula, store it, mark every cell in the strongly connected component as `#CYCLE!`, and propagate that to their dependents.
- **Trade-off:** every evaluation path must handle `Error`, but the API stops throwing on writes, which matches spreadsheet UX.

### 3. A cell's type changes
**Q:** A cell goes from formula to number to empty. What has to be cleaned up each time?

**A:**
- **Formula → number:** remove the cell from each old dependency's `dependents` set, and clear its own `dependsOn` entry. Its cached value becomes the number. Its dependents must be recomputed or invalidated.
- **Number → empty:** delete the stored content (in sparse storage, remove the map entry). The value becomes 0. **Keep its `dependents` entry**, because other formulas still reference this cell and must update when it's set again.
- **Undo:** the history stores the previous raw text (`"=A1+B1"`, then `"5"`), so undoing re-runs the normal write path and rebuilds the edges.
- **The common bug:** deleting the `dependents` entry when a cell is cleared. Later writes to that cell then stop updating the formulas that reference it.

### 4. Operators and precedence
**Q:** Add `*`, `/` and parentheses. How does the parser change?

**A:**
- Left-to-right `+`/`-` splitting can't express precedence: `2+3*4` must be 14. Use **recursive descent**: `expr := term (('+'|'-') term)*`, `term := factor (('*'|'/') factor)*`, `factor := number | ref | SUM(...) | '(' expr ')'`. Shunting-yard is the other standard choice.
- Text-splitting code has to be rewritten. With an expression tree (AST), only the parser and one new `Binary` operator node change. Evaluation, dependency extraction and the graph stay the same.
- Add `/` by zero as a `#DIV/0!` error value (see question 2), and decide between integer division and `BigDecimal`.
- **Trade-off:** an AST takes more code up front, but it pays off on the first new operator.

### 5. Scale and collaboration
**Q:** 1M rows, a range like `SUM(A1:A1000000)`, and two users editing at once. What changes?

**A:**
- **Storage:** sparse `Map<CellRef, Cell>` instead of a 26M-slot array.
- **Large ranges in the graph:** don't add 1M edges. Store **range nodes**: the formula depends on the range `A1:A1000000`. When a cell changes, find the ranges that contain it with an interval index (for example a per-column interval tree).
- **Incremental sums:** when A5 changes from 3 to 7, the sum changes by +4. Apply the delta instead of re-summing 1M cells. This works for `SUM`, but not for `MIN`/`MAX`, which need a segment tree.
- **Concurrent edits:** route all writes through **a single writer per sheet** (an ordered queue, so each write sees a consistent graph). Or use operational transforms/CRDTs on the raw contents. Computed values are always derived on the server, never merged.
- **Trade-off:** range nodes and interval lookups add complexity, so introduce them only once range sizes actually hurt.

---

## Rubric

| Signal | Hire | Strong hire |
|---|---|---|
| Modeling | Cells store a string and an int | A `CellRef` value object, raw vs. computed clearly separated, input validated on write |
| Dependencies | Recomputes everything on write | Forward and reverse maps, replaced dependencies, each affected cell computed once |
| Correctness | Chains work | Diamond, replaced formulas, multiplicity, cycles rejected with no partial state |
| Extensibility | `SUM` bolted onto string parsing | `SUM` and new operators slot in, and undo reuses the write path |
| Trade-offs | Picks one approach | Explains compute on read vs. cache, eager vs. lazy, batch validation semantics |
