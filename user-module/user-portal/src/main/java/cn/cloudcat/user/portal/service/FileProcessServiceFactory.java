package cn.cloudcat.user.portal.service;

import cn.cloudcat.user.common.constant.FileType;
import cn.cloudcat.user.common.constant.KnowledgeBaseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文件处理服务工厂，根据文件类型和知识库类型自动匹配对应的处理器。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
public class FileProcessServiceFactory {
    /** Spring 自动注入所有 FileProcessService 实现 */
    @Autowired
    private List<FileProcessService> fileProcessServiceList;

    /**
     * 根据文件类型和知识库类型获取匹配的文件处理服务。
     *
     * @param fileProcessType   文件类型（PDF/EXCEL/CSV/DOC）
     * @param knowledgeBaseType 知识库类型
     * @return 匹配的处理器，无匹配时返回 null
     */
    public FileProcessService get(FileType fileProcessType, KnowledgeBaseType knowledgeBaseType) {
        return fileProcessServiceList.stream()
                .filter(service -> service.supports(fileProcessType, knowledgeBaseType))
                .findFirst().orElse(null);
    }
}
