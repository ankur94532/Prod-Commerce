package com.gocommerce.catalog.seed;

import com.gocommerce.catalog.entity.Product;
import com.gocommerce.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The seeder copies the generated catalog into the database property by property, and a
 * property left out of that list does not fail anything: the row is written with a default
 * and nothing complains.
 *
 * That is not hypothetical. `productFamily` was added to Product, populated by the generator,
 * indexed by search, and used to collapse variant results -- and it was not copied here, so
 * every product reached the database as its own family and collapsing became a no-op that
 * still passed every test and returned plausible-looking results.
 *
 * So this test does not name fields. It reflects over Product's own properties and asserts
 * each one survives seeding, which fails on the next field somebody forgets rather than on
 * the one already fixed.
 */
class CatalogSeederFidelityTest {

    /** Not written by the seeder: the identifier is the database's, and equality is by slug. */
    private static final List<String> NOT_COPIED = List.of("id", "class");

    @Test
    void everyGeneratedProductPropertyReachesTheDatabase() throws Exception {
        var saved = new HashMap<String, Product>();
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findBySlug(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            saved.put(product.getSlug(), product);
            return product;
        });

        int count = new CatalogSeeder(repository).seed(120);
        assertThat(count).isEqualTo(saved.size());

        List<PropertyDescriptor> properties = new ArrayList<>();
        for (PropertyDescriptor descriptor : Introspector.getBeanInfo(Product.class).getPropertyDescriptors()) {
            if (descriptor.getReadMethod() != null && !NOT_COPIED.contains(descriptor.getName())) {
                properties.add(descriptor);
            }
        }
        // A guard on the guard: if Product ever loses its properties to a refactor, an empty
        // loop below would pass without checking anything.
        assertThat(properties).hasSizeGreaterThan(8);

        Map<String, List<String>> dropped = new HashMap<>();
        for (Product source : ProductSeedCatalog.products(120)) {
            Product target = saved.get(source.getSlug());
            assertThat(target).as("product %s was never saved", source.getSlug()).isNotNull();
            for (PropertyDescriptor property : properties) {
                Object expected = property.getReadMethod().invoke(source);
                Object actual = property.getReadMethod().invoke(target);
                if (expected != null && !expected.equals(actual)) {
                    dropped.computeIfAbsent(property.getName(), key -> new ArrayList<>()).add(source.getSlug());
                }
            }
        }
        assertThat(dropped)
                .as("the seeder did not carry these Product properties into the saved row")
                .isEmpty();
    }

    @Test
    void variantsAreSeededIntoTheFamilyOfTheProductTheyVaryFrom() {
        // The property collapsing depends on: a variant belongs to its base product, and a
        // base product is a family of one. If every product were its own family -- the state
        // the dropped setter produced -- this catalog would collapse to nothing.
        var products = ProductSeedCatalog.products(120);
        var families = new HashMap<String, List<String>>();
        for (Product product : products) {
            assertThat(product.getProductFamily()).as("%s has no family", product.getSlug()).isNotBlank();
            families.computeIfAbsent(product.getProductFamily(), key -> new ArrayList<>()).add(product.getSlug());
        }

        assertThat(families).as("120 products should not be 120 separate families").hasSizeLessThan(products.size());
        long grouped = families.values().stream().filter(members -> members.size() > 1).count();
        assertThat(grouped).as("at least some products should have variants grouped with them").isPositive();
        for (var entry : families.entrySet()) {
            for (String slug : entry.getValue()) {
                assertThat(slug).as("variant %s should extend its family slug", slug).startsWith(entry.getKey());
            }
        }
    }
}
