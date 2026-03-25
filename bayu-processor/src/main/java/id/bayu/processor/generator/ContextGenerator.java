package id.bayu.processor.generator;

import com.palantir.javapoet.*;
import id.bayu.processor.model.*;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContextGenerator {

    private final String packageName;
    private final Filer filer;

    public ContextGenerator(String packageName, Filer filer) {
        this.packageName = packageName;
        this.filer = filer;
    }

    public void generate(List<BeanDefinition> sortedBeans, List<RouteDefinition> routes,
                         List<RepositoryDefinition> repositories,
                         List<InterceptorDefinition> interceptors,
                         List<String[]> webSockets) throws IOException {
        ClassName applicationContext = ClassName.get("id.bayu.core", "ApplicationContext");
        ClassName configLoader = ClassName.get("id.bayu.core.config", "ConfigLoader");
        ClassName httpServer = ClassName.get("id.bayu.web.server", "BayuHttpServer");
        ClassName hashMap = ClassName.get("java.util", "HashMap");
        ClassName map = ClassName.get("java.util", "Map");

        boolean hasDataLayer = !repositories.isEmpty();

        // Fields
        FieldSpec beansField = FieldSpec.builder(
                ParameterizedTypeName.get(map, ClassName.get(Class.class), ClassName.get(Object.class)),
                "beans", Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T<>()", hashMap)
                .build();

        FieldSpec configField = FieldSpec.builder(configLoader, "config", Modifier.PRIVATE).build();
        FieldSpec serverField = FieldSpec.builder(httpServer, "httpServer", Modifier.PRIVATE).build();

        // initialize() method
        MethodSpec.Builder initBuilder = MethodSpec.methodBuilder("initialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(String[].class, "args")
                .addComment("Load configuration")
                .addStatement("this.config = new $T()", configLoader)
                .addStatement("config.load(args)");

        // DataSource setup if repositories exist
        if (hasDataLayer) {
            ClassName hikariConfig = ClassName.get("com.zaxxer.hikari", "HikariConfig");
            ClassName hikariDataSource = ClassName.get("com.zaxxer.hikari", "HikariDataSource");
            ClassName txManagerType = ClassName.get("id.bayu.data.transaction", "TransactionManager");
            ClassName schemaGenType = ClassName.get("id.bayu.data.schema", "SchemaGenerator");

            initBuilder.addCode("\n")
                    .addComment("Create DataSource")
                    .addStatement("var hikariConfig = new $T()", hikariConfig)
                    .addStatement("hikariConfig.setJdbcUrl(config.get($S))", "datasource.url")
                    .addStatement("hikariConfig.setUsername(config.get($S, $S))", "datasource.username", "")
                    .addStatement("hikariConfig.setPassword(config.get($S, $S))", "datasource.password", "")
                    .addStatement("hikariConfig.setMaximumPoolSize(config.getInt($S, 10))", "datasource.pool.maximum-size")
                    .addStatement("var dataSource = new $T(hikariConfig)", hikariDataSource)
                    .addStatement("var txManager = new $T(dataSource)", txManagerType)
                    .addStatement("beans.put($T.class, txManager)", txManagerType)
                    .addStatement("var jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper()")
                    .addStatement("jsonMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())")
                    .addStatement("jsonMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)")
                    .addStatement("jsonMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)");

            // Instantiate repository implementations
            initBuilder.addCode("\n").addComment("Create repositories");
            for (RepositoryDefinition repo : repositories) {
                ClassName implType = ClassName.bestGuess(repo.getImplQualifiedName());
                ClassName interfaceType = ClassName.bestGuess(repo.getQualifiedName());
                String varName = decapitalize(repo.getSimpleName());
                initBuilder.addStatement("var $L = new $T(dataSource, txManager, jsonMapper)", varName, implType);
                initBuilder.addStatement("beans.put($T.class, $L)", interfaceType, varName);
            }

            // Auto-create schema (DDL)
            initBuilder.addCode("\n").addComment("Auto-create schema")
                    .addStatement("var schemaGen = new $T(dataSource)", schemaGenType)
                    .addStatement("var ddlStatements = new java.util.ArrayList<String>()");
            for (RepositoryDefinition repo : repositories) {
                String varName = decapitalize(repo.getSimpleName());
                initBuilder.addStatement("ddlStatements.add($L.getDDL())", varName);
            }
            initBuilder.addStatement("schemaGen.execute(ddlStatements)");
        }

        initBuilder.addCode("\n").addComment("Create beans in dependency order");

        // Generate bean instantiation (skip repos, already instantiated)
        for (BeanDefinition bean : sortedBeans) {
            // Skip repository beans - they are instantiated above with DataSource
            boolean isRepo = repositories.stream()
                    .anyMatch(r -> r.getQualifiedName().equals(bean.getQualifiedName()));
            if (!isRepo) {
                generateBeanInstantiation(initBuilder, bean, repositories);
            }
        }

        // Run startup hooks
        ClassName bayuStartup = ClassName.get("id.bayu.core", "BayuStartup");
        initBuilder.addCode("\n").addComment("Run startup hooks");
        initBuilder.beginControlFlow("for (var bean : beans.values())");
        initBuilder.beginControlFlow("if (bean instanceof $T startup)", bayuStartup);
        initBuilder.addStatement("startup.onStartup()");
        initBuilder.endControlFlow();
        initBuilder.endControlFlow();

        // Generate HTTP server setup
        ClassName corsConfigType = ClassName.get("id.bayu.web.server", "CorsConfig");

        initBuilder.addCode("\n")
                .addComment("Start HTTP server")
                .addStatement("int port = config.getInt($S, 8080)", "server.port")
                .addStatement("this.httpServer = new $T(port)", httpServer)
                .addComment("Configure CORS")
                .addStatement("String corsOrigins = config.get($S, $S)", "server.cors.origins", "*")
                .addStatement("var corsConfig = new $T(java.util.Arrays.asList(corsOrigins.split($S)))", corsConfigType, ",")
                .addStatement("httpServer.setCorsConfig(corsConfig)")
                .addStatement("registerRoutes()");

        // Register WebSocket endpoints
        ClassName wsListenerType = ClassName.get("id.bayu.web.server", "BayuHttpServer", "WebSocketListener");
        for (String[] ws : webSockets) {
            String qualifiedName = ws[0];
            String path = ws[1];
            String varName = decapitalize(qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1));
            initBuilder.addStatement("httpServer.addWebSocket($S, ($T) beans.get($L.class))",
                    path, wsListenerType, qualifiedName);
        }

        initBuilder.addStatement("httpServer.start()");

        MethodSpec initMethod = initBuilder.build();

        // registerRoutes() method
        MethodSpec routesMethod = generateRouteRegistration(routes, interceptors);

        // getBean(Class) method
        MethodSpec getBeanByType = MethodSpec.methodBuilder("getBean")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addTypeVariable(TypeVariableName.get("T"))
                .returns(TypeVariableName.get("T"))
                .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), TypeVariableName.get("T")), "type")
                .addStatement("return type.cast(beans.get(type))")
                .build();

        // getBean(String, Class) method
        MethodSpec getBeanByName = MethodSpec.methodBuilder("getBean")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addTypeVariable(TypeVariableName.get("T"))
                .returns(TypeVariableName.get("T"))
                .addParameter(String.class, "name")
                .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), TypeVariableName.get("T")), "type")
                .addComment("Name-based lookup not yet implemented, fallback to type")
                .addStatement("return getBean(type)")
                .build();

        // close() method
        MethodSpec.Builder closeBuilder = MethodSpec.methodBuilder("close")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .beginControlFlow("if (httpServer != null)")
                .addStatement("httpServer.stop()")
                .endControlFlow();

        MethodSpec closeMethod = closeBuilder.build();

        // Build the class
        TypeSpec contextClass = TypeSpec.classBuilder("BayuGeneratedContext")
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(applicationContext)
                .addField(beansField)
                .addField(configField)
                .addField(serverField)
                .addMethod(initMethod)
                .addMethod(routesMethod)
                .addMethod(getBeanByType)
                .addMethod(getBeanByName)
                .addMethod(closeMethod)
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, contextClass)
                .addFileComment("Generated by Bayu Framework - DO NOT EDIT")
                .indent("    ")
                .build();

        javaFile.writeTo(filer);
    }

    private void generateBeanInstantiation(MethodSpec.Builder builder, BeanDefinition bean,
                                            List<RepositoryDefinition> repositories) {
        ClassName beanType = ClassName.bestGuess(bean.getQualifiedName());
        String varName = bean.getBeanName();

        if (bean.getKind() == BeanDefinition.Kind.BEAN_METHOD) {
            // Call the @Bean method on the config class
            String configVar = decapitalize(bean.getConfigClass().getSimpleName().toString());
            builder.addStatement("var $L = $L.$L()", varName, configVar,
                    bean.getBeanMethod().getSimpleName());
        } else if (!bean.getConstructorParams().isEmpty()) {
            // Constructor injection
            StringBuilder ctorArgs = new StringBuilder();
            for (int i = 0; i < bean.getConstructorParams().size(); i++) {
                if (i > 0) ctorArgs.append(", ");
                BeanDefinition.ConstructorParam param = bean.getConstructorParams().get(i);
                ctorArgs.append(findBeanVarName(param.qualifiedTypeName()));
            }
            builder.addStatement("var $L = new $T($L)", varName, beanType, ctorArgs.toString());
        } else {
            // Default constructor
            builder.addStatement("var $L = new $T()", varName, beanType);
        }

        // Field injection - use setter methods for cross-package access
        for (FieldDependency field : bean.getFieldDependencies()) {
            String setterName = "set" + capitalize(field.fieldName());
            if (field.isValue()) {
                generateValueInjection(builder, varName, field, setterName);
            } else {
                // Check if dependency is a repository (interface - need cast from beans map)
                boolean isRepoDep = repositories.stream()
                        .anyMatch(r -> r.getQualifiedName().equals(field.fieldQualifiedTypeName()));
                if (isRepoDep) {
                    ClassName depType = ClassName.bestGuess(field.fieldQualifiedTypeName());
                    builder.addStatement("$L.$L(($T) beans.get($T.class))",
                            varName, setterName, depType, depType);
                } else {
                    String depVar = findBeanVarName(field.fieldQualifiedTypeName());
                    builder.addStatement("$L.$L($L)", varName, setterName, depVar);
                }
            }
        }

        builder.addStatement("beans.put($T.class, $L)", beanType, varName);
        builder.addCode("\n");
    }

    private void generateValueInjection(MethodSpec.Builder builder, String varName,
                                        FieldDependency field, String setterName) {
        // Parse ${key:-default} or ${key:default}
        String expr = field.valueExpression();
        if (expr == null) return;

        String key;
        String defaultValue;

        if (expr.startsWith("${") && expr.endsWith("}")) {
            String inner = expr.substring(2, expr.length() - 1);
            // Support both :- and : as separator
            int sep = inner.indexOf(":-");
            if (sep != -1) {
                key = inner.substring(0, sep);
                defaultValue = inner.substring(sep + 2);
            } else {
                sep = inner.indexOf(':');
                if (sep != -1) {
                    key = inner.substring(0, sep);
                    defaultValue = inner.substring(sep + 1);
                } else {
                    key = inner;
                    defaultValue = null;
                }
            }
        } else {
            key = expr;
            defaultValue = null;
        }

        String typeName = field.fieldQualifiedTypeName();
        if (defaultValue != null) {
            switch (typeName) {
                case "int", "java.lang.Integer" ->
                        builder.addStatement("$L.$L(config.getInt($S, $L))", varName, setterName, key, defaultValue);
                case "long", "java.lang.Long" ->
                        builder.addStatement("$L.$L(config.getLong($S, $LL))", varName, setterName, key, defaultValue);
                case "boolean", "java.lang.Boolean" ->
                        builder.addStatement("$L.$L(config.getBool($S, $L))", varName, setterName, key, defaultValue);
                default ->
                        builder.addStatement("$L.$L(config.get($S, $S))", varName, setterName, key, defaultValue);
            }
        } else {
            switch (typeName) {
                case "int", "java.lang.Integer" ->
                        builder.addStatement("$L.$L(config.getInt($S, 0))", varName, setterName, key);
                case "long", "java.lang.Long" ->
                        builder.addStatement("$L.$L(config.getLong($S, 0L))", varName, setterName, key);
                case "boolean", "java.lang.Boolean" ->
                        builder.addStatement("$L.$L(config.getBool($S, false))", varName, setterName, key);
                default ->
                        builder.addStatement("$L.$L(config.get($S))", varName, setterName, key);
            }
        }
    }

    private MethodSpec generateRouteRegistration(List<RouteDefinition> routes,
                                                  List<InterceptorDefinition> interceptors) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("registerRoutes")
                .addModifiers(Modifier.PRIVATE);

        if (routes.isEmpty()) {
            return builder.build();
        }

        builder.addStatement("var router = httpServer.getRouter()");
        builder.addCode("\n");

        // Instantiate interceptor chain
        if (!interceptors.isEmpty()) {
            ClassName interceptorInterface = ClassName.get("id.bayu.security.interceptor", "BayuInterceptor");
            ClassName listType = ClassName.get("java.util", "List");
            builder.addStatement("$T<$T> interceptorChain = $T.of(\n" +
                    interceptors.stream()
                            .map(i -> "    (" + i.qualifiedName() + ") beans.get(" + i.qualifiedName() + ".class)")
                            .reduce((a, b) -> a + ",\n" + b)
                            .orElse("") +
                    "\n)", listType, interceptorInterface, listType);
            builder.addCode("\n");
        }

        // Deduplicate controller variable declarations
        Set<String> declaredControllers = new java.util.LinkedHashSet<>();
        for (RouteDefinition route : routes) {
            String controllerVar = route.controller().getBeanName();
            if (declaredControllers.add(controllerVar)) {
                ClassName controllerType = ClassName.bestGuess(route.controller().getQualifiedName());
                builder.addStatement("var $L = ($T) beans.get($T.class)",
                        controllerVar, controllerType, controllerType);
            }
        }

        builder.addCode("\n");

        for (RouteDefinition route : routes) {
            generateRouteHandler(builder, route, !interceptors.isEmpty());
        }

        return builder.build();
    }

    private void generateRouteHandler(MethodSpec.Builder builder, RouteDefinition route,
                                       boolean hasInterceptors) {
        String controllerVar = route.controller().getBeanName();

        builder.addStatement("router.addRoute($S, $S, (ctx, res) -> {\n" +
                generateHandlerBody(route, controllerVar, hasInterceptors) +
                "\n})", route.httpMethod(), route.fullPath());
    }

    private String generateHandlerBody(RouteDefinition route, String controllerVar,
                                        boolean hasInterceptors) {
        StringBuilder body = new StringBuilder();

        // Interceptor preHandle
        if (hasInterceptors) {
            body.append("    for (var interceptor : interceptorChain) {\n");
            body.append("        if (!interceptor.preHandle(ctx, res)) return;\n");
            body.append("    }\n");
        }

        // @Secured check
        if (route.isSecured()) {
            body.append("    var secCtx = id.bayu.security.interceptor.SecurityContext.getRoles();\n");
            StringBuilder roleCheck = new StringBuilder("    if (");
            String[] roles = route.securedRoles();
            for (int i = 0; i < roles.length; i++) {
                if (i > 0) roleCheck.append(" && ");
                roleCheck.append("!secCtx.contains(\"").append(roles[i]).append("\")");
            }
            roleCheck.append(") {\n");
            body.append(roleCheck);
            body.append("        res.status(403).json(java.util.Map.of(\"error\", \"Forbidden\", \"message\", \"Required roles: ")
                    .append(String.join(", ", roles)).append("\"));\n");
            body.append("        return;\n");
            body.append("    }\n");
        }

        // Extract parameters
        for (RouteDefinition.ParamBinding param : route.params()) {
            switch (param.type()) {
                case PATH_VARIABLE -> {
                    String conversion = typeConversion("ctx.pathVariable(\"" + param.name() + "\")",
                            param.parameterQualifiedTypeName());
                    body.append("    var ").append(param.parameterName()).append(" = ").append(conversion).append(";\n");
                }
                case REQUEST_PARAM -> {
                    if (param.defaultValue() != null && !param.defaultValue().isEmpty()) {
                        body.append("    var ").append(param.parameterName()).append(" = ");
                        String raw = "ctx.queryParam(\"" + param.name() + "\", \"" + param.defaultValue() + "\")";
                        body.append(typeConversion(raw, param.parameterQualifiedTypeName())).append(";\n");
                    } else {
                        body.append("    var ").append(param.parameterName()).append(" = ");
                        String raw = "ctx.queryParam(\"" + param.name() + "\")";
                        body.append(typeConversion(raw, param.parameterQualifiedTypeName())).append(";\n");
                    }
                }
                case REQUEST_BODY -> {
                    body.append("    var ").append(param.parameterName()).append(" = ctx.body(")
                            .append(param.parameterQualifiedTypeName()).append(".class);\n");
                }
            }
        }

        // Call controller method
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < route.params().size(); i++) {
            if (i > 0) args.append(", ");
            args.append(route.params().get(i).parameterName());
        }

        boolean hasReturn = !"void".equals(route.returnTypeName());
        int status = route.responseStatus();

        if (status != 200) {
            body.append("    res.status(").append(status).append(");\n");
        }

        if (hasReturn) {
            body.append("    var result = ").append(controllerVar).append(".").append(route.methodName())
                    .append("(").append(args).append(");\n");

            // Interceptor postHandle
            if (hasInterceptors) {
                body.append("    for (var interceptor : interceptorChain) {\n");
                body.append("        interceptor.postHandle(ctx, res, result);\n");
                body.append("    }\n");
            }

            body.append("    res.json(result);\n");
        } else {
            body.append("    ").append(controllerVar).append(".").append(route.methodName())
                    .append("(").append(args).append(");\n");
            if (status != 200) {
                body.append("    res.empty();\n");
            } else {
                body.append("    res.status(204).empty();\n");
            }
        }

        return body.toString();
    }

    private String typeConversion(String expr, String typeName) {
        return switch (typeName) {
            case "java.lang.Long", "long" -> "Long.parseLong(" + expr + ")";
            case "java.lang.Integer", "int" -> "Integer.parseInt(" + expr + ")";
            case "java.lang.Double", "double" -> "Double.parseDouble(" + expr + ")";
            case "java.lang.Float", "float" -> "Float.parseFloat(" + expr + ")";
            case "java.lang.Boolean", "boolean" -> "Boolean.parseBoolean(" + expr + ")";
            case "java.util.UUID" -> "java.util.UUID.fromString(" + expr + ")";
            default -> expr;
        };
    }

    private String findBeanVarName(String qualifiedTypeName) {
        // Simple: decapitalize the simple class name
        int lastDot = qualifiedTypeName.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? qualifiedTypeName.substring(lastDot + 1) : qualifiedTypeName;
        return decapitalize(simpleName);
    }

    private String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
