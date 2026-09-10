package com.gocommerce.catalog.seed;

import com.gocommerce.catalog.entity.Product;
import com.gocommerce.catalog.repository.ProductRepository;
import org.springframework.stereotype.Service;

@Service
public class CatalogSeeder {
    private final ProductRepository products;

    public CatalogSeeder(ProductRepository products) {
        this.products = products;
    }

    public int seed(int size) {
        int saved = 0;
        for (Product source : ProductSeedCatalog.products(size)) {
            Product target = products.findBySlug(source.getSlug()).orElseGet(Product::new);
            target.setSlug(source.getSlug());
            target.setName(source.getName());
            target.setDescription(source.getDescription());
            target.setPrice(source.getPrice());
            target.setCurrency(source.getCurrency());
            target.setCategorySlug(source.getCategorySlug());
            target.setBrand(source.getBrand());
            target.setImageUrls(source.getImageUrls());
            target.setStockQuantity(source.getStockQuantity());
            target.setActive(source.isActive());
            target.setAttributes(source.getAttributes());
            // Every property of the generated product has to be copied here. A field added to
            // Product and forgotten in this list does not fail: the row is written with a
            // default and the omission only shows up as a feature that quietly does nothing.
            // That is what happened to productFamily, which left search collapsing every
            // product into a family of one. CatalogSeederFidelityTest now fails on a
            // dropped field instead.
            target.setProductFamily(source.getProductFamily());
            products.save(target);
            saved++;
        }
        return saved;
    }
}
