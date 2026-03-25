package id.bayu.processor.model;

import java.util.List;

public record QueryMethodDefinition(
        String methodName,
        String returnType,
        boolean returnsList,
        boolean returnsOptional,
        boolean isCount,
        boolean isExists,
        boolean isDelete,
        String sql,
        List<QueryParam> params,
        String orderByClause
) {
    public record QueryParam(String paramName, String paramType, String qualifiedParamType) {}
}
