package com.agent.rag.service;

import com.agent.rag.dto.ParsedDocument;

public interface DocumentParserService {

    ParsedDocument parse(String fileName, String contentType, byte[] content);
}
