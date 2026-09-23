package com.example.reactiveorderapi.order.service;

import com.example.reactiveorderapi.exception.InsufficientStockException;
import com.example.reactiveorderapi.exception.ProductInactiveException;
import com.example.reactiveorderapi.exception.ProductNotFoundException;
import com.example.reactiveorderapi.inventory.entity.Inventory;
import com.example.reactiveorderapi.order.entity.OrderItem;
import com.example.reactiveorderapi.product.entity.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Cálculo del pedido a partir de datos del backend. Es una función pura (sin E/S): recibe el
 * catálogo ya cargado de forma reactiva y es trivial de probar.
 * <p>
 * La comprobación de stock de este paso es solo un fallo rápido con un buen mensaje de error.
 * La garantía real contra el overselling es el UPDATE condicional de la reserva.
 */
public final class OrderPricing {

    private static final int MONEY_SCALE = 2;

    private OrderPricing() {
    }

    public static PricedOrder price(RequestedItems requested,
                                    Map<Long, Product> products,
                                    Map<Long, Inventory> inventory) {
        List<PricedLine> lines = requested.quantities().entrySet().stream()
                .map(entry -> priceLine(entry.getKey(), entry.getValue(), products, inventory))
                .toList();
        BigDecimal total = lines.stream()
                .map(PricedLine::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        return new PricedOrder(lines, total);
    }

    private static PricedLine priceLine(Long productId, int quantity,
                                        Map<Long, Product> products,
                                        Map<Long, Inventory> inventory) {
        Product product = products.get(productId);
        if (product == null) {
            throw new ProductNotFoundException(productId);
        }
        if (!product.active()) {
            throw new ProductInactiveException(productId);
        }
        Inventory stock = inventory.get(productId);
        if (stock == null || !stock.canFulfil(quantity)) {
            throw new InsufficientStockException(productId, quantity);
        }
        // El precio sale siempre del backend; el cliente solo envía producto y cantidad.
        return new PricedLine(productId, quantity, product.price());
    }

    public record PricedOrder(List<PricedLine> lines, BigDecimal totalAmount) {
    }

    public record PricedLine(Long productId, int quantity, BigDecimal unitPrice) {

        public BigDecimal subtotal() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        }

        public OrderItem toItem(Long orderId) {
            return OrderItem.of(orderId, productId, quantity, unitPrice, subtotal());
        }
    }
}
