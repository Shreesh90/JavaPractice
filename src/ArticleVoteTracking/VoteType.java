package ArticleVoteTracking;

public enum VoteType {
    UP(1),
    NONE(0),
    DOWN(-1);

    private final int value;

    VoteType(int i) {
        this.value = i;
    }

    int getValue() {
        return value;
    }

    boolean isFlip(VoteType voteType) {
        return Math.abs(this.value - voteType.value) == 2;
    }
}
