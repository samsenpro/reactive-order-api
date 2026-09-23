package com.example.reactiveorderapi.common;

import java.util.List;

/**
 * Página de resultados. R2DBC no tiene {@code Page}: el contenido y el total se obtienen
 * con dos consultas independientes que se ejecutan en paralelo ({@code Mono.zip}).
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> of(List<T> content, PageQuery query, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / query.size());
        return new PageResponse<>(content, query.page(), query.size(), totalElements, totalPages);
    }
}
