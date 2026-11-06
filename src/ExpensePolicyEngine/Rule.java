package ExpensePolicyEngine;

public interface Rule {
    public Violation check(Expense e);
}
