package Spreadsheet;

import java.util.Objects;

public class Cell {
    private final CellRef ref;
    private final String raw;

    public Cell(CellRef ref, String raw) {
        Objects.requireNonNull(ref);
        Objects.requireNonNull(raw);

        this.ref = ref;
        this.raw = raw;
    }

    public String getId() {
        return ref.toId();
    }

    public int getRow() {
        return ref.row();
    }

    public int getColumn() {
        return ref.col();
    }

    public String getRaw() {
        return raw;
    }
}
