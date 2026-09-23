package com.example.reactiveorderapi.product.repository;

import com.example.reactiveorderapi.common.PageQuery;
import com.example.reactiveorderapi.product.dto.ProductSearchCriteria;
import com.example.reactiveorderapi.product.entity.Product;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Búsqueda con filtros opcionales. Se construye un {@link Criteria} dinámico con
 * {@link R2dbcEntityTemplate}; los valores siempre se pasan como parámetros enlazados.
 */
@Repository
public class ProductSearchRepository {

    private final R2dbcEntityTemplate template;

    public ProductSearchRepository(R2dbcEntityTemplate template) {
        this.template = template;
    }

    public Flux<Product> search(ProductSearchCriteria criteria, PageQuery page) {
        Query query = Query.query(toCriteria(criteria))
                .sort(PageQuery.NEWEST_FIRST)
                .limit(page.size())
                .offset(page.offset());
        return template.select(query, Product.class);
    }

    public Mono<Long> count(ProductSearchCriteria criteria) {
        return template.count(Query.query(toCriteria(criteria)), Product.class);
    }

    private Criteria toCriteria(ProductSearchCriteria filter) {
        Criteria criteria = Criteria.empty();
        if (filter.name() != null) {
            criteria = criteria.and("name").like("%" + escapeLike(filter.name()) + "%").ignoreCase(true);
        }
        if (filter.sku() != null) {
            criteria = criteria.and("sku").is(filter.sku());
        }
        if (filter.active() != null) {
            criteria = criteria.and("active").is(filter.active());
        }
        if (filter.minPrice() != null) {
            criteria = criteria.and("price").greaterThanOrEquals(filter.minPrice());
        }
        if (filter.maxPrice() != null) {
            criteria = criteria.and("price").lessThanOrEquals(filter.maxPrice());
        }
        return criteria;
    }

    /** Evita que % y _ del usuario actúen como comodines de LIKE. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
