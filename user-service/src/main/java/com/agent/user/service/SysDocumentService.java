package com.agent.user.service;

import com.agent.user.dto.DocumentResponse;
import java.util.List;

public interface SysDocumentService {
    List<DocumentResponse> listDocumentsForUser(Long userId);
}
