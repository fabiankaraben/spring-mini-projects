package com.example.grpc.domain;

/**
 * Lifecycle status of a product in the catalog.
 *
 * <p>This Java enum mirrors the {@code ProductStatus} enum defined in
 * {@code product_catalog.proto}. The two are kept separate so the JPA entity
 * and the gRPC wire format can evolve independently. The mapping between them
 * is handled in {@link com.example.grpc.mapper.ProductMapper}.
 *
 * <p>Status transitions:
 * <ul>
 *   <li>{@code ACTIVE} → {@code OUT_OF_STOCK}: when stock reaches 0 after an UpdateStock call.</li>
 *   <li>{@code OUT_OF_STOCK} → {@code ACTIVE}: when stock is restored to > 0 via UpdateStock.</li>
 *   <li>{@code ACTIVE} / {@code OUT_OF_STOCK} → {@code DISCONTINUED}: via DeleteProduct (soft-delete).</li>
 * </ul>
 */
public enum ProductStatus {

    /**
     * The product is available for purchase and has stock > 0.
     */
    ACTIVE,

    /**
     * The product exists in the catalog but has zero remaining stock.
     * It becomes {@code ACTIVE} again when stock is replenished.
     */
    OUT_OF_STOCK,

    /**
     * The product has been soft-deleted and is no longer available.
     * It will not appear in normal listing queries.
     */
    DISCONTINUED
}
