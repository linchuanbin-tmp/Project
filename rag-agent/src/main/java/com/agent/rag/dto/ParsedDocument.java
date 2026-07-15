package com.agent.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDocument {

    private boolean parsed;

    private String text;

    private String contentType;

    private String errorMessage;
}
