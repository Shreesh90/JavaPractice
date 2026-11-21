package ArticleVoteTracking;

import java.util.*;

public class VoteTrackingSystem {

    public record VoteEvent(String articleId, VoteType voteType, long count) { } // Part1
    public record FlipEvent(String userId, VoteType fromVote, VoteType toVote, long count) { } // Part3

    Map<String, Map<String, VoteType>> userArticleVoteType = new HashMap<>(); // Part1
    Map<String, Deque<VoteEvent>> usersVotes = new HashMap<>(); // Part1
    Map<String, Integer> articleVoteCount = new HashMap<>(); // Part2
    Map<String, Deque<FlipEvent>> articleFlipEventMap = new HashMap<>(); // Part3
    TreeMap<Integer, TreeSet<String>> scoreToArticles = new TreeMap<>(); // Part4
    Map<String, LinkedHashMap<String, FlipEvent>> uniqueFlipsPerUser = new HashMap<>(); // Part5


    private long globalSequenceNumber = 0;

    public VoteTrackingSystem() {
        this.globalSequenceNumber=1;
    }

    public void vote(String userId, String articleId, VoteType voteType) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(articleId, "articleId cannot be null");
        Objects.requireNonNull(voteType, "voteType cannot be null");

        Map<String, VoteType> temp = userArticleVoteType.get(userId);
        VoteType prevVote = temp == null ? VoteType.NONE : temp.getOrDefault(articleId, VoteType.NONE);
        if(prevVote == voteType) return;

        // Record Flip Event
        if(prevVote.isFlip(voteType)) {
            FlipEvent flipEvent = new FlipEvent(userId, prevVote, voteType, globalSequenceNumber);
            Deque<FlipEvent> articleFlips = articleFlipEventMap.computeIfAbsent(articleId, k -> new ArrayDeque<>());
            articleFlips.addLast(flipEvent);
            if(articleFlips.size() > 3) {
                articleFlips.removeFirst();
            }

            // Record Unique Flips per User
            LinkedHashMap<String, FlipEvent> userFlips = uniqueFlipsPerUser.computeIfAbsent(userId, k -> new LinkedHashMap<>());
            userFlips.remove(articleId); // Remove existing entry to maintain insertion order
            userFlips.put(articleId, flipEvent);
            if(userFlips.size() > 3) {
                userFlips.pollFirstEntry();
            }
        }

        VoteEvent voteEvent = new VoteEvent(articleId, voteType, globalSequenceNumber);
        globalSequenceNumber++;

        // Record User Event
        Deque<VoteEvent> articleVote = usersVotes.computeIfAbsent(userId, k -> new ArrayDeque<>());
        articleVote.push(voteEvent);
        if(articleVote.size() > 3) {
            articleVote.removeLast();
        }

        int delta = voteType.getValue() - prevVote.getValue();

        // Update Score to Articles Mapping
        int prevScore = articleVoteCount.getOrDefault(articleId, 0);
        int newScore = prevScore + delta;
        if(articleVoteCount.containsKey(articleId)) {
            scoreToArticles.get(prevScore).remove(articleId);
            if (scoreToArticles.get(prevScore).isEmpty()) {
                scoreToArticles.remove(prevScore);
            }
        }
        scoreToArticles.computeIfAbsent(newScore, k -> new TreeSet<>()).add(articleId);

        // Update Article Score
        articleVoteCount.merge(articleId, delta, Integer::sum);

        // Record State
        userArticleVoteType.computeIfAbsent(userId, k -> new HashMap<>()).put(articleId, voteType);
    }

    public List<VoteEvent> getLastVotes(String userId) {
        Deque<VoteEvent> articleVote = usersVotes.getOrDefault(userId, new ArrayDeque<>());
        return List.copyOf(articleVote);
    }

    int getScore(String articleId) {
        return articleVoteCount.getOrDefault(articleId, 0);
    }

    List<FlipEvent> getLast3Flips(String articleId) {
        Deque<FlipEvent> flipSequence = articleFlipEventMap.getOrDefault(articleId, new ArrayDeque<>());
        return List.copyOf(flipSequence);
    }

    List<String> getTopK(int k) {
        List<String> topKArticles = new ArrayList<>();
        for(TreeSet<String> articles : scoreToArticles.descendingMap().values()) {
            for(String articleId: articles) {
                if (topKArticles.size() < k) {
                    topKArticles.add(articleId);
                } else {
                    return topKArticles;
                }
            }
        }
        return topKArticles;
    }

    List<FlipEvent> getRecentUniqueFlips(String userId) {
        LinkedHashMap<String, FlipEvent> userFlips = uniqueFlipsPerUser.get(userId);
        if(userFlips == null) {
            return Collections.emptyList();
        }
        return List.copyOf(userFlips.sequencedValues().reversed());
    }

    public static void main(String[] args) {
        VoteTrackingSystem voteTrackingSystem = new VoteTrackingSystem();
        voteTrackingSystem.vote("user1", "article1", VoteType.UP);
        voteTrackingSystem.vote("user1", "article1", VoteType.DOWN);
        voteTrackingSystem.vote("user2", "article1", VoteType.DOWN);
        voteTrackingSystem.vote("user2", "article1", VoteType.UP);
        voteTrackingSystem.vote("user1", "article1", VoteType.NONE);
        voteTrackingSystem.vote("user1", "article1", VoteType.UP);
        voteTrackingSystem.vote("user1", "article1", VoteType.DOWN);
        voteTrackingSystem.vote("user1", "article1", VoteType.DOWN);

        List<VoteEvent> lastVotes = voteTrackingSystem.getLastVotes("user2");
        for (VoteEvent voteEvent : lastVotes) {
            System.out.println(voteEvent);
        }
        System.out.println("Score for article1: " + voteTrackingSystem.getScore("article1"));

        List<FlipEvent> last3Flips = voteTrackingSystem.getLast3Flips("article1");
        System.out.println("Last 3 flips for user1: " + last3Flips);
    }
}
