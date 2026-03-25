package id.bayu.processor.model;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;

public class BeanDefinition {

    public enum Kind {
        COMPONENT, SERVICE, CONTROLLER, CONFIGURATION, BEAN_METHOD
    }

    private final TypeElement typeElement;
    private final String beanName;
    private final Kind kind;
    private final List<FieldDependency> fieldDependencies = new ArrayList<>();
    private final List<ConstructorParam> constructorParams = new ArrayList<>();

    // For @Bean methods
    private TypeElement configClass;
    private ExecutableElement beanMethod;

    // Fully qualified class name
    private final String qualifiedName;

    public BeanDefinition(TypeElement typeElement, String beanName, Kind kind) {
        this.typeElement = typeElement;
        this.beanName = beanName;
        this.kind = kind;
        this.qualifiedName = typeElement.getQualifiedName().toString();
    }

    // Static factory for @Bean methods
    public static BeanDefinition forBeanMethod(TypeElement returnType, String beanName,
                                                TypeElement configClass, ExecutableElement beanMethod) {
        BeanDefinition def = new BeanDefinition(returnType, beanName, Kind.BEAN_METHOD);
        def.configClass = configClass;
        def.beanMethod = beanMethod;
        return def;
    }

    public TypeElement getTypeElement() { return typeElement; }
    public String getBeanName() { return beanName; }
    public Kind getKind() { return kind; }
    public String getQualifiedName() { return qualifiedName; }
    public List<FieldDependency> getFieldDependencies() { return fieldDependencies; }
    public List<ConstructorParam> getConstructorParams() { return constructorParams; }
    public TypeElement getConfigClass() { return configClass; }
    public ExecutableElement getBeanMethod() { return beanMethod; }

    public void addFieldDependency(FieldDependency dep) {
        fieldDependencies.add(dep);
    }

    public void addConstructorParam(ConstructorParam param) {
        constructorParams.add(param);
    }

    public record ConstructorParam(String typeName, String qualifiedTypeName, String paramName) {}

    @Override
    public String toString() {
        return kind + ":" + beanName + "(" + qualifiedName + ")";
    }
}
