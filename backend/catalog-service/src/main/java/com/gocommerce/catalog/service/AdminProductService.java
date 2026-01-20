// src/main/java/com/gocommerce/catalog/service/AdminProductService.java
package com.gocommerce.catalog.service;

import com.gocommerce.catalog.dto.AdminProductRequest;
import com.gocommerce.catalog.entity.Product;
import com.gocommerce.catalog.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminProductService {

    private final ProductRepository productRepository;

    public AdminProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<Product> listProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    public Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
    }

    public Product createProduct(AdminProductRequest dto) {
        List<String> imageUrls = dto.getImageUrls() != null
                ? dto.getImageUrls()
                : new ArrayList<>();

        Map<String, String> attributes = dto.getAttributes() != null
                ? dto.getAttributes()
                : new HashMap<>();

        Product product = new Product(
                dto.getSlug(),
                dto.getName(),
                dto.getDescription(),
                dto.getPrice(),
                dto.getCurrency(),
                dto.getCategorySlug(),
                dto.getBrand(),
                imageUrls,
                dto.getStockQuantity(),
                dto.getActive() != null ? dto.getActive() : true,
                attributes
        );
        return productRepository.save(product);
    }

    public Product updateProduct(Long id, AdminProductRequest dto) {
        Product product = getProduct(id);

        product.setSlug(dto.getSlug());
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setCurrency(dto.getCurrency());
        product.setCategorySlug(dto.getCategorySlug());
        product.setBrand(dto.getBrand());

        List<String> imageUrls = dto.getImageUrls() != null
                ? dto.getImageUrls()
                : new ArrayList<>();
        product.setImageUrls(imageUrls);

        product.setStockQuantity(dto.getStockQuantity());

        if (dto.getActive() != null) {
            product.setActive(dto.getActive());
        }

        Map<String, String> attributes = dto.getAttributes() != null
                ? dto.getAttributes()
                : new HashMap<>();
        product.setAttributes(attributes);

        return productRepository.save(product);
    }

    public Product updateStatus(Long id, boolean active) {
        Product product = getProduct(id);
        product.setActive(active);
        return productRepository.save(product);
    }

    // NEW: hard delete
    public void deleteProduct(Long id) {
        Product product = getProduct(id);
        productRepository.delete(product);
    }
}
