package com.gocommerce.catalog.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class ProductResponse {

    private String id;
    private String slug;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private String categorySlug;
    private String brand;
    private List<String> imageUrls;
    private Integer stockQuantity;
    private Map<String, String> attributes;

    public ProductResponse() {
    }

    public ProductResponse(String id,
                           String slug,
                           String name,
                           String description,
                           BigDecimal price,
                           String currency,
                           String categorySlug,
                           String brand,
                           List<String> imageUrls,
                           Integer stockQuantity,
                           Map<String, String> attributes) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.categorySlug = categorySlug;
        this.brand = brand;
        this.imageUrls = imageUrls;
        this.stockQuantity = stockQuantity;
        this.attributes = attributes;
    }

    public String getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCategorySlug() {
        return categorySlug;
    }

    public String getBrand() {
        return brand;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setCategorySlug(String categorySlug) {
        this.categorySlug = categorySlug;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }
}
