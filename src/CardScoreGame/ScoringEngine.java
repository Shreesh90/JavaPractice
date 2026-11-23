package CardScoreGame;

import java.util.ArrayList;
import java.util.List;

public class ScoringEngine {
    private static final char PLACEHOLDER = '?';

    private final LabelOrder labelOrder; // null when the engine only compares pre-built Hands (e.g. PokerEvaluator)
    private final HandTypeRuleRegistry registry;
    private final TieBreaker tieBreaker;

    public ScoringEngine(LabelOrder labelOrder, HandTypeRuleRegistry registry, TieBreaker tieBreaker) {
        if (registry == null || tieBreaker == null) {
            throw new IllegalArgumentException("HandTypeRuleRegistry and TieBreaker are required");
        }
        this.labelOrder = labelOrder;
        this.registry = registry;
        this.tieBreaker = tieBreaker;
    }

    public ScoringEngine(HandTypeRuleRegistry registry, TieBreaker tieBreaker) {
        this(null, registry, tieBreaker);
    }

    private LabelOrder labelOrder() {
        if (labelOrder == null) {
            throw new IllegalStateException("This engine has no LabelOrder; it only compares pre-built Hands");
        }
        return labelOrder;
    }

    public int evaluate(String hand1, String hand2) {
        return compare(Hand.parse(hand1, labelOrder()), Hand.parse(hand2, labelOrder()));
    }

    public int evaluate(List<Integer> hand1, List<Integer> hand2) {
        return evaluate(toCardString(hand1), toCardString(hand2));
    }

    public List<String> bestAndWorst(String hand) {
        validatePattern(hand);
        if (hand.indexOf(PLACEHOLDER) < 0) {
            return List.of(hand, hand);
        }

        // [0] = best hand, [1] = worst hand; array so recursion can update them
        String[] bestWorst = new String[2];

        fillPlaceholders(hand.toCharArray(), 0, bestWorst);

        return List.of(bestWorst[0], bestWorst[1]);
    }

    public PartialComparison comparePartial(String hand1, String hand2) {
        List<String> bounds1 = bestAndWorst(hand1); // [best, worst]
        List<String> bounds2 = bestAndWorst(hand2);

        if (evaluate(bounds1.get(1), bounds2.get(0)) == 1) return PartialComparison.HAND_1;
        if (evaluate(bounds2.get(1), bounds1.get(0)) == 1) return PartialComparison.HAND_2;
        // Both complete and neither strictly wins, so they must be equal
        if (hand1.indexOf(PLACEHOLDER) < 0 && hand2.indexOf(PLACEHOLDER) < 0) return PartialComparison.TIE;
        return PartialComparison.UNKNOWN;
    }

    public int compare(Hand hand1, Hand hand2) {
        int byType = Integer.compare(registry.classify(hand1).weight(), registry.classify(hand2).weight());
        if (byType > 0) return 1;
        if (byType < 0) return 2;

        int byTieBreak = tieBreaker.compare(hand1, hand2);
        if (byTieBreak > 0) return 1;
        if (byTieBreak < 0) return 2;
        return 0;
    }

    // List input supports single-digit labels only; letters like 'T' can't be expressed as Integers.
    private String toCardString(List<Integer> hand) {
        if (hand == null) {
            throw new IllegalArgumentException("Hand must not be null");
        }
        StringBuilder cards = new StringBuilder(hand.size());
        for (Integer card : hand) {
            if (card == null || card < 0 || card > 9) {
                throw new IllegalArgumentException("Invalid card label '" + card + "' in hand: " + hand);
            }
            cards.append((char) ('0' + card));
        }
        return cards.toString();
    }

    private void fillPlaceholders(char[] cards, int index, String[] bestWorst) {
        if (index == Hand.SIZE) {
            recordCandidate(new String(cards), bestWorst);
            return;
        }
        if (cards[index] != PLACEHOLDER) {
            fillPlaceholders(cards, index + 1, bestWorst);
            return;
        }
        for (int rank = 0; rank < labelOrder().size(); rank++) {
            cards[index] = labelOrder().labelAt(rank);
            fillPlaceholders(cards, index + 1, bestWorst);
        }
        cards[index] = PLACEHOLDER;
    }

    private void recordCandidate(String candidate, String[] bestWorst) {
        if (bestWorst[0] == null || evaluate(candidate, bestWorst[0]) == 1) {
            bestWorst[0] = candidate;
        }
        if (bestWorst[1] == null || evaluate(candidate, bestWorst[1]) == 2) {
            bestWorst[1] = candidate;
        }
    }

    private void validatePattern(String hand) {
        if (hand == null || hand.length() != Hand.SIZE) {
            throw new IllegalArgumentException("Hand must have exactly " + Hand.SIZE + " cards: " + hand);
        }
        for (char c : hand.toCharArray()) {
            if (c != PLACEHOLDER && !labelOrder().isValid(c)) {
                throw new IllegalArgumentException("Invalid card label '" + c + "' in hand: " + hand);
            }
        }
    }

    public static void main(String[] args) {
        List<HandTypeRule> baseRules = List.of(
                new SignatureRule("Five of a kind", List.of(5), 70),
                new SignatureRule("Four of a kind", List.of(4, 1), 60),
                new SignatureRule("Full house", List.of(3, 2), 50),
                new SignatureRule("Two pair", List.of(2, 2, 1), 40),
                new SignatureRule("Three of a kind", List.of(3, 1, 1), 30),
                new SignatureRule("One pair", List.of(2, 1, 1, 1), 20),
                new SignatureRule("High card", List.of(1, 1, 1, 1, 1), 10));
        LabelOrder digits = new LabelOrder("123456789");

        // Part 1 & 2: base rules, digit labels
        ScoringEngine engine = new ScoringEngine(digits, new HandTypeRuleRegistry(baseRules), new PositionalTieBreaker());
        System.out.println(engine.evaluate("77777", "99998")); // expect 1
        System.out.println(engine.evaluate("22334", "33322")); // expect 2
        System.out.println(engine.evaluate("99987", "22334")); // expect 2 (two pair beats three of a kind here)
        System.out.println(engine.evaluate("11234", "12345")); // expect 1

        System.out.println(engine.evaluate(List.of(3, 3, 3, 2, 2), List.of(2, 2, 3, 3, 4))); // expect 1
        System.out.println(engine.evaluate(List.of(1, 2, 3, 4, 5), List.of(9, 9, 9, 9, 8))); // expect 2

        System.out.println(engine.bestAndWorst("99???")); // expect [99999, 99321]
        System.out.println(engine.bestAndWorst("1?1?1")); // expect [11111, 13121]
        System.out.println(engine.bestAndWorst("?????")); // expect [99999, 54321]

        // Part 3a: same engine class, configuration adds Straight between Full house (50) and Two pair (40)
        List<HandTypeRule> withStraight = new ArrayList<>(baseRules);
        withStraight.add(new StraightRule(45));
        ScoringEngine straightEngine = new ScoringEngine(digits, new HandTypeRuleRegistry(withStraight), new PositionalTieBreaker());
        System.out.println(straightEngine.evaluate("75364", "22334")); // expect 1
        System.out.println(straightEngine.evaluate("33322", "34567")); // expect 1
        System.out.println(straightEngine.evaluate("12345", "98765")); // expect 2

        // Part 3b: only the LabelOrder changes
        ScoringEngine faceEngine = new ScoringEngine(new LabelOrder("23456789TJQKA"), new HandTypeRuleRegistry(withStraight), new PositionalTieBreaker());
        System.out.println(faceEngine.evaluate("TJQKA", "9TJQK")); // expect 1 (both straights, A > K at index 4)
        System.out.println(faceEngine.evaluate("2345A", "23459")); // expect 1 (both high card, A > 9 at index 4)

        // Part 5: partial vs partial, base rules
        System.out.println(engine.comparePartial("1234?", "99999")); // expect HAND_2
        System.out.println(engine.comparePartial("9999?", "12?45")); // expect HAND_1
        System.out.println(engine.comparePartial("99???", "1234?")); // expect UNKNOWN
        System.out.println(engine.comparePartial("33322", "33322")); // expect TIE
        System.out.println(engine.comparePartial("3332?", "33322")); // expect UNKNOWN
        System.out.println(engine.comparePartial("?????", "?????")); // expect UNKNOWN
    }
}
