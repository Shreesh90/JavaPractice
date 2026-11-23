package CardScoreGame.poker;

import java.util.List;

/** Ordered ranks, lowest first. Ranks are strings so multi-character ranks like "10" work. */
public final class RankOrder {
    private final List<String> ranks;

    public RankOrder(List<String> ranks) {
        if (ranks == null || ranks.isEmpty()) {
            throw new IllegalArgumentException("Ranks must not be empty");
        }
        for (int i = 0; i < ranks.size(); i++) {
            String rank = ranks.get(i);
            if (rank == null || rank.isBlank() || ranks.indexOf(rank) != i) {
                throw new IllegalArgumentException("Invalid or duplicate rank '" + rank + "' in: " + ranks);
            }
        }
        this.ranks = List.copyOf(ranks);
    }

    public boolean isValid(String rank) {
        return ranks.contains(rank);
    }

    public int rankOf(String rank) {
        int index = ranks.indexOf(rank);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown rank '" + rank + "'");
        }
        return index;
    }
}
