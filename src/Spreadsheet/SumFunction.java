package Spreadsheet;

import java.util.List;
import java.util.Objects;
import java.util.function.ToLongFunction;

public class SumFunction {
    private final ToLongFunction<CellRef> cellValue;

    public SumFunction(ToLongFunction<CellRef> cellValue) {
        Objects.requireNonNull(cellValue);
        this.cellValue = cellValue;
    }

    public long evaluate(List<String> arguments) {
        long total = 0;
        for (String argument : arguments) {
            total += evaluateArgument(argument.trim());
        }
        return total;
    }

    private long evaluateArgument(String argument) {
        if (argument.contains(":")) {
            return sumRange(argument);
        }
        if (argument.matches("-?[0-9]+")) {
            return Long.parseLong(argument);
        }
        return cellValue.applyAsLong(CellRef.parse(argument));
    }

    private long sumRange(String range) {
        String[] ends = range.split(":");
        if (ends.length != 2) {
            throw new IllegalArgumentException("Invalid range: " + range);
        }
        CellRef first = CellRef.parse(ends[0]);
        CellRef second = CellRef.parse(ends[1]);
        int minRow = Math.min(first.row(), second.row());
        int maxRow = Math.max(first.row(), second.row());
        int minCol = Math.min(first.col(), second.col());
        int maxCol = Math.max(first.col(), second.col());

        long total = 0;
        for (int r = minRow; r <= maxRow; r++) {
            for (int c = minCol; c <= maxCol; c++) {
                total += cellValue.applyAsLong(new CellRef(r, c));
            }
        }
        return total;
    }
}
