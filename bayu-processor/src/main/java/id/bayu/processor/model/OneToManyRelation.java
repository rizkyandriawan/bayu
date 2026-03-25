package id.bayu.processor.model;

import java.util.List;

public record OneToManyRelation(
        String fieldName,
        String setterName,
        String getterName,
        EntityMetadata childMeta,
        String fkFieldName,
        String fkColumnName,
        boolean cascade,
        boolean orphanRemoval,
        List<OneToManyRelation> nested
) {}
