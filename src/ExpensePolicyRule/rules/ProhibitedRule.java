package ExpensePolicyRule.rules;

import ExpensePolicyRule.dto.Expense;
import ExpensePolicyRule.ExpenseType;
import ExpensePolicyRule.dto.Violation;

import java.util.Optional;

public class ProhibitedRule implements Rule {
    private final String id;
    private final ExpenseType prohibitedType;

    public ProhibitedRule(String id, ExpenseType prohibitedType) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("blank id");
        if (prohibitedType == null) throw new IllegalArgumentException("null type");
        this.id = id;
        this.prohibitedType = prohibitedType;
    }

    @Override
    public String id() {
        return this.id;
    }

    @Override
    public Optional<Violation> evaluate(Expense e) {
        if (e.type() != prohibitedType) return Optional.empty();
        return Optional.of(new Violation(id, e.expenseId(), "type prohibited"));
    }
}
