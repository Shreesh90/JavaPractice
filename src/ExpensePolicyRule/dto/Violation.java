package ExpensePolicyRule.dto;

import java.util.List;

// expenseIds: the members counted by a group violation; empty for a per-expense violation
public record Violation(String ruleId, String subjectId, String message, List<String> expenseIds) {
    public Violation {
        expenseIds = List.copyOf(expenseIds);
    }

    public Violation(String ruleId, String subjectId, String message) {
        this(ruleId, subjectId, message, List.of());
    }
}
