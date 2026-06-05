package cn.cloudcat.user.portal.service;

import cn.cloudcat.user.portal.entity.KnowledgeSegment;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.Serializable;

/**
 * 知识片段表 Service 接口
 *
 * @author Kang
 * @since 2026-06-02
 */
public interface KnowledgeSegmentService extends IService<KnowledgeSegment> {

    /** 根据chunkId获取文本内容（优先Redis缓存，未命中则查DB） */
    public String getTextByChunkId(Serializable chunkId);
}
