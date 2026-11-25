package ExpensePolicyRule;

import ExpensePolicyRule.dto.Expense;

public enum GroupBy {
    TRIP,
    TRIP_EMPLOYEE;

    // null = the expense is not in any group of this kind
    public String keyOf(Expense e) {
        if (e.tripId() == null || e.tripId().isBlank()) return null;
        return switch (this) {
            case TRIP -> e.tripId();
            case TRIP_EMPLOYEE -> e.employeeID() == null || e.employeeID().isBlank() ? null : e.tripId() + "/" + e.employeeID();
        };
    }
}
