package id.bayu.web.validation;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Pattern {
    String value();
    String message() default "must match pattern {value}";
}
