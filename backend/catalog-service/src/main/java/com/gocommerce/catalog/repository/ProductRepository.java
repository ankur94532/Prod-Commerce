package com.gocommerce.catalog.repository;

import com.gocommerce.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByCategorySlugAndActiveTrue(String categorySlug, Pageable pageable);

    Optional<Product> findBySlugAndActiveTrue(String slug);

    Optional<Product> findBySlug(String slug);

    Optional<Product> findByIdAndActiveTrue(Long id);

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

    @Modifying
    @Query("""
           UPDATE Product p
           SET p.stockQuantity = p.stockQuantity + :quantity
           WHERE p.id = :productId
           """)
    int incrementStock(@Param("productId") Long productId,
                       @Param("quantity") int quantity);
    @Query("select distinct p.categorySlug from Product p where p.active = true order by p.categorySlug")
    List<String> findDistinctActiveCategorySlugs();

    @Query("select distinct p.brand from Product p where p.active = true and p.brand is not null order by p.brand")
    List<String> findDistinctActiveBrands();

    @Query("select min(p.price) from Product p where p.active = true")
    BigDecimal findMinActivePrice();

    @Query("select max(p.price) from Product p where p.active = true")
    BigDecimal findMaxActivePrice();

    @Query("""
           select distinct value(a)
           from Product p join p.attributes a
           where p.active = true
             and key(a) = :key
             and (:categorySlug is null or :categorySlug = '' or p.categorySlug = :categorySlug)
           order by value(a)
           """)
    List<String> findDistinctActiveAttributeValues(@Param("key") String key,
                                                   @Param("categorySlug") String categorySlug);
}
