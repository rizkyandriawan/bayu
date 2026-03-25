package id.bayu.processor.resolver;

import id.bayu.processor.model.BeanDefinition;
import id.bayu.processor.model.FieldDependency;
import id.bayu.processor.registry.BeanRegistry;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;
import java.util.*;

public class DependencyResolver {

    private final BeanRegistry registry;
    private final Messager messager;

    public DependencyResolver(BeanRegistry registry, Messager messager) {
        this.registry = registry;
        this.messager = messager;
    }

    /**
     * Topologically sort beans so dependencies come before dependents.
     * Returns sorted list or null if a cycle is detected.
     */
    public List<BeanDefinition> resolve() {
        // graph: bean -> set of beans it depends on
        Map<String, Set<String>> dependsOn = new HashMap<>();
        // reverse graph: bean -> set of beans that depend on it
        Map<String, Set<String>> dependedBy = new HashMap<>();
        Map<String, BeanDefinition> beanMap = new HashMap<>();

        // Build adjacency lists
        for (BeanDefinition bean : registry.getAll()) {
            String key = bean.getQualifiedName();
            beanMap.put(key, bean);
            dependsOn.put(key, new HashSet<>());
            dependedBy.putIfAbsent(key, new HashSet<>());

            Set<String> deps = collectDependencies(bean);
            for (String dep : deps) {
                // Only count dependencies that are registered beans
                if (registry.contains(dep)) {
                    dependsOn.get(key).add(dep);
                    dependedBy.computeIfAbsent(dep, k -> new HashSet<>()).add(key);
                }
            }
        }

        // Kahn's algorithm: in-degree = number of dependencies a bean has
        Map<String, Integer> inDegree = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : dependsOn.entrySet()) {
            inDegree.put(entry.getKey(), entry.getValue().size());
        }

        // Start with beans that have no dependencies
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<BeanDefinition> sorted = new ArrayList<>();

        while (!queue.isEmpty()) {
            String current = queue.poll();
            BeanDefinition bean = beanMap.get(current);
            if (bean != null) {
                sorted.add(bean);
            }

            // For each bean that depends on current, reduce its in-degree
            for (String dependent : dependedBy.getOrDefault(current, Set.of())) {
                int newDegree = inDegree.merge(dependent, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(dependent);
                }
            }
        }

        if (sorted.size() < dependsOn.size()) {
            Set<String> inCycle = new HashSet<>(dependsOn.keySet());
            sorted.forEach(b -> inCycle.remove(b.getQualifiedName()));
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Circular dependency detected among beans: " + inCycle);
            return null;
        }

        return sorted;
    }

    private Set<String> collectDependencies(BeanDefinition bean) {
        Set<String> deps = new HashSet<>();

        // Field dependencies (only @Autowired, not @Value)
        for (FieldDependency field : bean.getFieldDependencies()) {
            if (!field.isValue()) {
                deps.add(field.fieldQualifiedTypeName());
            }
        }

        // Constructor dependencies
        for (BeanDefinition.ConstructorParam param : bean.getConstructorParams()) {
            deps.add(param.qualifiedTypeName());
        }

        // For @Bean methods, depend on the config class
        if (bean.getKind() == BeanDefinition.Kind.BEAN_METHOD && bean.getConfigClass() != null) {
            deps.add(bean.getConfigClass().getQualifiedName().toString());
        }

        return deps;
    }
}
