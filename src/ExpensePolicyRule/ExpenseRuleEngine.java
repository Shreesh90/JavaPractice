package ExpensePolicyRule;

import ExpensePolicyRule.dto.Expense;
import ExpensePolicyRule.dto.Violation;
import ExpensePolicyRule.rules.GroupRule;
import ExpensePolicyRule.rules.Rule;

import java.util.*;

public class ExpenseRuleEngine {

    public List<Violation> evaluateRules(List<Rule> rules, List<Expense> expenses) {
        return evaluateRules(rules, List.of(), expenses);
    }

    // Knows nothing about rule types: new rules are new Rule instances, this loop never changes
    public List<Violation> evaluateRules(List<Rule> rules, List<GroupRule> groupRules, List<Expense> expenses) {
        if (rules == null || groupRules == null || expenses == null) throw new IllegalArgumentException("null input");
        Set<String> ruleIds = new HashSet<>();
        for (Rule rule : rules) {
            if (rule == null) throw new IllegalArgumentException("null rule");
            if (!ruleIds.add(rule.id())) throw new IllegalArgumentException("duplicate rule");
        }
        for (GroupRule rule : groupRules) {
            if (rule == null) throw new IllegalArgumentException("null rule");
            if (!ruleIds.add(rule.id())) throw new IllegalArgumentException("duplicate rule");
        }

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
}
