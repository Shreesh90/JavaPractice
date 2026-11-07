# Music Analytics System (Rippling SDE2 Style)

Interview format: coding + light system design, 45 to 60 minutes, in-memory and single process first, then concurrency and scale.

---

## 1. Problem Statement

We are building the analytics core for a music streaming service.

```
add_song(song_id)
play_song(user_id, song_id)
print_analytics()
print_recently_played(user_id)
print_recently_played(user_id, k)
```

### Part 1: Most played songs by unique users
`print_analytics()` prints songs ranked by the number of **distinct users** who played them, highest first.
A user playing the same song 100 times counts as **one**.

### Part 2: Recently played unique songs
`print_recently_played(user_id)` prints the user's unique songs, most recent first.
If a user plays A, B, A, C, the output is `C, A, B`.
`print_recently_played(user_id, k)` prints only the top `k`.

### Part 3 (extensions)
- Thread safety (multiple threads calling `play_song`).
- `get_top_songs_last_24h(now)`: only plays in the last 24 hours count.
- Scale: millions of users, many machines.

---

## 2. Clarifying Questions a Strong Candidate Asks
- What happens when `play_song` is called for an unknown song? (Our choice: throw `IllegalArgumentException`; `add_song` is required.)
- Do zero-play songs appear in `print_analytics`? (Our code prints them, because the count-0 bucket is reached.)
- Tie-breaking inside the same count? (Arbitrary with a `HashSet`; use `LinkedHashSet` or sort by song ID if determinism is needed.)
- What should recent history print for a user who never played anything? (Our choice: print nothing.)
- Is history bounded? (Yes, capped at 1,000 per user.)

---

## 3. Key Pitfall: Distinct Users, Not Play Counts

The first wrong design counts plays per song. User 1 plays A five times and user 2 plays it once. That gives 6, but the answer must be **2**.

The requirement only needs to know "has this user ever played this song, yes or no". So per user we keep a **set of songs** (not a count map), and we increment a song's count **only when the add to the set is new**. A repeat play changes nothing.

---

## 4. Data Structures (Final Design)

### 4.1 Part 1: Bucketed doubly linked list (LFU-style)

| Structure | Purpose |
|---|---|
| `Map<userId, Set<songId>> allSongsPlayedByUsers` | "Has this user ever played this song?" Uncapped. |
| `Map<songId, Bucket> songsToBucket` | Find a song's current bucket in O(1). |
| Doubly linked list of `Bucket(count, Set<songId>)` | Songs grouped by distinct-user count, ascending head to tail. |
| Sentinels `headBucket`, `tailBucket` | Avoid null checks at the ends. |

```
head <-> [count=0: {...}] <-> [count=1: {B}] <-> [count=2: {A}] <-> tail
```

**Why this design (options we compared):**

| Option | Update on play | Read top-K | Problem |
|---|---|---|---|
| Map + sort on every read | O(1) | O(S log S) | Reads are frequent, so this is wasteful |
| Balanced tree (TreeMap) | O(log S) | O(K) | Works, but not O(1) |
| Heap / priority queue | O(log S) | O(K log S) | Cannot update an entry in place, so stale entries pile up |
| List indexed by count | O(1) | walks empty slots | Counts can reach millions with mostly empty slots |
| **Linked list of buckets** | **O(1)** | **O(songs printed)** | None; counts only increase by exactly 1, which this exploits |

**Play (first-time pair only):** song moves from bucket `k` to bucket `k+1`.
1. Remove the song from its current bucket.
2. If that bucket is now empty, unlink it (never unlink `head`).
3. Reuse the next bucket if its count is `k+1`, else create one right after.
4. Add the song and update `songsToBucket`.

**`print_analytics`:** walk from `tailBucket.prev` backwards to `headBucket`, printing each bucket's songs. Cost is O(songs printed), with no empty slots to cross.

### 4.2 Part 2: Per-user LRU (linked list + map)

| Structure | Purpose |
|---|---|
| Doubly linked list of nodes, most recent at head | Order of recent songs |
| `Map<songId, Node>` | O(1) lookup to move a replayed song to the front |
| Capacity cap (1,000) | Bounds memory |

- **First play:** add a node at the front, add to the map, size++.
- **Replay:** find the node through the map, unlink it, re-add at the front. Size unchanged.
- **Eviction (only when size > capacity):** remove `tailNode.prev` from the list **and** from the map, size--.
- **Print:** walk from the head to the tail sentinel, O(k).

### 4.3 Why two separate per-user structures

If "has this user played it" reused the capped history, an evicted song would look like a first play when replayed, and the song's distinct count would go up **twice** for the same user.

| Question | Lifetime | Structure |
|---|---|---|
| Has this user **ever** played it? (Part 1 correctness) | Forever | Uncapped set |
| What did they play **recently**? (Part 2) | Last 1,000 | Capped LRU |

### 4.4 Complexity summary

| Operation | Time |
|---|---|
| `add_song` | O(1) |
| `play_song` (repeat pair) | O(1), history update only |
| `play_song` (new pair) | O(1) average |
| `print_analytics` | O(songs printed) |
| `print_recently_played(user, k)` | O(k) |

---

## 5. Concurrency Design

### 5.1 Shared mutable state, most dangerous first
1. The **bucket linked list** and `songsToBucket`. A move touches several nodes, and a race corrupts the structure silently.
2. `allSongsPlayedByUsers` (map of sets).
3. Per-user history (list + map, rewired on every play).
4. `userRecentSongHistory` (map of histories).

### 5.2 Principle
A concurrent collection makes **each single call** atomic, but not a **sequence** of calls. Read-then-act decisions need a lock.

Example race with only a `ConcurrentHashMap`: two users play song A at the same moment. Both read A's bucket (count 2), and both move it to count 3. The song ends in the wrong place or in two buckets.

### 5.3 Final locking design

| What | Protection | Why |
|---|---|---|
| Per-user ever-played set | `ConcurrentHashMap.newKeySet()`; `add()` is atomic and returns `true` only for the first insert | Replaces check-then-insert; filters repeat plays **before** the lock |
| `songsToBucket`, `allSongsPlayedByUsers`, `userRecentSongHistory` | `ConcurrentHashMap` | Safe single operations and lazy `computeIfAbsent` |
| **Bucket move** in `play_song` | **Write lock** (`ReentrantReadWriteLock`) | The read of the song's bucket and the move must be one atomic step |
| `add_song` | **Write lock** (guard `containsKey` **inside** the lock) | Rewires the list; otherwise two threads can both pass the guard |
| `print_analytics` | **Read lock** | Many prints can run together, but never during a move |
| Per-user history | `synchronized` on `play`, `get` of `UserSongHistory` | One lock per user, so users do not block each other |

### 5.4 Rules we follow
1. **`lock()` goes immediately before `try`, with `unlock()` in `finally`.** An exception must never leave the lock held.
2. **Repeat plays never take the write lock.** The user-set gate filters them out first, and only first-time pairs enter the critical section.
3. **Keep the `songsToBucket.get` inside the write lock.** The move is decided from that read.
4. **Never hold the bucket lock and a user-history lock at the same time**, so there is no deadlock. Release the write lock before updating the history.
5. **Do not print while holding the read lock.** Copy the results under the lock, release it, then print.

### 5.5 `play_song` flow

```
play_song(user, song):
    1. if song unknown -> throw
    2. isNew = userPlayed[user].add(song)       // atomic, outside the lock
    3. if isNew:
           writeLock.lock(); try:
               move song from bucket k to bucket k+1
           finally: writeLock.unlock()
    4. history[user].play(song)                 // per-user synchronized
```

### 5.6 Bug we hit: lock leak
Taking `lock()` before `if (isNew)` but unlocking only inside it leaks the write lock on every repeat play. The next thread blocks forever. Single-threaded tests pass, because a reentrant lock lets the owning thread continue. Only a multi-threaded test catches it.

---

## 6. Reference Implementation (Java)

### Bucket.java
```java
package MusicSystem;

import java.util.HashSet;
import java.util.Set;

public class Bucket {
    int count;
    Set<String> songs = new HashSet<>();
    Bucket prev, next;
    Bucket(int count) { this.count = count; }
}
```

### UserSongHistory.java
```java
package MusicSystem;

import java.util.*;

public class UserSongHistory {
    private class Node {
        String songId;
        Node next, prev;
        Node(String songId) { this.songId = songId; }
    }

    private final int capacity;
    private final Map<String, Node> songToNode = new HashMap<>();
    private final Node headNode = new Node(null);
    private final Node tailNode = new Node(null);
    private int currentSize = 0;

    public UserSongHistory(int capacity) {
        this.capacity = capacity;
        headNode.next = tailNode;
        tailNode.prev = headNode;
    }

    private void addNodeToFront(Node n) {
        Node first = headNode.next;
        n.next = first;
        n.prev = headNode;
        headNode.next = n;
        first.prev = n;
        songToNode.put(n.songId, n);
    }

    public synchronized void play(String songId) {
        Node existing = songToNode.get(songId);
        if (existing != null) {
            existing.prev.next = existing.next;
            existing.next.prev = existing.prev;
            addNodeToFront(existing);
        } else {
            addNodeToFront(new Node(songId));
            currentSize++;
            if (currentSize > capacity) {
                Node last = tailNode.prev;
                last.prev.next = last.next;
                last.next.prev = last.prev;
                songToNode.remove(last.songId);
                currentSize--;
            }
        }
    }

    public synchronized List<String> get() { return get(capacity); }

    public synchronized List<String> get(int k) {
        List<String> out = new ArrayList<>();
        Node cur = headNode.next;
        while (cur != tailNode && k > 0) {
            out.add(cur.songId);
            cur = cur.next;
            k--;
        }
        return out;
    }
}
```

### MusicSystem.java
```java
package MusicSystem;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MusicSystem {
    private final ConcurrentHashMap<String, Bucket> songsToBucket = new ConcurrentHashMap<>();
    private final Bucket headBucket = new Bucket(-1);
    private final Bucket tailBucket = new Bucket(Integer.MAX_VALUE);

    private final ConcurrentHashMap<String, Set<String>> allSongsPlayedByUsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UserSongHistory> userRecentSongHistory = new ConcurrentHashMap<>();
    private static final int SONG_HISTORY_CAPACITY = 1000;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public MusicSystem() {
        headBucket.next = tailBucket;
        tailBucket.prev = headBucket;
    }

    public void addSong(String songId) {
        lock.writeLock().lock();
        try {
            if (songsToBucket.containsKey(songId)) return;
            addSongBucket(songId, 0, headBucket);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Caller must hold the write lock.
    private void addSongBucket(String songId, int count, Bucket anchor) {
        if (anchor.next.count == count) {
            anchor.next.songs.add(songId);
            songsToBucket.put(songId, anchor.next);
        } else {
            Bucket after = anchor.next;
            Bucket b = new Bucket(count);
            anchor.next = b;
            b.prev = anchor;
            b.next = after;
            after.prev = b;
            b.songs.add(songId);
            songsToBucket.put(songId, b);
        }
    }

    public void playSong(String userId, String songId) {
        if (!songsToBucket.containsKey(songId)) throw new IllegalArgumentException("Unknown song: " + songId);

        boolean isNew = allSongsPlayedByUsers
                .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(songId);

        if (isNew) {
            lock.writeLock().lock();
            try {
                Bucket cur = songsToBucket.get(songId);
                int count = cur.count;
                cur.songs.remove(songId);
                if (cur.songs.isEmpty()) {
                    cur.prev.next = cur.next;
                    cur.next.prev = cur.prev;
                    addSongBucket(songId, count + 1, cur.prev);
                } else {
                    addSongBucket(songId, count + 1, cur);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }

        userRecentSongHistory
                .computeIfAbsent(userId, k -> new UserSongHistory(SONG_HISTORY_CAPACITY))
                .play(songId);
    }

    public void printAnalytics() {
        List<String> lines = new ArrayList<>();
        lock.readLock().lock();
        try {
            for (Bucket b = tailBucket.prev; b != headBucket; b = b.prev) {
                for (String song : b.songs) lines.add(song + ": " + b.count);
            }
        } finally {
            lock.readLock().unlock();
        }
        lines.forEach(System.out::println);   // print outside the lock
    }

    public void printRecentlyPlayed(String userId) {
        UserSongHistory h = userRecentSongHistory.get(userId);
        if (h == null) return;
        h.get().forEach(System.out::println);
    }

    public void printRecentlyPlayed(String userId, int k) {
        UserSongHistory h = userRecentSongHistory.get(userId);
        if (h == null) return;
        h.get(k).forEach(System.out::println);
    }
}
```

---

## 7. Tests to Run

**Functional**
```
addSong(A), addSong(B)
u1 plays A, u1 plays A, u2 plays A, u2 plays B, u1 plays B
printAnalytics            -> A: 2, B: 2   (order within a tie is arbitrary)
printRecentlyPlayed(u1)   -> B, A
printRecentlyPlayed(u1,1) -> B
```

**Edge cases**
- Unknown song throws, and the user's played-set is not modified.
- Duplicate `addSong` does not move a song with count 3 back to the zero bucket.
- Unknown user on `printRecentlyPlayed` prints nothing.
- Eviction: a song evicted from history is still **not** double counted when replayed.

**Concurrency**
```java
ExecutorService ex = Executors.newFixedThreadPool(32);
CountDownLatch done = new CountDownLatch(100);
for (int i = 0; i < 100; i++) {
    final String user = "u" + i;     // use a fixed "u0" for the same-user run
    ex.submit(() -> { ms.playSong(user, "A"); done.countDown(); });
}
done.await(); ex.shutdown();
ms.printAnalytics();
```
- 100 **different** users play A: expect `A: 100`.
- 100 threads, the **same** user plays A: expect `A: 1`, and the test must not hang (this catches lock leaks).
- Without the write lock, expect lost updates: A in two buckets or a count below 100.

---

## 8. Scale Discussion (Staff-Level Follow-ups)

1. **The ever-played set is too big for one machine.** Move it to a store keyed by `(userId, songId)` with insert-if-absent. The insert result tells you if the pair was new. Increment the song's counter only then.
2. **Atomicity across processes.** `synchronized` and in-process locks do not span machines. Use a store transaction that writes the pair and the counter together, or derive the count from the pair records so there is no second number to drift. A crash between the two writes otherwise leaves the count permanently one short.
3. **Avoid one global lock.** Lock per song, or use striped locks (a fixed array of locks picked by `hash(key) % N`). That bounds lock memory. Partitioning by song ID with a single writer per partition (for example a Kafka partition) removes locking entirely.
4. **Sharding.** Partition plays by `user_id` to keep Part 2 local to one shard. Unique counts per song then need a merge across shards at read time.
5. **Approximation.** A Bloom filter per song answers "probably seen this user" in small memory (false positives). A HyperLogLog per song estimates distinct users without storing them (about 1 to 2 percent error, and no exact answers or removals).
6. **Ingestion.** A log or stream feeds aggregators, so the write path stays append-only.

---

## 9. Part 3 in Progress: 24-Hour Window

`play_song(user, song, timestamp)` and `get_top_songs_last_24h(now)`: only plays inside the window count.

Open design questions:
1. "Has this user played this song" now needs a **time**: store the user's **last play time** per song, not a boolean.
2. A user plays A at hour 1 and again at hour 20. They stop counting at **hour 44** (24 hours after their latest play), not hour 25. So a replay refreshes the timestamp.
3. When a user expires, the song's count goes **down by one**. The bucket list supports this (move from `k` to `k-1`, also O(1)), so the structure still works.
4. Finding expired plays efficiently: keep a queue or min-heap ordered by timestamp, with lazy invalidation if the user's timestamp has since been refreshed. Expire either eagerly on each call, or lazily just before a read.
5. Windowed unique counts are harder than windowed totals, because you cannot simply decrement on expiry without knowing the user's most recent play. Time-bucketed counters (for example hourly) answer approximate versions cheaply.

---

## 10. Scoring Guide (What a Staff Interviewer Looks For)

| Area | Meets SDE2 bar | Strong signal |
|---|---|---|
| Requirements | Asks about ties, unknown songs, output format | States assumptions before coding |
| Part 1 | `song -> set(users)` with sort on read | Bucket list or heap so reads are cheap; notes counts only go up by one |
| Part 2 | Ordered map per user with O(1) move-to-front | Explains why a list scan is O(n); caps history and evicts the map entry too |
| Correctness | Distinct users, not plays | Spots the eviction double-count bug unprompted |
| Concurrency | Finds the shared state | Separates "atomic call" from "atomic sequence"; keeps repeat plays out of the lock; no lock leaks; separate per-user lock |
| Scale | "Shard it" | Partitions by user, merges counts, weighs exact sets vs HyperLogLog, and handles crash consistency |
| Code quality | Readable, working, tested | Clean separation so Part 2 did not force a rewrite of Part 1 |

### Common failure points
- Counting plays instead of unique users.
- A list for recent plays, which makes replays O(n).
- Updating the map without updating the bucket (or the reverse).
- Forgetting to remove the evicted node's map entry.
- Taking a lock but releasing it on only some code paths.
- Jumping to distributed designs before the single-node version is correct.