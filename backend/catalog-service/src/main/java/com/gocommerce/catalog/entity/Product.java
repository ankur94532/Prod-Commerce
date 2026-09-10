// src/main/java/com/gocommerce/catalog/entity/Product.java
package com.gocommerce.catalog.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.*;
import jakarta.persistence.FetchType;
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency; // e.g. "INR"

    @Column(name = "category_slug")
    private String categorySlug; // e.g. "smartphones"

    /**
     * The product this row is a variant of. Search collapses result pages on it, so six
     * colours of one backpack occupy one slot instead of six. Defaults to the slug, which
     * makes a standalone product a family of one rather than a null nobody handles.
     */
    @Column(name = "product_family", nullable = false)
    private String productFamily;

    private String brand;

    // 👉 EAGER so it's fully loaded before JSON serialization
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url")
    private List<String> imageUrls = new ArrayList<>();

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Column(nullable = false)
    private boolean active;

    // 👉 Also EAGER to avoid the same issue if you serialize attributes
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_attributes", joinColumns = @JoinColumn(name = "product_id"))
    @MapKeyColumn(name = "attr_key")
    @Column(name = "attr_value")
    private Map<String, String> attributes = new HashMap<>();

    public Product() {
    }

    public Product(String slug,
                   String name,
                   String description,
                   BigDecimal price,
                   String currency,
                   String categorySlug,
                   String brand,
                   List<String> imageUrls,
                   Integer stockQuantity,
                   boolean active,
                   Map<String, String> attributes) {
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.categorySlug = categorySlug;
        this.brand = brand;
        this.imageUrls = imageUrls != null ? imageUrls : new ArrayList<>();
        this.stockQuantity = stockQuantity;
        this.active = active;
        this.attributes = attributes != null ? attributes : new HashMap<>();
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCategorySlug() {
        return categorySlug;
    }

    public void setCategorySlug(String categorySlug) {
        this.categorySlug = categorySlug;
    }

    public String getProductFamily() {
        return productFamily;
    }

    public void setProductFamily(String productFamily) {
        this.productFamily = productFamily;
    }

    /**
     * A product with no family would be invisible to collapsed search, so the column is NOT
     * NULL and this fills it in rather than letting an admin create a row that cannot be
     * grouped. Runs on update too: renaming a slug must not leave the family dangling.
     */
    @PrePersist
    @PreUpdate
    void defaultProductFamilyToSlug() {
        if (productFamily == null || productFamily.isBlank()) {
            productFamily = slug;
        }
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }
}
