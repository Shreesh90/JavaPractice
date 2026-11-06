package ExpensePolicyEngine;

public class Violation {
    String ruleId;
    String expenseId;
    String groupId;

    public Violation(String ruleId, String expenseId, String groupId) {
        this.groupId = groupId;
        this.expenseId = expenseId;
        this.ruleId = ruleId;
    }
}
