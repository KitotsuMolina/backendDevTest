package com.nunegal.similar;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

@Component
class ProductApiClient {
    private final WebClient webClient;
    private final ProductsProperties properties;

    ProductApiClient(WebClient productsWebClient, ProductsProperties properties) {
        this.webClient = productsWebClient;
        this.properties = properties;
    }

    Mono<List<String>> similarIds(String id) {
        return get("/product/{id}/similarids", id, JsonNode.class, properties.idsTimeout())
                .map(body -> {
                    if (!body.isArray()) {
                        throw new UpstreamException(UpstreamException.Kind.FAILURE, "INVALID_IDS");
                    }
                    var ids = new ArrayList<String>();
                    for (JsonNode value : body) {
                        // The supplied mocks use integers, while the contract declares strings.
                        if ((!value.isTextual() && !value.isIntegralNumber()) || value.asText().isBlank()) {
                            throw new UpstreamException(UpstreamException.Kind.FAILURE, "INVALID_ID");
                        }
                        ids.add(value.asText());
                    }
                    return List.copyOf(ids);
                });
    }

    Mono<Product> product(String id) {
        return get("/product/{id}", id, Product.class, properties.detailTimeout())
                .flatMap(product -> product.isValidFor(id) ? Mono.just(product)
                        : Mono.error(new UpstreamException(UpstreamException.Kind.FAILURE, "INVALID_DETAIL")));
    }

    private <T> Mono<T> get(String path, String id, Class<T> type, Duration timeout) {
        return webClient.get().uri(path, id).exchangeToMono(response -> {
                    if (response.statusCode().value() == 200) return response.bodyToMono(type);
                    var kind = response.statusCode().value() == 404
                            ? UpstreamException.Kind.NOT_FOUND : UpstreamException.Kind.FAILURE;
                    return response.releaseBody().then(Mono.error(
                            new UpstreamException(kind, "HTTP_" + response.statusCode().value())));
                })
                .switchIfEmpty(Mono.error(new UpstreamException(UpstreamException.Kind.FAILURE, "EMPTY_BODY")))
                // Includes pool acquisition, connection, headers and complete body decoding.
                .timeout(timeout)
                .onErrorMap(error -> !(error instanceof UpstreamException), error ->
                        new UpstreamException(error instanceof TimeoutException
                                ? UpstreamException.Kind.TIMEOUT : UpstreamException.Kind.FAILURE,
                                failureReason(error)));
    }
    private String failureReason(Throwable error) {
        Throwable cause = error instanceof WebClientRequestException requestError
                ? requestError.getMostSpecificCause() : error;
        return cause.getClass().getSimpleName();
    }
}
