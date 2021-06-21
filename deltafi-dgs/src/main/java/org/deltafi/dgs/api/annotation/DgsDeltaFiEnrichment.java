package org.deltafi.dgs.api.annotation;
import com.netflix.graphql.dgs.DgsData;
import org.deltafi.dgs.generated.DgsConstants;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@DgsData(parentType=DgsConstants.DELTAFIENRICHMENTS.TYPE_NAME)
public @interface DgsDeltaFiEnrichment {
    @SuppressWarnings("unused")
    String field() default "";
}