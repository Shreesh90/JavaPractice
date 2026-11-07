package MusicSystem;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MusicSystem {

    // Map of song to its bucket
//    private final Map<String, Bucket> songsToBucket = new HashMap<>();
    private final ConcurrentHashMap<String, Bucket> songsToBucket = new ConcurrentHashMap<>();

    private final Bucket headBucket = new Bucket(Integer.MAX_VALUE);
    private final Bucket tailBucket = new Bucket(Integer.MAX_VALUE);

//    private Map<String, Set<String>> allSongsPlayedByUsers = new HashMap<>();
//    private Map<String, UserSongHistory> userRecentSongHistory = new HashMap<>();

    private ConcurrentHashMap<String, Set<String>> allSongsPlayedByUsers = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, UserSongHistory> userRecentSongHistory = new ConcurrentHashMap<>();
    private final int SONG_HISTORY_CAPACITY = 1000;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public MusicSystem() {
        headBucket.next = tailBucket;
        tailBucket.prev = headBucket;
    }

    public void addSong(String songId) {
        lock.writeLock().lock();
        try {
            if(songsToBucket.containsKey(songId)) return ;
            if(headBucket.next.count == 0) {
                headBucket.next.songs.add(songId);
                songsToBucket.put(songId, headBucket.next);
            } else {
                Bucket zeroBucket = new Bucket(0);
                zeroBucket.prev = headBucket;
                zeroBucket.next = headBucket.next;
                headBucket.next = zeroBucket;
                zeroBucket.next.prev = zeroBucket;
                zeroBucket.songs.add(songId);
                songsToBucket.put(songId, zeroBucket);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void addSongBucket(String songId, int songCount, Bucket temp) {
        if(temp.next.count == songCount) {
            temp.next.songs.add(songId);
            songsToBucket.put(songId, temp.next);
        } else {
            Bucket temp2 = temp.next;
            Bucket newBucket = new Bucket(songCount);

            temp.next = newBucket;
            newBucket.next = temp2;
            temp2.prev = newBucket;
            newBucket.prev = temp;

            newBucket.songs.add(songId);
            songsToBucket.put(songId, newBucket);
        }

    }

    public void playSong(String userId, String songId) {
        if (!songsToBucket.containsKey(songId)) throw new IllegalArgumentException();
        boolean userPlayingFirstTime = allSongsPlayedByUsers.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(songId);

        if (userPlayingFirstTime) {
            lock.writeLock().lock();
            try {
                Bucket songBucket = songsToBucket.get(songId);
                int songCount = songBucket.count;
                songBucket.songs.remove(songId);

                if (songBucket.songs.isEmpty()) {
                    songBucket.prev.next = songBucket.next;
                    songBucket.next.prev = songBucket.prev;
                    addSongBucket(songId, songCount + 1, songBucket.prev);
                } else {
                    addSongBucket(songId, songCount + 1, songBucket);
                }
            } finally{
                lock.writeLock().unlock();
            }
        }
        userRecentSongHistory.computeIfAbsent(userId, k -> new UserSongHistory(SONG_HISTORY_CAPACITY)).play(songId);
    }

    public void printAnalytics() {
        lock.readLock().lock();
        try {
            Bucket songBucket = tailBucket.prev;
            while(songBucket != headBucket) {
                System.out.println(songBucket.count);
                for(String song: songBucket.songs) {
                    System.out.println(song);
                }
                songBucket = songBucket.prev;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public void printRecentlyPlayed(String userId) {
        if(!userRecentSongHistory.containsKey(userId)) return ;
        UserSongHistory userSongHistory = userRecentSongHistory.get(userId);
        List<String> songs = userSongHistory.get();
        for(String song: songs) System.out.println(song);
    }

    public void printRecentlyPlayed(String userId, int k) {
        if(!userRecentSongHistory.containsKey(userId)) return ;
        UserSongHistory userSongHistory = userRecentSongHistory.get(userId);
        List<String> songs = userSongHistory.get(k);
        for(String song: songs) System.out.println(song);
    }
}
