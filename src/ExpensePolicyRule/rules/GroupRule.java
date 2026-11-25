package ExpensePolicyRule.rules;

import ExpensePolicyRule.dto.Expense;
import ExpensePolicyRule.GroupBy;
import ExpensePolicyRule.dto.Violation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class GroupRule {
    private final String id;
    private final GroupBy groupBy;
    private final Predicate<Expense> counts;
    private final long limitCents;

    public GroupRule(String id, GroupBy groupBy, Predicate<Expense> counts, long limitCents) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("blank id");
        if (groupBy == null) throw new IllegalArgumentException("null groupBy");
        if (counts == null) throw new IllegalArgumentException("null counts");
        if (limitCents < 0) throw new IllegalArgumentException("negative limit");
        this.id = id;
        this.groupBy = groupBy;
        this.counts = counts;
        this.limitCents = limitCents;
    }

    public String id() {
        return id;
    }

    public GroupBy groupBy() {
        return groupBy;
    }

    // Sums the counted members of one group; strictly greater than the limit is a violation
    public Optional<Violation> evaluate(String groupKey, List<Expense> members) {
        long total = 0;
        List<String> countedIds = new ArrayList<>();
        for (Expense e : members) {
            if (!counts.test(e)) continue;
            total = Math.addExact(total, e.amount());
            countedIds.add(e.expenseId());
        }
        if (total <= limitCents) return Optional.empty();
        return Optional.of(new Violation(id, groupKey, "group over limit", countedIds));
    }
}
