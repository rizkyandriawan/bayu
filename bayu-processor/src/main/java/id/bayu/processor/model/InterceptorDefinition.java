package id.bayu.processor.model;

import javax.lang.model.element.TypeElement;

public record InterceptorDefinition(
        TypeElement typeElement,
        String qualifiedName,
        String simpleName,
        int order
) implements Comparable<InterceptorDefinition> {
    @Override
    public int compareTo(InterceptorDefinition other) {
        return Integer.compare(this.order, other.order);
    }
}
