# Rippling SDE2 Phone Screen: Key-Value Store Mock Interview Summary

Language used: Java. Format: live coding, code must run, built incrementally.

---

## 1. The question

Design and implement an in-memory key-value datastore, built up in three stages:

| Part | Requirement |
|---|---|
| 1 | `set(key, value)`, `get(key)`, `delete(key)` |
| 2 | `begin()`, `commit()`, `rollback()`, including **nested** transactions |
| 3 | Multi-threaded use: each thread runs its own transactions safely |

Then verbal follow-ups: lost updates, concurrency control, durability.

**Interview rules reported for this round**
- The code has to run, or it is a reject. Build in small steps and run after each one.
- Choose the part 1 data structure with part 2 in mind, or you rewrite it.
- If you have seen the question before, say so and ask for another (the swap reported was currency conversion).
- Clarify requirements out loud and state trade-offs as you go.

---

## 2. Initial basic approach (Part 1)

**Decisions agreed up front**
- Generic `KVStore<K, V>`; keys must be hashable and immutable (proper `equals` / `hashCode`).
- Backed by the built-in `HashMap`. Re-implementing a hash table burns time that belongs to Part 2.
- `null` is **never** a valid key or value. `get` returns `null` for a missing key, and `delete` returns `true` only if something was removed.
- Null inputs are rejected with an exception. Silently ignoring them hides bugs.

**Complexity:** `set`, `get`, `delete` are O(1) average (worst case O(log n) with Java 8+ treeified buckets; key hashing costs O(key length)). Space O(n).

### Challenges in Part 1

| Challenge | Solution |
|---|---|
| `if (v != null)` in `set` silently dropped null writes | Throw instead, using `Objects.requireNonNull` on key and value |
| `@NotNull` annotation looked like validation | It is a compile-time hint only, and an external dependency. Use real runtime checks |
| Null key slipped through `set` (HashMap allows one) | Check the key in every method, before any branching |
| Field was package-private | `private final` |
| Tests were a single happy-path `get` | Add missing key, overwrite, delete present, delete missing, null rejected |

---

## 3. Part 2: transactions

### Design that worked
- Each transaction is an **overlay map holding only its changes**. `begin` creates an empty one.
- Open transactions live on a **stack** (`Deque<Map<K,V>>`). Nesting is strict, so only the top is ever committed or rolled back. No transaction IDs needed.
- `get` walks the layers **top to bottom**, then main. The first layer that contains the key wins.
- A delete writes a **tombstone** (a `null` value) into the top layer. Since `null` is never a real value, it is an unambiguous "deleted" marker.
- `rollback` pops the top layer.
- `commit` pops the top layer and:
    - if another transaction is still open: **merges into the parent with `putAll`**, tombstones copied as nulls
    - if it was the outermost: applies to main (`null` means remove, otherwise put)

### Challenges in Part 2 (found incrementally)

| # | Challenge | Why it failed | Solution |
|---|---|---|---|
| 1 | Copy the whole map on `begin` | O(n) time and memory per transaction | Overlay of changes only |
| 2 | Delete a key that exists only in main | Nothing in the empty overlay says "deleted", so `get` falls through and returns the old value | Tombstone (`null`) in the overlay; use `containsKey` to tell "tombstone" from "absent" |
| 3 | Second `begin` "against main" | Inner transaction must see the outer one's uncommitted writes, and inner commit must land in the outer layer | Stack of layers; inner commit merges into parent |
| 4 | `get` crashed on a miss inside a transaction | `poll()` loop ran off the end of the stack, then `ArrayDeque` rejected a null push | Iterate the deque (`for (Map m : stack)`), no mutation |
| 5 | `delete` inside a transaction mutated lower layers and called `mp.remove` directly | Rollback could not undo it; return value was wrong | `if (get(k) == null) return false;` then write a tombstone in the **top layer only** |
| 6 | Double delete returned `true` | A tombstone was skipped and the search fell back to main | Let `get` decide visibility: a tombstone ends the search |
| 7 | Outermost `commit` ran `mp.put(key, null)` | A tombstone for a key main never had landed as a stored null | `null` means `remove` (a no-op if absent); never put a null |
| 8 | Nested `commit` removed the tombstone from the parent | That un-hid the old value in main | Copy tombstones across with `putAll` |
| 9 | Null-value check regressed | A null in a transaction became a silent delete | `requireNonNull` on key and value at the top of `set` |
| 10 | `IllegalArgumentException` for no open transaction | The argument is fine, the state is wrong | `IllegalStateException` |

**Lesson:** several of these bugs hid each other (a buggy `delete` masked a buggy `commit`). Test each trace on its own, and write the failing test before the fix.

**Alternative design (mention, don't build):** an undo log. Writes go straight to main, and each transaction records previous values (first write wins). `get` is O(1), `rollback` is O(changes), outermost `commit` is O(1). It suits read-heavy single-writer stores, but dirty writes are visible, so it needs locking.

---

## 4. Part 3: multi-threading

### Design that worked
- **One transaction stack per thread**: `ThreadLocal<Deque<Map<K,V>>>` created with `ThreadLocal.withInitial(ArrayDeque::new)`. Layers are private, so they need no lock.
- Only the **main map is shared**, guarded by one lock object.
- Lock exactly where main is touched:
    - the final read of main in `get`
    - the no-transaction write in `set`
    - the no-transaction path of `delete`
    - the **whole outermost commit loop, as one block**
- Isolation given: uncommitted writes are invisible to other threads (no dirty reads).

### Challenges in Part 3

| Challenge | Why it matters | Solution |
|---|---|---|
| "Thread B should see thread A's uncommitted `a=1`" | That is a dirty read; if A rolls back, B acted on a value that never existed | B sees only committed state |
| One shared stack | B's `begin` stacks on A's layers; B's commit merges into A's transaction | Per-thread stack via `ThreadLocal` |
| "ConcurrentHashMap is enough" | Each `put` is safe, but a commit of keys `a` and `b` is two puts; a reader can see `a` new and `b` old (torn commit) | One lock around the whole commit loop |
| Lock placed inside the commit loop | Still allows a torn commit between iterations | Move `synchronized` outside the `for` |
| `get` read main without the lock | HashMap read racing a write can return stale or corrupt data | Lock the read of main |
| `delete` did check-then-act (`get`, then `remove`) | Two threads could both return `true` for one key | No-transaction path: `synchronized { return mp.remove(k) != null; }`; transaction path stays lock-free |
| Compile error iterating `st` | The field is a `ThreadLocal`, not a `Deque` | `st.get()` (read it once into a local variable) |

### Lock options discussed
| Tool | Behavior | Use when |
|---|---|---|
| `synchronized` | One thread at a time, automatic release | Simplest; fine for an interview |
| `ReentrantLock` | Same, explicit `lock()` / `unlock()` in `finally`; `tryLock` with timeout | You need timeouts or fairness |
| `ReentrantReadWriteLock` | Many readers, one exclusive writer | Read-heavy store; commit takes the write lock |

"Reentrant" means the same thread can re-acquire the lock without deadlocking itself.

### Thread-pool caution
A `ThreadLocal` stack outlives a request on a pooled thread. Make sure `commit` or `rollback` always runs, or the next task inherits leftover layers.

---

## 5. Final code (all three parts)

```java
package KVStore;

import java.util.*;

public class KVStore<K, V> {

    private final Map<K, V> mp = new HashMap<>();
    // Each thread gets its own private stack of overlay maps (null value = tombstone)
    private final ThreadLocal<Deque<Map<K, V>>> st = ThreadLocal.withInitial(ArrayDeque::new);
    private final Object lock = new Object();

    public void set(K k, V v) {
        Objects.requireNonNull(k);
        Objects.requireNonNull(v);
        if (st.get().isEmpty()) {
            synchronized (lock) { mp.put(k, v); }
        } else {
            st.get().peek().put(k, v);
        }
    }

    public V get(K k) {
        Objects.requireNonNull(k);
        for (Map<K, V> layer : st.get()) {                  // top -> bottom
            if (layer.containsKey(k)) return layer.get(k);  // null here = deleted, stop
        }
        synchronized (lock) { return mp.get(k); }
    }

    public boolean delete(K k) {
        Objects.requireNonNull(k);
        if (st.get().isEmpty()) {
            synchronized (lock) { return mp.remove(k) != null; }   // check and act in one step
        }
        if (get(k) == null) return false;
        st.get().peek().put(k, null);                        // tombstone in the top layer only
        return true;
    }

    public void begin() {
        st.get().push(new HashMap<>());
    }

    public void commit() {
        if (st.get().isEmpty()) throw new IllegalStateException("no open transaction");
        Map<K, V> curr = st.get().pop();
        if (!st.get().isEmpty()) {                           // nested: merge, keep tombstones
            st.get().peek().putAll(curr);
            return;
        }
        synchronized (lock) {                                // outermost: one atomic apply
            for (Map.Entry<K, V> e : curr.entrySet()) {
                if (e.getValue() == null) mp.remove(e.getKey());
                else mp.put(e.getKey(), e.getValue());
            }
        }
    }

    public void rollback() {
        if (st.get().isEmpty()) throw new IllegalStateException("no open transaction");
        st.get().pop();
    }
}
```

### Tests that were run (all passed)
- Part 1: get after set, delete present and missing, null value rejected
- Part 2: rollback restores a deleted key; nested commit does not leak past an outer rollback; a nested tombstone hides a parent value; double delete; commit and rollback with no transaction throw
- Part 3: another thread cannot see an uncommitted write; two threads committing different values to one key; **no torn commits** (four writers committing `a` and `b` together while a reader checks they match); racing deletes never both succeed

A passing concurrency test is evidence, not proof. The proof is the argument: every touch of shared state is under one lock, and everything else is private to one thread.

---

## 6. Follow-up discussion (verbal only, not coded)

### 6.1 Lost update

**Scenario:** two threads each `begin`, read a counter at 1, add one, and commit.
**Result:** both write 2, so the final value is 2 instead of 3. One update is silently lost.
**Why this design allows it:** a transaction does not record what it read, and `commit` overwrites main without checking whether the key changed since `begin`.

### 6.2 Fixing it: optimistic vs pessimistic

**Optimistic concurrency control (OCC)**
- Store a **version number per key** in main (value plus version).
- A transaction records the version of every key it reads (its read set).
- At the outermost commit, inside the lock, **validate**: for each key in the read set, is the version in main still what I saw?
- If any changed: abort with a conflict exception and let the caller retry (first committer wins).
- If all match: apply the writes and bump each written key's version.

**Pessimistic locking**
- Lock a key when it is read and hold the lock until commit.
- Brings deadlocks: use a consistent lock order or timeouts.

**Choosing**
- Conflicts rare (threads mostly touch different keys): optimistic, with no waiting and cheap retries.
- Conflicts frequent (one hot counter): pessimistic, because optimistic burns work on retries that keep failing.

### 6.3 Isolation level of this design

- Uncommitted writes are private, so there are no dirty reads.
- Reads see the latest committed state at the time of each read, so this is effectively **read-committed**.
- Anomalies that remain: non-repeatable reads, lost updates.
- To go further: **snapshot isolation / MVCC**. Versioned values, a transaction reads as of its start version, and commit stamps a new version. OCC at commit gives first-committer-wins conflict detection.

### 6.4 Durability: crash recovery

**What a crash loses**
- Private stacks (uncommitted work): harmless, since nothing was promised.
- **The main map, including every committed write.** Callers were told commit succeeded, so this breaks a promise.

**Wrong fix:** apply to memory, then update a database. A crash between the two steps loses a "committed" transaction.

**Right fix: write-ahead log (WAL)**
1. At the outermost commit, append **one record describing the whole transaction** to a log file.
2. **fsync** to force it to disk.
3. Only then apply to the in-memory map and tell the caller it succeeded.
- Crash before the append finishes: the transaction never committed, and nobody was told it did.
- Crash after: the log has it.
- Rollback and nested commits cost nothing on disk, because only the outermost commit writes the log.

**Recovery:** replay the log into an empty map on restart.
**Checkpointing:** periodically write a snapshot of the whole map and truncate the log up to that point, so replay stays short.
**Torn writes:** a crash can leave a half-written record at the end, so each record carries a length and a checksum, and recovery discards a partial tail.
**Throughput:** group commit batches several fsyncs into one, trading a little latency for much higher throughput.

### 6.5 DDIA mapping (the "senior" angle)

| Interview concept | DDIA hook |
|---|---|
| Overlay layers and tombstones | Ch. 3: LSM-trees, memtable with tombstones merged on read |
| Persisting commits | Ch. 3: WAL, snapshots, crash recovery |
| Atomic commit | Ch. 7: atomicity (ACID) |
| Per-session overlays | Ch. 7: read committed vs snapshot isolation, MVCC |
| Conflict detection | Ch. 7: lost updates, write skew, serializable snapshot isolation |
| Locks | Ch. 7: two-phase locking and deadlocks |

A good closing line: "This is a memtable-with-tombstones design. For durability I'd add a WAL and flush committed transactions to an LSM or B-tree. For stronger isolation I'd add MVCC."

---

## 7. Extra follow-ups to be ready for (mentioned, not discussed in depth)

- **`count(value)`:** maintain a value-to-count map. With the overlay design, keep a per-layer delta, so `count = base_count + sum of layer deltas`, O(depth). Never scan all keys.
- **TTL:** store `(value, expires_at)`, expire lazily on read, and sweep with a min-heap (validate stale entries on pop).
- **Savepoints:** `savepoint(name)` and `rollback_to(name)` map naturally onto the layer stack.
- **`keys()` / iteration:** merge layers top-down and skip tombstones.
- **Scale:** shard by key hash with one lock per shard.
- **Seen-before rule:** if the interviewer gives you a question you have already done, say so immediately.
- **Currency conversion (reported swap):** a directed weighted graph of currencies. BFS or DFS for a conversion path; Floyd-Warshall to precompute all pairs; for "maximize the rate", use `-log(rate)` weights and Bellman-Ford, where a negative cycle means arbitrage.

---

## 8. What the interviewer scores (SDE2)

- **Working code, run with tests**, in about 20 to 25 minutes, built incrementally.
- **Clarifying questions and explicit assumptions** before coding.
- **Edge cases raised before being prompted** (nulls, double delete, no open transaction, nested commit then outer rollback).
- **Trade-offs stated** (overlay vs undo log; optimistic vs pessimistic; `synchronized` vs read-write lock).
- **Clean structure:** small helpers, `private final` state, correct exception types.
- **Depth in follow-ups:** concurrency, isolation, durability.