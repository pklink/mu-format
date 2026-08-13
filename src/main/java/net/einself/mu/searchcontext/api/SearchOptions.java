package net.einself.mu.searchcontext.api;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Validated search parameters.
 *
 * @param scope
 *            the entity types to search
 * @param field
 *            restrict matching to this single TOML attribute, or null to search
 *            the standard attributes of each entity type
 * @param year
 *            keep only releases with this {@code release-year-original}, or
 *            null
 * @param medium
 *            keep only releases with this {@code source-medium}, or null
 * @param role
 *            credit matching counts only credits with this role, or null for
 *            any role
 * @param limit
 *            maximum number of results, 0 for unlimited
 */
public record SearchOptions(
                                Set<EntityType> scope,
                                @Nullable String field,
                                @Nullable String year,
                                @Nullable String medium,
                                @Nullable String role,
                                int limit) {

    public boolean searches(EntityType type) {
        return scope.contains(type);
    }

}
