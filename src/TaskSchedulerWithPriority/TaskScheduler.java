package TaskSchedulerWithPriority;

import java.util.*;
import java.util.function.Predicate;

public class TaskScheduler {

    private final List<Task> tasks;
    private final List<Task> scheduledTask;

    public TaskScheduler(List<Task> tasks) {
        this.tasks = tasks;
        this.scheduledTask = computeSchedule();
    }

    private List<Task> computeSchedule() {
        Map<Task, Integer> inorder = new HashMap<>();
        Set<Task> uniqueTasks = new HashSet<>();
        Map<Task, List<Task>> graph = new HashMap<>();

        for(Task task: tasks) {
            for(Task dep: task.dependencies) {
                graph.computeIfAbsent(dep, k-> new ArrayList<>()).add(task);
                if(!uniqueTasks.contains(dep)) {
                    inorder.put(task, inorder.getOrDefault(task,0)+1);
                    uniqueTasks.add(dep);
                }
            }
            uniqueTasks.add(task);
        }

        Comparator<Task> comparator = Comparator
                .comparingInt(Task::getPriority).reversed()
                .thenComparingInt(t -> t.dependencies.size())
                .thenComparing(Task::getId);



        PriorityQueue<Task> q = new PriorityQueue<>(comparator);
//        Queue<Task> q = new ArrayDeque<>();
        List<Task> out = new ArrayList<>();

        for(Task task: uniqueTasks) {
            if(!inorder.containsKey(task)) {
                q.add(task);
//                out.add(task);
            }
        }
        while(!q.isEmpty()) {
            Task task = q.poll();
            out.add(task);
            if(!graph.containsKey(task)) continue;

            for(Task nei: graph.get(task)) {
                inorder.put(nei, inorder.get(nei)-1);
                if(inorder.get(nei) == 0) {
                    q.add(nei);
                }
            }
        }

        if(out.size() != uniqueTasks.size()) throw new RuntimeException("Cycle detected");
        return out;
    }

    public List<Task> filter(Predicate<Task> filter) {
        return scheduledTask
                .stream()
                .filter(filter)
                .toList();
    }
}
