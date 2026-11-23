package CardScoreGame;

import java.util.Collections;
import java.util.HashSet;

public record StraightRule(int weight) implements HandTypeRule {
    @Override
    public String name() {
        return "Straight";
    }

    @Override
    public boolean matches(Hand hand) {
        boolean allDistinct = new HashSet<>(hand.ranks()).size() == Hand.SIZE;
        int span = Collections.max(hand.ranks()) - Collections.min(hand.ranks());
        return allDistinct && span == Hand.SIZE - 1;
    }
}
