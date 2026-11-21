package ArticleVoteTracking;

public enum VoteType {
    UP(1),
    NONE(0),
    DOWN(-1),
    DEFAULT(-2);

    private final int value;

    VoteType(int i) {
        this.value = i;
    }

    int getValue() {
        return value;
    }
}
