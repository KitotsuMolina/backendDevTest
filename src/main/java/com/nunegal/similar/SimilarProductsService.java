package com.nunegal.similar;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
class SimilarProductsService {
    private static final Logger log = LoggerFactory.getLogger(SimilarProductsService.class);
    private final ProductApiClient client;
    private final ProductsProperties properties;

    SimilarProductsService(ProductApiClient client, ProductsProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    Mono<List<Product>> similarProducts(String id) {
        return client.similarIds(id)
                .flatMapMany(ids -> Flux.fromIterable(ids).distinct())
                .flatMapSequential(similarId -> client.product(similarId)
                        .onErrorResume(UpstreamException.class, error -> {
                            log.warn("External API detail omitted: kind={} reason={}", error.kind(), error.reason());
                            return Mono.empty();
                        }), properties.concurrency(), 1)
                .collectList()
                // Caps the whole operation even if the upstream supplies many batches of IDs.
                .timeout(properties.requestTimeout(),
                        Mono.error(new UpstreamException(UpstreamException.Kind.TIMEOUT)))
                .doOnError(UpstreamException.class,
                        error -> log.warn("Similar products request failed: kind={} reason={}", error.kind(), error.reason()));
    }
}
