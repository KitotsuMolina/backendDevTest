package com.nunegal.similar;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
class SimilarProductsController {
    private final SimilarProductsService service;

    SimilarProductsController(SimilarProductsService service) {
        this.service = service;
    }

    @GetMapping(value = "/product/{productId}/similar", produces = "application/json")
    Mono<List<Product>> similarProducts(@PathVariable String productId) {
        return service.similarProducts(productId);
    }
}
