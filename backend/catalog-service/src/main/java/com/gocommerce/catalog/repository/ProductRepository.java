package com.gocommerce.catalog.repository;

import com.gocommerce.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByCategorySlugAndActiveTrue(String categorySlug, Pageable pageable);

    Optional<Product> findBySlugAndActiveTrue(String slug);

    /**
     * Decrement stock if there is enough quantity.
     * Returns number of rows updated (0 means not enough stock or product not found).
     *
     * NOTE: assumes Product has a field named 'stock'.
     */
    @Modifying
    @Query("""
           UPDATE Product p
           SET p.stockQuantity = p.stockQuantity - :quantity
           WHERE p.id = :productId AND p.stockQuantity >= :quantity
           """)
    int decrementStockIfEnough(@Param("productId") Long productId,
                               @Param("quantity") int quantity);
    @Query("select distinct p.categorySlug from Product p where p.active = true")
    List<String> findDistinctActiveCategorySlugs();
}
