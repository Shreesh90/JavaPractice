package ExpensePolicyEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupAggregate {
    SpendCategory category;
    GroupBy groupBy;
    Map<String, Long> accumulate = new HashMap<>();
    List<GroupRule> rules = new ArrayList<>();

    public GroupAggregate(SpendCategory category, GroupBy groupBy) {
        this.category = category;
        this.groupBy = groupBy;
    }

    public void addRule(GroupRule rule) {
        int l = 0, r = rules.size()-1;
        while(l<=r) {
            int mid = (l+r)/2;
            if(rules.get(mid).limit >= rule.limit) r = mid-1;
            else l = mid+1;
        }
        rules.add(r, rule);
    }

    public List<Violation> check(Expense e) {
        List<Violation> out = new ArrayList<>();
        String key = groupBy.getKey(e);

        if(key == null || key.isEmpty()) return out;
        long total = accumulate.getOrDefault(key, 0L);
        total += e.amount;
        accumulate.put(key, total);

        int l =0, r=rules.size()-1;
        while(l<=r) {
            int mid = (l+r)/2;
            if(rules.get(mid).limit >= total) r = mid-1;
            else l = mid+1;
        }
        for(int i=0;i<=r;i++) {
            out.add(new Violation(rules.get(i).id, null, key));
        }
        return out;
    }
}
