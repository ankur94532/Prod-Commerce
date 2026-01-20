package com.gocommerce.catalog.service;

import com.gocommerce.catalog.dto.ProductResponse;
import com.gocommerce.catalog.entity.Product;
import com.gocommerce.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository);
    }

    private Product sampleProduct() {
        return new Product(
                "s26-ultra-256gb-gray",
                "Galaxy S26 Ultra 256GB (Gray)",
                "Flagship smartphone with high-end camera and display.",
                new BigDecimal("89999"),
                "INR",
                "smartphones",
                "Samsung",
                List.of("https://via.placeholder.com/640x480?text=S26+Ultra+256GB+Gray"),
                50,
                true,
                Map.of("storage", "256GB", "color", "Gray"));
    }

    @Test
    void listProducts_withoutCategory_returnsActiveProducts() {
        Pageable pageable = PageRequest.of(0, 10);
        Product p = sampleProduct();
        Page<Product> page = new PageImpl<>(List.of(p), pageable, 1);

        when(productRepository.findByActiveTrue(pageable)).thenReturn(page);

        Page<ProductResponse> result = productService.listProducts(null, pageable);

        assertEquals(1, result.getTotalElements());
        ProductResponse resp = result.getContent().get(0);
        assertEquals(p.getSlug(), resp.getSlug());
        assertEquals(p.getName(), resp.getName());
        verify(productRepository).findByActiveTrue(pageable);
        verify(productRepository, never()).findByCategorySlugAndActiveTrue(any(), any());
    }

    @Test
    void listProducts_withCategory_filtersByCategory() {
        Pageable pageable = PageRequest.of(0, 10);
        Product p = sampleProduct();
        Page<Product> page = new PageImpl<>(List.of(p), pageable, 1);

        when(productRepository.findByCategorySlugAndActiveTrue("smartphones", pageable))
                .thenReturn(page);

        Page<ProductResponse> result = productService.listProducts("smartphones", pageable);

        assertEquals(1, result.getTotalElements());
        verify(productRepository).findByCategorySlugAndActiveTrue("smartphones", pageable);
        verify(productRepository, never()).findByActiveTrue(any());
    }

    @Test
    void listProducts_emptyPage_returnsEmpty() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(productRepository.findByActiveTrue(pageable)).thenReturn(page);

        Page<ProductResponse> result = productService.listProducts(null, pageable);

        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void getBySlug_whenFound_returnsProductResponse() {
        Product p = sampleProduct();

        when(productRepository.findBySlugAndActiveTrue("s26-ultra-256gb-gray"))
                .thenReturn(Optional.of(p));

        ProductResponse resp = productService.getBySlug("s26-ultra-256gb-gray");

        assertNotNull(resp);
        assertEquals("s26-ultra-256gb-gray", resp.getSlug());
        assertEquals("Galaxy S26 Ultra 256GB (Gray)", resp.getName());
    }

    @Test
    void getBySlug_whenNotFound_throws() {
        when(productRepository.findBySlugAndActiveTrue("unknown"))
                .thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> productService.getBySlug("unknown"));
    }
}
