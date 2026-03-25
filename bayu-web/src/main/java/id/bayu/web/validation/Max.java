package id.bayu.web.validation;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Max {
    long value();
    String message() default "must be at most {value}";
}
