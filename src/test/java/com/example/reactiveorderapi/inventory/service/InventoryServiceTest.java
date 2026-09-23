package com.example.reactiveorderapi.inventory.service;

import com.example.reactiveorderapi.exception.InsufficientStockException;
import com.example.reactiveorderapi.exception.ProductNotFoundException;
import com.example.reactiveorderapi.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.ZoneOffset;

import static com.example.reactiveorderapi.support.Fixtures.NOW;
import static com.example.reactiveorderapi.support.Fixtures.inventory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void reserveSucceedsWhenTheAtomicUpdateAffectsARow() {
        when(inventoryRepository.reserve(1L, 3)).thenReturn(Mono.just(1L));

        StepVerifier.create(inventoryService.reserve(1L, 3, 99L)).verifyComplete();
    }

    @Test
    void reserveFailsWhenNoRowSatisfiesTheStockCondition() {
        when(inventoryRepository.reserve(1L, 3)).thenReturn(Mono.just(0L));

        StepVerifier.create(inventoryService.reserve(1L, 3, 99L))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(InsufficientStockException.class);
                    assertThat(((InsufficientStockException) error).getRequestedQuantity()).isEqualTo(3);
                })
                .verify();
    }

    @Test
    void removeStockReturnsUpdatedInventory() {
        when(inventoryRepository.decreaseAvailable(1L, 2)).thenReturn(Mono.just(1L));
        when(inventoryRepository.findByProductId(1L)).thenReturn(Mono.just(inventory(1L, 3, 0)));

        StepVerifier.create(inventoryService.removeStock(1L, 2))
                .assertNext(inventory -> assertThat(inventory.availableQuantity()).isEqualTo(3))
                .verifyComplete();
    }

    @Test
    void removeStockDistinguishesInsufficientStock() {
        when(inventoryRepository.decreaseAvailable(1L, 5)).thenReturn(Mono.just(0L));
        when(inventoryRepository.findByProductId(1L)).thenReturn(Mono.just(inventory(1L, 2, 0)));

        StepVerifier.create(inventoryService.removeStock(1L, 5))
                .expectError(InsufficientStockException.class)
                .verify();
    }

    @Test
    void removeStockDistinguishesMissingProduct() {
        when(inventoryRepository.decreaseAvailable(1L, 5)).thenReturn(Mono.just(0L));
        when(inventoryRepository.findByProductId(1L)).thenReturn(Mono.empty());

        StepVerifier.create(inventoryService.removeStock(1L, 5))
                .expectError(ProductNotFoundException.class)
                .verify();
    }

    @Test
    void addStockToMissingProductFails() {
        when(inventoryRepository.increaseAvailable(1L, 5)).thenReturn(Mono.just(0L));

        StepVerifier.create(inventoryService.addStock(1L, 5))
                .expectError(ProductNotFoundException.class)
                .verify();
    }

    @Test
    void releaseAbortsWhenReservationsAreInconsistent() {
        when(inventoryRepository.release(1L, 5)).thenReturn(Mono.just(0L));

        StepVerifier.create(inventoryService.release(1L, 5, 99L))
                .expectError(IllegalStateException.class)
                .verify();
    }
}
