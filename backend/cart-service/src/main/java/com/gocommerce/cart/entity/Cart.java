package com.gocommerce.cart.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RedisHash(value = "cart", timeToLive = 2_592_000)
public class Cart {

    @Id
    private String userId;

    private List<CartItem> items = new ArrayList<>();
    private long revision;
    private Map<String, String> appliedMutations = new LinkedHashMap<>();

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

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    @JsonIgnore
    public Map<String, String> getAppliedMutations() {
        return appliedMutations;
    }

    public void setAppliedMutations(Map<String, String> appliedMutations) {
        this.appliedMutations = appliedMutations == null ? new LinkedHashMap<>() : appliedMutations;
    }
}
