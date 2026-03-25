package id.bayu.processor.parser;

import id.bayu.processor.model.ColumnMapping;
import id.bayu.processor.model.EntityMetadata;
import id.bayu.processor.model.QueryMethodDefinition;
import id.bayu.processor.model.QueryMethodDefinition.QueryParam;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Spring Data-style method names into SQL queries at compile time.
 *
 * Supports:
 *   findBy{Property}[Operator][And|Or]{Property}[Operator][OrderBy{Property}[Asc|Desc]]
 *   countBy{Property}...
 *   existsBy{Property}...
 *   deleteBy{Property}...
 *
 * Operators: LessThan, GreaterThan, LessThanEqual, GreaterThanEqual,
 *            Like, Containing, StartingWith, EndingWith,
 *            In, Between, IsNull, IsNotNull, Not, NotNull, True, False
 */
public class QueryMethodParser {

    private static final Pattern SUBJECT_PATTERN = Pattern.compile(
            "^(find|get|read|query|count|exists|delete)(All)?By(.+)$"
    );

    private static final Pattern ORDER_BY_PATTERN = Pattern.compile(
            "^(.+?)OrderBy(.+)$"
    );

    private final EntityMetadata entity;

    public QueryMethodParser(EntityMetadata entity) {
        this.entity = entity;
    }

    public QueryMethodDefinition parse(String methodName, String returnType, boolean returnsList,
                                        boolean returnsOptional, List<QueryParam> methodParams) {
        Matcher subjectMatcher = SUBJECT_PATTERN.matcher(methodName);
        if (!subjectMatcher.matches()) {
            return null; // Not a derived query method
        }

        String subject = subjectMatcher.group(1);
        String predicatePart = subjectMatcher.group(3);

        boolean isCount = "count".equals(subject);
        boolean isExists = "exists".equals(subject);
        boolean isDelete = "delete".equals(subject);

        // Split off OrderBy clause
        String orderByClause = null;
        Matcher orderMatcher = ORDER_BY_PATTERN.matcher(predicatePart);
        if (orderMatcher.matches()) {
            predicatePart = orderMatcher.group(1);
            orderByClause = parseOrderBy(orderMatcher.group(2));
        }

        // Parse conditions
        List<Condition> conditions = parseConditions(predicatePart);
        if (conditions == null) {
            return null; // Parse failure
        }

        // Build SQL
        StringBuilder sql = new StringBuilder();
        if (isCount) {
            sql.append("SELECT COUNT(*) FROM ").append(entity.getTableName());
        } else if (isExists) {
            sql.append("SELECT COUNT(*) FROM ").append(entity.getTableName());
        } else if (isDelete) {
            sql.append("DELETE FROM ").append(entity.getTableName());
        } else {
            sql.append("SELECT * FROM ").append(entity.getTableName());
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ");
            List<QueryParam> boundParams = new ArrayList<>();
            int paramIdx = 0;

            for (int i = 0; i < conditions.size(); i++) {
                Condition cond = conditions.get(i);
                if (i > 0) {
                    sql.append(cond.connector().equals("Or") ? " OR " : " AND ");
                }

                ColumnMapping col = cond.column();
                String colName = col.columnName();

                switch (cond.operator()) {
                    case EQUALS -> {
                        sql.append(colName).append(" = ?");
                        boundParams.add(getParam(methodParams, paramIdx++));
                    }
                    case NOT -> {
                        sql.append(colName).append(" != ?");
                        boundParams.add(getParam(methodParams, paramIdx++));
                    }
                    case LESS_THAN -> {
                        sql.append(colName).append(" < ?");
                        boundParams.add(getParam(methodParams, paramIdx++));
                    }
                    case GREATER_THAN -> {
                        sql.append(colName).append(" > ?");
                        boundParams.add(getParam(methodParams, paramIdx++));
                    }
                    case LESS_THAN_EQUAL -> {
                        sql.append(colName).append(" <= ?");
                        boundParams.add(getParam(methodParams, paramIdx++));
                    }
                    case GREATER_THAN_EQUAL -> {
                        sql.append(colName).append(" >= ?");
                        boundParams.add(getParam(methodParams, paramIdx++));
                    }
                    case LIKE -> {
                        sql.append(colName).append(" LIKE ?");
                        boundParams.add(getParam(methodParams, paramIdx++));
                    }
                    case CONTAINING -> {
                        sql.append(colName).append(" LIKE CONCAT('%', ?, '%')");
                        boundParams.add(getParam(methodParams, paramIdx++));
                    }
                    case STARTING_WITH -> {
                        sql.append(colName).append(" LIKE CONCAT(?, '%')");
                        boundParams.add(getParam(methodParams, paramIdx++));
                    }
                    case ENDING_WITH -> {
                        sql.append(colName).append(" LIKE CONCAT('%', ?)");
                        boundParams.add(getParam(methodParams, paramIdx++));
                    }
                    case IN -> {
                        sql.append(colName).append(" IN (?)");
                        boundParams.add(getParam(methodParams, paramIdx++));
                    }
                    case BETWEEN -> {
                        sql.append(colName).append(" BETWEEN ? AND ?");
                        boundParams.add(getParam(methodParams, paramIdx++));
                        boundParams.add(getParam(methodParams, paramIdx++));
                    }
                    case IS_NULL -> sql.append(colName).append(" IS NULL");
                    case IS_NOT_NULL -> sql.append(colName).append(" IS NOT NULL");
                    case TRUE -> sql.append(colName).append(" = TRUE");
                    case FALSE -> sql.append(colName).append(" = FALSE");
                }
            }

            if (orderByClause != null) {
                sql.append(" ").append(orderByClause);
            }

            return new QueryMethodDefinition(
                    methodName, returnType, returnsList, returnsOptional,
                    isCount, isExists, isDelete,
                    sql.toString(), boundParams, orderByClause
            );
        }

        if (orderByClause != null) {
            sql.append(" ").append(orderByClause);
        }

        return new QueryMethodDefinition(
                methodName, returnType, returnsList, returnsOptional,
                isCount, isExists, isDelete,
                sql.toString(), List.of(), orderByClause
        );
    }

    private List<Condition> parseConditions(String predicate) {
        List<Condition> conditions = new ArrayList<>();
        // Split on And/Or while keeping the connector
        // "NameAndAgeGreaterThan" -> ["Name", "And", "AgeGreaterThan"]
        List<String> parts = new ArrayList<>();
        List<String> connectors = new ArrayList<>();

        // Split carefully - And/Or must be at word boundaries between properties
        String remaining = predicate;
        connectors.add("And"); // first condition has implicit And

        while (!remaining.isEmpty()) {
            // Try to match a property + operator from the beginning
            PropertyMatch match = matchProperty(remaining);
            if (match == null) {
                return null; // Can't parse
            }

            parts.add(remaining.substring(0, match.consumedLength()));
            remaining = remaining.substring(match.consumedLength());

            // Check for And/Or connector
            if (remaining.startsWith("And")) {
                connectors.add("And");
                remaining = remaining.substring(3);
            } else if (remaining.startsWith("Or")) {
                connectors.add("Or");
                remaining = remaining.substring(2);
            } else if (!remaining.isEmpty()) {
                return null; // Unexpected content
            }
        }

        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            String connector = connectors.get(i);
            Condition cond = parseCondition(part, connector);
            if (cond == null) return null;
            conditions.add(cond);
        }

        return conditions;
    }

    private PropertyMatch matchProperty(String str) {
        // Try longest entity field name match first, then check for operator suffix
        List<ColumnMapping> columns = entity.getColumns();

        // Sort by field name length descending (longest match first)
        List<ColumnMapping> sorted = columns.stream()
                .sorted((a, b) -> b.fieldName().length() - a.fieldName().length())
                .toList();

        for (ColumnMapping col : sorted) {
            String capitalizedField = capitalize(col.fieldName());
            if (str.startsWith(capitalizedField)) {
                String afterField = str.substring(capitalizedField.length());
                Operator op = matchOperator(afterField);
                int consumedLen = capitalizedField.length() + (op != null ? op.suffix.length() : 0);

                // Make sure we're not consuming into the next And/Or/property
                String afterOp = str.substring(consumedLen);
                if (afterOp.isEmpty() || afterOp.startsWith("And") || afterOp.startsWith("Or")) {
                    return new PropertyMatch(col, op != null ? op : Operator.EQUALS, consumedLen);
                }
            }
        }
        return null;
    }

    private Operator matchOperator(String str) {
        // Check operators from longest to shortest to avoid prefix conflicts
        Operator[] operators = {
                Operator.GREATER_THAN_EQUAL, Operator.LESS_THAN_EQUAL,
                Operator.GREATER_THAN, Operator.LESS_THAN,
                Operator.IS_NOT_NULL, Operator.IS_NULL,
                Operator.STARTING_WITH, Operator.ENDING_WITH, Operator.CONTAINING,
                Operator.BETWEEN, Operator.NOT, Operator.LIKE, Operator.IN,
                Operator.TRUE, Operator.FALSE
        };

        for (Operator op : operators) {
            if (str.startsWith(op.suffix)) {
                return op;
            }
        }
        return null;
    }

    private Condition parseCondition(String part, String connector) {
        for (ColumnMapping col : entity.getColumns()) {
            String capitalizedField = capitalize(col.fieldName());
            if (part.startsWith(capitalizedField)) {
                String operatorStr = part.substring(capitalizedField.length());
                Operator op = operatorStr.isEmpty() ? Operator.EQUALS : matchOperator(operatorStr);
                if (op == null) op = Operator.EQUALS;
                return new Condition(col, op, connector);
            }
        }
        return null;
    }

    private String parseOrderBy(String orderByPart) {
        // "NameAscAgeDesc" -> "ORDER BY name ASC, age DESC"
        StringBuilder orderBy = new StringBuilder("ORDER BY ");
        String remaining = orderByPart;
        boolean first = true;

        while (!remaining.isEmpty()) {
            boolean matched = false;
            for (ColumnMapping col : entity.getColumns()) {
                String capitalizedField = capitalize(col.fieldName());
                if (remaining.startsWith(capitalizedField)) {
                    if (!first) orderBy.append(", ");
                    orderBy.append(col.columnName());

                    remaining = remaining.substring(capitalizedField.length());
                    if (remaining.startsWith("Desc")) {
                        orderBy.append(" DESC");
                        remaining = remaining.substring(4);
                    } else if (remaining.startsWith("Asc")) {
                        orderBy.append(" ASC");
                        remaining = remaining.substring(3);
                    } else {
                        orderBy.append(" ASC");
                    }
                    first = false;
                    matched = true;
                    break;
                }
            }
            if (!matched) break;
        }

        return orderBy.toString();
    }

    private QueryParam getParam(List<QueryParam> params, int index) {
        if (index < params.size()) return params.get(index);
        return new QueryParam("param" + index, "Object", "java.lang.Object");
    }

    private String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private record PropertyMatch(ColumnMapping column, Operator operator, int consumedLength) {}
    private record Condition(ColumnMapping column, Operator operator, String connector) {}

    enum Operator {
        EQUALS(""),
        NOT("Not"),
        LESS_THAN("LessThan"),
        GREATER_THAN("GreaterThan"),
        LESS_THAN_EQUAL("LessThanEqual"),
        GREATER_THAN_EQUAL("GreaterThanEqual"),
        LIKE("Like"),
        CONTAINING("Containing"),
        STARTING_WITH("StartingWith"),
        ENDING_WITH("EndingWith"),
        IN("In"),
        BETWEEN("Between"),
        IS_NULL("IsNull"),
        IS_NOT_NULL("IsNotNull"),
        TRUE("True"),
        FALSE("False");

        final String suffix;
        Operator(String suffix) { this.suffix = suffix; }
    }
}
