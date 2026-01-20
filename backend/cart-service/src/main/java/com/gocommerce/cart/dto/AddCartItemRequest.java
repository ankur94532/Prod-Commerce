package com.gocommerce.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class AddCartItemRequest {

    @NotBlank
    private String productId;

    @NotBlank
    private String productSlug;

    @NotBlank
    private String name;

    @NotNull
    private BigDecimal price;

    @NotBlank
    private String currency;

    @Min(1)
    private int quantity;

    private String imageUrl;

    public String getProductId() {
        return productId;
    }

    public String getProductSlug() {
        return productSlug;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public void setProductSlug(String productSlug) {
        this.productSlug = productSlug;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
