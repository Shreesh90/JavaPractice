package KVStore;

import java.util.*;

public class KVStore<K,V> {

    private final Map<K,V> mp = new HashMap<>();

    // Thread unsafe declaration of stack
    // private final Deque<Map<K,V>> st = new ArrayDeque<>();

    // Each thread gets its own private stack
    private final ThreadLocal<Deque<Map<K,V>>> st = ThreadLocal.withInitial(ArrayDeque::new);

    private final Object lock = new Object();

    public KVStore() {}

    public void set(K k,V  v) {
        Objects.requireNonNull(k);
        Objects.requireNonNull(v);

        if(st.get().isEmpty()) {
            synchronized (lock) {
                mp.put(k, v);
            }
        }
        else st.get().peek().put(k,v);
    }

    public V get(K k) {
        Objects.requireNonNull(k);
        for(Map<K,V> temp: st.get()) {
            if(temp.containsKey(k)) return temp.get(k);
        }
        synchronized (lock) {
            return mp.get(k);
        }
    }

    public boolean delete(K k) {
        Objects.requireNonNull(k);

        if(st.get().isEmpty()) {
            synchronized (lock) {
                return mp.remove(k) != null;
            }
        }
        if(get(k) == null) return false;
        st.get().peek().put(k, null);
        return true;
    }

    public void begin() {
        Map<K,V> newMap = new HashMap<>();

        // Below is Thread Unsafe stack of map
        // st.push(newMap);

        // Thread safe stack of map
        st.get().push(newMap);
    }

    public void commit() {
        if(st.get().isEmpty()) throw new IllegalStateException();

        Map<K,V> curr = st.get().pop();
        if(!st.get().isEmpty()) {
            st.get().peek().putAll(curr);
            return;
        }
        synchronized (lock) {
            for(Map.Entry<K,V> entry: curr.entrySet()){
                if (entry.getValue() == null) {
                    mp.remove(entry.getKey());
                } else {
                    mp.put(entry.getKey(), entry.getValue());
                }
            }
        }
  }
    public void rollback() {
        if(st.get().isEmpty()) throw new IllegalStateException();
        st.get().pop();
    }

    public static void main(String[] args){
        KVStore<String, Integer> kvStore = new KVStore<String, Integer>();
        kvStore.set("Hello", 100);
        kvStore.set("Shree", 200);
        kvStore.set("Chh", 300);
        System.out.println(kvStore.get("Shree"));
    }
}
