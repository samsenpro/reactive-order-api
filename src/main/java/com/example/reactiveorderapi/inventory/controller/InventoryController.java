package com.example.reactiveorderapi.inventory.controller;

import com.example.reactiveorderapi.inventory.dto.InventoryResponse;
import com.example.reactiveorderapi.inventory.dto.StockAdjustmentRequest;
import com.example.reactiveorderapi.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Consultar el stock de un producto")
    public Mono<InventoryResponse> find(@PathVariable Long productId) {
        return inventoryService.findByProductId(productId).map(InventoryResponse::from);
    }

    @PatchMapping("/{productId}/add")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Añadir unidades disponibles (ADMIN)")
    public Mono<InventoryResponse> add(@PathVariable Long productId,
                                       @Valid @RequestBody StockAdjustmentRequest request) {
        return inventoryService.addStock(productId, request.quantity()).map(InventoryResponse::from);
    }

    @PatchMapping("/{productId}/remove")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Retirar unidades disponibles; nunca deja stock negativo (ADMIN)")
    public Mono<InventoryResponse> remove(@PathVariable Long productId,
                                          @Valid @RequestBody StockAdjustmentRequest request) {
        return inventoryService.removeStock(productId, request.quantity()).map(InventoryResponse::from);
    }
}
