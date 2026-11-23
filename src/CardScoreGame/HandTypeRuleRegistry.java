package CardScoreGame;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HandTypeRuleRegistry {
    private final List<HandTypeRule> rulesStrongestFirst;

    public HandTypeRuleRegistry(List<HandTypeRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("Registry needs at least one rule");
        }
        Set<Integer> weights = new HashSet<>();
        for (HandTypeRule rule : rules) {
            if (!weights.add(rule.weight())) {
                throw new IllegalArgumentException("Duplicate weight " + rule.weight() + " for rule: " + rule.name());
            }
        }
        List<HandTypeRule> sorted = new ArrayList<>(rules);
        sorted.sort(Comparator.comparingInt(HandTypeRule::weight).reversed());
        this.rulesStrongestFirst = List.copyOf(sorted);
    }

    public HandTypeRule classify(Hand hand) {
        for (HandTypeRule rule : rulesStrongestFirst) {
            if (rule.matches(hand)) {
                return rule;
            }
        }
        throw new IllegalArgumentException("No hand type matches: " + hand);
    }
}
