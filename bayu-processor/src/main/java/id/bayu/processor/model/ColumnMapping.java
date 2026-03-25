package id.bayu.processor.model;

public record ColumnMapping(
        String fieldName,
        String columnName,
        String javaType,
        String qualifiedJavaType,
        boolean isId,
        boolean isGenerated,
        boolean nullable,
        boolean isCreatedAt,
        boolean isUpdatedAt,
        boolean isElementCollection,
        boolean isOneToMany,
        String oneToManyMappedBy,
        String oneToManyTargetType,
        String elementCollectionItemType
) {
    // Backwards-compatible constructor
    public ColumnMapping(String fieldName, String columnName, String javaType,
                         String qualifiedJavaType, boolean isId, boolean isGenerated, boolean nullable) {
        this(fieldName, columnName, javaType, qualifiedJavaType, isId, isGenerated, nullable,
                false, false, false, false, null, null, null);
    }

    public String jdbcGetter() {
        if (isElementCollection) return "getString"; // JSON column
        return switch (qualifiedJavaType) {
            case "java.lang.String" -> "getString";
            case "java.lang.Integer", "int" -> "getInt";
            case "java.lang.Long", "long" -> "getLong";
            case "java.lang.Double", "double" -> "getDouble";
            case "java.lang.Float", "float" -> "getFloat";
            case "java.lang.Boolean", "boolean" -> "getBoolean";
            case "java.lang.Short", "short" -> "getShort";
            case "java.lang.Byte", "byte" -> "getByte";
            case "java.math.BigDecimal" -> "getBigDecimal";
            case "java.time.LocalDate" -> "getObject";
            case "java.time.LocalDateTime" -> "getObject";
            case "java.time.Instant" -> "getObject";
            case "java.util.UUID" -> "getObject";
            default -> "getObject";
        };
    }

    public String jdbcSetter() {
        if (isElementCollection) return "setString"; // JSON column
        return switch (qualifiedJavaType) {
            case "java.lang.String" -> "setString";
            case "java.lang.Integer", "int" -> "setInt";
            case "java.lang.Long", "long" -> "setLong";
            case "java.lang.Double", "double" -> "setDouble";
            case "java.lang.Float", "float" -> "setFloat";
            case "java.lang.Boolean", "boolean" -> "setBoolean";
            case "java.lang.Short", "short" -> "setShort";
            case "java.lang.Byte", "byte" -> "setByte";
            case "java.math.BigDecimal" -> "setBigDecimal";
            case "java.time.LocalDate", "java.time.LocalDateTime", "java.time.Instant" -> "setObject";
            case "java.util.UUID" -> "setObject";
            default -> "setObject";
        };
    }

    public boolean needsTypecastOnGet() {
        if (isElementCollection) return false;
        return switch (qualifiedJavaType) {
            case "java.time.LocalDate", "java.time.LocalDateTime", "java.time.Instant", "java.util.UUID" -> true;
            default -> false;
        };
    }

    public boolean isUuid() {
        return "java.util.UUID".equals(qualifiedJavaType);
    }

    public boolean isRegularColumn() {
        return !isOneToMany;
    }

    public String sqlType() {
        if (isElementCollection) return "TEXT"; // JSON stored as TEXT
        return switch (qualifiedJavaType) {
            case "java.lang.String" -> nullable ? "VARCHAR(255)" : "VARCHAR(255) NOT NULL";
            case "java.lang.Integer", "int" -> "INT";
            case "java.lang.Long", "long" -> "BIGINT";
            case "java.lang.Double", "double" -> "DOUBLE";
            case "java.lang.Float", "float" -> "FLOAT";
            case "java.lang.Boolean", "boolean" -> "BOOLEAN";
            case "java.math.BigDecimal" -> "DECIMAL(19,4)";
            case "java.time.LocalDate" -> "DATE";
            case "java.time.LocalDateTime" -> "TIMESTAMP";
            case "java.time.Instant" -> "TIMESTAMP";
            case "java.util.UUID" -> "VARCHAR(36)";
            default -> "TEXT";
        };
    }
}
