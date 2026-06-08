package com.chubby.dolphin.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderStatusDto {
    private String provider;
    private String model;
    private boolean enabled;
    private boolean available;
}
