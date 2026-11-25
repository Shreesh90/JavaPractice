package ExpensePolicyRule.rules;

import ExpensePolicyRule.dto.Expense;
import ExpensePolicyRule.dto.Violation;

import java.util.Optional;

public interface Rule {
    String id();
    Optional<Violation> evaluate(Expense e);
}
