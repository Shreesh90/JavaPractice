package CardScoreGame;

import java.util.HashMap;
import java.util.Map;

public final class LabelOrder {
    private final String labels;
    private final Map<Character, Integer> rankByLabel = new HashMap<>();

    public LabelOrder(String labels) {
        if (labels == null || labels.isEmpty()) {
            throw new IllegalArgumentException("Labels must not be empty");
        }
        for (int rank = 0; rank < labels.length(); rank++) {
            char label = labels.charAt(rank);
            if (label == '?' || rankByLabel.put(label, rank) != null) {
                throw new IllegalArgumentException("Invalid or duplicate label '" + label + "' in: " + labels);
            }
        }
        this.labels = labels;
    }

    public boolean isValid(char label) {
        return rankByLabel.containsKey(label);
    }

    public int rankOf(char label) {
        Integer rank = rankByLabel.get(label);
        if (rank == null) {
            throw new IllegalArgumentException("Unknown card label '" + label + "'");
        }
        return rank;
    }

    public char labelAt(int rank) {
        if (rank < 0 || rank >= labels.length()) {
            throw new IllegalArgumentException("Rank out of range: " + rank);
        }
        return labels.charAt(rank);
    }

    public int size() {
        return labels.length();
    }
}
