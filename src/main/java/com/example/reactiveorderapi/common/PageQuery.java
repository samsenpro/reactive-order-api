package com.example.reactiveorderapi.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Paginación con orden fijo definido en el servidor (del más reciente al más antiguo).
 * No se acepta un parámetro {@code sort} libre para no ordenar por columnas arbitrarias.
 */
public record PageQuery(int page, int size) {

    public static final String DEFAULT_PAGE = "0";
    public static final String DEFAULT_SIZE = "20";
    public static final int MAX_SIZE = 100;

    public static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

    public long offset() {
        return (long) page * size;
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size, NEWEST_FIRST);
    }
}
