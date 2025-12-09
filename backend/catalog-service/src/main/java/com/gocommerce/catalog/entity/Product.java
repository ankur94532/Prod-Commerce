package com.gocommerce.catalog.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Document(collection = "products")
public class Product {

    @Id
    private String id;

    @Indexed(unique = true)
    private String slug;

    private String name;
    private String description;

    private BigDecimal price;
    private String currency;        // e.g. "INR"

    private String categorySlug;    // e.g. "smartphones"
    private String brand;

    private List<String> imageUrls;

    private Integer stockQuantity;
    private boolean active;

    private Map<String, String> attributes; // e.g. color=black, storage=256GB

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
        this.imageUrls = imageUrls;
        this.stockQuantity = stockQuantity;
        this.active = active;
        this.attributes = attributes;
    }

    public String getId() {
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
