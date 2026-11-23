package CardScoreGame;

/** Parts 1–5: card by card from the most recently dealt (last index) to the first. */
public final class PositionalTieBreaker implements TieBreaker {
    @Override
    public int compare(Hand hand1, Hand hand2) {
        for (int i = Hand.SIZE - 1; i >= 0; i--) {
            int byRank = Integer.compare(hand1.ranks().get(i), hand2.ranks().get(i));
            if (byRank != 0) return byRank;
        }
        return 0;
    }
}
