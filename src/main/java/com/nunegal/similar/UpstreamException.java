package com.nunegal.similar;

class UpstreamException extends RuntimeException {
    enum Kind { NOT_FOUND, TIMEOUT, FAILURE }

    private final Kind kind;
    private final String reason;

    UpstreamException(Kind kind) {
        this(kind, kind.name());
    }

    UpstreamException(Kind kind, String reason) {
        super(kind.name());
        this.kind = kind;
        this.reason = reason;
    }

    Kind kind() {
        return kind;
    }

    String reason() {
        return reason;
    }
}
