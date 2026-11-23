package CardScoreGame;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ranks are positions in the LabelOrder, kept in dealt order (needed for the right-to-left tie-break). */
public record Hand(List<Integer> ranks) {
    public static final int SIZE = 5;

    public Hand {
        if (ranks == null || ranks.size() != SIZE) {
            throw new IllegalArgumentException("Hand must have exactly " + SIZE + " cards: " + ranks);
        }
        ranks = List.copyOf(ranks);
    }

    public static Hand parse(String cards, LabelOrder labelOrder) {
        if (cards == null || cards.length() != SIZE) {
            throw new IllegalArgumentException("Hand must have exactly " + SIZE + " cards: " + cards);
        }
        List<Integer> ranks = new ArrayList<>(SIZE);
        for (char label : cards.toCharArray()) {
            ranks.add(labelOrder.rankOf(label));
        }
        return new Hand(ranks);
    }

    public List<Integer> signature() {
        Map<Integer, Integer> countByRank = new HashMap<>();
        for (int rank : ranks) {
            countByRank.merge(rank, 1, Integer::sum);
        }
        List<Integer> signature = new ArrayList<>(countByRank.values());
        signature.sort(Comparator.reverseOrder());
        return signature;
    }
}
