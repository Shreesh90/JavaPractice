package ExpensePolicyRule;

import ExpensePolicyRule.dto.Expense;
import ExpensePolicyRule.dto.Violation;
import ExpensePolicyRule.rules.GroupRule;
import ExpensePolicyRule.rules.Rule;

import java.util.*;

public class ExpenseRuleEngine {
    private final Map<String, Rule> rules = new LinkedHashMap<>();
    private final Map<String, GroupRule> groupRules = new LinkedHashMap<>();
    private final Map<String, Expense> expenses = new LinkedHashMap<>();

    public void addRule(Rule rule) {
        if (rule == null) throw new IllegalArgumentException("null rule");
        if (rules.containsKey(rule.id())) throw new IllegalArgumentException("duplicate rule");
        rules.put(rule.id(), rule);
    }

    public void addGroupRule(GroupRule rule) {
        if (rule == null) throw new IllegalArgumentException("null rule");
        if (groupRules.containsKey(rule.id())) throw new IllegalArgumentException("duplicate rule");
        groupRules.put(rule.id(), rule);
    }

    public void addExpense(Expense expense) {
        if (expense == null) throw new IllegalArgumentException("null expense");
        if (expenses.containsKey(expense.expenseId())) throw new IllegalArgumentException("duplicate expense");
        expenses.put(expense.expenseId(), expense);
    }

    // Evaluated on demand, so a rule added after expenses still checks all of them
    public List<Violation> evaluate() {
        return evaluateRules(new ArrayList<>(rules.values()), new ArrayList<>(groupRules.values()),
                new ArrayList<>(expenses.values()));
    }

    public List<Violation> evaluateRules(List<Rule> rules, List<Expense> expenses) {
        return evaluateRules(rules, List.of(), expenses);
    }

    // Knows nothing about rule types: new rules are new Rule instances, this loop never changes
    public List<Violation> evaluateRules(List<Rule> rules, List<GroupRule> groupRules, List<Expense> expenses) {
        if (rules == null || groupRules == null || expenses == null) throw new IllegalArgumentException("null input");
        checkDuplicateRuleIds(rules, groupRules);

        List<Violation> violations = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (Expense expense : expenses) {
            if (expense == null) throw new IllegalArgumentException("null expense");
            if (!seenIds.add(expense.expenseId())) throw new IllegalArgumentException("duplicate expense");
            for (Rule rule : rules) {
                rule.evaluate(expense).ifPresent(violations::add);
            }
        }
        violations.sort(Comparator.comparing(Violation::subjectId).thenComparing(Violation::ruleId));
        violations.addAll(evaluateGroupRules(groupRules, expenses));
        return violations;
    }

    // One bucketing pass per distinct GroupBy, shared by every group rule that uses it
    private List<Violation> evaluateGroupRules(List<GroupRule> groupRules, List<Expense> expenses) {
        Map<GroupBy, Map<String, List<Expense>>> buckets = new EnumMap<>(GroupBy.class);
        for (GroupRule rule : groupRules) {
            buckets.putIfAbsent(rule.groupBy(), new HashMap<>());
        }
        for (Expense expense : expenses) {
            for (Map.Entry<GroupBy, Map<String, List<Expense>>> entry : buckets.entrySet()) {
                String key = entry.getKey().keyOf(expense);
                if (key != null) entry.getValue().computeIfAbsent(key, k -> new ArrayList<>()).add(expense);
            }
        }

        List<Violation> violations = new ArrayList<>();
        for (GroupRule rule : groupRules) {
            for (Map.Entry<String, List<Expense>> group : buckets.get(rule.groupBy()).entrySet()) {
                rule.evaluate(group.getKey(), group.getValue()).ifPresent(violations::add);
            }
        }
        violations.sort(Comparator.comparing(Violation::subjectId).thenComparing(Violation::ruleId));
        return violations;
    }

    // Ids are unique across both kinds, so a violation's ruleId points to exactly one rule
    private static void checkDuplicateRuleIds(List<Rule> rules, List<GroupRule> groupRules) {
        Set<String> ruleIds = new HashSet<>();
        for (Rule rule : rules) {
            if (rule == null) throw new IllegalArgumentException("null rule");
            if (!ruleIds.add(rule.id())) throw new IllegalArgumentException("duplicate rule");
        }
        for (GroupRule rule : groupRules) {
            if (rule == null) throw new IllegalArgumentException("null rule");
            if (!ruleIds.add(rule.id())) throw new IllegalArgumentException("duplicate rule");
        }
    }
}
