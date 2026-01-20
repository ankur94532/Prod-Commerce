package com.gocommerce.catalog.service;

import com.gocommerce.catalog.dto.AdminProductRequest;
import com.gocommerce.catalog.entity.Product;
import com.gocommerce.catalog.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private AdminProductService adminProductService;

    @BeforeEach
    void setUp() {
        adminProductService = new AdminProductService(productRepository);
    }

    private AdminProductRequest sampleRequest() {
        AdminProductRequest dto = new AdminProductRequest();
        dto.setSlug("s26-ultra-256gb-gray");
        dto.setName("Galaxy S26 Ultra 256GB (Gray)");
        dto.setDescription("Flagship smartphone");
        dto.setPrice(new BigDecimal("89999"));
        dto.setCurrency("INR");
        dto.setCategorySlug("smartphones");
        dto.setBrand("Samsung");
        dto.setImageUrls(List.of("url1", "url2"));
        dto.setStockQuantity(50);
        dto.setActive(true);
        dto.setAttributes(Map.of("storage", "256GB", "color", "Gray"));
        return dto;
    }

    @Test
    void listProducts_delegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("id").descending());
        Page<Product> page = new PageImpl<>(List.of(), pageable, 0);

        when(productRepository.findAll(pageable)).thenReturn(page);

        Page<Product> result = adminProductService.listProducts(pageable);

        assertThat(result).isSameAs(page);
        verify(productRepository).findAll(pageable);
    }

    @Test
    void getProduct_returnsProductWhenFound() {
        Product p = new Product();
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        Product result = adminProductService.getProduct(1L);

        assertThat(result).isSameAs(p);
    }

    @Test
    void getProduct_throwsWhenNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> adminProductService.getProduct(1L));
    }

    @Test
    void createProduct_savesWithDefaultsWhenNulls() {
        AdminProductRequest dto = sampleRequest();
        dto.setImageUrls(null);
        dto.setAttributes(null);
        dto.setActive(null);

        when(productRepository.save(any(Product.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Product created = adminProductService.createProduct(dto);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product saved = captor.getValue();

        assertThat(saved.getSlug()).isEqualTo(dto.getSlug());
        assertThat(saved.getImageUrls()).isNotNull();
        assertThat(saved.getImageUrls()).isEmpty();
        assertThat(saved.getAttributes()).isNotNull();
        assertThat(saved.getAttributes()).isEmpty();
        assertThat(saved.isActive()).isTrue();

        assertThat(created).isSameAs(saved);
    }

    @Test
    void updateProduct_updatesFieldsAndSaves() {
        AdminProductRequest dto = sampleRequest();

        Product existing = new Product();
        existing.setSlug("old-slug");
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        Product updated = adminProductService.updateProduct(1L, dto);

        assertThat(updated.getSlug()).isEqualTo(dto.getSlug());
        assertThat(updated.getName()).isEqualTo(dto.getName());
        assertThat(updated.getPrice()).isEqualByComparingTo(dto.getPrice());
        assertThat(updated.getImageUrls()).containsExactlyElementsOf(dto.getImageUrls());
        assertThat(updated.getAttributes()).isEqualTo(dto.getAttributes());

        verify(productRepository).save(existing);
    }

    @Test
    void updateStatus_setsActiveFlagAndSaves() {
        Product existing = new Product();
        existing.setActive(false);

        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        Product result = adminProductService.updateStatus(1L, true);

        assertThat(result.isActive()).isTrue();
        verify(productRepository).save(existing);
    }

    @Test
    void deleteProduct_loadsThenDeletes() {
        Product existing = new Product();
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

        adminProductService.deleteProduct(1L);

        verify(productRepository).delete(existing);
    }
}
