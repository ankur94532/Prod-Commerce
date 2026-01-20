package com.gocommerce.catalog.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class AdminProductRequest {

    private String slug;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;      // "INR"
    private String categorySlug;  // "smartphones"
    private String brand;
    private List<String> imageUrls;
    private Integer stockQuantity;
    private Boolean active;
    private Map<String, String> attributes;

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getCategorySlug() { return categorySlug; }
    public void setCategorySlug(String categorySlug) { this.categorySlug = categorySlug; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Map<String, String> getAttributes() { return attributes; }
    public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
}
