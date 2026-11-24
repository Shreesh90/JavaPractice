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
        rejectCycle(normalized);
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
        List<Integer> deps = task.dependencyIds() == null ? List.of() : List.copyOf(task.dependencyIds());
        return new Task(task.id(), task.description().trim(), task.dueDate(), task.isPriorityTask(),
                task.createdDate(), assignee, task.done(), task.parentTaskId(), deps);
    }

    // Kahn's algorithm; among ready tasks, pick by TASK_ORDER
    public List<Integer> scheduleOrder() {
        Map<Integer, Task> pending = new HashMap<>();
        for (Task t : tasks) {
            if (t.assignee().isEmpty() && !t.done()) pending.put(t.id(), t);
        }

        Map<Integer, List<Integer>> dependents = new HashMap<>();
        Map<Integer, Integer> inDegree = new HashMap<>();
        Deque<Integer> toBlock = new ArrayDeque<>();
        for (Task t : pending.values()) {
            int degree = 0;
            for (int depId : t.dependencyIds()) {
                Task dep = findById(depId).orElseThrow(() ->
                        new IllegalArgumentException("Unknown dependency " + depId + " for task " + t.id()));
                if (dep.done()) continue;
                degree++;   // a filtered dependency is never scheduled, so this never reaches 0
                if (pending.containsKey(depId)) {
                    dependents.computeIfAbsent(depId, k -> new ArrayList<>()).add(t.id());
                } else {
                    toBlock.add(t.id());
                }
            }
            inDegree.put(t.id(), degree);
        }

        PriorityQueue<Task> ready = new PriorityQueue<>(TASK_ORDER);
        for (Task t : pending.values()) {
            if (inDegree.get(t.id()) == 0) ready.add(t);
        }
        List<Integer> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            Task t = ready.poll();
            order.add(t.id());
            for (int next : dependents.getOrDefault(t.id(), List.of())) {
                if (inDegree.merge(next, -1, Integer::sum) == 0) ready.add(pending.get(next));
            }
        }

        // Unscheduled tasks are either blocked (transitively) or in/behind a cycle
        Set<Integer> blocked = new HashSet<>();
        while (!toBlock.isEmpty()) {
            int id = toBlock.poll();
            if (blocked.add(id)) toBlock.addAll(dependents.getOrDefault(id, List.of()));
        }
        Set<Integer> stuck = new TreeSet<>(pending.keySet());
        stuck.removeAll(order);
        stuck.removeAll(blocked);
        if (!stuck.isEmpty()) throw new IllegalArgumentException("Dependency cycle involving " + stuck);
        return order;
    }

    public List<String> printTasks() {
        List<String> lines = new ArrayList<>();
        Map<Integer, Boolean> visible = new HashMap<>();
        tasks.stream()
                .filter(t -> isVisible(t, visible))
                .sorted(TASK_ORDER)
                .forEach(t -> lines.add(format(t)));
        return lines;
    }

    // Visible = passes the filter and parent is visible; a missing parent ends the chain
    private boolean isVisible(Task task, Map<Integer, Boolean> memo) {
        Boolean cached = memo.get(task.id());
        if (cached != null) return cached;
        boolean result = task.assignee().isEmpty() && !task.done()
                && findById(task.parentTaskId()).map(p -> isVisible(p, memo)).orElse(true);
        memo.put(task.id(), result);
        return result;
    }

    // Earlier tasks are acyclic, so any new cycle must pass through this task
    private void rejectCycle(Task task) {
        List<Integer> path = new ArrayList<>(List.of(task.id()));
        int current = task.parentTaskId();
        while (current != 0) {
            path.add(current);
            if (current == task.id())
                throw new IllegalArgumentException("Cycle in parent links: " + path);
            Optional<Task> parent = findById(current);
            if (parent.isEmpty()) return;
            current = parent.get().parentTaskId();
        }
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

        TaskManager p3 = new TaskManager();
        p3.addTask(new Task(3, "Child", 3, false, 3, null, false, 2));
        p3.addTask(new Task(2, "Parent", 2, false, 2, null, false, 1));
        p3.addTask(new Task(4, "Unrelated", 4, false, 4, null, false, 0));
        p3.addTask(new Task(1, "Grandparent", 1, false, 1, "user123", false, 0));
        System.out.println(p3.printTasks());

        p3.addTask(new Task(8, "A", 5, false, 5, null, false, 9));
        try {
            p3.addTask(new Task(9, "B", 5, false, 6, null, false, 8));
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }

        TaskManager p4 = new TaskManager();
        p4.addTask(new Task(1, "Setup DB", 5, false, 1, null, false, 0));
        p4.addTask(new Task(2, "API", 3, true, 2, null, false, 0, List.of(1)));
        p4.addTask(new Task(3, "UI", 2, false, 3, null, false, 0, List.of(2)));
        p4.addTask(new Task(4, "Docs", 1, false, 4, null, false, 0));
        p4.addTask(new Task(5, "Old", 1, false, 5, null, true, 0));
        p4.addTask(new Task(6, "Cleanup", 4, false, 6, null, false, 0, List.of(5)));
        p4.addTask(new Task(7, "Blocked", 1, false, 7, null, false, 0, List.of(8)));
        p4.addTask(new Task(8, "Infra", 1, false, 8, "bob", false, 0));
        System.out.println(p4.scheduleOrder());

        TaskManager cycle = new TaskManager();
        cycle.addTask(new Task(1, "B1", 1, false, 1, null, false, 0, List.of(2)));
        cycle.addTask(new Task(2, "B2", 1, false, 2, null, false, 0, List.of(1)));
        try {
            cycle.scheduleOrder();
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }

        TaskManager unknown = new TaskManager();
        unknown.addTask(new Task(1, "C1", 1, false, 1, null, false, 0, List.of(99)));
        try {
            unknown.scheduleOrder();
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }

}
