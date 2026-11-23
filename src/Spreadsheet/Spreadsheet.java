package Spreadsheet;

import static Spreadsheet.Utils.apply;
import static Spreadsheet.Utils.check;
import static Spreadsheet.Utils.checkRaw;
import static Spreadsheet.Utils.isSumCall;
import static Spreadsheet.Utils.sumArguments;

public class Spreadsheet {
    private static final int COLUMNS = 26;

    private final int rows;
    private final Cell[][] cells;
    private final SumFunction sumFunction = new SumFunction(this::computedAt);

    public Spreadsheet(int rows) {
        if (rows <= 0) {
            throw new IllegalArgumentException("Rows must be positive: " + rows);
        }
        this.rows = rows;
        this.cells = new Cell[rows][COLUMNS];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                cells[r][c] = new Cell(new CellRef(r, c), ""); // integer - string, datetime -- Cell<K>
            }
        }
    }

    public void setCell(String id, String raw) {
        CellRef ref = CellRef.parse(id);
        checkBounds(ref);
        cells[ref.row()][ref.col()] = new Cell(ref, raw);
    }

    public void resetCell(String id) {
        setCell(id, "");
    }

    public String getRaw(String id) {
        CellRef ref = CellRef.parse(id);
        checkBounds(ref);
        return cells[ref.row()][ref.col()].getRaw();
    }

    public long getComputed(String id) {
        CellRef ref = CellRef.parse(id);
        return computedAt(ref);
    }

    private long computedAt(CellRef ref) {
        checkBounds(ref);
        String raw = cells[ref.row()][ref.col()].getRaw();
        if (raw.isEmpty()) {
            return 0;
        }
        if (raw.startsWith("=")) {
            return getValue(raw);
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cell " + ref + " is not a number or formula: " + raw);
        }
    }

    public long getValue(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Expression is empty");
        }
        String expr = expression.replace(" ", "");
        if (expr.startsWith("=")) {
            expr = expr.substring(1);
        }

        long sum = 0;
        StringBuilder token = new StringBuilder();
        char pendingOperator = '+';
        int depth = 0;
        for (char ch : expr.toCharArray()) {
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            }
            if (depth == 0 && (ch == '+' || ch == '-')) {
                sum = apply(sum, pendingOperator, operandValue(token.toString()));
                pendingOperator = ch;
                token.setLength(0);
            } else {
                token.append(ch);
            }
        }
        return apply(sum, pendingOperator, operandValue(token.toString()));
    }

    private long operandValue(String token) {
        if (isSumCall(token)) {
            return sumFunction.evaluate(sumArguments(token));
        }
        return !token.isEmpty() && Character.isDigit(token.charAt(0))
                ? Long.parseLong(token)
                : getComputed(token);
    }

    private void checkBounds(CellRef ref) {
        if (ref.row() >= rows || ref.col() >= COLUMNS) {
            throw new IllegalArgumentException("Cell out of range: " + ref);
        }
    }

    public static void main(String[] args) {
        Spreadsheet sheet = new Spreadsheet(10);
        sheet.setCell("A1", "10");
        sheet.setCell("B2", "5");
        sheet.setCell("C3", "3");

        check("A1+B2", sheet.getValue("=A1+B2"), 15);
        check("A1-B2+C3", sheet.getValue("=A1-B2+C3"), 8);
        check("unset cell is 0", sheet.getValue("=A1+Z10"), 10);

        sheet.resetCell("A1");
        check("after reset A1", sheet.getValue("=A1+B2"), 5);

        try {
            sheet.getValue("=A11");
            System.out.println("FAIL out of range: no exception");
        } catch (IllegalArgumentException e) {
            System.out.println("PASS out of range: " + e.getMessage());
        }

        Spreadsheet s = new Spreadsheet(10);
        s.setCell("A1", "10");
        s.setCell("B1", "5");
        s.setCell("C1", "=A1+B1");
        checkRaw("raw C1 formula", s.getRaw("C1"), "=A1+B1");
        check("computed C1", s.getComputed("C1"), 15);
        s.setCell("A1", "20");
        check("formula persists", s.getComputed("C1"), 25);
        s.setCell("D1", "=C1+1");
        check("chain through formula", s.getComputed("D1"), 26);
        s.setCell("C1", "7");
        checkRaw("formula replaced", s.getRaw("C1"), "7");
        check("D1 after replace", s.getComputed("D1"), 8);
        checkRaw("empty raw Z9", s.getRaw("Z9"), "");
        check("empty computed Z9", s.getComputed("Z9"), 0);

        Spreadsheet sumSheet = new Spreadsheet(5);
        sumSheet.setCell("A1", "1");
        sumSheet.setCell("B1", "2");
        sumSheet.setCell("A2", "3");
        sumSheet.setCell("B2", "=A1+10");
        sumSheet.setCell("B3", "4");
        check("SUM range with formula cell", sumSheet.getValue("=SUM(A1:B2)+B3-2"), 19);
        sumSheet.setCell("C1", "=SUM(B2:A1,A1,-5)");
        check("SUM reversed range, repeat cell, negative", sumSheet.getComputed("C1"), 13);
    }
}
