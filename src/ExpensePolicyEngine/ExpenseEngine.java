package ExpensePolicyEngine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpenseEngine {
    BasicRuleIndex basic = new BasicRuleIndex();
    Map<SpendCategory, List<GroupAggregate>> groups = new HashMap<>();

    public void addBasicRule(BasicRule r) {
        basic.addRule(r);
    }

    public void addGroupRule(GroupRule r, GroupBy groupBy) {
        List<GroupAggregate> list = groups.computeIfAbsent(r.category, k -> new ArrayList<>());
        GroupAggregate agg = new GroupAggregate(r.category, groupBy);
        agg.addRule(r);
        list.add(agg);
    }

    public ExpenseEngine() {
    }

    public List<Violation> submit(Expense e) {
        List<Violation> out = new ArrayList<>(basic.check(e));
        List<GroupAggregate> list = groups.get(e.category);
        for(GroupAggregate ga: list) {
            out.addAll(ga.check(e));
        }
        return out;
    }
}
