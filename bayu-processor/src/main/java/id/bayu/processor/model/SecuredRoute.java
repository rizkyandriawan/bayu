package id.bayu.processor.model;

import java.util.List;

public record SecuredRoute(
        String[] roles,
        boolean isMethodLevel
) {}
