package Spreadsheet;

public record CellRef(int row, int col) {
    private static final int COLUMNS = 26;

    public CellRef {
        if (row < 0 || col < 0 || col >= COLUMNS) {
            throw new IllegalArgumentException("Invalid cell position: row=" + row + ", col=" + col);
        }
    }

    public static CellRef parse(String id) {
        if (id == null || !id.matches("[A-Z][1-9][0-9]*")) {
            throw new IllegalArgumentException("Invalid cell id: " + id);
        }
        return new CellRef(Integer.parseInt(id.substring(1)) - 1, id.charAt(0) - 'A');
    }

    public String toId() {
        return (char) ('A' + col) + String.valueOf(row + 1);
    }
}
