package com.example.reactiveorderapi.product.dto;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Filtros opcionales del listado de productos. Un valor nulo significa "sin filtro".
 */
public record ProductSearchCriteria(
        String name,
        String sku,
        Boolean active,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {

    public ProductSearchCriteria {
        name = blankToNull(name);
        sku = sku == null || sku.isBlank() ? null : sku.trim().toUpperCase(Locale.ROOT);
    }

    public boolean hasInvalidPriceRange() {
        return minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
