package ExpensePolicyEngine;

public enum GroupBy {
    TRIP, EMPLOYEE;
    public String getKey(Expense e) {
        return this == TRIP ? e.tripId : e.employeeID;
    }
}
