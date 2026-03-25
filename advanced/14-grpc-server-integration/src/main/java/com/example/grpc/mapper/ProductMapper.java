package com.example.grpc.mapper;

import com.example.grpc.domain.Product;
import com.example.grpc.domain.ProductStatus;
import org.springframework.stereotype.Component;

/**
 * Mapper component that converts between JPA domain objects and protobuf messages.
 *
 * <p>Why a separate mapper?
 * <ul>
 *   <li>The JPA {@link Product} entity and the protobuf {@code Product} message have the
 *       same conceptual structure but different representations and concerns.</li>
 *   <li>Keeping them separate allows the persistence layer and the gRPC wire format to
 *       evolve independently without coupling the two layers.</li>
 *   <li>The mapper is the single, explicit place where the translation happens,
 *       making it easy to audit and test.</li>
 * </ul>
 *
 * <p>This class is registered as a Spring {@link Component} so it can be injected
 * into the gRPC service implementation.
 */
@Component
public class ProductMapper {

    /**
     * Convert a JPA {@link Product} domain object to a protobuf {@code Product} message.
     *
     * <p>Protobuf messages are immutable builders — the {@code .build()} call produces
     * a final, thread-safe message object ready for transmission over the wire.
     *
     * @param product the JPA entity to convert (must not be null)
     * @return the protobuf message equivalent
     */
    public com.example.grpc.proto.Product toProto(Product product) {
        return com.example.grpc.proto.Product.newBuilder()
                .setId(product.getId())
                .setName(product.getName())
                .setDescription(product.getDescription() != null ? product.getDescription() : "")
                .setCategory(product.getCategory() != null ? product.getCategory() : "")
                .setPrice(product.getPrice())
                .setStockQuantity(product.getStockQuantity())
                // Map the Java enum to the protobuf enum using the helper method below.
                .setStatus(toProtoStatus(product.getStatus()))
                .build();
    }

    /**
     * Map a JPA {@link ProductStatus} enum value to the corresponding protobuf
     * {@code ProductStatus} enum value.
     *
     * <p>The mapping is explicit to avoid relying on ordinal positions, which are
     * fragile when enum values are reordered in the future.
     *
     * @param status the JPA enum value
     * @return the corresponding protobuf enum value
     */
    public com.example.grpc.proto.ProductStatus toProtoStatus(ProductStatus status) {
        if (status == null) {
            return com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_UNKNOWN;
        }
        return switch (status) {
            case ACTIVE        -> com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_ACTIVE;
            case OUT_OF_STOCK  -> com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_OUT_OF_STOCK;
            case DISCONTINUED  -> com.example.grpc.proto.ProductStatus.PRODUCT_STATUS_DISCONTINUED;
        };
    }

    /**
     * Map a protobuf {@code ProductStatus} enum value to the corresponding JPA
     * {@link ProductStatus} enum value.
     *
     * @param protoStatus the protobuf enum value
     * @return the corresponding JPA enum value, or {@code ACTIVE} as a safe default
     */
    public ProductStatus fromProtoStatus(com.example.grpc.proto.ProductStatus protoStatus) {
        return switch (protoStatus) {
            case PRODUCT_STATUS_ACTIVE        -> ProductStatus.ACTIVE;
            case PRODUCT_STATUS_OUT_OF_STOCK  -> ProductStatus.OUT_OF_STOCK;
            case PRODUCT_STATUS_DISCONTINUED  -> ProductStatus.DISCONTINUED;
            default                           -> ProductStatus.ACTIVE;
        };
    }
}
