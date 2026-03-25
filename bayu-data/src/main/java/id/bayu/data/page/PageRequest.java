package id.bayu.data.page;

public record PageRequest(int page, int size, String sortBy, String sortDir) {

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size, null, "ASC");
    }

    public static PageRequest of(int page, int size, String sortBy, String sortDir) {
        return new PageRequest(page, size, sortBy, sortDir != null ? sortDir.toUpperCase() : "ASC");
    }

    public int offset() {
        return page * size;
    }

    public String orderClause(String defaultSort) {
        String col = sortBy != null ? sortBy : defaultSort;
        if (col == null) return "";
        return " ORDER BY " + col + " " + sortDir;
    }

    public String limitClause() {
        return " LIMIT " + size + " OFFSET " + offset();
    }
}
