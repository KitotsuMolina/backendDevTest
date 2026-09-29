package com.nunegal.similar;

import java.time.Duration;
import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
class HttpClientConfiguration {
    @Bean(destroyMethod = "dispose")
    ConnectionProvider productsConnections(ProductsProperties properties) {
        return ConnectionProvider.builder("products")
                .maxConnections(properties.maxConnections())
                .pendingAcquireMaxCount(properties.pendingConnections())
                .pendingAcquireTimeout(properties.acquireTimeout())
                // Retire idle sockets before the mock server closes its keep-alive connections.
                .maxIdleTime(Duration.ofSeconds(2))
                .evictInBackground(Duration.ofSeconds(1))
                .lifo()
                .build();
    }

    @Bean
    WebClient productsWebClient(WebClient.Builder builder, ProductsProperties properties,
                               ConnectionProvider productsConnections) {
        HttpClient client = HttpClient.create(productsConnections)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(properties.connectTimeout().toMillis()));
        return builder.baseUrl(properties.baseUrl().toString())
                .clientConnector(new ReactorClientHttpConnector(client))
                .codecs(config -> config.defaultCodecs().maxInMemorySize(256 * 1024))
                .build();
    }
}
