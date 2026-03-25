package id.bayu.processor.model;

import java.util.ArrayList;
import java.util.List;

public class EntityMetadata {

    private final String qualifiedName;
    private final String simpleName;
    private final String tableName;
    private final List<ColumnMapping> columns = new ArrayList<>();
    private ColumnMapping idColumn;
    private List<OneToManyRelation> oneToManyRelations = new ArrayList<>();

    public EntityMetadata(String qualifiedName, String simpleName, String tableName) {
        this.qualifiedName = qualifiedName;
        this.simpleName = simpleName;
        this.tableName = tableName;
    }

    public void addColumn(ColumnMapping column) {
        columns.add(column);
        if (column.isId()) {
            this.idColumn = column;
        }
    }

    public String getQualifiedName() { return qualifiedName; }
    public String getSimpleName() { return simpleName; }
    public String getTableName() { return tableName; }
    public List<ColumnMapping> getColumns() { return columns; }
    public ColumnMapping getIdColumn() { return idColumn; }

    /** Columns stored directly in this table (excludes @OneToMany) */
    public List<ColumnMapping> getRegularColumns() {
        return columns.stream().filter(ColumnMapping::isRegularColumn).toList();
    }

    public List<ColumnMapping> getNonIdColumns() {
        return getRegularColumns().stream().filter(c -> !c.isId()).toList();
    }

    /** Columns that are non-ID, non-audit (for INSERT) */
    public List<ColumnMapping> getInsertableColumns() {
        return getRegularColumns().stream()
                .filter(c -> !c.isId() || !c.isGenerated())
                .filter(c -> !c.isCreatedAt() && !c.isUpdatedAt())
                .toList();
    }

    public List<ColumnMapping> getOneToManyColumns() {
        return columns.stream().filter(ColumnMapping::isOneToMany).toList();
    }

    public List<ColumnMapping> getElementCollections() {
        return columns.stream().filter(ColumnMapping::isElementCollection).toList();
    }

    public ColumnMapping findColumnByFieldName(String fieldName) {
        return columns.stream()
                .filter(c -> c.fieldName().equalsIgnoreCase(fieldName))
                .findFirst()
                .orElse(null);
    }

    public List<OneToManyRelation> getOneToManyRelations() { return oneToManyRelations; }
    public void setOneToManyRelations(List<OneToManyRelation> relations) { this.oneToManyRelations = relations; }
    public boolean hasOneToMany() { return !oneToManyRelations.isEmpty(); }

    public boolean hasUuidId() {
        return idColumn != null && idColumn.isUuid();
    }

    public boolean hasAuditFields() {
        return columns.stream().anyMatch(c -> c.isCreatedAt() || c.isUpdatedAt());
    }

    /** Generate DDL CREATE TABLE statement */
    public String generateDDL() {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        sql.append(tableName).append(" (\n");

        List<ColumnMapping> regularCols = getRegularColumns();
        for (int i = 0; i < regularCols.size(); i++) {
            ColumnMapping col = regularCols.get(i);
            sql.append("    ").append(col.columnName()).append(" ").append(col.sqlType());
            if (col.isId()) {
                sql.append(" PRIMARY KEY");
                if (col.isGenerated() && !col.isUuid()) {
                    sql.append(" AUTO_INCREMENT");
                }
            }
            if (i < regularCols.size() - 1) sql.append(",");
            sql.append("\n");
        }

        sql.append(")");
        return sql.toString();
    }
}
