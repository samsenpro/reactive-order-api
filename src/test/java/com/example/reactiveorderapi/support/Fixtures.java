package com.example.reactiveorderapi.support;

import com.example.reactiveorderapi.inventory.entity.Inventory;
import com.example.reactiveorderapi.order.entity.Order;
import com.example.reactiveorderapi.order.entity.OrderItem;
import com.example.reactiveorderapi.order.entity.OrderStatus;
import com.example.reactiveorderapi.product.entity.Product;
import com.example.reactiveorderapi.security.AuthenticatedUser;
import com.example.reactiveorderapi.user.entity.Role;
import com.example.reactiveorderapi.user.entity.User;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entidades de prueba con id asignado, como las devolvería R2DBC.
 */
public final class Fixtures {

    public static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");

    private Fixtures() {
    }

    public static User user(Long id, Role role) {
        return new User(id, "User " + id, "user" + id + "@test.local", "$2a$10$hash", role, true, NOW, NOW);
    }

    public static AuthenticatedUser principal(Long id, Role role) {
        return AuthenticatedUser.from(user(id, role));
    }

    public static Product product(Long id, String price, boolean active) {
        return new Product(id, "Product " + id, null, "SKU-" + id, new BigDecimal(price), active, NOW, NOW);
    }

    public static Inventory inventory(Long productId, int available, int reserved) {
        return new Inventory(productId * 10, productId, available, reserved, NOW);
    }

    public static Order order(Long id, Long userId, OrderStatus status) {
        return new Order(id, userId, status, new BigDecimal("10.00"), NOW, NOW);
    }

    public static OrderItem item(Long orderId, Long productId, int quantity, String unitPrice) {
        BigDecimal price = new BigDecimal(unitPrice);
        return new OrderItem(orderId * 100 + productId, orderId, productId, quantity, price,
                price.multiply(BigDecimal.valueOf(quantity)));
    }
}
