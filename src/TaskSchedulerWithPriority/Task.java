package TaskSchedulerWithPriority;

import java.util.List;
import java.util.Map;

public class Task {
    int id;
    int priority;
    Map<String, String> attributes;
    List<Task> dependencies;

    public Task(String id, int priority, Map<String, String> attributes, List<Task> dependencies) {
        this.id = id;
        this.priority = priority;
        this.attributes = attributes;
        this.dependencies = dependencies;
    }

    public String getId() {
        return id;
    }

    public int getPriority() {
        return priority;
    }
}
