package cn.cloudcat.user.portal.entity;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文档上传请求参数，封装上传文件及其元数据。
 *
 * @author Kang
 * @param file              上传的文件
 * @param uploadUser        上传用户
 * @param title             文档标题
 * @param accessibleBy      可见范围
 * @param description       文档描述
 * @param knowledgeBaseType 知识库类型
 * @param tableName         关联表名（数据查询知识库时使用）
 * @since 2026-06-02
 */
public record DocumentUploadParam(MultipartFile file, String uploadUser, String title, String accessibleBy,
                                  String description, String knowledgeBaseType,String tableName) {
}
