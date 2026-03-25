package id.bayu.processor.model;

public record FieldDependency(
        String fieldName,
        String fieldTypeName,
        String fieldQualifiedTypeName,
        String qualifier,
        boolean isValue,
        String valueExpression
) {
    public static FieldDependency autowired(String fieldName, String fieldTypeName, String fieldQualifiedTypeName, String qualifier) {
        return new FieldDependency(fieldName, fieldTypeName, fieldQualifiedTypeName, qualifier, false, null);
    }

    public static FieldDependency value(String fieldName, String fieldTypeName, String fieldQualifiedTypeName, String valueExpression) {
        return new FieldDependency(fieldName, fieldTypeName, fieldQualifiedTypeName, null, true, valueExpression);
    }
}
