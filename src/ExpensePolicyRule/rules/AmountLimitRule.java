package ExpensePolicyRule.rules;

import ExpensePolicyRule.dto.Expense;
import ExpensePolicyRule.dto.Violation;

import java.util.Optional;
import java.util.function.Predicate;

public class AmountLimitRule implements Rule {
    private final String id;
    private final long limitCents;
    private final Predicate<Expense> appliesTo;

    public AmountLimitRule(String id, long limitCents, Predicate<Expense> appliesTo) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("blank id");
        if (limitCents < 0) throw new IllegalArgumentException("negative limit");
        if (appliesTo == null) throw new IllegalArgumentException("null scope");
        this.id = id;
        this.limitCents = limitCents;
        this.appliesTo = appliesTo;
    }

    @Override
    public String id() {
        return this.id;
    }

    // Strictly greater: an amount exactly at the limit is allowed
    @Override
    public Optional<Violation> evaluate(Expense e) {
        if (!appliesTo.test(e) || e.amount() <= limitCents) return Optional.empty();
        return Optional.of(new Violation(id, e.expenseId(), "over limit"));
    }
}
