package org.bieco.common.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class UiEvent {
    private String timestamp;
    private String type;
    private String event;
    private String data;
}
