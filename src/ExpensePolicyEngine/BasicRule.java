package ExpensePolicyEngine;

public class BasicRule {
    String id;
    SpendCategory category;
    long limit;
    public BasicRule(String id, SpendCategory cat, long limit) {
        this.id = id;
        this.category = cat;
        this.limit = limit;
    }
}
