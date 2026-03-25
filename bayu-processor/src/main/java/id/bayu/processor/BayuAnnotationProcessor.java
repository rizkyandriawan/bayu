package id.bayu.processor;

import com.palantir.javapoet.ClassName;
import id.bayu.processor.generator.ContextGenerator;
import id.bayu.processor.generator.RepositoryImplGenerator;
import id.bayu.processor.model.*;
import id.bayu.processor.parser.QueryMethodParser;
import id.bayu.processor.registry.BeanRegistry;
import id.bayu.processor.resolver.DependencyResolver;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

@SupportedAnnotationTypes({
        "id.bayu.core.annotation.BayuApplication",
        "id.bayu.core.annotation.Component",
        "id.bayu.core.annotation.Service",
        "id.bayu.core.annotation.Configuration",
        "id.bayu.web.annotation.RestController",
        "id.bayu.web.annotation.WebSocket",
        "id.bayu.data.annotation.Repository"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class BayuAnnotationProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Types typeUtils;
    private Filer filer;
    private Messager messager;
    private boolean processed = false;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (processed || roundEnv.processingOver()) return false;

        // Find @BayuApplication
        TypeElement appAnnotation = elementUtils.getTypeElement("id.bayu.core.annotation.BayuApplication");
        if (appAnnotation == null) return false;

        Set<? extends Element> appElements = roundEnv.getElementsAnnotatedWith(appAnnotation);
        if (appElements.isEmpty()) return false;

        Element appElement = appElements.iterator().next();
        String appPackage = elementUtils.getPackageOf(appElement).getQualifiedName().toString();

        messager.printMessage(Diagnostic.Kind.NOTE, "Bayu: Processing application in package " + appPackage);

        BeanRegistry registry = new BeanRegistry();
        List<RouteDefinition> routes = new ArrayList<>();
        List<RepositoryDefinition> repositories = new ArrayList<>();
        List<InterceptorDefinition> interceptors = new ArrayList<>();
        List<String[]> webSockets = new ArrayList<>(); // [qualifiedName, path]

        // Discover beans
        discoverComponents(roundEnv, registry);
        discoverServices(roundEnv, registry);
        discoverControllers(roundEnv, registry, routes);
        discoverConfigurations(roundEnv, registry);
        discoverRepositories(roundEnv, registry, repositories);
        discoverInterceptors(roundEnv, registry, interceptors);
        discoverWebSockets(roundEnv, registry, webSockets);

        // Resolve field dependencies for all beans
        for (BeanDefinition bean : registry.getAll()) {
            if (bean.getKind() != BeanDefinition.Kind.BEAN_METHOD) {
                resolveFields(bean);
            }
        }

        // Topological sort
        DependencyResolver resolver = new DependencyResolver(registry, messager);
        List<BeanDefinition> sorted = resolver.resolve();
        if (sorted == null) {
            return true; // Error already reported
        }

        // Sort interceptors by order
        Collections.sort(interceptors);

        messager.printMessage(Diagnostic.Kind.NOTE,
                "Bayu: Found " + sorted.size() + " beans, " + routes.size() + " routes, "
                        + repositories.size() + " repositories, " + interceptors.size() + " interceptors");

        // Generate repository implementations
        try {
            RepositoryImplGenerator repoGen = new RepositoryImplGenerator(filer);
            for (RepositoryDefinition repo : repositories) {
                repoGen.generate(repo);
                messager.printMessage(Diagnostic.Kind.NOTE,
                        "Bayu: Generated " + repo.getImplSimpleName() + " for " + repo.getSimpleName());
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Bayu: Failed to generate repository: " + e.getMessage());
        }

        // Generate context class
        try {
            ContextGenerator generator = new ContextGenerator(appPackage, filer);
            generator.generate(sorted, routes, repositories, interceptors, webSockets);
            generateServiceLoaderFile(appPackage);
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Bayu: Failed to generate context: " + e.getMessage());
        }

        processed = true;
        return true;
    }

    private void discoverComponents(RoundEnvironment roundEnv, BeanRegistry registry) {
        TypeElement annotation = elementUtils.getTypeElement("id.bayu.core.annotation.Component");
        if (annotation == null) return;

        for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
            if (element instanceof TypeElement typeElement) {
                String name = getBeanName(typeElement, "id.bayu.core.annotation.Component");
                registry.register(new BeanDefinition(typeElement, name, BeanDefinition.Kind.COMPONENT));
            }
        }
    }

    private void discoverServices(RoundEnvironment roundEnv, BeanRegistry registry) {
        TypeElement annotation = elementUtils.getTypeElement("id.bayu.core.annotation.Service");
        if (annotation == null) return;

        for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
            if (element instanceof TypeElement typeElement) {
                String name = getBeanName(typeElement, "id.bayu.core.annotation.Service");
                registry.register(new BeanDefinition(typeElement, name, BeanDefinition.Kind.SERVICE));
            }
        }
    }

    private void discoverControllers(RoundEnvironment roundEnv, BeanRegistry registry,
                                      List<RouteDefinition> routes) {
        TypeElement annotation = elementUtils.getTypeElement("id.bayu.web.annotation.RestController");
        if (annotation == null) return;

        for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
            if (element instanceof TypeElement typeElement) {
                String name = getBeanName(typeElement, "id.bayu.web.annotation.RestController");
                BeanDefinition bean = new BeanDefinition(typeElement, name, BeanDefinition.Kind.CONTROLLER);
                registry.register(bean);

                // Extract routes
                String basePath = getRequestMappingPath(typeElement);
                extractRoutes(typeElement, bean, basePath, routes);
            }
        }
    }

    private void discoverConfigurations(RoundEnvironment roundEnv, BeanRegistry registry) {
        TypeElement annotation = elementUtils.getTypeElement("id.bayu.core.annotation.Configuration");
        if (annotation == null) return;

        for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
            if (element instanceof TypeElement typeElement) {
                String name = getBeanName(typeElement, "id.bayu.core.annotation.Configuration");
                BeanDefinition configBean = new BeanDefinition(typeElement, name, BeanDefinition.Kind.CONFIGURATION);
                registry.register(configBean);

                // Find @Bean methods
                TypeElement beanAnnotation = elementUtils.getTypeElement("id.bayu.core.annotation.Bean");
                if (beanAnnotation == null) continue;

                for (Element enclosed : typeElement.getEnclosedElements()) {
                    if (enclosed instanceof ExecutableElement method) {
                        if (getAnnotationMirror(method, "id.bayu.core.annotation.Bean") != null) {
                            TypeMirror returnType = method.getReturnType();
                            if (returnType instanceof DeclaredType declaredType) {
                                TypeElement returnTypeElement = (TypeElement) declaredType.asElement();
                                String beanName = decapitalize(method.getSimpleName().toString());
                                BeanDefinition beanDef = BeanDefinition.forBeanMethod(
                                        returnTypeElement, beanName, typeElement, method);
                                registry.register(beanDef);
                            }
                        }
                    }
                }
            }
        }
    }

    private void discoverInterceptors(RoundEnvironment roundEnv, BeanRegistry registry,
                                      List<InterceptorDefinition> interceptors) {
        TypeElement annotation = elementUtils.getTypeElement("id.bayu.security.annotation.Interceptor");
        if (annotation == null) return;

        for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
            if (!(element instanceof TypeElement typeElement)) continue;

            // Verify it implements BayuInterceptor
            boolean implementsInterceptor = false;
            for (TypeMirror iface : typeElement.getInterfaces()) {
                if (iface.toString().equals("id.bayu.security.interceptor.BayuInterceptor")) {
                    implementsInterceptor = true;
                    break;
                }
            }
            if (!implementsInterceptor) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Bayu: @Interceptor class must implement BayuInterceptor", element);
                continue;
            }

            String orderStr = getAnnotationValue(typeElement, "id.bayu.security.annotation.Interceptor", "order");
            int order = 0;
            if (orderStr != null) {
                try { order = Integer.parseInt(orderStr); } catch (NumberFormatException ignored) {}
            }

            interceptors.add(new InterceptorDefinition(
                    typeElement,
                    typeElement.getQualifiedName().toString(),
                    typeElement.getSimpleName().toString(),
                    order));

            // Register as bean
            String name = decapitalize(typeElement.getSimpleName().toString());
            registry.register(new BeanDefinition(typeElement, name, BeanDefinition.Kind.COMPONENT));

            messager.printMessage(Diagnostic.Kind.NOTE,
                    "Bayu: Discovered interceptor " + typeElement.getSimpleName() + " (order=" + order + ")");
        }
    }

    private void discoverWebSockets(RoundEnvironment roundEnv, BeanRegistry registry,
                                    List<String[]> webSockets) {
        TypeElement annotation = elementUtils.getTypeElement("id.bayu.web.annotation.WebSocket");
        if (annotation == null) return;

        for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
            if (!(element instanceof TypeElement typeElement)) continue;

            String path = getAnnotationValue(typeElement, "id.bayu.web.annotation.WebSocket", "value");
            if (path == null || path.isEmpty()) path = "/ws";

            String qualifiedName = typeElement.getQualifiedName().toString();
            webSockets.add(new String[]{qualifiedName, path});

            // Register as bean
            String name = decapitalize(typeElement.getSimpleName().toString());
            registry.register(new BeanDefinition(typeElement, name, BeanDefinition.Kind.COMPONENT));

            messager.printMessage(Diagnostic.Kind.NOTE,
                    "Bayu: Discovered WebSocket " + typeElement.getSimpleName() + " at " + path);
        }
    }

    private void discoverRepositories(RoundEnvironment roundEnv, BeanRegistry registry,
                                      List<RepositoryDefinition> repositories) {
        TypeElement annotation = elementUtils.getTypeElement("id.bayu.data.annotation.Repository");
        if (annotation == null) return;

        for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
            if (!(element instanceof TypeElement typeElement)) continue;
            if (!typeElement.getKind().isInterface()) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Bayu: @Repository can only be applied to interfaces", element);
                continue;
            }

            // Resolve entity type and ID type from BayuRepository<T, ID>
            String[] typeArgs = resolveRepositoryTypeArgs(typeElement);
            if (typeArgs == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Bayu: @Repository interface must extend BayuRepository<Entity, ID>", element);
                continue;
            }

            String entityTypeName = typeArgs[0];
            String idTypeName = typeArgs[1];

            // Parse entity metadata
            TypeElement entityElement = elementUtils.getTypeElement(entityTypeName);
            if (entityElement == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Bayu: Could not find entity class " + entityTypeName, element);
                continue;
            }

            EntityMetadata entityMeta = parseEntityMetadata(entityElement);
            if (entityMeta.getIdColumn() == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Bayu: Entity " + entityTypeName + " has no @Id field", entityElement);
                continue;
            }

            // Resolve @OneToMany relations recursively
            resolveOneToManyRelations(entityMeta, 0);

            RepositoryDefinition repoDef = new RepositoryDefinition(typeElement, entityMeta, idTypeName);

            // Parse custom query methods (methods not in BayuRepository)
            QueryMethodParser parser = new QueryMethodParser(entityMeta);
            for (Element enclosed : typeElement.getEnclosedElements()) {
                if (!(enclosed instanceof ExecutableElement method)) continue;
                String methodName = method.getSimpleName().toString();

                // Skip default BayuRepository methods
                if (isBaseRepositoryMethod(methodName)) continue;

                // Check for @Query annotation first
                String customQuery = getAnnotationValue(method, "id.bayu.data.annotation.Query", "value");
                if (customQuery != null) {
                    // Custom SQL query
                    List<QueryMethodDefinition.QueryParam> params = extractMethodParams(method);
                    boolean returnsList = method.getReturnType().toString().startsWith("java.util.List");
                    boolean returnsOptional = method.getReturnType().toString().startsWith("java.util.Optional");
                    repoDef.addQueryMethod(new QueryMethodDefinition(
                            methodName, method.getReturnType().toString(),
                            returnsList, returnsOptional,
                            methodName.startsWith("count"), methodName.startsWith("exists"),
                            methodName.startsWith("delete"),
                            customQuery, params, null));
                } else {
                    // Derive query from method name
                    List<QueryMethodDefinition.QueryParam> params = extractMethodParams(method);
                    boolean returnsList = method.getReturnType().toString().startsWith("java.util.List");
                    boolean returnsOptional = method.getReturnType().toString().startsWith("java.util.Optional");

                    QueryMethodDefinition qm = parser.parse(methodName,
                            method.getReturnType().toString(), returnsList, returnsOptional, params);
                    if (qm != null) {
                        repoDef.addQueryMethod(qm);
                    } else {
                        messager.printMessage(Diagnostic.Kind.ERROR,
                                "Bayu: Cannot derive query from method name '" + methodName +
                                        "'. Use @Query to provide SQL.", method);
                    }
                }
            }

            repositories.add(repoDef);

            // Register the implementation as a bean (using COMPONENT kind)
            // The implementation class will be generated
            BeanDefinition repoBeanDef = new BeanDefinition(
                    typeElement, decapitalize(typeElement.getSimpleName().toString()),
                    BeanDefinition.Kind.COMPONENT);
            registry.register(repoBeanDef);

            messager.printMessage(Diagnostic.Kind.NOTE,
                    "Bayu: Discovered repository " + typeElement.getSimpleName() +
                            " for entity " + entityMeta.getSimpleName() +
                            " (" + repoDef.getQueryMethods().size() + " custom queries)");
        }
    }

    private String[] resolveRepositoryTypeArgs(TypeElement repoInterface) {
        for (TypeMirror superInterface : repoInterface.getInterfaces()) {
            if (superInterface instanceof DeclaredType declaredType) {
                String rawType = ((TypeElement) declaredType.asElement()).getQualifiedName().toString();
                if ("id.bayu.data.repository.BayuRepository".equals(rawType)) {
                    List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
                    if (typeArgs.size() == 2) {
                        return new String[]{
                                getQualifiedTypeName(typeArgs.get(0)),
                                getQualifiedTypeName(typeArgs.get(1))
                        };
                    }
                }
            }
        }
        return null;
    }

    private EntityMetadata parseEntityMetadata(TypeElement entityElement) {
        String tableName = getAnnotationValue(entityElement, "id.bayu.data.annotation.Table", "value");
        if (tableName == null || tableName.isEmpty()) {
            tableName = camelToSnake(entityElement.getSimpleName().toString()) + "s";
        }

        EntityMetadata meta = new EntityMetadata(
                entityElement.getQualifiedName().toString(),
                entityElement.getSimpleName().toString(),
                tableName);

        for (Element enclosed : entityElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) continue;
            VariableElement field = (VariableElement) enclosed;

            // Skip static fields
            if (field.getModifiers().contains(Modifier.STATIC)) continue;

            // Skip @ManyToOne fields - they represent object references, not columns
            // The FK column should be a separate UUID field (e.g., orderId)
            if (getAnnotationMirror(field, "id.bayu.data.annotation.ManyToOne") != null) continue;

            String fieldName = field.getSimpleName().toString();
            boolean isId = getAnnotationMirror(field, "id.bayu.data.annotation.Id") != null;
            boolean isGenerated = getAnnotationMirror(field, "id.bayu.data.annotation.GeneratedValue") != null;
            boolean isCreatedAt = getAnnotationMirror(field, "id.bayu.data.annotation.CreatedAt") != null;
            boolean isUpdatedAt = getAnnotationMirror(field, "id.bayu.data.annotation.UpdatedAt") != null;
            boolean isElementCollection = getAnnotationMirror(field, "id.bayu.data.annotation.ElementCollection") != null;
            boolean isOneToMany = getAnnotationMirror(field, "id.bayu.data.annotation.OneToMany") != null;

            String colName = getAnnotationValue(field, "id.bayu.data.annotation.Column", "value");
            if (colName == null || colName.isEmpty()) {
                colName = camelToSnake(fieldName);
            }

            String nullableStr = getAnnotationValue(field, "id.bayu.data.annotation.Column", "nullable");
            boolean nullable = nullableStr == null || Boolean.parseBoolean(nullableStr);

            // Handle @OneToMany
            String oneToManyMappedBy = null;
            String oneToManyTargetType = null;
            if (isOneToMany) {
                oneToManyMappedBy = getAnnotationValue(field, "id.bayu.data.annotation.OneToMany", "mappedBy");
                // Extract generic type from List<TargetType>
                TypeMirror fieldType = field.asType();
                if (fieldType instanceof DeclaredType dt && !dt.getTypeArguments().isEmpty()) {
                    oneToManyTargetType = getQualifiedTypeName(dt.getTypeArguments().get(0));
                }
            }

            // Handle @ElementCollection
            String elementCollectionItemType = null;
            if (isElementCollection) {
                TypeMirror fieldType = field.asType();
                if (fieldType instanceof DeclaredType dt && !dt.getTypeArguments().isEmpty()) {
                    elementCollectionItemType = getQualifiedTypeName(dt.getTypeArguments().get(0));
                }
            }

            meta.addColumn(new ColumnMapping(
                    fieldName, colName,
                    field.asType().toString(),
                    getQualifiedTypeName(field.asType()),
                    isId, isGenerated, nullable,
                    isCreatedAt, isUpdatedAt,
                    isElementCollection, isOneToMany,
                    oneToManyMappedBy, oneToManyTargetType,
                    elementCollectionItemType));
        }

        return meta;
    }

    private void resolveOneToManyRelations(EntityMetadata meta, int depth) {
        if (depth > 3) return; // Max recursion depth

        List<OneToManyRelation> relations = new ArrayList<>();

        for (ColumnMapping col : meta.getOneToManyColumns()) {
            String targetType = col.oneToManyTargetType();
            if (targetType == null) continue;

            TypeElement childElement = elementUtils.getTypeElement(targetType);
            if (childElement == null) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                        "Bayu: Could not find @OneToMany target type " + targetType);
                continue;
            }

            // Parse child entity metadata
            EntityMetadata childMeta = parseEntityMetadata(childElement);
            if (childMeta.getIdColumn() == null) continue;

            // Resolve FK field: try mappedBy, then mappedBy + "Id"
            String mappedBy = col.oneToManyMappedBy();
            String fkFieldName = resolveFkFieldName(childMeta, mappedBy);
            if (fkFieldName == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Bayu: Cannot resolve FK field for @OneToMany '" + col.fieldName() +
                                "'. mappedBy='" + mappedBy + "' not found on " + childMeta.getSimpleName() +
                                ". Try mappedBy=\"" + mappedBy + "Id\"");
                continue;
            }

            ColumnMapping fkCol = childMeta.findColumnByFieldName(fkFieldName);
            String fkColumnName = fkCol != null ? fkCol.columnName() : camelToSnake(fkFieldName);

            // Recursively resolve child's own @OneToMany
            resolveOneToManyRelations(childMeta, depth + 1);

            String fieldName = col.fieldName();
            relations.add(new OneToManyRelation(
                    fieldName,
                    "set" + capitalize(fieldName),
                    "get" + capitalize(fieldName),
                    childMeta,
                    fkFieldName,
                    fkColumnName,
                    true, true, // cascade + orphanRemoval default
                    childMeta.getOneToManyRelations()
            ));

            messager.printMessage(Diagnostic.Kind.NOTE,
                    "Bayu: Resolved @OneToMany " + meta.getSimpleName() + "." + fieldName +
                            " -> " + childMeta.getSimpleName() + " (FK: " + fkColumnName + ")");
        }

        meta.setOneToManyRelations(relations);
    }

    private String resolveFkFieldName(EntityMetadata childMeta, String mappedBy) {
        if (mappedBy == null) return null;
        // Exact match
        if (childMeta.findColumnByFieldName(mappedBy) != null) return mappedBy;
        // Try + "Id"
        String withId = mappedBy + "Id";
        if (childMeta.findColumnByFieldName(withId) != null) return withId;
        return null;
    }

    private List<QueryMethodDefinition.QueryParam> extractMethodParams(ExecutableElement method) {
        List<QueryMethodDefinition.QueryParam> params = new ArrayList<>();
        for (VariableElement param : method.getParameters()) {
            params.add(new QueryMethodDefinition.QueryParam(
                    param.getSimpleName().toString(),
                    param.asType().toString(),
                    getQualifiedTypeName(param.asType())));
        }
        return params;
    }

    private boolean isBaseRepositoryMethod(String name) {
        return switch (name) {
            case "findById", "findAll", "save", "deleteById", "count", "existsById" -> true;
            default -> false;
        };
    }

    private String camelToSnake(String camelCase) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) result.append('_');
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private void resolveFields(BeanDefinition bean) {
        // Walk superclass chain to find inherited @Autowired/@Value fields and methods
        List<Element> allElements = new ArrayList<>();
        TypeElement current = bean.getTypeElement();
        while (current != null) {
            allElements.addAll(current.getEnclosedElements());
            TypeMirror superMirror = current.getSuperclass();
            if (superMirror instanceof DeclaredType dt && dt.asElement() instanceof TypeElement superType) {
                if (superType.getQualifiedName().toString().equals("java.lang.Object")) break;
                current = superType;
            } else {
                break;
            }
        }

        for (Element enclosed : allElements) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) enclosed;

                // Check @Autowired
                if (getAnnotationMirror(field, "id.bayu.core.annotation.Autowired") != null) {
                    // Validate setter exists for private fields
                    String setterName = "set" + capitalize(field.getSimpleName().toString());
                    if (field.getModifiers().contains(Modifier.PRIVATE) && !hasMethod(bean.getTypeElement(), setterName)) {
                        messager.printMessage(Diagnostic.Kind.ERROR,
                                "Bayu: @Autowired field '" + field.getSimpleName() +
                                        "' in " + bean.getQualifiedName() +
                                        " is private but has no setter '" + setterName + "()'. " +
                                        "Add a public setter or use constructor injection.",
                                field);
                        continue;
                    }

                    String qualifier = getAnnotationValue(field, "id.bayu.core.annotation.Qualifier", "value");
                    bean.addFieldDependency(FieldDependency.autowired(
                            field.getSimpleName().toString(),
                            field.asType().toString(),
                            getQualifiedTypeName(field.asType()),
                            qualifier
                    ));
                }

                // Check @Value
                if (getAnnotationMirror(field, "id.bayu.core.annotation.Value") != null) {
                    String setterName = "set" + capitalize(field.getSimpleName().toString());
                    if (field.getModifiers().contains(Modifier.PRIVATE) && !hasMethod(bean.getTypeElement(), setterName)) {
                        messager.printMessage(Diagnostic.Kind.ERROR,
                                "Bayu: @Value field '" + field.getSimpleName() +
                                        "' in " + bean.getQualifiedName() +
                                        " is private but has no setter '" + setterName + "()'. " +
                                        "Add a public setter.",
                                field);
                        continue;
                    }

                    String valueExpr = getAnnotationValue(field, "id.bayu.core.annotation.Value", "value");
                    bean.addFieldDependency(FieldDependency.value(
                            field.getSimpleName().toString(),
                            field.asType().toString(),
                            getQualifiedTypeName(field.asType()),
                            valueExpr
                    ));
                }
            }

            // Check for @Autowired constructor
            if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement ctor = (ExecutableElement) enclosed;
                if (getAnnotationMirror(ctor, "id.bayu.core.annotation.Autowired") != null) {
                    for (VariableElement param : ctor.getParameters()) {
                        bean.addConstructorParam(new BeanDefinition.ConstructorParam(
                                param.asType().toString(),
                                getQualifiedTypeName(param.asType()),
                                param.getSimpleName().toString()
                        ));
                    }
                }
            }

            // Check for @Autowired setter methods: public void setXxx(Type param)
            if (enclosed.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosed;
                if (getAnnotationMirror(method, "id.bayu.core.annotation.Autowired") != null
                        && method.getSimpleName().toString().startsWith("set")
                        && method.getParameters().size() == 1) {
                    VariableElement param = method.getParameters().get(0);
                    // Derive field name from setter: setService -> service
                    String setterName = method.getSimpleName().toString();
                    String fieldName = Character.toLowerCase(setterName.charAt(3)) + setterName.substring(4);

                    bean.addFieldDependency(FieldDependency.autowired(
                            fieldName,
                            param.asType().toString(),
                            getQualifiedTypeName(param.asType()),
                            null
                    ));
                }
            }
        }
    }

    private void extractRoutes(TypeElement controller, BeanDefinition bean, String basePath,
                                List<RouteDefinition> routes) {
        // Build type variable resolution map from superclass chain
        // e.g., CategoryController extends BaseCrudController<Category> -> T = Category
        Map<String, String> typeVarMap = new HashMap<>();
        TypeElement current = controller;
        while (current != null) {
            TypeMirror superMirror = current.getSuperclass();
            if (superMirror instanceof DeclaredType dt && dt.asElement() instanceof TypeElement superType) {
                String superName = superType.getQualifiedName().toString();
                if (superName.equals("java.lang.Object")) break;
                // Map type parameters: superType has type params [T], dt has type args [Category]
                var typeParams = superType.getTypeParameters();
                var typeArgs = dt.getTypeArguments();
                for (int i = 0; i < Math.min(typeParams.size(), typeArgs.size()); i++) {
                    typeVarMap.put(typeParams.get(i).toString(), getQualifiedTypeName(typeArgs.get(i)));
                }
                current = superType;
            } else {
                break;
            }
        }

        // Collect methods from this class AND superclasses
        List<Element> allMethods = new ArrayList<>();
        current = controller;
        while (current != null) {
            allMethods.addAll(current.getEnclosedElements());
            TypeMirror superMirror = current.getSuperclass();
            if (superMirror instanceof DeclaredType dt && dt.asElement() instanceof TypeElement superType) {
                if (superType.getQualifiedName().toString().equals("java.lang.Object")) break;
                current = superType;
            } else {
                break;
            }
        }

        for (Element enclosed : allMethods) {
            if (!(enclosed instanceof ExecutableElement method)) continue;

            String httpMethod = null;
            String path = "";

            if (getAnnotationMirror(method, "id.bayu.web.annotation.GetMapping") != null) {
                httpMethod = "GET";
                path = getAnnotationValue(method, "id.bayu.web.annotation.GetMapping", "value");
            } else if (getAnnotationMirror(method, "id.bayu.web.annotation.PostMapping") != null) {
                httpMethod = "POST";
                path = getAnnotationValue(method, "id.bayu.web.annotation.PostMapping", "value");
            } else if (getAnnotationMirror(method, "id.bayu.web.annotation.PutMapping") != null) {
                httpMethod = "PUT";
                path = getAnnotationValue(method, "id.bayu.web.annotation.PutMapping", "value");
            } else if (getAnnotationMirror(method, "id.bayu.web.annotation.DeleteMapping") != null) {
                httpMethod = "DELETE";
                path = getAnnotationValue(method, "id.bayu.web.annotation.DeleteMapping", "value");
            } else if (getAnnotationMirror(method, "id.bayu.web.annotation.PatchMapping") != null) {
                httpMethod = "PATCH";
                path = getAnnotationValue(method, "id.bayu.web.annotation.PatchMapping", "value");
            }

            if (httpMethod == null) continue;

            String fullPath = normalizePath(basePath + "/" + (path != null ? path : ""));

            // Extract parameter bindings (resolve generics via typeVarMap)
            List<RouteDefinition.ParamBinding> params = new ArrayList<>();
            for (VariableElement param : method.getParameters()) {
                RouteDefinition.ParamBinding binding = extractParamBinding(param, typeVarMap);
                if (binding != null) {
                    params.add(binding);
                }
            }

            // Get response status
            int responseStatus = 200;
            String statusValue = getAnnotationValue(method, "id.bayu.web.annotation.ResponseStatus", "value");
            if (statusValue != null) {
                try {
                    responseStatus = Integer.parseInt(statusValue);
                } catch (NumberFormatException ignored) {}
            }

            String returnType = resolveTypeVar(method.getReturnType().toString(), typeVarMap);

            // Check @Secured on method first, then class level
            String[] securedRoles = extractSecuredRoles(method);
            if (securedRoles == null) {
                securedRoles = extractSecuredRoles(controller);
            }

            routes.add(new RouteDefinition(
                    httpMethod, fullPath, bean, method,
                    method.getSimpleName().toString(),
                    params, returnType, responseStatus, securedRoles
            ));

            messager.printMessage(Diagnostic.Kind.NOTE,
                    "Bayu: Registered route " + httpMethod + " " + fullPath +
                            " -> " + bean.getQualifiedName() + "." + method.getSimpleName());
        }
    }

    private RouteDefinition.ParamBinding extractParamBinding(VariableElement param,
                                                              Map<String, String> typeVarMap) {
        String simpleType = resolveTypeVar(getSimpleTypeName(param.asType()), typeVarMap);
        String qualifiedType = resolveTypeVar(getQualifiedTypeName(param.asType()), typeVarMap);

        // @PathVariable
        if (getAnnotationMirror(param, "id.bayu.web.annotation.PathVariable") != null) {
            String name = getAnnotationValue(param, "id.bayu.web.annotation.PathVariable", "value");
            if (name == null || name.isEmpty()) {
                name = param.getSimpleName().toString();
            }
            return new RouteDefinition.ParamBinding(
                    RouteDefinition.ParamType.PATH_VARIABLE,
                    name, param.getSimpleName().toString(),
                    simpleType, qualifiedType, true, null
            );
        }

        // @RequestParam
        if (getAnnotationMirror(param, "id.bayu.web.annotation.RequestParam") != null) {
            String name = getAnnotationValue(param, "id.bayu.web.annotation.RequestParam", "value");
            if (name == null || name.isEmpty()) {
                name = param.getSimpleName().toString();
            }
            String defaultValue = getAnnotationValue(param, "id.bayu.web.annotation.RequestParam", "defaultValue");
            return new RouteDefinition.ParamBinding(
                    RouteDefinition.ParamType.REQUEST_PARAM,
                    name, param.getSimpleName().toString(),
                    simpleType, qualifiedType, true, defaultValue
            );
        }

        // @RequestBody
        if (getAnnotationMirror(param, "id.bayu.web.annotation.RequestBody") != null) {
            return new RouteDefinition.ParamBinding(
                    RouteDefinition.ParamType.REQUEST_BODY,
                    null, param.getSimpleName().toString(),
                    simpleType, qualifiedType, true, null
            );
        }

        return null;
    }

    private String resolveTypeVar(String typeName, Map<String, String> typeVarMap) {
        if (typeName == null) return null;
        // Direct type variable: "T" -> "com.seduh.entity.Category"
        String resolved = typeVarMap.get(typeName);
        if (resolved != null) return resolved;
        return typeName;
    }

    // -- Utility methods --

    private String getBeanName(TypeElement typeElement, String annotationFqn) {
        String explicitName = getAnnotationValue(typeElement, annotationFqn, "value");
        if (explicitName != null && !explicitName.isEmpty()) {
            return explicitName;
        }
        return decapitalize(typeElement.getSimpleName().toString());
    }

    private String getRequestMappingPath(TypeElement typeElement) {
        String path = getAnnotationValue(typeElement, "id.bayu.web.annotation.RequestMapping", "value");
        return path != null ? path : "";
    }

    private AnnotationMirror getAnnotationMirror(Element element, String annotationFqn) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(annotationFqn)) {
                return mirror;
            }
        }
        return null;
    }

    private String getAnnotationValue(Element element, String annotationFqn, String attributeName) {
        AnnotationMirror mirror = getAnnotationMirror(element, annotationFqn);
        if (mirror == null) return null;

        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                mirror.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals(attributeName)) {
                return entry.getValue().getValue().toString();
            }
        }
        return null;
    }

    private String getQualifiedTypeName(TypeMirror type) {
        if (type instanceof DeclaredType declaredType) {
            return ((TypeElement) declaredType.asElement()).getQualifiedName().toString();
        }
        return type.toString();
    }

    private String getSimpleTypeName(TypeMirror type) {
        if (type instanceof DeclaredType declaredType) {
            return declaredType.asElement().getSimpleName().toString();
        }
        return type.toString();
    }

    private String normalizePath(String path) {
        // Remove double slashes and ensure leading slash
        String normalized = path.replaceAll("/+", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private String[] extractSecuredRoles(Element element) {
        AnnotationMirror mirror = getAnnotationMirror(element, "id.bayu.security.annotation.Secured");
        if (mirror == null) return null;

        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                mirror.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals("value")) {
                Object val = entry.getValue().getValue();
                if (val instanceof List<?> list) {
                    return list.stream()
                            .map(v -> v.toString().replace("\"", ""))
                            .toArray(String[]::new);
                }
            }
        }
        return null;
    }

    private boolean hasMethod(TypeElement type, String methodName) {
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD
                    && enclosed.getSimpleName().toString().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    private void generateServiceLoaderFile(String packageName) throws IOException {
        var file = filer.createResource(StandardLocation.CLASS_OUTPUT,
                "", "META-INF/services/id.bayu.core.ApplicationContext");
        try (Writer writer = file.openWriter()) {
            writer.write(packageName + ".BayuGeneratedContext\n");
        }
    }
}
