package id.bayu.processor.model;

import javax.lang.model.element.ExecutableElement;
import java.util.List;

public record RouteDefinition(
        String httpMethod,
        String fullPath,
        BeanDefinition controller,
        ExecutableElement method,
        String methodName,
        List<ParamBinding> params,
        String returnTypeName,
        int responseStatus,
        String[] securedRoles
) {
    public boolean isSecured() {
        return securedRoles != null && securedRoles.length > 0;
    }

    public record ParamBinding(
            ParamType type,
            String name,
            String parameterName,
            String parameterTypeName,
            String parameterQualifiedTypeName,
            boolean required,
            String defaultValue
    ) {}

    public enum ParamType {
        PATH_VARIABLE,
        REQUEST_PARAM,
        REQUEST_BODY
    }
}
