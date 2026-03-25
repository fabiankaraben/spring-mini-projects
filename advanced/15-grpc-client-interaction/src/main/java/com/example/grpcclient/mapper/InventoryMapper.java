package com.example.grpcclient.mapper;

import com.example.grpcclient.domain.InventoryItem;
import org.springframework.stereotype.Component;

/**
 * Mapper component that converts between JPA {@link InventoryItem} domain objects
 * and protobuf messages for the Inventory domain.
 *
 * <p>This class handles the translation between the JPA persistence layer and the
 * gRPC wire format, keeping both layers independent and easy to evolve separately.
 *
 * <p>The protobuf {@code InventoryItem} message includes {@code available_quantity}
 * as a field, which is computed from the JPA entity's total and reserved quantities.
 */
@Component
public class InventoryMapper {

    /**
     * Convert a JPA {@link InventoryItem} domain object to a protobuf
     * {@code InventoryItem} message.
     *
     * <p>The {@code available_quantity} field in the protobuf message is computed
     * on-the-fly as {@code total - reserved}, reflecting the current availability.
     *
     * @param item the JPA entity to convert (must not be null)
     * @return the protobuf message equivalent
     */
    public com.example.grpcclient.proto.InventoryItem toProto(InventoryItem item) {
        return com.example.grpcclient.proto.InventoryItem.newBuilder()
                .setSku(item.getSku())
                .setProductName(item.getProductName() != null ? item.getProductName() : "")
                .setTotalQuantity(item.getTotalQuantity())
                .setReservedQuantity(item.getReservedQuantity())
                // Compute available_quantity = total - reserved.
                // This is a derived field not stored in the database.
                .setAvailableQuantity(item.getAvailableQuantity())
                .build();
    }
}
