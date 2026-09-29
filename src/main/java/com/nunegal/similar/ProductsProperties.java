package com.nunegal.similar;

import java.net.URI;
import java.time.Duration;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("products")
public record ProductsProperties(
        @NotNull URI baseUrl,
        @Min(1) int concurrency,
        @Min(1) int maxConnections,
        @Min(1) int pendingConnections,
        @NotNull @DurationMin(millis = 1) Duration connectTimeout,
        @NotNull @DurationMin(millis = 1) Duration acquireTimeout,
        @NotNull @DurationMin(millis = 1) Duration idsTimeout,
        @NotNull @DurationMin(millis = 1) Duration detailTimeout,
        @NotNull @DurationMin(millis = 1) Duration requestTimeout) {
}
