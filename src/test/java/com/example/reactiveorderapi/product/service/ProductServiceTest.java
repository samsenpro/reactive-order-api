package com.example.reactiveorderapi.product.service;

import com.example.reactiveorderapi.common.PageQuery;
import com.example.reactiveorderapi.exception.BadRequestException;
import com.example.reactiveorderapi.exception.DuplicateResourceException;
import com.example.reactiveorderapi.exception.ProductInUseException;
import com.example.reactiveorderapi.exception.ProductNotFoundException;
import com.example.reactiveorderapi.inventory.service.InventoryService;
import com.example.reactiveorderapi.product.dto.CreateProductRequest;
import com.example.reactiveorderapi.product.dto.ProductResponse;
import com.example.reactiveorderapi.product.dto.ProductSearchCriteria;
import com.example.reactiveorderapi.product.entity.Product;
import com.example.reactiveorderapi.product.repository.ProductRepository;
import com.example.reactiveorderapi.product.repository.ProductSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;

import static com.example.reactiveorderapi.support.Fixtures.NOW;
import static com.example.reactiveorderapi.support.Fixtures.inventory;
import static com.example.reactiveorderapi.support.Fixtures.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductSearchRepository productSearchRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private TransactionalOperator transactionalOperator;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, productSearchRepository, inventoryService,
                transactionalOperator, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    class FindById {

        @Test
        void monoSuccessEmitsProduct() {
            when(productRepository.findById(1L)).thenReturn(Mono.just(product(1L, "9.99", true)));

            StepVerifier.create(productService.findById(1L))
                    .assertNext(response -> {
                        assertThat(response.id()).isEqualTo(1L);
                        assertThat(response.price()).isEqualByComparingTo("9.99");
                    })
                    .verifyComplete();
        }

        @Test
        void monoEmptyBecomesProductNotFound() {
            when(productRepository.findById(1L)).thenReturn(Mono.empty());

            StepVerifier.create(productService.findById(1L))
                    .expectErrorSatisfies(error -> assertThat(error)
                            .isInstanceOf(ProductNotFoundException.class)
                            .hasMessage("Product not found: 1"))
                    .verify();
        }

        @Test
        void monoErrorFromRepositoryIsPropagated() {
            when(productRepository.findById(1L))
                    .thenReturn(Mono.error(new DataAccessResourceFailureException("db down")));

            StepVerifier.create(productService.findById(1L))
                    .expectError(DataAccessResourceFailureException.class)
                    .verify();
        }
    }

    @Nested
    class Stream {

        @Test
        void fluxWithSeveralElementsIsMappedInOrder() {
            when(productRepository.findAllByActiveTrueOrderByIdAsc())
                    .thenReturn(Flux.just(product(1L, "1.00", true), product(2L, "2.00", true), product(3L, "3.00", true)));

            StepVerifier.create(productService.streamActiveProducts().map(ProductResponse::id))
                    .expectNext(1L, 2L, 3L)
                    .verifyComplete();
        }

        @Test
        void emptyFluxCompletesWithoutElements() {
            when(productRepository.findAllByActiveTrueOrderByIdAsc()).thenReturn(Flux.empty());

            StepVerifier.create(productService.streamActiveProducts()).verifyComplete();
        }

        @Test
        void fluxErrorArrivesAfterAlreadyEmittedElements() {
            when(productRepository.findAllByActiveTrueOrderByIdAsc())
                    .thenReturn(Flux.concat(Flux.just(product(1L, "1.00", true)),
                            Flux.error(new DataAccessResourceFailureException("connection lost"))));

            StepVerifier.create(productService.streamActiveProducts())
                    .expectNextCount(1)
                    .expectError(DataAccessResourceFailureException.class)
                    .verify();
        }

        /**
         * Backpressure: el consumidor pide 2 elementos y la fuente solo recibe demanda acotada
         * ({@code limitRate}); nunca se le pide "todo" (Long.MAX_VALUE).
         */
        @Test
        void consumerDemandIsPropagatedInBoundedBatches() {
            TestPublisher<Product> source = TestPublisher.create();
            when(productRepository.findAllByActiveTrueOrderByIdAsc()).thenReturn(source.flux());

            StepVerifier.create(productService.streamActiveProducts(), 2)
                    .then(() -> source.assertMinRequested(1).assertMaxRequested(ProductService.STREAM_BATCH_SIZE))
                    .then(() -> source.next(product(1L, "1.00", true), product(2L, "2.00", true)))
                    .expectNextCount(2)
                    .thenCancel()
                    .verify();
            source.assertCancelled();
        }
    }

    @Nested
    class Search {

        @Test
        void combinesPageAndTotalFromParallelQueries() {
            ProductSearchCriteria criteria = new ProductSearchCriteria("key", null, true, null, null);
            PageQuery page = new PageQuery(0, 2);
            when(productSearchRepository.search(criteria, page))
                    .thenReturn(Flux.just(product(1L, "1.00", true), product(2L, "2.00", true)));
            when(productSearchRepository.count(criteria)).thenReturn(Mono.just(5L));

            StepVerifier.create(productService.search(criteria, page))
                    .assertNext(result -> {
                        assertThat(result.content()).extracting(ProductResponse::id).containsExactly(1L, 2L);
                        assertThat(result.totalElements()).isEqualTo(5);
                        assertThat(result.totalPages()).isEqualTo(3);
                    })
                    .verifyComplete();
        }

        @Test
        void invalidPriceRangeFailsWithoutQuerying() {
            ProductSearchCriteria criteria =
                    new ProductSearchCriteria(null, null, null, new BigDecimal("50"), new BigDecimal("10"));

            StepVerifier.create(productService.search(criteria, new PageQuery(0, 20)))
                    .expectError(BadRequestException.class)
                    .verify();
            verify(productSearchRepository, never()).search(any(), any());
        }
    }

    @Nested
    class Create {

        @BeforeEach
        void transactionPassesThrough() {
            when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        void createsProductAndInventoryWithNormalizedSku() {
            when(productRepository.existsBySku("KB-01")).thenReturn(Mono.just(false));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product toSave = invocation.getArgument(0);
                return Mono.just(new Product(7L, toSave.name(), toSave.description(), toSave.sku(), toSave.price(),
                        toSave.active(), toSave.createdAt(), toSave.updatedAt()));
            });
            when(inventoryService.createFor(7L, 3)).thenReturn(Mono.just(inventory(7L, 3, 0)));

            StepVerifier.create(productService.create(
                            new CreateProductRequest(" Keyboard ", null, "kb-01", new BigDecimal("10.50"), 3)))
                    .assertNext(created -> {
                        assertThat(created.id()).isEqualTo(7L);
                        assertThat(created.sku()).isEqualTo("KB-01");
                        assertThat(created.name()).isEqualTo("Keyboard");
                    })
                    .verifyComplete();
        }

        @Test
        void duplicatedSkuFailsBeforeInserting() {
            when(productRepository.existsBySku("KB-01")).thenReturn(Mono.just(true));

            StepVerifier.create(productService.create(
                            new CreateProductRequest("Keyboard", null, "KB-01", BigDecimal.TEN, null)))
                    .expectError(DuplicateResourceException.class)
                    .verify();
            verify(productRepository, never()).save(any());
            verify(inventoryService, never()).createFor(anyLong(), anyInt());
        }
    }

    @Test
    void deletingProductWithOrdersIsReportedAsInUse() {
        Product product = product(1L, "1.00", true);
        when(productRepository.findById(1L)).thenReturn(Mono.just(product));
        when(productRepository.delete(product)).thenReturn(Mono.error(new DataIntegrityViolationException("fk")));

        StepVerifier.create(productService.delete(1L))
                .expectError(ProductInUseException.class)
                .verify();
    }

    @Test
    void deactivateMarksProductInactive() {
        when(productRepository.findById(1L)).thenReturn(Mono.just(product(1L, "1.00", true)));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(productService.deactivate(1L))
                .assertNext(response -> assertThat(response.active()).isFalse())
                .verifyComplete();
    }
}
