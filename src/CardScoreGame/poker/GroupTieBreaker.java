package CardScoreGame.poker;

import CardScoreGame.Hand;
import CardScoreGame.TieBreaker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Part 6: group cards by rank, order groups by (count desc, rank desc), compare group ranks in that order. */
public final class GroupTieBreaker implements TieBreaker {
    @Override
    public int compare(Hand hand1, Hand hand2) {
        List<Integer> order1 = groupRanks(hand1);
        List<Integer> order2 = groupRanks(hand2);
        for (int i = 0; i < Math.min(order1.size(), order2.size()); i++) {
            int byRank = Integer.compare(order1.get(i), order2.get(i));
            if (byRank != 0) return byRank;
        }
        return 0;
    }

    private List<Integer> groupRanks(Hand hand) {
        Map<Integer, Integer> countByRank = new HashMap<>();
        for (int rank : hand.ranks()) {
            countByRank.merge(rank, 1, Integer::sum);
        }
        List<Map.Entry<Integer, Integer>> groups = new ArrayList<>(countByRank.entrySet());
        groups.sort(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry.<Integer, Integer>comparingByKey().reversed()));

        List<Integer> ranks = new ArrayList<>(groups.size());
        for (Map.Entry<Integer, Integer> group : groups) {
            ranks.add(group.getKey());
        }
        return ranks;
    }
}
