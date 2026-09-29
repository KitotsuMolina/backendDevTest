package com.nunegal.similar;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimilarProductsIntegrationTest {
    record Reply(int status, String body, long delayMillis) { }
    private static final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<String> requests = new ConcurrentLinkedQueue<>();
    private static final DisposableServer upstream = HttpServer.create().host("127.0.0.1").port(0)
            .handle((request, response) -> {
                requests.add(request.uri());
                Reply reply = replies.getOrDefault(request.uri(), new Reply(404, "", 0));
                return Mono.delay(Duration.ofMillis(reply.delayMillis()))
                        .then(Mono.defer(() -> response.status(reply.status())
                                .header("Content-Type", "application/json")
                                .sendString(Mono.just(reply.body())).then()));
            }).bindNow();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("products.base-url", () -> "http://127.0.0.1:" + upstream.port());
        registry.add("products.ids-timeout", () -> "500ms");
        registry.add("products.detail-timeout", () -> "500ms");
        registry.add("products.request-timeout", () -> "2s");
    }

    @LocalServerPort int port;
    private WebTestClient http;

    @BeforeEach
    void setup() {
        replies.clear();
        requests.clear();
        http = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterAll
    static void stopServer() {
        upstream.disposeNow();
    }

    private void stub(String path, int status, String body, long delay) {
        replies.put(path, new Reply(status, body, delay));
    }

    private String product(String id) {
        return "{\"id\":\"" + id + "\",\"name\":\"Example\",\"price\":9.99,\"availability\":false}";
    }

    private WebTestClient.ResponseSpec get(String id) {
        return http.get().uri("/product/{id}/similar", id).exchange();
    }

    @Test
    void realHttpCallsAcceptNumericIdsAndPreserveSimilarityOrder() {
        stub("/product/root/similarids", 200, "[2,3,4,2]", 0);
        stub("/product/2", 200, product("2"), 150);
        stub("/product/3", 200, product("3"), 50);
        stub("/product/4", 200, product("4"), 0);
        get("root").expectStatus().isOk().expectHeader().contentType("application/json")
                .expectBody().jsonPath("$.length()").isEqualTo(3)
                .jsonPath("$[0].id").isEqualTo("2")
                .jsonPath("$[1].id").isEqualTo("3")
                .jsonPath("$[2].id").isEqualTo("4")
                .jsonPath("$[0].price").isEqualTo(9.99)
                .jsonPath("$[0].availability").isEqualTo(false);
        assertThat(requests).containsExactlyInAnyOrder("/product/root/similarids", "/product/2", "/product/3", "/product/4");
    }

    @Test
    void emptyListIsSuccessfulAndDoesNotFetchOriginalProduct() {
        stub("/product/root/similarids", 200, "[]", 0);
        get("root").expectStatus().isOk().expectBody().json("[]");
        assertThat(requests).containsExactly("/product/root/similarids");
    }

    @Test
    void nonexistentSourceReturns404() {
        get("missing").expectStatus().isNotFound().expectBody().isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 429, 500, 503})
    void idsHttpFailureReturns502(int status) {
        stub("/product/root/similarids", status, "{}", 0);
        get("root").expectStatus().isEqualTo(502).expectBody().isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[null]", "[true]", "[\"\"]", "[1.2]", "invalid", ""})
    void invalidIdsAreNotTreatedAsEmptySuccess(String body) {
        stub("/product/root/similarids", 200, body, 0);
        get("root").expectStatus().isEqualTo(502);
    }

    @Test
    void slowIdsReturn504WithinDeadline() {
        stub("/product/root/similarids", 200, "[]", 3000);
        long start = System.nanoTime();
        get("root").expectStatus().isEqualTo(504);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 500})
    void failedDetailIsOmittedRatherThanChangingSourceStatus(int status) {
        stub("/product/root/similarids", 200, "[\"a\",\"b\",\"c\"]", 0);
        stub("/product/a", 200, product("a"), 0);
        stub("/product/b", status, "{}", 0);
        stub("/product/c", 200, product("c"), 0);
        get("root").expectStatus().isOk().expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo("a")
                .jsonPath("$[1].id").isEqualTo("c");
    }

    @Test
    void slowDetailIsOmittedAndFasterLaterDetailSurvives() {
        stub("/product/root/similarids", 200, "[\"a\",\"b\"]", 0);
        stub("/product/a", 200, product("a"), 3000);
        stub("/product/b", 200, product("b"), 0);
        long start = System.nanoTime();
        get("root").expectStatus().isOk().expectBody()
                .jsonPath("$.length()").isEqualTo(1).jsonPath("$[0].id").isEqualTo("b");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "invalid", "", "{\"id\":\"other\",\"name\":\"X\",\"price\":1,\"availability\":true}"})
    void invalidDetailIsOmitted(String body) {
        stub("/product/root/similarids", 200, "[\"a\"]", 0);
        stub("/product/a", 200, body, 0);
        get("root").expectStatus().isOk().expectBody().json("[]");
    }

    @Test
    void healthIsAvailableWithoutCallingDependency() {
        http.get().uri("/actuator/health").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
        assertThat(requests).isEmpty();
    }
}
