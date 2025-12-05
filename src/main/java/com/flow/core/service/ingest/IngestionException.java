package com.flow.core.service.ingest;

/**
 * Exception thrown when ingestion processing fails.
 */
public class IngestionException extends RuntimeException {

    private final String entityId;
    private final String errorCode;

    public IngestionException(String message) {
        super(message);
        this.entityId = null;
        this.errorCode = "INGESTION_ERROR";
    }

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
        this.entityId = null;
        this.errorCode = "INGESTION_ERROR";
    }

    public IngestionException(String message, String entityId, String errorCode) {
        super(message);
        this.entityId = entityId;
        this.errorCode = errorCode;
    }

    public IngestionException(String message, String entityId, String errorCode, Throwable cause) {
        super(message, cause);
        this.entityId = entityId;
        this.errorCode = errorCode;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

