# Camel Cards: Hand Evaluator with Completions (Rippling SDE2)

**Where it shows up:** AI-assisted phone screens and onsite AI coding rounds.
- **Part 2 must be finished** to move forward, and you get about 40 minutes of real coding.
- The explicit grading focus is **OOP**: "We want to see good OOP practices" and "many more hand types will be introduced in the future."

## Problem framing

> "We're building the scoring engine for a card game. Each player gets a hand, and we need to decide who wins. **We'll be adding many more hand types in the future**, so I'm going to watch your OOP closely. Before you write any code, walk me through your classes. You can use the AI assistant, but you own the design and the complexity analysis."

**What makes or breaks this round**
- **Explain the design first, for 3–5 minutes.** Candidates who start coding immediately get marked down.
- **Open/Closed design:** adding a new hand type must not require editing the evaluator, comparator or classifier.
- **Confirm the rules before coding.** Variants of this question differ in labels, type order and tie-break direction.

### Clarifying questions to ask before you code
| Question | Rule for this run (Parts 1–5) |
|---|---|
| Label set and order? | `1`–`9`, with **9 high** and 1 lowest |
| Hand size? | 5 cards |
| Repeated labels allowed? Is it a real deck? | Unlimited repeats, no deck (Part 6 changes this) |
| Type order? | See the table below. **It is deliberately non-standard.** |
| Tie-break? | Card by card, **right to left** (the most recently dealt card first). **Don't sort.** |
| Return type? | `1` (hand 1 wins), `2` (hand 2 wins), `0` (tie) |
| Can two hands be exactly equal? | Yes, and that returns `0` |
| Malformed input (wrong length, unknown label)? | Throw `IllegalArgumentException` |
| Input format? | A `String` (`"33322"`) **or** a `List<Integer>`. Normalize both in one place. |
| Do known cards keep fixed positions (Part 2)? | Yes |
| Can zero cards be known (Part 2)? | Yes, `?????` is valid |

### Hand types (strongest → weakest)
| Strength | Type | Count signature | Example |
|---|---|---|---|
| 7 | Five of a kind | [5] | `77777` |
| 6 | Four of a kind | [4,1] | `99998` |
| 5 | Full house | [3,2] | `33322` |
| 4 | **Two pair** | [2,2,1] | `22334` |
| 3 | **Three of a kind** | [3,1,1] | `99987` |
| 2 | One pair | [2,1,1,1] | `11234` |
| 1 | High card | [1,1,1,1,1] | `12345` |

> ⚠️ **Two pair beats three of a kind** in this variant. That's deliberate: it catches anyone who hard-codes standard poker order.

The **count signature** is the label counts sorted in descending order. `33322` has three 3s and two 2s, so its signature is `[3,2]`. Compute it **once per hand**.

### Overview of the parts
| Part | Topic | Key skill |
|---|---|---|
| 1 | Compare two hands | OOP design, no-sort tie-break |
| 2 ⭐ | Best and worst completions of `?` | Reuse Part 1, complexity breakdown |
| 3 | Add Straight, expand labels | Open/Closed principle |
| 4 | Bid scoring and a wildcard `J` | Sorting with a comparator, the greedy trap |
| 5 | Comparing two partial hands | Best/worst bounds instead of brute force |
| 6 | Real poker cards with suits | Swapping the tie-break strategy |

---

## Part 1: Compare two hands (~15–20 min)

```java
int evaluate(String hand1, String hand2);                  // 1 / 2 / 0
int evaluate(List<Integer> hand1, List<Integer> hand2);    // same output
```

**Rules**
1. Each hand gets **exactly one** type, the strongest one that matches.
2. The stronger type always wins.
3. Within the same type, compare positions **right to left** (index 4, then 3, and so on). The first position that differs decides, and the higher label wins.

**The list overload:** the input is 5 integers from `1` to `9` **in dealt order**, so `List.of(3,3,3,2,2)` is the same hand as `"33322"`. The output is identical to the `String` version. Both overloads should turn their input into the same `Hand` object and call **one** shared comparison:
```java
int evaluate(String h1, String h2)               { return compare(parser.parse(h1), parser.parse(h2)); }
int evaluate(List<Integer> h1, List<Integer> h2) { return compare(parser.parse(h1), parser.parse(h2)); }
```

**Test cases**
```
evaluate("33322", "99987") -> 1   full house > three of a kind
evaluate("22334", "99912") -> 1   two pair > three (non-standard order)
evaluate("11111", "99998") -> 1   five > four; labels don't matter across types
evaluate("11234", "98765") -> 1   one pair > high card
evaluate("12345", "54321") -> 1   both high card; index 4: 5 > 1  (sorting would wrongly give 0)
evaluate("23324", "32234") -> 2   both two pair; index 4: 4 = 4, index 3: 2 < 3
evaluate("55555", "55555") -> 0

evaluate(List.of(3,3,3,2,2), List.of(9,9,9,8,7)) -> 1
evaluate(List.of(1,2,3,4,5), List.of(5,4,3,2,1)) -> 1
evaluate(List.of(2,3,3,2,4), List.of(3,2,2,3,4)) -> 2

evaluate("1234", "12345")                         -> IllegalArgumentException (4 cards)
evaluate(List.of(1,2,3,4,10), List.of(1,2,3,4,5)) -> IllegalArgumentException (10 isn't a label)
evaluate(null, "12345")                           -> IllegalArgumentException
```

**The design the interviewer expects you to explain**
| Class | Responsibility |
|---|---|
| `Hand` | Holds the cards **in dealt order** and computes the count profile (counts and sorted signature) **once** |
| `HandType` / `HandTypeRule` (an interface or abstract class) | `name()`, `strength()`, `matches(Hand)`. Each type owns its own matching rule. |
| `HandTypeRegistry` | The types ordered by strength. The classifier picks the strongest match. Adding a type means **registering it**. |
| `TieBreaker` (a strategy) | The positional right-to-left comparison. It's pluggable because Part 6 replaces it. |
| `HandComparator` | Compares type strength first, then delegates to the `TieBreaker` |
| `HandParser` / `LabelOrder` | The single point that turns a string or list into a `Hand`, validates it, and defines label order |

**Traps**
- **Sorting the cards** before the tie-break. `12345` and `54321` contain the same cards but resolve differently.
- Hard-coding standard poker order, which makes two pair vs. three come out wrong.
- Re-counting labels on every comparison instead of profiling the hand once.
- `if (signature == [3,2]) ... else if ...` inside the evaluator. That breaks Open/Closed.
- Duplicating classify or compare logic in the `List` overload.

---

## Part 2: Best and worst completions (⭐ the gate, ~20 min)

A partial hand uses `?` for unknown cards. **Known cards keep their positions.**

```java
List<String> bestAndWorst(String partialHand);   // [best, worst]
```

**Rules**
- `best` is the strongest complete hand that matches the pattern, judged with **your Part 1 comparator**.
- `worst` is the weakest such hand.
- A hand with no `?` returns itself for both.
- Zero known cards (`?????`) is valid.
- Any label `1`–`9` can fill a `?`.
- **Reuse Part 1 as the single source of truth.** Don't write separate "best type" or "worst type" logic.

**Test cases**
```
"99???"  -> best 99999, worst 99321   a pair is forced; smallest distinct non-9 values, filled right to left
"2345?"  -> best 23455, worst 23451   labels are 1–9, so 1 is available (a 2–9 label set would give 23456)
"9?9?9"  -> best 99999, worst 92919   three is the weakest reachable type, since two pair outranks it here
"1?1?1"  -> best 11111, worst 13121
"?????"  -> best 99999, worst 54321
"33322"  -> best 33322, worst 33322
"99??"   -> IllegalArgumentException (4 cards)
"99?X?"  -> IllegalArgumentException (unknown label)
```

**What the interviewer checks**
- **Brute force is accepted**, but you must break down the complexity step by step:
  - number of completions = `L^m`, where L = 9 labels and m = number of `?`
  - cost per completion = O(H) to classify + O(H) to compare, where H = 5
  - total = **O(L^m · H)**, at most 9⁵ = 59,049 for this hand size. That's polynomial for a fixed hand size and exponential in m.
- **One loop over all completions** that tracks both `max` and `min` with the same comparator. Don't write two separate methods.
- **Ties among best candidates aren't possible.** Two different completions always differ at some position, and the comparator checks every position. So `best` and `worst` are unique. Say this out loud.
- **Discuss:** can you avoid brute force? You'd need a greedy rule per type, which gets tricky because of the non-standard order. Brute force with pruning is the honest answer.

---

## Part 3: Extensibility drill (the interviewer makes these changes live)

You **may only add classes or configuration**. Any edit to the evaluator, classifier or comparator counts as a failed check.

### 3a. Add Straight
A Straight is **5 consecutive distinct labels, in any order**, such as `34567` or `75364`. It goes **between Full house and Two pair**.

**How a Straight differs from High card:** both have 5 distinct labels, so both have the signature `[1,1,1,1,1]`. A Straight's labels are also **consecutive**.

| Hand | Distinct? | Consecutive when sorted? | Type |
|---|---|---|---|
| `34567` | ✓ | 3,4,5,6,7 ✓ | Straight |
| `75364` | ✓ | sorted: 3,4,5,6,7 ✓ | Straight, in any order |
| `12345` | ✓ | ✓ | Straight |
| `12346` | ✓ | ✗ (5 is missing) | High card |
| `23345` | ✗ (two 3s) | — | One pair |

**Detection rule:** all labels are distinct **and** `max − min == 4`.

```
evaluate("75364", "22334") -> 1   straight > two pair
evaluate("33322", "34567") -> 1   full house > straight
evaluate("12346", "22334") -> 2   12346 is a high card
evaluate("12345", "98765") -> 2   both straights; index 4: 5 = 5, index 3: 4 < 6
```

**What the interviewer checks**
- **Counts alone aren't enough.** If `matches()` only receives the signature, it can't tell `34567` from `12346`, so it needs the whole `Hand`.
- **Two types match the same hand.** `34567` matches both Straight and High card, so the classifier must pick the **strongest** match.
- **Inserting between strengths 5 and 4** shouldn't require renumbering. Use gapped weights (`10, 20, … 70`, with Straight at `45`) or the registry's position, and explain the choice.
- Sorting is fine for **detecting** a Straight. The tie-break still uses the original dealt order.

### 3b. Expand the labels
Change the labels to `2 3 4 5 6 7 8 9 T J Q K A`. Change it in **one place** (a `LabelOrder` config), and parsing, ordering and tie-breaks should all follow.
```
evaluate("TJQKA", "9TJQK") -> 1   both straights, A > K at index 4
evaluate("2345A", "23459") -> 1   both high card, A > 9 at index 4
```

---

## Part 4: Bid scoring and a wildcard

### 4a. Total winnings
```java
long totalWinnings(List<HandBid> hands);   // record HandBid(String hand, int bid)
```
Sort the hands from weakest to strongest. The weakest gets rank 1. The total is `Σ rank × bid`.
```
[("33322",10), ("99987",20), ("12345",5), ("22334",7)]
order: 12345(1) < 99987(2) < 22334(3) < 33322(4)
total = 1×5 + 2×20 + 3×7 + 4×10 = 106
```
**What the interviewer checks:** O(n log n) comparisons × O(H) each. Profile each hand **once** before sorting, not inside the comparator. Equal hands keep their input order (stable sort).

### 4b. Wildcard `J`
- `J` takes whatever label makes the **type** strongest.
- In tie-breaks, `J` counts as the **lowest** card, below `1`.

```
"2334J"  -> TWO PAIR   (not three of a kind!)
evaluate("2334J", "99912") -> 1   two pair > three
evaluate("JJJJJ", "11111") -> 2   both five of a kind; index 4: J (lowest) < 1
evaluate("2334J", "23342") -> 2   both two pair; index 4: J (lowest) < 2
add ("2334J", 3) to the 4a list -> 12345(1) 99987(2) 2334J(3) 22334(4) 33322(5)
                                -> total = 5 + 40 + 9 + 28 + 50 = 132
```

> ⚠️ **The greedy trap:** the usual "the joker joins the largest group" rule turns `2334J` into three of a kind (`23343`). In this variant two pair (`23342` or `23344`) is stronger. **Greedy is wrong when the type order is non-standard.**

**How 4b relates to Part 2**
| | Part 2 (`?`) | Part 4b (`J`) |
|---|---|---|
| What it is | An **unknown** card | A **real** card in the hand |
| What you need | Both the best **and** the worst completion | Only the best type |
| Tie-break | Uses the **filled-in** label | Uses **J as the lowest card**, at its original position |

- **The type:** treat each `J` as `?` and reuse Part 2's **best** completion.
- **The tie-break:** use the **original** cards with `J` mapped to the lowest value.
- Example where they differ: Part 2 thinking turns `"2334J"` into `"23344"` and says it beats `"23342"`. The 4b rule says index 4 is J (lowest) vs. 2, so `"23342"` wins.

**What the interviewer checks:** keep the type view (jokers substituted) separate from the tie-break view (the original cards). If `Hand` computes both from one array, the joker forces you to split them.

---

## Part 5: Comparing two partial hands

```java
PartialComparison comparePartial(String hand1, String hand2);   // HAND_1 / HAND_2 / TIE / UNKNOWN
```

**Rules**
- Either hand (or both) may contain `?`. Each `?` can be any label `1`–`9`, and known cards keep their positions.
- The two hands are completed **independently** (no shared deck).
- "Stronger" means the full Part 1 comparator: type first, then the right-to-left tie-break.
- `HAND_1` if **every** completion of hand 1 beats **every** completion of hand 2. `HAND_2` likewise.
- `TIE` only if every combination ties, which means both hands are complete and identical.
- `UNKNOWN` otherwise, including when one combination ties and another doesn't.

**Test cases** (base rule set, without Straight. With Straight, `"1234?"` has best `12345` and worst `12346`, but these results don't change.)
```
comparePartial("1234?", "99999") -> HAND_2    h1's best 12344 (pair) < five of a kind
comparePartial("9999?", "12?45") -> HAND_1    h1's worst 99991 (four) > h2's best 12545 (pair)
comparePartial("99???", "1234?") -> UNKNOWN   h1's worst 99321 < h2's best 12344, but h1's best 99999 > h2's worst 12345
comparePartial("33322", "33322") -> TIE
comparePartial("3332?", "33322") -> UNKNOWN   h1 can tie (33322), win (33323, four) or lose (33321, three)
comparePartial("?????", "?????") -> UNKNOWN
```

**The approach**
1. Compute `[best1, worst1]` and `[best2, worst2]` with Part 2.
2. `compare(worst1, best2)` says hand 1 wins → `HAND_1`
3. `compare(worst2, best1)` says hand 2 wins → `HAND_2`
4. Both hands have no `?` (so they must be equal) → `TIE`
5. Otherwise → `UNKNOWN`

**What the interviewer checks**
- **Don't compare every pair of completions.** That's O(9^m1 × 9^m2). Comparing the bounds costs **O(9^m1 + 9^m2)**.
- **Why the bounds are enough:** the completions are independent, so for every completion c1 of hand 1 and c2 of hand 2, `worst1 ≤ c1` and `c2 ≤ best2`. If `worst1 > best2`, then `c1 > c2` for every pair. (The candidate in the source couldn't explain this and ran out of time.)
- Use the **full comparator**, not just the type weight. `99321` and `12344` are both one pair and are decided by the tie-break.
- Use **strict `>`**. If `worst1 == best2`, one combination ties, so the result is `UNKNOWN`.

---

## Part 6 (onsite variant): Real poker cards with suits

```java
String evaluate(List<String> user1, List<String> user2);   // "user1" / "user2" / "tie"
```

Part 6 changes **three things at once**: the card format, the type order and the tie-break. The real question is how much of your design survives.

### Card format
- Each card is a **rank followed by a suit**: `"10H"`, `"AS"`, `"7D"`, `"QC"`.
- Ranks, low → high: `2 3 4 5 6 7 8 9 10 J Q K A`
- Suits: `H`, `D`, `S`, `C`. **Suits never affect strength** (no flushes). They only make each card unique.
- **Parsing trap:** `"10H"` has 3 characters. Take the suit as the **last** character and the rank as **everything before it**.

### Validation (throw `IllegalArgumentException`)
- Not exactly 5 cards in a hand
- Unknown rank or suit (`"1H"`, `"AX"`, `"11S"`)
- The **same card twice**, within a hand or **across both hands**, because a real deck has one of each card

### Hand types: standard order, no straights or flushes
| Type | Signature |
|---|---|
| Four of a kind | [4,1] |
| Full house | [3,2] |
| **Three of a kind** | [3,1,1] |
| **Two pair** | [2,2,1] |
| One pair | [2,1,1,1] |
| High card | [1,1,1,1,1] |

There's no five of a kind (impossible with one deck), and **three of a kind beats two pair again**. In your design, that's only a different registry configuration.

### Tie-break: by groups, not positions
| Type | Compare in this order |
|---|---|
| Four of a kind | quad rank → kicker |
| Full house | triple rank → pair rank |
| Three of a kind | triple rank → kickers high to low |
| Two pair | higher pair → lower pair → kicker |
| One pair | pair rank → 3 kickers high to low |
| High card | all 5 cards high to low |

A **kicker** is a card that isn't part of the main group.

**One rule covers every row of that table:**
1. Group the cards by rank and count each rank.
2. Sort the groups by **(count desc, rank desc)**.
3. Compare the two sorted lists of ranks element by element. The first difference decides.

```
["10H","10D","4S","4C","9H"]  -> groups 10×2, 4×2, 9×1  -> key [10, 4, 9]
["10S","10C","4H","4D","AS"]  -> groups 10×2, 4×2, A×1  -> key [10, 4, A]
10 = 10, 4 = 4, 9 < A  ->  "user2"
```

**Why sorting is right here but wrong in Part 1:** in Part 1 the rule is based on **position**, so sorting destroys information. Here the rule is based on **rank groups**, and dealt order doesn't matter.

### Test cases
```
evaluate(["10H","10D","4S","4C","9H"], ["10S","10C","4H","4D","AS"]) -> "user2"   same two pair, kicker A > 9
evaluate(["3H","3D","3S","9C","9D"],   ["2H","2D","2S","AC","AD"])   -> "user1"   full house: triple 3 > 2; the pair isn't compared
evaluate(["8H","8D","8S","8C","2H"],   ["7H","7D","7S","7C","AH"])   -> "user1"   quad rank is compared before the kicker
evaluate(["AH","AD","9S","5C","3D"],   ["AS","AC","9H","5D","2C"])   -> "user1"   one pair, last kicker 3 > 2
evaluate(["KH","QD","9S","5C","3D"],   ["KS","QC","9H","5D","3C"])   -> "tie"     same ranks, different suits
evaluate(["2H","2D","2S","3C","4D"],   ["AH","AD","KS","KC","QD"])   -> "user1"   three of a kind > two pair (standard order)
evaluate(["AS","2D","3S","4C","5D"],   ["AS","KC","QH","JD","9S"])   -> IllegalArgumentException   "AS" appears in both hands
evaluate(["1H","2D","3S","4C","5D"],   ["AH","KC","QH","JD","9S"])   -> IllegalArgumentException   "1" isn't a rank
```

### What this means for the Parts 1–5 design
| Parts 1–5 | Part 6 needs | Change |
|---|---|---|
| `compare()` loops over positions from right to left | A group-wise comparison | Pull out a **`TieBreaker` strategy**: `PositionalTieBreaker` and `GroupTieBreaker`. `compare()` compares types, then delegates. |
| `LabelOrder` uses one character per label | `"10"` is two characters, plus suits | A `Card(rank, suit)` record and a card parser. `LabelOrder` becomes a rank order. |
| `Hand.parse` takes a string | Takes a `List<String>` of cards | A new parsing entry point that produces the same `Hand`, plus the duplicate check |
| Returns `1` / `2` / `0` | `"user1"` / `"user2"` / `"tie"` | Map the result at the API boundary, not inside `compare()` |
| The registry config | Standard order, no five of a kind | Configuration only ✓ |

**What the interviewer checks:** the `HandTypeRule`s and the registry should work **unchanged**. If you only add a `TieBreaker`, a card parser and new configuration, the design passed. If you had to rewrite the classifier or comparator, it failed.

### Follow-up: network delay (partial hands with a real deck)
- A player may have received fewer than 5 cards. Return the winner only if it's forced for every valid completion, and `"unknown"` otherwise.
- The missing cards come from the **remaining deck** (52 minus every card already shown), and no card can go to both players.
- **Completions are no longer independent.** If user1's best completion needs the last ace, user2 can't also get it.
- **The bounds check is still safe in one direction.** If `worst(user1) > best(user2)` over independent completions, user1 wins in every real deal, because real deals are a **subset**. But the check may return `"unknown"` when the result is actually decided.
- **An exact answer** enumerates real joint deals: C(remaining, k1) × C(remaining − k1, k2) combinations, which is still feasible because k is small. Discuss the trade-off between the cheap bounds and exact enumeration.

---

## Follow-up discussion questions (with answers)

> How to answer: state **the specific issue**, then **the fix**, then **the trade-off**.

### 1. Two hand types with the same strength
**Q:** Product adds a new type with the same strength as Full house. What changes?

**A:**
- The classifier's "exactly one type" rule becomes ambiguous if a hand matches both. Decide on a **secondary priority** (registration order) to pick one type, or make the two types mutually exclusive by definition.
- The comparator only compares `strength()`, so two hands of different types with equal strength fall through to the tie-break. **That's correct if product says they're equal in rank.**
- With gapped weights this needs no renumbering. If types are ranked by list index, equal strength can't be expressed, which is an argument for explicit weights.

### 2. Sorting 1,000 hands
**Q:** What does sorting 1,000 hands cost? Can you precompute a single comparable key per hand?

**A:**
- Naively, O(n log n) comparisons × O(H) each, plus classification. If you classify **inside** the comparator, you re-classify each hand O(log n) times. **Profile each hand once first.**
- **A single key:** `strength × 10^H + positional value read right to left` (base 10 for labels 1–9). Each comparison becomes one `long` comparison, O(1).
- **Trade-off:** the key bakes in the tie-break rule and the label count. Part 6's group tie-break or more labels would need a different key, so keep the key **produced by the `TieBreaker`**, not hard-coded in `Hand`.

### 3. H cards instead of 5
**Q:** Hands now have H cards. What breaks or grows?

**A:**
- Count signatures still work, but more of them exist, for example three pair `[2,2,2]` for H = 6. Those are new `HandTypeRule`s, so the design holds if `Hand.SIZE` isn't hard-coded in the rules.
- Part 2 brute force is O(L^m · H), exponential in the number of unknowns, so it becomes impractical as m grows. You'd need per-type reasoning ("can this type be reached with these known cards?") and pruning.
- Validation, the Straight rule (`max − min == H − 1`) and the bounds in Part 5 all have to read H from **one** place.

### 4. Is a partial hand certain to win against a known hand?
**Q:** Given an opponent's full hand, is a partial hand certain to win or lose?

**A:**
- This is Part 5 with one side complete: its best and worst are the hand itself.
- `worst(partial) > opponent` → certain win. `best(partial) < opponent` → certain loss. Otherwise uncertain.
- Cost: one `bestAndWorst` call, O(9^m · H). With a real deck, remove the opponent's cards from the pool. Since the opponent's hand is fixed, the partial hand's completions are independent again, so the bounds are exact.

### 5. The smallest test set that pins the rules
**Q:** What's the smallest set of tests that would fail if someone swapped (a) the tie-break direction, (b) added sorting, or (c) used standard type order?

**A:**
- **(a) Direction:** `evaluate("23324", "32234") -> 2`. Index 4 ties, and index 3 decides. Reading left to right instead, index 0 (2 vs. 3) also gives 2, so add `evaluate("91111", "19111")`. Read left to right, hand 1 wins at index 0. Read right to left, index 1 (1 vs. 9) decides, so hand 2 wins.
- **(b) Sorting:** `evaluate("12345", "54321") -> 1`. Sorting makes them identical (`0`).
- **(c) Type order:** `evaluate("22334", "99912") -> 1`. Standard order would say three of a kind wins.
- Plus one boundary test per adjacent pair of types (full house vs. three, two pair vs. one pair) to catch swapped priorities.

---

## Rubric

| Signal | Hire | Strong hire |
|---|---|---|
| OOP | `HandType` classes exist | Registry, a strategy for the tie-break and a single parser. Adding a type is one new class plus one registration. |
| Correctness | Parts 1–2 pass | Non-standard order, the no-sort tie-break and the joker greedy trap all handled without hints |
| Reuse | Part 2 works | Part 2 reuses the Part 1 comparator, and Parts 4b and 5 reuse Part 2 |
| Complexity | "It's brute force" | O(L^m · H) worked out step by step, plus the reduction from O(L^m1 · L^m2) to O(L^m1 + L^m2) in Part 5 |
| Communication | Codes immediately | Explains the class design first and confirms the rules before coding |

**Sources:** [Rank five-card hands + completions](https://prachub.com/interview-questions/rank-five-card-hands-by-type-then-find-best-and-worst-completions-of-unknown-cards) · [Camel Cards evaluator](https://prachub.com/interview-questions/design-a-camel-cards-hand-evaluator-and-find-best-and-worst-completions) · [Extensible poker evaluator (4-card)](https://prachub.com/interview-questions/design-an-extensible-poker-hand-evaluator) · [Numeric hands](https://prachub.com/coding-questions/compare-two-five-card-numeric-hands) · [Partial numeric hand](https://prachub.com/coding-questions/find-the-best-and-worst-completion-of-a-partial-numeric-hand) · [Complete or partial hands](https://prachub.com/coding-questions/compare-complete-or-partial-hands)
