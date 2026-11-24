package TaskManager;

import java.util.List;

record Task(int id, String description, int dueDate, boolean isPriorityTask,
            int createdDate, String assignee, boolean done, int parentTaskId, List<Integer> dependencyIds) {

    Task(int id, String description, int dueDate, boolean isPriorityTask,
         int createdDate, String assignee, boolean done, int parentTaskId) {
        this(id, description, dueDate, isPriorityTask, createdDate, assignee, done, parentTaskId, List.of());
    }
}
