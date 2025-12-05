package com.flow.core.service.ingest;

/**
 * Strategy interface for handling ingestion work items.
 *
 * Implements the Strategy pattern to allow different handling
 * logic for different types of work items.
 *
 * @param <T> the type of work item this handler processes
 */
public interface IngestionHandler<T extends IngestionWorkItem> {

    /**
     * Handles the given work item.
     *
     * @param workItem the work item to process
     * @throws IngestionException if processing fails
     */
    void handle(T workItem);

    /**
     * Gets the type of work item this handler supports.
     *
     * @return the work item class
     */
    Class<T> getSupportedType();
}

