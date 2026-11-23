package CardScoreGame;

public interface HandTypeRule {
    String name();

    int weight();

    boolean matches(Hand hand);
}
