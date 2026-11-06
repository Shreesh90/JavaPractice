package ExpensePolicyEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BasicRuleIndex {
    Map<SpendCategory, List<BasicRule>> byCategory = new HashMap<>();

    public void addRule(BasicRule r) {
        List<BasicRule> list = byCategory.computeIfAbsent(r.category, k -> new ArrayList<>());
        list.add(lower_bound(list, r.limit), r);
    }

    public List<Violation> check(Expense e) {
        List<Violation> out = new ArrayList<>();
        List<BasicRule> rules = byCategory.get(e.category);
        if(rules == null) return out;

        int pivot = lower_bound(rules, e.amount);
        for(int i=0;i<pivot;i++) {
            out.add(new Violation(rules.get(i).id, e.id, null));
        }
        return out;
    }

    int lower_bound(List<BasicRule> list, long t) {
        int l = 0, r = list.size()-1;
        while(l<=r) {
            int mid = (l+r)/2;
            if(list.get(mid).limit>=t) r = mid-1;
            else l = mid+1;
        }
        return r;
    }
}
