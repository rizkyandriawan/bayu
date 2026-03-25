package id.bayu.processor.registry;

import id.bayu.processor.model.BeanDefinition;

import java.util.*;

public class BeanRegistry {

    private final Map<String, BeanDefinition> beansByName = new LinkedHashMap<>();
    private final Map<String, BeanDefinition> beansByType = new LinkedHashMap<>();

    public void register(BeanDefinition bean) {
        beansByName.put(bean.getBeanName(), bean);
        beansByType.put(bean.getQualifiedName(), bean);
    }

    public BeanDefinition getByName(String name) {
        return beansByName.get(name);
    }

    public BeanDefinition getByType(String qualifiedName) {
        return beansByType.get(qualifiedName);
    }

    public BeanDefinition findByType(String qualifiedName) {
        // Direct match
        BeanDefinition direct = beansByType.get(qualifiedName);
        if (direct != null) return direct;

        // Check if any bean implements/extends the requested type
        // This is a simplified check - full implementation would walk the type hierarchy
        for (BeanDefinition bean : beansByName.values()) {
            if (bean.getQualifiedName().equals(qualifiedName)) {
                return bean;
            }
        }
        return null;
    }

    public Collection<BeanDefinition> getAll() {
        return Collections.unmodifiableCollection(beansByName.values());
    }

    public boolean contains(String qualifiedName) {
        return beansByType.containsKey(qualifiedName);
    }

    public int size() {
        return beansByName.size();
    }
}
