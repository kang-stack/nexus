package cn.cloudcat.user.common.constant;

/**
 * 文件类型
 *
 * @author Kang
 * @since 2026-06-02
 */
public enum FileType {
    /** PDF 文档 */
    PDF("pdf"),
    /** Word 文档 */
    DOC("doc"),
    /** 纯文本文件 */
    TXT("txt"),
    /** HTML 文件 */
    HTML("html"),
    /** Markdown 文件 */
    MARKDOWN("markdown"),
    /** CSV 表格文件 */
    CSV("csv"),
    /** Excel 表格文件 */
    EXCEL("excel");

    private final String type;

    FileType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
