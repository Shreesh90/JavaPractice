# Extensible Logger (Rippling SDE2 Mock Interview)

**Round:** Technical screen / LLD
**Difficulty:** Medium
**Focus:** Object-oriented design, extensibility, concurrency basics
**Language:** Your choice (this write-up assumes Java)

---

## 1. Problem Statement

Build a simple logging utility for an application.

The `Logger` must support **multiple log handlers**, and it must be possible to **add new handlers later without modifying the core `Logger` class**.

### Handlers to implement first

| Handler | Behaviour |
|---|---|
| **Filter** | Removes every occurrence of a configurable string from the message, then prints it |
| **Truncate** | Cuts the message to N characters, then prints it |
| **Uppercase** | Converts the message to uppercase, then prints it |
| **Array** | Stores the message in an in-memory array instead of printing |

### Key rule

Every handler receives the **original message**. Handlers do not depend on each other and never see another handler's output.

---

## 2. Example

Configuration: filter removes `"l"`, truncate N = 5.

```
logger.log("hello world")

console:  heo word
console:  hello
console:  HELLO WORLD
array:    ["hello world"]
```

Handlers run in the order they were registered.

---

## 3. Future Requirements (design for these now)

The design must accommodate, without changing `Logger`:

1. A **database handler** that opens a new database connection on every log call.
2. A **remote API handler** that sends each message to an HTTP endpoint.

---

## 4. Clarifying Questions to Ask the Interviewer

Settle these before coding.

- **Filter semantics:** One left-to-right pass, or repeat until the string no longer appears? (Removing `"ab"` from `"aabb"` gives `"ab"` after one pass.) Case-sensitive?
- **Truncation edge cases:** What if N is larger than the message? Zero? Negative? Characters or bytes?
- **Ordering:** Is handler order part of the contract?
- **Duplicates:** Can the same handler type be registered twice with different settings?
- **Failure policy:** If one handler throws, do the others still run? Should `log` throw?
- **Concurrency:** Will `log` be called from multiple threads?

---

## 5. Part 1: Logger and the Four Handlers

Implement the `Logger` and the four handlers so the example above produces the shown output.

**Deliverables**

- A handler abstraction, with a `Logger` that depends only on it
- Correct behaviour and configuration for each handler, including edge cases
- Proof that every handler gets the original message
- A demo or test that checks both printed and stored output
- Show how a caller registers handlers

**Hint:** What is the smallest contract the logger needs from a handler, so that it never has to know which kinds of handler exist?

---

## 6. Part 2: Expensive Handlers, Optimization and Flexibility

1. Add the **database handler** (new connection per call) and the **remote API handler**. Show how they plug in without touching `Logger`, and how connections are acquired and released.
2. Discuss how to **optimize** the design.
3. Discuss how to make handlers **more flexible to pass in and configure**.

**Hints**

- Printing a line is cheap. Opening a connection or making a network call is not. What should the caller of `log` have to wait for?
- Three handlers change the message and then print it; one stores it unchanged. What would you write to get an uppercased message into the array, or a truncated one to the remote endpoint?

**Topics to cover**

- Cost of a connection or request per message, and remedies: reuse, batching, async delivery (with their risks)
- Failure isolation, retries, and what happens to messages when a destination is slow or down
- Composing message transformations with destinations; registering handlers at runtime or from configuration

---

## 7. Follow-up Questions

1. The remote endpoint becomes slow and `log` calls start blocking the application. What do you change, and which messages can now be lost?
2. The process exits while messages are still buffered for the database. How do you make sure they are written?
3. A new requirement: "uppercase, then truncate, then send to the remote endpoint" as **one** handler. How do you support arbitrary chains without a class for every combination?
4. How do you unit-test the database and remote handlers without a real database or network?

---

## 8. What a Strong Answer Covers

- Settles filter semantics, truncation bounds, ordering and failure policy **before** coding
- An abstraction that keeps `Logger` closed to modification and open to new handlers
- Small, correct handlers, tested on the example and on edge cases
- A concrete plan for expensive handlers: connection reuse, buffering, async delivery, backpressure, flush at shutdown
- A reasoned position on flexibility (composition over one class per combination) without over-engineering

---

## 9. Design Checklist (self-review)

- [ ] `Logger` knows only the handler interface, never a concrete handler
- [ ] Handlers are constructed by the caller and passed in, with no switch or string constants inside `Logger`
- [ ] Every handler receives the original message
- [ ] One failing handler does not stop the others
- [ ] Transformers (string in, string out) are separate from destinations (deliver a string)
- [ ] Slow destinations are wrapped by an async handler with a bounded queue and a rejection policy
- [ ] `close()` shuts down the executor and waits with a timeout
- [ ] Dropped messages are counted, not lost silently
- [ ] External dependencies (DB, HTTP) are injected so they can be mocked in tests