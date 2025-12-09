package com.gocommerce.cart.entity;

import java.math.BigDecimal;

public class CartItem {

    private String productId;
    private String productSlug;
    private String name;
    private BigDecimal price;
    private String currency;
    private int quantity;
    private String imageUrl;

    public CartItem() {
    }

    public CartItem(String productId,
                    String productSlug,
                    String name,
                    BigDecimal price,
                    String currency,
                    int quantity,
                    String imageUrl) {
        this.productId = productId;
        this.productSlug = productSlug;
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
    }

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
