package com.agent.rag.service.impl;

import com.agent.rag.dto.ParsedDocument;
import com.agent.rag.service.DocumentParserService;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
public class TikaDocumentParserService implements DocumentParserService {

    @Override
    public ParsedDocument parse(String fileName, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            return failed("Document content is empty.");
        }

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        if (contentType != null && !contentType.isBlank()) {
            metadata.set(Metadata.CONTENT_TYPE, contentType);
        }

        try {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            parser.parse(new ByteArrayInputStream(content), handler, metadata, context);
            String text = normalizeText(handler.toString());
            if (text.isBlank()) {
                return failed("Parser did not extract readable text.");
            }
            return ParsedDocument.builder()
                    .parsed(true)
                    .text(text)
                    .contentType(metadata.get(Metadata.CONTENT_TYPE))
                    .build();
        } catch (IOException | SAXException | TikaException e) {
            return failed(e.getMessage());
        }
    }

    private ParsedDocument failed(String message) {
        return ParsedDocument.builder()
                .parsed(false)
                .errorMessage(message)
                .build();
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
