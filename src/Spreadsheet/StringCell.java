package Spreadsheet;

public class StringCell implements Cell {
    private final String id;
    private final String raw;

    public StringCell(String id, String raw) {
        Cell.validateId(id);
        if (raw == null) {
            throw new IllegalArgumentException("Raw value cannot be null for " + id);
        }
        this.id = id;
        this.raw = raw;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getRow() {
        return Cell.rowOf(id);
    }

    @Override
    public int getColumn() {
        return Cell.columnOf(id);
    }

    public String getRaw() {
        return raw;
    }
}
