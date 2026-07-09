package com.agent.rag.mapper;

import com.agent.rag.entity.RagDocumentChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface RagDocumentChunkMapper extends BaseMapper<RagDocumentChunk> {

    @Delete("DELETE FROM rag_document_chunk WHERE document_id = #{documentId}")
    int hardDeleteByDocumentId(@Param("documentId") Long documentId);
}
