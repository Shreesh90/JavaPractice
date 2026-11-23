package CardScoreGame;

/** Orders two hands of the same type. Returns positive if hand1 is stronger, negative if hand2, 0 if equal. */
public interface TieBreaker {
    int compare(Hand hand1, Hand hand2);
}
