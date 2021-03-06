package org.javers.core.metamodel.clazz;

import org.javers.core.metamodel.annotation.Entity;
import org.javers.core.metamodel.annotation.Id;
import org.javers.core.metamodel.annotation.TypeName;

/**
 * @author bartosz.walacik
 */
@TypeName("myName")
@Entity
public class JaversEntityWithTypeAlias {
    @Id
    private String id;
}
