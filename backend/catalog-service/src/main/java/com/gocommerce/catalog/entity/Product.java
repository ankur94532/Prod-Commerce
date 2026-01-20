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
