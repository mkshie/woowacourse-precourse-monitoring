package org.monitoring.openmission.domain.item.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ItemCreateRequest(
        @NotNull(message = "이름은 필수입니다.")
        String name,
        @Min(value = 0, message = "가격은 양수여야합니다.")
        Integer price,
        Integer stock
) {
}
