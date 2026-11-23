package Spreadsheet;

import java.util.Arrays;
import java.util.List;

public final class Utils {
    private static final String SUM_PREFIX = "SUM(";

    private Utils() {
    }

    static long apply(long sum, char operator, long value) {
        return operator == '+' ? sum + value : sum - value;
    }

    static boolean isSumCall(String token) {
        return token.startsWith(SUM_PREFIX) && token.endsWith(")");
    }

    static List<String> sumArguments(String token) {
        String inner = token.substring(SUM_PREFIX.length(), token.length() - 1);
        return inner.isEmpty() ? List.of() : Arrays.asList(inner.split(","));
    }

    static void check(String name, long actual, long expected) {
        System.out.println((actual == expected ? "PASS " : "FAIL ") + name
                + " -> expected " + expected + ", got " + actual);
    }

    static void checkRaw(String name, String actual, String expected) {
        System.out.println((actual.equals(expected) ? "PASS " : "FAIL ") + name
                + " -> expected \"" + expected + "\", got \"" + actual + "\"");
    }
}
