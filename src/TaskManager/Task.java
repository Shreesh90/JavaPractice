package TaskManager;

record Task(int id, String description, int dueDate, boolean isPriorityTask,
            int createdDate, String assignee, boolean done, int parentTaskId) {}