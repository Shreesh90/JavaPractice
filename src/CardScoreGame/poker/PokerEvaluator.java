package CardScoreGame.poker;

import CardScoreGame.Hand;
import CardScoreGame.HandTypeRuleRegistry;
import CardScoreGame.ScoringEngine;
import CardScoreGame.SignatureRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PokerEvaluator {
    private final RankOrder rankOrder;
    private final ScoringEngine engine;

    public PokerEvaluator(RankOrder rankOrder, ScoringEngine engine) {
        if (rankOrder == null || engine == null) {
            throw new IllegalArgumentException("RankOrder and ScoringEngine are required");
        }
        this.rankOrder = rankOrder;
        this.engine = engine;
    }

    public String evaluate(List<String> user1, List<String> user2) {
        List<Card> cards1 = parseCards(user1);
        List<Card> cards2 = parseCards(user2);
        requireNoDuplicates(cards1, cards2);

        int result = engine.compare(toHand(cards1), toHand(cards2));
        return switch (result) {
            case 1 -> "user1";
            case 2 -> "user2";
            default -> "tie";
        };
    }

    private List<Card> parseCards(List<String> hand) {
        if (hand == null || hand.size() != Hand.SIZE) {
            throw new IllegalArgumentException("Hand must have exactly " + Hand.SIZE + " cards: " + hand);
        }
        List<Card> cards = new ArrayList<>(Hand.SIZE);
        for (String text : hand) {
            cards.add(Card.parse(text, rankOrder));
        }
        return cards;
    }

    private void requireNoDuplicates(List<Card> cards1, List<Card> cards2) {
        Set<Card> seen = new HashSet<>();
        for (Card card : cards1) {
            if (!seen.add(card)) throw new IllegalArgumentException("Duplicate card: " + card);
        }
        for (Card card : cards2) {
            if (!seen.add(card)) throw new IllegalArgumentException("Duplicate card: " + card);
        }
    }

    private Hand toHand(List<Card> cards) {
        List<Integer> ranks = new ArrayList<>(cards.size());
        for (Card card : cards) {
            ranks.add(rankOrder.rankOf(card.rank()));
        }
        return new Hand(ranks);
    }

    public static void main(String[] args) {
        RankOrder ranks = new RankOrder(List.of("2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"));
        HandTypeRuleRegistry standardRules = new HandTypeRuleRegistry(List.of(
                new SignatureRule("Four of a kind", List.of(4, 1), 60),
                new SignatureRule("Full house", List.of(3, 2), 50),
                new SignatureRule("Three of a kind", List.of(3, 1, 1), 40),
                new SignatureRule("Two pair", List.of(2, 2, 1), 30),
                new SignatureRule("One pair", List.of(2, 1, 1, 1), 20),
                new SignatureRule("High card", List.of(1, 1, 1, 1, 1), 10)));
        PokerEvaluator poker = new PokerEvaluator(ranks, new ScoringEngine(standardRules, new GroupTieBreaker()));

        System.out.println(poker.evaluate(List.of("10H", "10D", "4S", "4C", "9H"), List.of("10S", "10C", "4H", "4D", "AS"))); // expect user2
        System.out.println(poker.evaluate(List.of("3H", "3D", "3S", "9C", "9D"), List.of("2H", "2D", "2S", "AC", "AD")));    // expect user1
        System.out.println(poker.evaluate(List.of("8H", "8D", "8S", "8C", "2H"), List.of("7H", "7D", "7S", "7C", "AH")));    // expect user1
        System.out.println(poker.evaluate(List.of("AH", "AD", "9S", "5C", "3D"), List.of("AS", "AC", "9H", "5D", "2C")));    // expect user1
        System.out.println(poker.evaluate(List.of("KH", "QD", "9S", "5C", "3D"), List.of("KS", "QC", "9H", "5D", "3C")));    // expect tie
        System.out.println(poker.evaluate(List.of("2H", "2D", "2S", "3C", "4D"), List.of("AH", "AD", "KS", "KC", "QD")));    // expect user1

        printError(() -> poker.evaluate(List.of("AS", "2D", "3S", "4C", "5D"), List.of("AS", "KC", "QH", "JD", "9S")));    // expect duplicate AS
        printError(() -> poker.evaluate(List.of("1H", "2D", "3S", "4C", "5D"), List.of("AH", "KC", "QH", "JD", "9S")));    // expect unknown rank 1
    }

    private static void printError(Runnable call) {
        try {
            call.run();
            System.out.println("no exception (unexpected)");
        } catch (IllegalArgumentException e) {
            System.out.println("IllegalArgumentException: " + e.getMessage());
        }
    }
}
