package Spreadsheet;

public interface Cell {
    String getId();

    int getRow();

    int getColumn();

    // extra added
    static void validateId(String id) {
        if (id == null || !id.matches("[A-Z][1-9][0-9]*")) {
            throw new IllegalArgumentException("Invalid cell id: " + id);
        }
    }

    static int rowOf(String id) {
        validateId(id);
        return Integer.parseInt(id.substring(1)) - 1;
    }

    static int columnOf(String id) {
        validateId(id);
        return id.charAt(0) - 'A';
    }
}
