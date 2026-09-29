package com.nunegal.similar;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SimilarProductsServiceTest {
    private final ProductApiClient client = mock(ProductApiClient.class);
    private final SimilarProductsService service = new SimilarProductsService(client,
            new ProductsProperties(URI.create("http://localhost"), 2, 10, 10,
                    Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(2),
                    Duration.ofSeconds(6), Duration.ofSeconds(8)));

    private Product product(String id) {
        return new Product(id, "Product " + id, BigDecimal.ONE, true);
    }

    @Test
    void preservesOrderAndLimitsConcurrencyDespiteDifferentCompletionTimes() {
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        when(client.similarIds("root")).thenReturn(Mono.just(List.of("a", "b", "c")));
        for (String id : List.of("a", "b", "c")) {
            when(client.product(id)).thenAnswer(invocation -> Mono.defer(() -> {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                return Mono.delay(Duration.ofSeconds(id.equals("a") ? 3 : 1))
                        .doOnNext(ignored -> active.decrementAndGet()).thenReturn(product(id));
            }));
        }
        StepVerifier.withVirtualTime(() -> service.similarProducts("root"))
                .thenAwait(Duration.ofSeconds(5))
                .expectNext(List.of(product("a"), product("b"), product("c")))
                .verifyComplete();
        assertThat(maximum.get()).isEqualTo(2);
    }

    @Test
    void emptyIdsDoNotTriggerDetailRequests() {
        when(client.similarIds("root")).thenReturn(Mono.just(List.of()));
        StepVerifier.create(service.similarProducts("root")).expectNext(List.of()).verifyComplete();
        verify(client).similarIds("root");
        verifyNoMoreInteractions(client);
    }

    @Test
    void removesDuplicateIdsKeepingFirstOccurrence() {
        when(client.similarIds("root")).thenReturn(Mono.just(List.of("b", "a", "b")));
        when(client.product("a")).thenReturn(Mono.just(product("a")));
        when(client.product("b")).thenReturn(Mono.just(product("b")));
        StepVerifier.create(service.similarProducts("root"))
                .expectNext(List.of(product("b"), product("a"))).verifyComplete();
        verify(client, times(1)).product("b");
    }

    @Test
    void partialFailuresPreserveSuccessfulProducts() {
        when(client.similarIds("root")).thenReturn(Mono.just(List.of("a", "b", "c")));
        when(client.product("a")).thenReturn(Mono.just(product("a")));
        when(client.product("b")).thenReturn(Mono.error(new UpstreamException(UpstreamException.Kind.FAILURE)));
        when(client.product("c")).thenReturn(Mono.just(product("c")));
        StepVerifier.create(service.similarProducts("root"))
                .expectNext(List.of(product("a"), product("c"))).verifyComplete();
    }

    @Test
    void overallDeadlineCancelsOutstandingWork() {
        var cancelled = new AtomicInteger();
        when(client.similarIds("root")).thenReturn(Mono.just(List.of("a")));
        when(client.product("a")).thenReturn(Mono.<Product>never().doOnCancel(cancelled::incrementAndGet));
        StepVerifier.withVirtualTime(() -> service.similarProducts("root"))
                .thenAwait(Duration.ofSeconds(8))
                .expectErrorSatisfies(error -> assertThat(((UpstreamException) error).kind())
                        .isEqualTo(UpstreamException.Kind.TIMEOUT)).verify();
        assertThat(cancelled.get()).isEqualTo(1);
    }

    @Test
    void idsFailureIsNotConvertedIntoEmptySuccess() {
        when(client.similarIds("root")).thenReturn(Mono.error(new UpstreamException(UpstreamException.Kind.NOT_FOUND)));
        StepVerifier.create(service.similarProducts("root"))
                .expectErrorSatisfies(error -> assertThat(((UpstreamException) error).kind())
                        .isEqualTo(UpstreamException.Kind.NOT_FOUND)).verify();
        verify(client).similarIds("root");
        verifyNoMoreInteractions(client);
    }
}
