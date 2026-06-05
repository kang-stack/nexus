package cn.cloudcat.user.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统管理员实体，对应 acc_user_admin 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Data
@TableName("acc_user_admin")
public class SysAdmin {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 管理员业务ID */
    @TableField("admin_id")
    private String adminId;

    /** 用户名 */
    private String username;

    /** 密码（BCrypt 加密） */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 角色：SUPER_ADMIN/ADMIN/OPERATOR */
    private String role;

    /** 状态：active/disabled */
    private String status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 乐观锁版本号 */
    @Version
    private Integer lockVersion;

    /** 逻辑删除：0-未删除，1-已删除 */
    @TableLogic
    private Integer deleted;
}
