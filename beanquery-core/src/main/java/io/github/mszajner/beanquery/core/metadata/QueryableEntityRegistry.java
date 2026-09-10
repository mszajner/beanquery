/*
 * Copyright 2026 Mirosław Szajner
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.mszajner.beanquery.core.metadata;

import io.github.mszajner.beanquery.core.annotation.Queryable;
import io.github.mszajner.beanquery.core.annotation.QueryableField;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.metamodel.EntityType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.util.ReflectionUtils;

/**
 * Builds and holds the query whitelist.
 *
 * <p>At startup ({@link SmartInitializingSingleton}) it reads every entity
 * managed by the JPA {@link EntityManagerFactory}, keeps the ones annotated with
 * {@link Queryable}, and turns their {@link QueryableField}-annotated fields into
 * {@link EntityMetadata} keyed by the entity's query name.
 *
 * <p>The model is validated eagerly; any problem (name collision, bad
 * {@code nested} usage, missing nested sub-property) throws
 * {@link QueryableMetadataException} and aborts the application start.
 */
public class QueryableEntityRegistry implements SmartInitializingSingleton {

    private final EntityManagerFactory entityManagerFactory;

    private volatile Map<String, EntityMetadata> entitiesByName = Map.of();

    public QueryableEntityRegistry(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = Objects.requireNonNull(entityManagerFactory, "entityManagerFactory");
    }

    @Override
    public void afterSingletonsInstantiated() {
        initialize(managedEntityClasses());
    }

    // -- public API ---------------------------------------------------------

    /**
     * @throws UnknownEntityException if no entity is registered under {@code name}
     */
    public EntityMetadata getRequired(String name) {
        EntityMetadata metadata = entitiesByName.get(name);
        if (metadata == null) {
            throw new UnknownEntityException(name);
        }
        return metadata;
    }

    /** All registered entities, in discovery order. */
    public Collection<EntityMetadata> getAll() {
        return entitiesByName.values();
    }

    // -- building ----------------------------------------------------------

    /** Package-private entry point so the build/validation logic is unit-testable without JPA. */
    void initialize(Collection<Class<?>> candidateEntityClasses) {
        this.entitiesByName = Collections.unmodifiableMap(scan(candidateEntityClasses));
    }

    private Collection<Class<?>> managedEntityClasses() {
        List<Class<?>> classes = new ArrayList<>();
        for (EntityType<?> entityType : entityManagerFactory.getMetamodel().getEntities()) {
            Class<?> javaType = entityType.getJavaType();
            if (javaType != null) {
                classes.add(javaType);
            }
        }
        return classes;
    }

    static Map<String, EntityMetadata> scan(Collection<Class<?>> candidateEntityClasses) {
        Map<String, EntityMetadata> result = new LinkedHashMap<>();
        Map<String, Class<?>> nameOwners = new LinkedHashMap<>();

        for (Class<?> entityClass : candidateEntityClasses) {
            Queryable queryable = entityClass.getAnnotation(Queryable.class);
            if (queryable == null) {
                continue;
            }
            String name = resolveName(entityClass, queryable);
            Class<?> previousOwner = nameOwners.putIfAbsent(name, entityClass);
            if (previousOwner != null) {
                throw new QueryableMetadataException(
                        "Duplicate queryable entity name '" + name + "' declared by "
                                + previousOwner.getName() + " and " + entityClass.getName());
            }
            result.put(name, buildEntityMetadata(name, entityClass));
        }
        return result;
    }

    static EntityMetadata buildEntityMetadata(String name, Class<?> entityClass) {
        Map<String, FieldMetadata> fields = new LinkedHashMap<>();

        ReflectionUtils.doWithFields(entityClass, field -> {
            QueryableField annotation = field.getAnnotation(QueryableField.class);
            if (annotation == null) {
                return;
            }
            for (FieldMetadata fieldMetadata : buildFields(entityClass, field, annotation)) {
                if (fields.putIfAbsent(fieldMetadata.name(), fieldMetadata) != null) {
                    throw new QueryableMetadataException(
                            "Duplicate queryable field '" + fieldMetadata.name() + "' on " + entityClass.getName());
                }
            }
        });

        return new EntityMetadata(name, entityClass, List.copyOf(fields.values()));
    }

    private static List<FieldMetadata> buildFields(Class<?> entityClass, Field field, QueryableField annotation) {
        String[] nested = annotation.nested();
        boolean association = field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class);

        if (!association) {
            if (nested.length > 0) {
                throw new QueryableMetadataException(
                        "Field '" + field.getName() + "' on " + entityClass.getName()
                                + " declares nested sub-fields but is not a @ManyToOne/@OneToOne association");
            }
            return List.of(scalarField(field, annotation));
        }

        if (nested.length == 0) {
            throw new QueryableMetadataException(
                    "Association field '" + field.getName() + "' on " + entityClass.getName()
                            + " must declare nested sub-fields, e.g. @QueryableField(nested = {\"id\", \"name\"})");
        }

        Class<?> targetType = field.getType();
        List<FieldMetadata> nestedFields = new ArrayList<>(nested.length);
        for (String subProperty : nested) {
            if (subProperty.contains(".")) {
                throw new QueryableMetadataException(
                        "Nested field '" + field.getName() + "." + subProperty + "' on " + entityClass.getName()
                                + " exceeds the maximum nesting depth of 1");
            }
            Field subField = ReflectionUtils.findField(targetType, subProperty);
            if (subField == null) {
                throw new QueryableMetadataException(
                        "Nested field '" + subProperty + "' does not exist on " + targetType.getName()
                                + " (declared via '" + field.getName() + "' on " + entityClass.getName() + ")");
            }
            String path = field.getName() + "." + subProperty;
            nestedFields.add(new FieldMetadata(
                    path,
                    path,
                    subField.getType(),
                    annotation.selectable(),
                    annotation.filterable(),
                    annotation.sortable(),
                    resolveOperators(annotation, subField.getType()),
                    FieldKind.JOINED));
        }
        return nestedFields;
    }

    private static FieldMetadata scalarField(Field field, QueryableField annotation) {
        return new FieldMetadata(
                field.getName(),
                field.getName(),
                field.getType(),
                annotation.selectable(),
                annotation.filterable(),
                annotation.sortable(),
                resolveOperators(annotation, field.getType()),
                FieldKind.COLUMN);
    }

    private static Set<FilterOperator> resolveOperators(QueryableField annotation, Class<?> javaType) {
        FilterOperator[] explicit = annotation.operators();
        if (explicit.length == 0) {
            return DefaultOperators.forType(javaType);
        }
        return EnumSet.copyOf(List.of(explicit));
    }

    private static String resolveName(Class<?> entityClass, Queryable queryable) {
        String declared = queryable.name();
        if (declared != null && !declared.isBlank()) {
            return declared.trim();
        }
        return decapitalize(entityClass.getSimpleName());
    }

    /**
     * Lower-camel-case the simple class name, matching {@code java.beans.Introspector}:
     * the first character is lowercased unless the first two are both upper case.
     */
    private static String decapitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }
}
