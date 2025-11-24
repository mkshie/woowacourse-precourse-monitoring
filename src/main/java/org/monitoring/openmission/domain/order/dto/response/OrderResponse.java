package org.monitoring.openmission.domain.order.dto.response;

import org.monitoring.openmission.domain.order.entity.Order;

public record OrderResponse(
        Long Id,
        Integer quantity,
        Integer totalPrice,
        Long itemId,
        String itemName
) {
    public static OrderResponse of(Order order){
        return new OrderResponse(order.getId(),order.getQuantity(), order.getTotalPrice(), order.getItem().getId(), order.getItem().getName());
    }
}
