package com.example.reactiveorderapi.product.controller;

import com.example.reactiveorderapi.common.PageQuery;
import com.example.reactiveorderapi.common.PageResponse;
import com.example.reactiveorderapi.product.dto.CreateProductRequest;
import com.example.reactiveorderapi.product.dto.ProductRequest;
import com.example.reactiveorderapi.product.dto.ProductResponse;
import com.example.reactiveorderapi.product.dto.ProductSearchCriteria;
import com.example.reactiveorderapi.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear un producto con su inventario inicial (ADMIN)")
    public Mono<ResponseEntity<ProductResponse>> create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request)
                .map(created -> ResponseEntity
                        .created(URI.create("/api/v1/products/" + created.id()))
                        .body(created));
    }

    @GetMapping
    @Operation(summary = "Listar productos con filtros y paginación")
    public Mono<PageResponse<ProductResponse>> search(
            @Parameter(description = "Contiene el texto (sin distinguir mayúsculas)", example = "keyboard")
            @RequestParam(required = false) String name,
            @Parameter(example = "KB-001") @RequestParam(required = false) String sku,
            @Parameter(example = "true") @RequestParam(required = false) Boolean active,
            @Parameter(example = "10.00") @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(example = "100.00") @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = PageQuery.DEFAULT_PAGE) @Min(0) int page,
            @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE) @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
        return productService.search(
                new ProductSearchCriteria(name, sku, active, minPrice, maxPrice), new PageQuery(page, size));
    }

    @GetMapping(value = "/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Stream de productos activos en NDJSON (un JSON por línea, con backpressure)")
    public Flux<ProductResponse> stream() {
        return productService.streamActiveProducts();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un producto")
    public Mono<ProductResponse> findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar un producto (ADMIN)")
    public Mono<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activar un producto (ADMIN)")
    public Mono<ProductResponse> activate(@PathVariable Long id) {
        return productService.activate(id);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar un producto; deja de poder venderse (ADMIN)")
    public Mono<ProductResponse> deactivate(@PathVariable Long id) {
        return productService.deactivate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar un producto sin pedidos (ADMIN)")
    public Mono<Void> delete(@PathVariable Long id) {
        return productService.delete(id);
    }
}
