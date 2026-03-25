package id.bayu.processor.model;

import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;

public class RepositoryDefinition {

    private final TypeElement interfaceElement;
    private final String qualifiedName;
    private final String simpleName;
    private final String packageName;
    private final EntityMetadata entity;
    private final String idType;
    private final List<QueryMethodDefinition> queryMethods = new ArrayList<>();

    public RepositoryDefinition(TypeElement interfaceElement, EntityMetadata entity, String idType) {
        this.interfaceElement = interfaceElement;
        this.qualifiedName = interfaceElement.getQualifiedName().toString();
        this.simpleName = interfaceElement.getSimpleName().toString();
        int lastDot = qualifiedName.lastIndexOf('.');
        this.packageName = lastDot > 0 ? qualifiedName.substring(0, lastDot) : "";
        this.entity = entity;
        this.idType = idType;
    }

    public void addQueryMethod(QueryMethodDefinition method) {
        queryMethods.add(method);
    }

    public TypeElement getInterfaceElement() { return interfaceElement; }
    public String getQualifiedName() { return qualifiedName; }
    public String getSimpleName() { return simpleName; }
    public String getPackageName() { return packageName; }
    public EntityMetadata getEntity() { return entity; }
    public String getIdType() { return idType; }
    public List<QueryMethodDefinition> getQueryMethods() { return queryMethods; }
    public String getImplSimpleName() { return simpleName + "$BayuImpl"; }
    public String getImplQualifiedName() { return packageName + "." + getImplSimpleName(); }
}
