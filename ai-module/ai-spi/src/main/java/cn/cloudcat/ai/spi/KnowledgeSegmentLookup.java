package cn.cloudcat.ai.spi;

import java.io.Serializable;

/**
 * 按 chunkId 解析知识片段正文（实现由 icc-admin 提供）。
 */
public interface KnowledgeSegmentLookup {

    String getTextByChunkId(Serializable chunkId);
}
