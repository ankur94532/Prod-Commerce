package com.gocommerce.catalog.service;

import com.gocommerce.catalog.exception.OutOfStockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class InventoryService {
    private final JdbcTemplate jdbc;

    public InventoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void decrementStock(Long productId, int quantity, String reservationId) {
        String state = lockReservation(productId, quantity, reservationId);
        if (state.equals("RESERVED")) return;
        if (state.equals("RELEASED")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation already released");
        }
        int updated = jdbc.update("""
                UPDATE products SET stock_quantity = stock_quantity - ?
                WHERE id = ? AND active = true AND stock_quantity >= ?
                """, quantity, productId, quantity);
        if (updated == 0) throw new OutOfStockException(productId, quantity);
        jdbc.update("UPDATE inventory_reservations SET state = 'RESERVED' WHERE reservation_id = ?", reservationId);
    }

    @Transactional
    public void incrementStock(Long productId, int quantity, String reservationId) {
        String state = lockReservation(productId, quantity, reservationId);
        if (state.equals("RELEASED")) return;
        if (state.equals("RESERVED")) {
            int updated = jdbc.update("UPDATE products SET stock_quantity = stock_quantity + ? WHERE id = ?",
                    quantity, productId);
            if (updated != 1) throw new IllegalStateException("Reserved product missing: " + productId);
        }
        // Also persist a tombstone when reserve has not arrived (or rolled back).
        jdbc.update("UPDATE inventory_reservations SET state = 'RELEASED' WHERE reservation_id = ?", reservationId);
    }

    private String lockReservation(Long productId, int quantity, String reservationId) {
        if (productId == null || quantity <= 0 || reservationId == null || reservationId.isBlank()
                || reservationId.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Positive quantity and reservation ID required");
        }
        jdbc.update("""
                INSERT INTO inventory_reservations (reservation_id, product_id, quantity, state)
                VALUES (?, ?, ?, 'NEW') ON CONFLICT (reservation_id) DO NOTHING
                """, reservationId, productId, quantity);
        return jdbc.queryForObject("""
                SELECT product_id, quantity, state FROM inventory_reservations
                WHERE reservation_id = ? FOR UPDATE
                """, (rs, n) -> {
            if (rs.getLong("product_id") != productId || rs.getInt("quantity") != quantity) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation payload mismatch");
            }
            return rs.getString("state");
        }, reservationId);
    }
}
