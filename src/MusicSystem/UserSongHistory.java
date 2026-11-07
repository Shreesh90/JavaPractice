package MusicSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserSongHistory {
    private class Node {
        String songId;
        Node next,prev;

        public Node(String songId) {this.songId = songId;}
    }

    int capacity;
    private Map<String, Node> songToNode = new HashMap<>();
    private final Node headNode = new Node(null);
    private final Node tailNode = new Node(null);
    int currentSize=0;

    public UserSongHistory(int capacity) {
        this.capacity = capacity;
        headNode.next = tailNode;
        tailNode.prev = headNode;
    }

    private void addNodeToFront(Node songNode) {
        Node temp = headNode.next;
        songNode.next = temp;
        songNode.prev = headNode;
        headNode.next = songNode;
        temp.prev = songNode;
        songToNode.put(songNode.songId, songNode);
    }

    synchronized public void play(String songId) {
        if (songToNode.containsKey(songId)) {
            Node songNode = songToNode.get(songId);
            Node p = songNode.prev;
            Node n = songNode.next;
            p.next = n;
            n.prev = p;
            addNodeToFront(songNode);
        } else {
            Node songNode = new Node(songId);
            addNodeToFront(songNode);
            currentSize+=1;
        }
        if(currentSize > capacity) {
            Node temp = tailNode.prev;
            temp.prev.next = temp.next;
            temp.next.prev = temp.prev;
            songToNode.remove(temp.songId);
            currentSize--;
        }
    }

    synchronized public List<String> get() {
        return this.get(capacity);
    }

    synchronized public List<String> get(int k) {
        List<String> out = new ArrayList<>();
        Node temp = headNode.next;

        while(temp != tailNode && k>0) {
            out.add(temp.songId);
            temp = temp.next;
            k--;
        }
        return out;
    }
}
