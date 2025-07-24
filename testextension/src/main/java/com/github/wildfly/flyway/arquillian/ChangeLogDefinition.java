package com.github.wildfly.flyway.arquillian;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChangeLogDefinition {
    ResourceLocation resourceLocation() default ResourceLocation.VFS_ROOT;
}