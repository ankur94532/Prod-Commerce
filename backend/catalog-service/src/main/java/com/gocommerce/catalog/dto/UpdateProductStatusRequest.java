package com.gocommerce.catalog.dto;

public class UpdateProductStatusRequest {
    private boolean active;

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
