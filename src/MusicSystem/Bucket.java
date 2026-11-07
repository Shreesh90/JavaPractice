package MusicSystem;

import java.util.HashSet;
import java.util.Set;

public class Bucket {
    int count;
    Set<String> songs = new HashSet<>();
    Bucket prev, next;
    Bucket(int count) {this.count = count;}
}
