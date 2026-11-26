package ExpensePolicyRule;

import ExpensePolicyRule.dto.Expense;
import ExpensePolicyRule.dto.Violation;
import ExpensePolicyRule.rules.AmountLimitRule;
import ExpensePolicyRule.rules.CompoundRule;
import ExpensePolicyRule.rules.GroupRule;
import ExpensePolicyRule.rules.ProhibitedRule;
import ExpensePolicyRule.rules.Rule;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        rulesWithPredicates();
        System.out.println();
        groupRules();
        System.out.println();
        compoundRules();
    }

    // Building blocks A and F are not reported themselves; only X, built from them, is passed to the engine
    static void compoundRules() {
        Rule restaurantOver75 = new AmountLimitRule("A", 7500, e -> "restaurant".equals(e.sellerType()));
        Rule isFood = new ProhibitedRule("F", ExpenseType.FOOD);                       // "matches" = type is FOOD
        Rule notFood = new CompoundRule("NF", CompoundRule.Op.NOT, List.of(isFood));
        Rule x = new CompoundRule("X", CompoundRule.Op.AND, List.of(restaurantOver75, notFood));  // pricey non-meal at a restaurant

        List<Expense> expenses = List.of(
                new Expense("E1", null, null, ExpenseType.FOOD, 9000, "restaurant", "ABC"),            // A ✓, food → no
                new Expense("E2", null, null, ExpenseType.ENTERTAINMENT, 12000, "restaurant", "ABC"),  // A ✓, not food → X
                new Expense("E3", null, null, ExpenseType.ENTERTAINMENT, 4000, "restaurant", "ABC"));  // A ✗ → no, NF never checked

        for (Violation v : new ExpenseRuleEngine().evaluateRules(List.of(x), expenses)) {
            System.out.println(v.subjectId() + " " + v.ruleId() + " " + v.message());
        }
    }

    // Trip T1 shared by Alice and Bob; E6 has no trip, so no group rule sees it
    static void groupRules() {
        List<GroupRule> groupRules = List.of(
                new GroupRule("G1", GroupBy.TRIP, e -> true, 200000),                          // trip total over $2000
                new GroupRule("G2", GroupBy.TRIP, e -> e.type() == ExpenseType.FOOD, 20000),   // trip meals over $200
                new GroupRule("G3", GroupBy.TRIP_EMPLOYEE, e -> true, 120000));                // per person per trip over $1200

        List<Expense> expenses = List.of(
                new Expense("E1", "T1", "Alice", ExpenseType.AIRFARE, 90000, "airline", "AirX"),
                new Expense("E2", "T1", "Alice", ExpenseType.FOOD, 15000, "restaurant", "ABC"),
                new Expense("E3", "T1", "Bob", ExpenseType.AIRFARE, 80000, "airline", "AirX"),
                new Expense("E4", "T1", "Bob", ExpenseType.FOOD, 10000, "restaurant", "ABC"),
                new Expense("E5", "T1", "Bob", ExpenseType.HOTEL, 35000, "hotel", "Inn"),
                new Expense("E6", null, "Bob", ExpenseType.FOOD, 5000, "restaurant", "ABC"));

        // Expected: T1 G1 [E1..E5] (2300), T1 G2 [E2, E4] (250), T1/Bob G3 [E3, E4, E5] (1250); Alice is 1050, fine
        for (Violation v : new ExpenseRuleEngine().evaluateRules(List.of(), groupRules, expenses)) {
            System.out.println(v.subjectId() + " " + v.ruleId() + " " + v.message() + " " + v.expenseIds());
        }
    }

    // The same AmountLimitRule class becomes different rules by passing a different predicate
    static void rulesWithPredicates() {
        List<Rule> rules = List.of(
                new AmountLimitRule("R1", 17500, e -> true),                                // any expense over $175
                new AmountLimitRule("R2", 4500, e -> "restaurant".equals(e.sellerType())),  // restaurant over $45
                new ProhibitedRule("R3", ExpenseType.ENTERTAINMENT));

        List<Expense> expenses = List.of(
                new Expense("E1", null, null, ExpenseType.FOOD, 25000, "restaurant", "ABC"),     // R1, R2
                new Expense("E2", null, null, ExpenseType.ENTERTAINMENT, 3000, "venue", "Cinema"),// R3
                new Expense("E3", null, null, ExpenseType.FOOD, 4500, "restaurant", "ABC"));      // none (exactly 45)

        for (Violation v : new ExpenseRuleEngine().evaluateRules(rules, expenses)) {
            System.out.println(v.subjectId() + " " + v.ruleId() + " " + v.message());
        }
    }
}
