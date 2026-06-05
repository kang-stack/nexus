package cn.cloudcat.user.portal.spi;

import cn.cloudcat.user.portal.service.KnowledgeSegmentService;
import cn.cloudcat.ai.spi.KnowledgeSegmentLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.Serializable;

@Service
@RequiredArgsConstructor
public class PortalKnowledgeSegmentLookup implements KnowledgeSegmentLookup {

    private final KnowledgeSegmentService knowledgeSegmentService;

    @Override
    public String getTextByChunkId(Serializable chunkId) {
        return knowledgeSegmentService.getTextByChunkId(chunkId);
    }
}
