package com.gocommerce.cart.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.util.ArrayList;
import java.util.List;

@RedisHash("cart")
public class Cart {

    @Id
    private String userId;

    private List<CartItem> items = new ArrayList<>();

    public Cart() {
    }

    public Cart(String userId) {
        this.userId = userId;
        this.items = new ArrayList<>();
    }

    public String getUserId() {
        return userId;
    }

    public List<CartItem> getItems() {
        return items;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setItems(List<CartItem> items) {
        this.items = items;
    }
}
