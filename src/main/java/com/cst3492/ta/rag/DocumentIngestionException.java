package com.cst3492.ta.rag;

import java.io.IOException;

/** An import failure, distinct from a valid search with no relevant results. */
public final class DocumentIngestionException extends IOException {
    public enum Reason {
        UNSUPPORTED_FORMAT, UNREADABLE_FILE, ENCRYPTED_DOCUMENT, MALFORMED_DOCUMENT
    }

    private final Reason reason;

    DocumentIngestionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    DocumentIngestionException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
