package com.nunegal.similar;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.assertThat;

class ProductApiClientTest {
    private ProductApiClient client(int port) {
        var properties = new ProductsProperties(URI.create("http://127.0.0.1:" + port),
                2, 10, 10, Duration.ofMillis(500), Duration.ofMillis(500),
                Duration.ofMillis(500), Duration.ofMillis(500), Duration.ofSeconds(2));
        return new ProductApiClient(WebClient.create(properties.baseUrl().toString()), properties);
    }

    @Test
    void disconnectedDependencyIsAnUpstreamFailure() {
        var server = HttpServer.create().host("127.0.0.1").port(0)
                .handle((request, response) -> {
                    request.withConnection(connection -> connection.dispose());
                    return Mono.empty();
                }).bindNow();
        try {
            StepVerifier.create(client(server.port()).similarIds("root"))
                    .expectErrorSatisfies(error -> assertThat(((UpstreamException) error).kind())
                            .isEqualTo(UpstreamException.Kind.FAILURE))
                    .verify(Duration.ofSeconds(3));
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void deadlineIncludesBodyEvenAfterHeadersHaveArrived() {
        var headersSent = new AtomicBoolean();
        var server = HttpServer.create().host("127.0.0.1").port(0)
                .handle((request, response) -> response.status(200)
                        .header("Content-Type", "application/json")
                        .sendHeaders().then().doOnSuccess(ignored -> headersSent.set(true))
                        .then(Mono.never())).bindNow();
        try {
            StepVerifier.create(client(server.port()).similarIds("root"))
                    .expectErrorSatisfies(error -> assertThat(((UpstreamException) error).kind())
                            .isEqualTo(UpstreamException.Kind.TIMEOUT))
                    .verify(Duration.ofSeconds(3));
            assertThat(headersSent).isTrue();
        } finally {
            server.disposeNow();
        }
    }
}
