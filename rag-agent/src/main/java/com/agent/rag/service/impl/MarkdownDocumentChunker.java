package com.agent.rag.service.impl;

import com.agent.rag.dto.DocumentChunk;
import com.agent.rag.service.DocumentChunker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@Component
public class MarkdownDocumentChunker implements DocumentChunker {

    @Value("${rag.index.chunk-size-tokens:700}")
    private int chunkSizeTokens;

    @Value("${rag.index.chunk-overlap-tokens:100}")
    private int chunkOverlapTokens;

    @Override
    public List<DocumentChunk> split(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }

        int maxChars = Math.max(400, chunkSizeTokens * 4);
        int overlapChars = Math.max(0, Math.min(chunkOverlapTokens * 4, maxChars / 2));
        List<String> blocks = splitIntoBlocks(content);
        List<DocumentChunk> chunks = new ArrayList<>();

        StringBuilder current = new StringBuilder();
        for (String block : blocks) {
            if (current.length() > 0 && current.length() + block.length() + 2 > maxChars) {
                addChunk(chunks, current.toString());
                String overlap = tail(current.toString(), overlapChars);
                current.setLength(0);
                if (StringUtils.hasText(overlap)) {
                    current.append(overlap).append("\n\n");
                }
            }

            if (block.length() > maxChars) {
                if (current.length() > 0) {
                    addChunk(chunks, current.toString());
                    current.setLength(0);
                }
                splitLongBlock(block, maxChars, overlapChars, chunks);
            } else {
                current.append(block).append("\n\n");
            }
        }

        if (StringUtils.hasText(current.toString())) {
            addChunk(chunks, current.toString());
        }

        return chunks;
    }

    private List<String> splitIntoBlocks(String content) {
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n").trim();
        String[] rawBlocks = normalized.split("\\n\\s*\\n");
        List<String> blocks = new ArrayList<>();
        for (String block : rawBlocks) {
            String trimmed = block.trim();
            if (StringUtils.hasText(trimmed)) {
                blocks.add(trimmed);
            }
        }
        return blocks;
    }

    private void splitLongBlock(String block, int maxChars, int overlapChars, List<DocumentChunk> chunks) {
        int start = 0;
        while (start < block.length()) {
            int end = Math.min(start + maxChars, block.length());
            addChunk(chunks, block.substring(start, end));
            if (end >= block.length()) {
                break;
            }
            start = Math.max(end - overlapChars, start + 1);
        }
    }

    private void addChunk(List<DocumentChunk> chunks, String text) {
        String normalized = text.trim();
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        chunks.add(DocumentChunk.builder()
                .chunkIndex(chunks.size())
                .text(normalized)
                .tokenCount(estimateTokens(normalized))
                .contentHash(sha256(normalized))
                .build());
    }

    private int estimateTokens(String text) {
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private String tail(String text, int maxChars) {
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(text.length() - maxChars).trim();
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash document chunk", e);
        }
    }
}
