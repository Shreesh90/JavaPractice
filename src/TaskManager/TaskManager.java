package TaskManager;

import java.util.*;

public class TaskManager {
    // -1 (no due date) sorts after every real date
    private static final Comparator<Task> TASK_ORDER = Comparator
            .comparingInt((Task t) -> t.dueDate() == -1 ? Integer.MAX_VALUE : t.dueDate())
            .thenComparing(Task::isPriorityTask, Comparator.reverseOrder())
            .thenComparingInt(Task::createdDate)
            .thenComparingInt(Task::id);

    List<Task> tasks = new ArrayList<>();
    Map<Integer, List<Task>> parentToChildTasks = new HashMap<>();

    public TaskManager() {
    }

    public void addTask(Task task) {
        Task normalized = validateTask(task);
        Optional<Task> existing = findById(normalized.id());
        if (existing.isPresent()) {
            if (existing.get().equals(normalized)) return;
            throw new IllegalArgumentException("Duplicate id with different data: " + normalized.id());
        }
        parentToChildTasks.computeIfAbsent(normalized.parentTaskId(), k -> new ArrayList<>()).add(normalized);
        tasks.add(normalized);
    }

    public Task validateTask(Task task) {
        if (task == null) throw new IllegalArgumentException("task cannot be null");
        if (task.id() <= 0) throw new IllegalArgumentException("id must be > 0: " + task.id());
        if (task.description() == null || task.description().isBlank())
            throw new IllegalArgumentException("description cannot be blank for task " + task.id());
        if (task.dueDate() < -1 || task.dueDate() == 0)
            throw new IllegalArgumentException("dueDate must be -1 or > 0: " + task.dueDate());
        if (task.createdDate() <= 0) throw new IllegalArgumentException("createdDate must be > 0: " + task.createdDate());
        if (task.parentTaskId() < 0 || task.parentTaskId() == task.id())
            throw new IllegalArgumentException("invalid parentTaskId: " + task.parentTaskId());
        String assignee = task.assignee() == null ? "" : task.assignee().trim();
        return new Task(task.id(), task.description().trim(), task.dueDate(), task.isPriorityTask(),
                task.createdDate(), assignee, task.done(), task.parentTaskId());
    }

    public List<String> printTasks() {
        List<String> lines = new ArrayList<>();
        tasks.stream()
                .filter(t -> t.assignee().isEmpty() && !t.done())
                .sorted(TASK_ORDER)
                .forEach(t -> lines.add(format(t)));
        return lines;
    }

    public List<String> printTaskTree() {
        // Roots: no parent, or parent never added
        List<Task> roots = tasks.stream()
                .filter(t -> t.parentTaskId() == 0 || findById(t.parentTaskId()).isEmpty())
                .toList();
        List<String> lines = new ArrayList<>();
        appendTree(roots, 0, lines);
        return lines;
    }

    private void appendTree(List<Task> siblings, int depth, List<String> lines) {
        List<Task> visible = siblings.stream()
                .filter(t -> t.assignee().isEmpty() && !t.done())
                .sorted(TASK_ORDER)
                .toList();
        for (Task task : visible) {
            lines.add("  ".repeat(depth) + task.id() + ": " + task.description());
            appendTree(parentToChildTasks.getOrDefault(task.id(), List.of()), depth + 1, lines);
        }
    }

    private String format(Task task) {
        String line = task.id() + ": " + task.description();
        if (task.parentTaskId() == 0) return line;
        // Parent never added: print without the suffix
        return findById(task.parentTaskId())
                .map(p -> line + ": " + p.id() + ": " + p.description())
                .orElse(line);
    }

    private Optional<Task> findById(int id) {
        return tasks.stream().filter(t -> t.id() == id).findFirst();
    }

    public static void main(String[] args) {
        TaskManager tm = new TaskManager();
        tm.addTask(new Task(1, "Launch", 10, false, 1, null, false, 0));
        tm.addTask(new Task(2, "Design", 4, false, 2, null, false, 1));
        tm.addTask(new Task(3, "Build", 2, false, 3, null, false, 1));
        tm.addTask(new Task(4, "Mockups", 3, false, 4, null, false, 2));
        tm.addTask(new Task(5, "Hire", 5, false, 5, null, false, 0));
        tm.addTask(new Task(6, "Old spike", 1, false, 6, "bob", false, 1));
        tm.addTask(new Task(7, "Spike note", 1, false, 7, null, false, 6));
        tm.printTaskTree().forEach(System.out::println);

        System.out.println(tm.printTasks());
    }

}
