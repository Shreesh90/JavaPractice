package ArticleVoteTracking;

import java.util.*;

public class VoteTrackingSystem {

    public record VoteEvent(String articleId, VoteType voteType, long count) { }
    Map<String, Map<String, VoteType>> userArticleVoteType = new HashMap<>();
    Map<String, Deque<VoteEvent>> usersVotes = new HashMap<>();
    Map<String, Integer> articleVoteCount = new HashMap<>();

    private long globalSequenceNumber = 0;

    public VoteTrackingSystem() {
        this.globalSequenceNumber=1;
    }

    public void vote(String userId, String articleId, VoteType voteType) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(articleId, "articleId cannot be null");
        Objects.requireNonNull(voteType, "voteType cannot be null");

        Map<String, VoteType> temp = userArticleVoteType.get(userId);
        if(temp != null && temp.containsKey(articleId) && temp.get(articleId) == voteType) return;

        VoteEvent voteEvent = new VoteEvent(articleId, voteType, globalSequenceNumber);
        globalSequenceNumber++;

        Deque<VoteEvent> articleVote = usersVotes.computeIfAbsent(userId, k -> new ArrayDeque<>());
        articleVote.push(voteEvent);
        if(articleVote.size() > 3) {
            articleVote.removeLast();
        }

        int prevVote = userArticleVoteType.getOrDefault(userId, new HashMap<>()).getOrDefault(articleId, VoteType.DEFAULT).getValue();
        int newVote = voteType.getValue();
        if(prevVote != -2) {
            int val = newVote - prevVote;
            articleVoteCount.put(articleId, articleVoteCount.getOrDefault(articleId, 0) + val);
        } else {
            articleVoteCount.put(articleId, articleVoteCount.getOrDefault(articleId, 0) + newVote);
        }

        userArticleVoteType.computeIfAbsent(userId, k -> new HashMap<>()).put(articleId, voteType);
    }

    public List<VoteEvent> getLastVotes(String userId) {
        Deque<VoteEvent> articleVote = usersVotes.getOrDefault(userId, new ArrayDeque<>());
        return List.copyOf(articleVote);
    }

    int getScore(String articleId) {
        return articleVoteCount.getOrDefault(articleId, 0);
    }

    public static void main(String[] args) {
        VoteTrackingSystem voteTrackingSystem = new VoteTrackingSystem();
        voteTrackingSystem.vote("user1", "article1", VoteType.UP);
        voteTrackingSystem.vote("user2", "article1", VoteType.UP);
        voteTrackingSystem.vote("user1", "article1", VoteType.DOWN);
        voteTrackingSystem.vote("user2", "article1", VoteType.NONE);
        voteTrackingSystem.vote("user2", "article1", VoteType.NONE);
        voteTrackingSystem.vote("user3", "article1", VoteType.NONE);

        List<VoteEvent> lastVotes = voteTrackingSystem.getLastVotes("user2");
        for (VoteEvent voteEvent : lastVotes) {
            System.out.println(voteEvent);
        }
        System.out.println("Score for article1: " + voteTrackingSystem.getScore("article1"));
    }
}
