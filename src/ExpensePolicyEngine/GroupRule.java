package ExpensePolicyEngine;

public class GroupRule {
    String id;
    SpendCategory category;
    long limit;

    public GroupRule(String id, SpendCategory cat, long limit) {
        this.id = id;
        this.category = cat;
        this.limit = limit;
    }

}
