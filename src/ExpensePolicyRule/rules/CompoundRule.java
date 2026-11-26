package ExpensePolicyRule.rules;

import ExpensePolicyRule.dto.Expense;
import ExpensePolicyRule.dto.Violation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// A rule built from other rules: a child "matches" when it would report a violation
public class CompoundRule implements Rule {
    public enum Op { AND, OR, NOT }

    private final String id;
    private final Op op;
    private final List<Rule> children;

    // Children are existing Rule objects, so a rule can never reference itself or a later rule: no cycles
    public CompoundRule(String id, Op op, List<Rule> children) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("blank id");
        if (op == null) throw new IllegalArgumentException("null op");
        if (children == null || children.isEmpty()) throw new IllegalArgumentException("no children");
        // contains(null) throws on List.of(...), so check element by element
        for (Rule child : children) {
            if (child == null) throw new IllegalArgumentException("null child");
        }
        if (op == Op.NOT && children.size() != 1) throw new IllegalArgumentException("NOT needs one");
        this.id = id;
        this.op = op;
        this.children = List.copyOf(children);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Optional<Violation> evaluate(Expense e) {
        // Keys are Rule objects: relies on rule classes keeping the default identity-based equals/hashCode
        if (!matches(e, new HashMap<>())) return Optional.empty();
        return Optional.of(new Violation(id, e.expenseId(), "compound matched"));
    }

    // memo: a sub-rule shared inside this tree is evaluated once per expense
    private boolean matches(Expense e, Map<Rule, Boolean> memo) {
        Boolean cached = memo.get(this);
        if (cached != null) return cached;
        // allMatch / anyMatch stop early: AND at the first false, OR at the first true
        boolean result = switch (op) {
            case AND -> children.stream().allMatch(child -> childMatches(child, e, memo));
            case OR -> children.stream().anyMatch(child -> childMatches(child, e, memo));
            case NOT -> !childMatches(children.get(0), e, memo);
        };
        memo.put(this, result);
        return result;
    }

    private static boolean childMatches(Rule child, Expense e, Map<Rule, Boolean> memo) {
        if (child instanceof CompoundRule compound) return compound.matches(e, memo);
        Boolean cached = memo.get(child);
        if (cached == null) {
            cached = child.evaluate(e).isPresent();
            memo.put(child, cached);
        }
        return cached;
    }
}
