package cn.cloudcat.user.portal.service.impl;

import cn.cloudcat.user.common.constant.FileType;
import cn.cloudcat.user.common.constant.KnowledgeBaseType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * word文档处理服务实现类
 *
 * @author Kang
 * @since 2026-06-02
 */
@Slf4j
@Service
public class WordProcessServiceImpl extends MinerUProcessBaseServiceImpl {

    @Override
    public boolean supports(FileType fileType, KnowledgeBaseType knowledgeBaseType) {
        return fileType == FileType.DOC;
    }
}

