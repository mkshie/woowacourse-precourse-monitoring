package org.monitoring.openmission.domain.item.dto.response;

import org.monitoring.openmission.domain.item.entity.Item;

public record ItemResponse(
        Long Id,
        String name,
        Integer price,
        Integer stock
) {
    public static ItemResponse of(Item item){
        return new ItemResponse(item.getId(), item.getName(), item.getPrice(), item.getStock());
    }
}
