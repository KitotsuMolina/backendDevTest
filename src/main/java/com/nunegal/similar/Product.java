package com.nunegal.similar;

import java.math.BigDecimal;

public record Product(String id, String name, BigDecimal price, Boolean availability) {
    boolean isValidFor(String requestedId) {
        return requestedId.equals(id) && name != null && !name.isBlank()
                && price != null && availability != null;
    }
}
