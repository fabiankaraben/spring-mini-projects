package com.example.grpcclient.mapper;

import com.example.grpcclient.domain.Order;
import com.example.grpcclient.domain.OrderLineItem;
import com.example.grpcclient.domain.OrderStatus;
import com.example.grpcclient.proto.OrderServiceProto;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * Mapper component that converts between JPA domain objects and protobuf messages
 * for the Order domain.
 *
 * <p>Why a separate mapper?
 * <ul>
 *   <li>The JPA {@link Order} entity and the protobuf {@code Order} message have different
 *       representations and serve different layers (persistence vs. wire format).</li>
 *   <li>Keeping them separate allows each layer to evolve independently.</li>
 *   <li>The mapper is the single explicit place where translation happens, making
 *       it easy to audit, test, and modify.</li>
 * </ul>
 *
 * <p>This class is registered as a Spring {@link Component} so it can be injected
 * into the gRPC service implementations.
 */
@Component
public class OrderMapper {

    /**
     * ISO-8601 formatter for converting {@link java.time.LocalDateTime} to string
     * for use in protobuf messages (proto3 has no native DateTime type).
     */
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Convert a JPA {@link Order} domain object to a protobuf {@code Order} message.
     *
     * <p>All nested {@link OrderLineItem}s are also converted to protobuf
     * {@code OrderItem} messages and embedded in the response.
     *
     * @param order the JPA entity to convert (must not be null)
     * @return the protobuf message equivalent
     */
    public com.example.grpcclient.proto.Order toProto(Order order) {
        // Build the protobuf Order message using the builder pattern.
        com.example.grpcclient.proto.Order.Builder builder =
                com.example.grpcclient.proto.Order.newBuilder()
                        .setOrderId(order.getOrderId())
                        .setCustomerId(order.getCustomerId())
                        .setTotalAmount(order.getTotalAmount())
                        .setStatus(toProtoStatus(order.getStatus()))
                        .setCreatedAt(order.getCreatedAt().format(ISO_FORMATTER));

        // Convert each JPA line item to a protobuf OrderItem and add to the builder.
        for (OrderLineItem item : order.getItems()) {
            builder.addItems(toProtoItem(item));
        }

        return builder.build();
    }

    /**
     * Convert a JPA {@link OrderLineItem} to a protobuf {@code OrderItem} message.
     *
     * @param item the JPA line item entity (must not be null)
     * @return the protobuf message equivalent
     */
    public com.example.grpcclient.proto.OrderItem toProtoItem(OrderLineItem item) {
        return com.example.grpcclient.proto.OrderItem.newBuilder()
                .setSku(item.getSku())
                .setProductName(item.getProductName() != null ? item.getProductName() : "")
                .setQuantity(item.getQuantity())
                .setUnitPrice(item.getUnitPrice())
                .build();
    }

    /**
     * Map a JPA {@link OrderStatus} enum value to the corresponding protobuf
     * {@code OrderStatus} enum value.
     *
     * <p>The mapping is explicit (not ordinal-based) to survive future reordering.
     *
     * @param status the JPA enum value (may be null)
     * @return the corresponding protobuf enum value
     */
    public com.example.grpcclient.proto.OrderStatus toProtoStatus(OrderStatus status) {
        if (status == null) {
            return com.example.grpcclient.proto.OrderStatus.ORDER_STATUS_UNKNOWN;
        }
        return switch (status) {
            case PENDING    -> com.example.grpcclient.proto.OrderStatus.ORDER_STATUS_PENDING;
            case CONFIRMED  -> com.example.grpcclient.proto.OrderStatus.ORDER_STATUS_CONFIRMED;
            case SHIPPED    -> com.example.grpcclient.proto.OrderStatus.ORDER_STATUS_SHIPPED;
            case DELIVERED  -> com.example.grpcclient.proto.OrderStatus.ORDER_STATUS_DELIVERED;
            case CANCELLED  -> com.example.grpcclient.proto.OrderStatus.ORDER_STATUS_CANCELLED;
        };
    }

    /**
     * Map a protobuf {@code OrderStatus} enum value to the corresponding JPA
     * {@link OrderStatus} enum value.
     *
     * @param protoStatus the protobuf enum value
     * @return the corresponding JPA enum value, or PENDING as a safe default
     */
    public OrderStatus fromProtoStatus(com.example.grpcclient.proto.OrderStatus protoStatus) {
        return switch (protoStatus) {
            case ORDER_STATUS_PENDING    -> OrderStatus.PENDING;
            case ORDER_STATUS_CONFIRMED  -> OrderStatus.CONFIRMED;
            case ORDER_STATUS_SHIPPED    -> OrderStatus.SHIPPED;
            case ORDER_STATUS_DELIVERED  -> OrderStatus.DELIVERED;
            case ORDER_STATUS_CANCELLED  -> OrderStatus.CANCELLED;
            default                      -> OrderStatus.PENDING;
        };
    }
}
