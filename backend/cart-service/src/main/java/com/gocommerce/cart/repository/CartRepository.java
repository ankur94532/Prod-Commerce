package com.gocommerce.cart.repository;

import com.gocommerce.cart.entity.Cart;
import org.springframework.data.repository.CrudRepository;

public interface CartRepository extends CrudRepository<Cart, String> {
}
