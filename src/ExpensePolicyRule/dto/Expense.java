package ExpensePolicyRule.dto;

import ExpensePolicyRule.ExpenseType;

// amount is in cents
public record Expense(String expenseId, String tripId, String employeeID, ExpenseType type, long amount, String sellerType, String sellerName) {
    public Expense {
        if (expenseId == null || expenseId.isBlank()) throw new IllegalArgumentException("blank id");
        if (type == null) throw new IllegalArgumentException("null type");
        if (amount < 0) throw new IllegalArgumentException("negative amount");
        // "/" separates trip and employee in group keys, so it can't appear inside them
        if ((tripId != null && tripId.contains("/")) || (employeeID != null && employeeID.contains("/")))
            throw new IllegalArgumentException("slash in id");
    }
}
