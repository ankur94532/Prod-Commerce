package com.gocommerce.search.service;

import com.gocommerce.search.config.ProductIndexSettings;
import com.gocommerce.search.model.ProductDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the physical layout of the product index.
 *
 * <p>Reindexing used to delete the live index and rebuild it in place. That gave every
 * rebuild a window in which search returned nothing, and any failure part-way through left
 * the index empty or half-populated with no way back. Documents now live in a timestamped
 * concrete index, and {@code products} is an alias pointing at it. A rebuild fills a brand
 * new index and only then moves the alias, which Elasticsearch applies atomically: readers
 * see the old index until the instant they see the complete new one, and a failed rebuild
 * changes nothing.
 */
@Component
public class SearchIndexManager {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexManager.class);

    /** Must match {@code @Document(indexName)} on {@link ProductDocument}. */
    public static final String ALIAS = "products";
    private static final String INDEX_PREFIX = ALIAS + "-";

    private final ElasticsearchOperations operations;

    public SearchIndexManager(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    private IndexOperations indexOps(String name) {
        return operations.indexOps(IndexCoordinates.of(name));
    }

    /** The concrete index the alias currently points at, or null when the alias does not exist. */
    public String currentIndex() {
        try {
            Map<String, Set<org.springframework.data.elasticsearch.core.index.AliasData>> aliases =
                    indexOps(ALIAS).getAliases(ALIAS);
            return aliases.keySet().stream().min(Comparator.naturalOrder()).orElse(null);
        } catch (RuntimeException error) {
            log.debug("Could not resolve the alias {}: {}", ALIAS, error.toString());
            return null;
        }
    }

    /** True when {@code products} exists as a concrete index rather than as an alias. */
    public boolean hasLegacyConcreteIndex() {
        return currentIndex() == null && indexOps(ALIAS).exists();
    }

    /** Creates an empty, correctly mapped index that nothing reads yet. */
    public String createIndex() {
        String name = INDEX_PREFIX + System.currentTimeMillis();
        IndexOperations ops = indexOps(name);
        ops.create(ProductIndexSettings.settings());
        ops.putMapping(operations.indexOps(ProductDocument.class).createMapping());
        log.info("Created search index {}", name);
        return name;
    }

    public void refresh(String index) {
        indexOps(index).refresh();
    }

    public long count(String index) {
        return operations.count(
                org.springframework.data.elasticsearch.core.query.Query.findAll(),
                ProductDocument.class, IndexCoordinates.of(index));
    }

    /**
     * Points the alias at {@code newIndex} and stops pointing it at anything else, in a
     * single Elasticsearch alias action. Returns the indices the alias previously pointed
     * at, which the caller can delete once it is satisfied.
     */
    public List<String> promote(String newIndex) {
        List<String> previous = previousIndices();

        AliasActions actions = new AliasActions();
        for (String index : previous) {
            actions.add(new AliasAction.Remove(
                    AliasActionParameters.builder().withIndices(index).withAliases(ALIAS).build()));
        }
        actions.add(new AliasAction.Add(
                AliasActionParameters.builder().withIndices(newIndex).withAliases(ALIAS).build()));

        indexOps(newIndex).alias(actions);
        log.info("Search alias {} now points at {} (was {})", ALIAS, newIndex,
                previous.isEmpty() ? "nothing" : previous);
        return previous;
    }

    private List<String> previousIndices() {
        try {
            return indexOps(ALIAS).getAliases(ALIAS).keySet().stream().sorted().toList();
        } catch (RuntimeException error) {
            return List.of();
        }
    }

    /** Deletes an index. Never throws: a leftover index wastes disk, it does not break reads. */
    public void dropIndex(String index) {
        if (index == null || index.equals(ALIAS)) {
            return;
        }
        try {
            indexOps(index).delete();
            log.info("Deleted search index {}", index);
        } catch (RuntimeException error) {
            log.warn("Could not delete the retired search index {}: {}", index, error.toString());
        }
    }

    /**
     * Removes indices left behind by interrupted rebuilds: anything matching the naming
     * scheme that the alias does not point at and that is older than the retention window.
     */
    public int dropOrphanedIndices(String keep, long olderThanMillis) {
        int dropped = 0;
        try {
            long cutoff = System.currentTimeMillis() - olderThanMillis;
            for (var information : operations.indexOps(IndexCoordinates.of(INDEX_PREFIX + "*")).getInformation()) {
                String name = information.getName();
                if (name.equals(keep) || !name.startsWith(INDEX_PREFIX)) {
                    continue;
                }
                try {
                    if (Long.parseLong(name.substring(INDEX_PREFIX.length())) < cutoff) {
                        dropIndex(name);
                        dropped++;
                    }
                } catch (NumberFormatException ignored) {
                    // Not one of ours; leave it alone.
                }
            }
        } catch (RuntimeException error) {
            log.debug("Could not enumerate search indices for cleanup: {}", error.toString());
        }
        return dropped;
    }

    /**
     * One-time migration from the old layout, where {@code products} was a concrete index.
     * An alias cannot share a name with an index, so the old index has to be dropped before
     * the alias can be created. The caller must have already built and verified
     * {@code newIndex}, so the gap is a single delete-then-alias, not a full rebuild.
     */
    public void replaceLegacyIndex(String newIndex) {
        log.warn("Migrating the legacy concrete index {} to an alias. Search returns nothing "
                + "for the moment between the delete and the alias creation.", ALIAS);
        indexOps(ALIAS).delete();
        indexOps(newIndex).alias(new AliasActions().add(new AliasAction.Add(
                AliasActionParameters.builder().withIndices(newIndex).withAliases(ALIAS).build())));
        log.info("Migration complete: {} is now an alias for {}", ALIAS, newIndex);
    }
}
