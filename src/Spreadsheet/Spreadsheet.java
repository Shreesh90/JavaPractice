package Spreadsheet;

import java.util.ArrayDeque;
import java.util.Deque;

public class Spreadsheet {
    private static final int COLUMNS = 26;

    private final int rows;
    private final Cell[][] cells;

    public Spreadsheet(int rows) {
        if (rows <= 0) {
            throw new IllegalArgumentException("Rows must be positive: " + rows);
        }
        this.rows = rows;
        this.cells = new Cell[rows][COLUMNS];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                cells[r][c] = new StringCell(idOf(r, c), ""); // integer - string, datetime -- Cell<K>
            }
        }
    }

    public void setCell(String id, String raw) {
        Cell cell = new StringCell(id, raw);
        checkBounds(cell.getRow(), cell.getColumn(), id);
        cells[cell.getRow()][cell.getColumn()] = cell;
    }

    public void resetCell(String id) {
        setCell(id, "");
    }

    public String getRaw(String id) {
        return cellAt(id).getRaw();
    }

    public long getComputed(String id) {
        String raw = cellAt(id).getRaw();
        if (raw.isEmpty()) {
            return 0;
        }
        if (raw.startsWith("=")) {
            return getValue(raw);
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cell " + id + " is not a number or formula: " + raw);
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

        Deque<Long> stack = new ArrayDeque<>();
        StringBuilder token = new StringBuilder();
        char pendingOperator = '+';
        for (char ch : expr.toCharArray()) {
            if (ch == '+' || ch == '-') {
                pushOperand(stack, token.toString(), pendingOperator);
                pendingOperator = ch;
                token.setLength(0);
            } else {
                token.append(ch);
            }
        }
        pushOperand(stack, token.toString(), pendingOperator);

        long result = 0;
        while (!stack.isEmpty()) {
            result += stack.pop();
        }
        return result;
    }

    private void pushOperand(Deque<Long> stack, String id, char operator) {
        long value = !id.isEmpty() && Character.isDigit(id.charAt(0))
                ? Long.parseLong(id)
                : getComputed(id);
        stack.push(operator == '+' ? value : -value);
    }

    private StringCell cellAt(String id) {
        int row = Cell.rowOf(id);
        int col = Cell.columnOf(id);
        checkBounds(row, col, id);
        if (cells[row][col] instanceof StringCell stringCell) {
            return stringCell;
        }
        throw new IllegalStateException("Unsupported cell type at " + id);
    }

    private void checkBounds(int row, int col, String id) {
        if (row < 0 || row >= rows || col < 0 || col >= COLUMNS) {
            throw new IllegalArgumentException("Cell out of range: " + id);
        }
    }

    private static String idOf(int row, int col) {
        return (char) ('A' + col) + String.valueOf(row + 1);
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
    }

    private static void checkRaw(String name, String actual, String expected) {
        System.out.println((actual.equals(expected) ? "PASS " : "FAIL ") + name
                + " -> expected \"" + expected + "\", got \"" + actual + "\"");
    }

    private static void check(String name, long actual, long expected) {
        System.out.println((actual == expected ? "PASS " : "FAIL ") + name
                + " -> expected " + expected + ", got " + actual);
    }
}
