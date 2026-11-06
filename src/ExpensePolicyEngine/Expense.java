package ExpensePolicyEngine;

public class Expense {
    String id;
    String employeeID;
    SpendCategory category;
    String tripId;
    String vendor;
    long amount;
    long date;

    public Expense(String id, String employeeID, SpendCategory category, String tripId, String vendor, long amount, long date) {
        this.id = id;
        this.employeeID = employeeID;
        this.category = category;
        this.tripId = tripId;
        this.vendor = vendor;
        this.amount = amount;
        this.date = date;
    }
}
