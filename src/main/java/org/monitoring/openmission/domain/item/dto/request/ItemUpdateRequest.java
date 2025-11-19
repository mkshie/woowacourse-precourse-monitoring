package org.monitoring.openmission.domain.item.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record ItemUpdateRequest(
        String name,
        Integer price,
        Integer stock
) {
}
