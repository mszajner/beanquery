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
package io.github.mszajner.beanquery.core.query;

import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a {@link QueryRequest} against the database with the JPA Criteria API,
 * including an AND/OR filter tree.
 *
 * <p>Only whitelisted paths from the supplied {@link EntityMetadata} ever reach
 * JPA. Depth-1 nested paths ({@code "customer.name"}) become {@code LEFT JOIN}s,
 * deduplicated <em>globally</em> per query - one association is joined once no
 * matter how many AND/OR branches (or {@code select}/{@code sort}) reference it.
 * Rows come back as {@link LinkedHashMap}s keyed by the request's {@code select}
 * names. A second {@code COUNT} query built from the same predicate tree produces
 * {@code totalElements}.
 */
public class DynamicQueryExecutor {

    private static final char LIKE_ESCAPE = '\\';

    private final EntityManager entityManager;
    private final FilterValueConverter valueConverter;

    public DynamicQueryExecutor(EntityManager entityManager, FilterValueConverter valueConverter) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
        this.valueConverter = Objects.requireNonNull(valueConverter, "valueConverter");
    }

    @Transactional(readOnly = true)
    public QueryResult execute(EntityMetadata meta, QueryRequest request) {
        return execute(meta, request, List.of());
    }

    /**
     * @param mandatoryPredicates host-supplied predicates (already resolved) that are
     *                             {@code AND}-ed over the whole user filter tree - in
     *                             both the row query and the {@code COUNT} query
     */
    @Transactional(readOnly = true)
    public QueryResult execute(EntityMetadata meta, QueryRequest request, List<ResolvedFilterNode> mandatoryPredicates) {
        Objects.requireNonNull(meta, "meta");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(mandatoryPredicates, "mandatoryPredicates");

        List<FieldMetadata> selectFields = resolveSelect(meta, request.select());
        ResolvedFilterNode filters = withMandatory(resolveFilters(meta, request.filters()), mandatoryPredicates);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        Page page = request.page();

        List<Map<String, Object>> rows = fetchRows(cb, meta, selectFields, filters, request.sort(), page);
        long totalElements = count(cb, meta, filters);
        int totalPages = page.size() == 0 ? 0 : (int) ((totalElements + page.size() - 1) / page.size());

        return new QueryResult(rows, new PageInfo(page.number(), page.size(), totalElements, totalPages));
    }

    /**
     * Test seam - runs an already-resolved tree without the JSON conversion layer.
     * It selects a single arbitrary selectable field of {@code meta} and applies
     * {@code resolved} as-is. Not part of the supported public API; it is only
     * {@code public} because the executor IT lives in a different package.
     */
    @Transactional(readOnly = true)
    public QueryResult executeResolved(EntityMetadata meta, ResolvedFilterNode resolved, Page page) {
        Objects.requireNonNull(meta, "meta");
        Objects.requireNonNull(page, "page");

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        List<FieldMetadata> selectFields = resolveSelect(meta, List.of(firstSelectable(meta)));

        List<Map<String, Object>> rows = fetchRows(cb, meta, selectFields, resolved, List.of(), page);
        long totalElements = count(cb, meta, resolved);
        int totalPages = page.size() == 0 ? 0 : (int) ((totalElements + page.size() - 1) / page.size());

        return new QueryResult(rows, new PageInfo(page.number(), page.size(), totalElements, totalPages));
    }

    private ResolvedFilterNode resolveFilters(EntityMetadata meta, FilterNode filters) {
        try {
            return valueConverter.resolve(filters, meta);
        } catch (FilterValueConversionException e) {
            throw new InvalidQueryException(e.getErrors());
        }
    }

    /**
     * {@code AND} the mandatory predicates on top of the whole user tree, so a user's
     * top-level {@code OR} can never widen past them.
     */
    private static ResolvedFilterNode withMandatory(ResolvedFilterNode userTree, List<ResolvedFilterNode> mandatory) {
        if (mandatory.isEmpty()) {
            return userTree;
        }
        List<ResolvedFilterNode> parts = new ArrayList<>(mandatory.size() + 1);
        if (userTree != null) {
            parts.add(userTree);
        }
        parts.addAll(mandatory);
        return parts.size() == 1 ? parts.get(0) : new ResolvedFilterNode.Group(LogicalOperator.AND, parts);
    }

    // -- main query ------------------------------------------------------

    private List<Map<String, Object>> fetchRows(CriteriaBuilder cb, EntityMetadata meta,
            List<FieldMetadata> selectFields, ResolvedFilterNode filters, List<Sort> sort, Page page) {

        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Object> root = cq.from(entityClass(meta));
        PathResolver paths = new PathResolver(root);

        List<Selection<?>> selections = new ArrayList<>(selectFields.size());
        for (FieldMetadata field : selectFields) {
            selections.add(paths.resolve(field.path()));
        }
        cq.select(cb.tuple(selections.toArray(new Selection<?>[0])));

        Predicate where = toPredicate(cb, paths, filters);
        if (where != null) {
            cq.where(where);
        }

        cq.orderBy(buildOrder(cb, paths, root, meta, sort));

        TypedQuery<Tuple> query = entityManager.createQuery(cq);
        query.setFirstResult((int) Math.min((long) page.number() * page.size(), Integer.MAX_VALUE));
        query.setMaxResults(page.size());

        List<Tuple> tuples = query.getResultList();
        List<Map<String, Object>> rows = new ArrayList<>(tuples.size());
        for (Tuple tuple : tuples) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < selectFields.size(); i++) {
                row.put(selectFields.get(i).name(), tuple.get(i));
            }
            rows.add(row);
        }
        return rows;
    }

    // -- count query ----------------------------------------------------

    private long count(CriteriaBuilder cb, EntityMetadata meta, ResolvedFilterNode filters) {
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Object> root = cq.from(entityClass(meta));
        PathResolver paths = new PathResolver(root);

        cq.select(cb.count(root));
        Predicate where = toPredicate(cb, paths, filters);
        if (where != null) {
            cq.where(where);
        }
        return entityManager.createQuery(cq).getSingleResult();
    }

    // -- predicate tree ----------------------------------------------

    private Predicate toPredicate(CriteriaBuilder cb, PathResolver paths, ResolvedFilterNode node) {
        if (node == null) {
            return null;
        }
        if (node instanceof ResolvedFilterNode.AlwaysFalse) {
            return cb.equal(cb.literal(1), cb.literal(0));
        }
        if (node instanceof ResolvedFilterNode.Group group) {
            Predicate[] parts = group.children().stream()
                    .map(child -> toPredicate(cb, paths, child))
                    .filter(Objects::nonNull)
                    .toArray(Predicate[]::new);
            if (parts.length == 0) {
                return null;
            }
            if (parts.length == 1) {
                return parts[0];
            }
            return group.logic() == LogicalOperator.OR ? cb.or(parts) : cb.and(parts);
        }
        ResolvedFilterNode.Condition condition = (ResolvedFilterNode.Condition) node;
        Expression<?> path = paths.resolve(condition.field().path());
        return conditionPredicate(cb, path, condition.op(), condition.value());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate conditionPredicate(CriteriaBuilder cb, Expression<?> path, FilterOperator op, Object value) {
        return switch (op) {
            case EQ -> cb.equal(path, value);
            case NE -> cb.notEqual(path, value);
            case GT -> cb.greaterThan((Expression) path, (Comparable) value);
            case GTE -> cb.greaterThanOrEqualTo((Expression) path, (Comparable) value);
            case LT -> cb.lessThan((Expression) path, (Comparable) value);
            case LTE -> cb.lessThanOrEqualTo((Expression) path, (Comparable) value);
            case LIKE -> cb.like((Expression<String>) path, contains((String) value), LIKE_ESCAPE);
            case ILIKE -> cb.like(cb.lower((Expression<String>) path),
                    contains(((String) value).toLowerCase(Locale.ROOT)), LIKE_ESCAPE);
            case IN -> path.in((Collection<?>) value);
            case NOT_IN -> cb.not(path.in((Collection<?>) value));
            case BETWEEN -> {
                Range range = (Range) value;
                yield cb.between((Expression) path, (Comparable) range.lower(), (Comparable) range.upper());
            }
            case IS_NULL -> cb.isNull(path);
            case IS_NOT_NULL -> cb.isNotNull(path);
        };
    }

    private static String contains(String literal) {
        return "%" + escapeLike(literal) + "%";
    }

    private static String escapeLike(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == LIKE_ESCAPE || c == '%' || c == '_') {
                sb.append(LIKE_ESCAPE);
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // -- order by -----------------------------------------------------

    private List<Order> buildOrder(CriteriaBuilder cb, PathResolver paths, Root<Object> root,
            EntityMetadata meta, List<Sort> sortClauses) {

        List<Order> orders = new ArrayList<>(sortClauses.size() + 1);
        Set<String> orderedPaths = new HashSet<>();
        for (Sort clause : sortClauses) {
            FieldMetadata field = requireField(meta, clause.field());
            orderedPaths.add(field.path());
            Expression<?> path = paths.resolve(field.path());
            orders.add(clause.direction() == Direction.DESC ? cb.desc(path) : cb.asc(path));
        }
        // stabilise on the entity id so ties (and repeated pages) are deterministic
        String idAttribute = idAttributeName(meta);
        if (orderedPaths.add(idAttribute)) {
            orders.add(cb.asc(root.get(idAttribute)));
        }
        return orders;
    }

    // -- helpers -----------------------------------------------------

    private List<FieldMetadata> resolveSelect(EntityMetadata meta, List<String> select) {
        List<FieldMetadata> fields = new ArrayList<>(select.size());
        for (String name : select) {
            fields.add(requireField(meta, name));
        }
        return fields;
    }

    private static String firstSelectable(EntityMetadata meta) {
        return meta.fields().stream()
                .filter(FieldMetadata::selectable)
                .map(FieldMetadata::name)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Entity '" + meta.name() + "' has no selectable field"));
    }

    private static FieldMetadata requireField(EntityMetadata meta, String name) {
        return meta.field(name).orElseThrow(() -> new InvalidQueryException(
                List.of("unknown field '" + name + "' for entity '" + meta.name() + "'")));
    }

    @SuppressWarnings("unchecked")
    private static Class<Object> entityClass(EntityMetadata meta) {
        return (Class<Object>) meta.entityClass();
    }

    private String idAttributeName(EntityMetadata meta) {
        EntityType<?> entityType = entityManager.getMetamodel().entity(meta.entityClass());
        for (SingularAttribute<?, ?> attribute : entityType.getSingularAttributes()) {
            if (attribute.isId()) {
                return attribute.getName();
            }
        }
        throw new IllegalStateException(
                "Entity " + meta.entityClass().getName() + " has no single id attribute to stabilise ordering");
    }

    /**
     * Resolves whitelisted paths, deduplicating LEFT JOINs per association. One
     * instance is shared across select, the whole filter tree and sort within a
     * single Criteria query.
     */
    private static final class PathResolver {

        private final Root<Object> root;
        private final Map<String, Join<Object, Object>> joins = new HashMap<>();

        PathResolver(Root<Object> root) {
            this.root = root;
        }

        Expression<?> resolve(String path) {
            int dot = path.indexOf('.');
            if (dot < 0) {
                return root.get(path);
            }
            String association = path.substring(0, dot);
            String leaf = path.substring(dot + 1);
            Join<Object, Object> join = joins.computeIfAbsent(association, a -> root.join(a, JoinType.LEFT));
            return join.get(leaf);
        }
    }
}
