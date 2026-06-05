CREATE TABLE acc_user_conversation (
    id VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '会话主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    device_sn VARCHAR(64) DEFAULT NULL COMMENT '设备序列号',
    channel VARCHAR(16) NOT NULL DEFAULT 'WECHAT' COMMENT '渠道来源：WECHAT等',
    entry_intent VARCHAR(64) DEFAULT NULL COMMENT '入口意图编码',
    latest_diagnosis_id VARCHAR(64) DEFAULT NULL COMMENT '最近一次诊断运行ID',
    topic_key VARCHAR(64) DEFAULT NULL COMMENT '话题标识',
    turn_count INT NOT NULL DEFAULT 0 COMMENT '对话轮次计数',
    chitchat_only_count INT NOT NULL DEFAULT 0 COMMENT '纯闲聊轮次计数',
    solution_given TINYINT NOT NULL DEFAULT 0 COMMENT '是否已给出解决方案：0-未给出，1-已给出',
    ai_reply_locked TINYINT NOT NULL DEFAULT 0 COMMENT 'AI回复是否锁定（如转人工后）：0-否，1-是',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态：ACTIVE/CLOSED等',
    satisfaction_status VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '满意度状态：NONE/SUBMITTED等',
    close_reason VARCHAR(32) DEFAULT NULL COMMENT '会话关闭原因',
    last_user_at DATETIME DEFAULT NULL COMMENT '用户最后发言时间',
    last_ai_at DATETIME DEFAULT NULL COMMENT 'AI最后回复时间',
    ext_json JSON DEFAULT NULL COMMENT '扩展JSON（DialogueState等）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_user (user_id, created_at),
    KEY idx_timer (status, last_ai_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户客服会话表';

CREATE TABLE acc_user_message (
    id VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '消息主键ID',
    conversation_id VARCHAR(64) NOT NULL COMMENT '关联会话ID',
    role VARCHAR(16) NOT NULL COMMENT '消息角色：user/assistant/system',
    content_type VARCHAR(32) NOT NULL COMMENT '内容类型：text/markdown/json/buttons',
    content TEXT COMMENT '消息正文内容',
    payload_json JSON COMMENT '附加负载JSON（按钮、动作等）',
    trigger_type VARCHAR(32) DEFAULT NULL COMMENT '消息触发类型',
    reply_source VARCHAR(16) DEFAULT NULL COMMENT '回复来源：rag/rule/chitchat/diagnosis',
    intent_code VARCHAR(64) DEFAULT NULL COMMENT '意图编码',
    branch_id BIGINT DEFAULT NULL COMMENT '知识库分支ID',
    diagnosis_id VARCHAR(64) DEFAULT NULL COMMENT '关联诊断运行ID',
    ai_trace_id VARCHAR(64) DEFAULT NULL COMMENT '关联LLM追踪ID',
    seq_no INT DEFAULT 0 COMMENT '消息序号（会话内递增）',
    client_msg_id VARCHAR(64) DEFAULT NULL COMMENT '客户端消息ID（幂等去重）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_client (conversation_id, client_msg_id),
    KEY idx_conv (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话消息表';

CREATE TABLE acc_user_satisfaction (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    conversation_id VARCHAR(64) NOT NULL COMMENT '关联会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    device_sn VARCHAR(64) DEFAULT NULL COMMENT '设备序列号',
    useful TINYINT NOT NULL COMMENT '是否有帮助：0-否，1-是',
    trigger_type VARCHAR(32) NOT NULL COMMENT '触发类型：manual/auto等',
    last_intent_code VARCHAR(64) DEFAULT NULL COMMENT '评价前最后意图编码',
    last_branch_id BIGINT DEFAULT NULL COMMENT '评价前最后分支ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_conv (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话满意度评价表';

CREATE TABLE acc_user_diagnosis_run (
    id VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '诊断记录主键ID',
    conversation_id VARCHAR(64) DEFAULT NULL COMMENT '关联会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    device_sn VARCHAR(64) NOT NULL COMMENT '设备序列号',
    snapshot_json JSON NOT NULL COMMENT '诊断输入快照JSON',
    findings_json JSON DEFAULT NULL COMMENT '诊断中间发现JSON',
    report_json JSON NOT NULL COMMENT '诊断报告JSON',
    rule_version VARCHAR(32) NOT NULL COMMENT '使用的DMN规则版本',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_sn_time (device_sn, created_at),
    KEY idx_conv (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备诊断运行记录表';

CREATE TABLE acc_user_chat_turn_trace (
    id VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '轨迹记录ID（通常与turn_id相同）',
    turn_id VARCHAR(64) NOT NULL COMMENT '单轮对话ID',
    conversation_id VARCHAR(64) NOT NULL COMMENT '关联会话ID',
    user_message_id VARCHAR(64) DEFAULT NULL COMMENT '用户消息ID',
    user_id BIGINT DEFAULT NULL COMMENT '用户ID',
    user_text VARCHAR(512) DEFAULT NULL COMMENT '用户原文（截断存储）',
    outcome VARCHAR(32) NOT NULL COMMENT '最终路径：BRANCH/RAG/CHITCHAT/DUPLICATE/ERROR',
    resolved_intent VARCHAR(64) DEFAULT NULL COMMENT '解析到的意图编码',
    primary_root_cause VARCHAR(64) DEFAULT NULL COMMENT '主根因编码',
    segment_count INT NOT NULL DEFAULT 0 COMMENT '回复分段数量',
    total_cost_ms BIGINT NOT NULL DEFAULT 0 COMMENT '本轮总耗时（毫秒）',
    trace_json JSON NOT NULL COMMENT 'Skill执行轨迹JSON',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_conv_time (conversation_id, created_at),
    KEY idx_turn (turn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单轮对话Skill执行轨迹表';

CREATE TABLE acc_user_action_template (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    action_code VARCHAR(64) NOT NULL COMMENT '动作编码',
    action_name VARCHAR(128) NOT NULL COMMENT '动作名称',
    success_message_template TEXT COMMENT '执行成功后的提示消息模板',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    UNIQUE KEY uk_action (action_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户动作模板表';

CREATE TABLE acc_user_rule_scene (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    scene_code VARCHAR(64) NOT NULL COMMENT '场景编码',
    scene_name VARCHAR(128) NOT NULL COMMENT '场景名称',
    dimension_schema_json JSON NOT NULL COMMENT '维度定义JSON Schema',
    dmn_resource_path VARCHAR(256) NOT NULL COMMENT 'DMN主决策表classpath路径',
    priority_dmn_path VARCHAR(256) DEFAULT NULL COMMENT 'DMN优先级决策表classpath路径',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_scene (scene_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DMN诊断场景配置表';

CREATE TABLE acc_user_rule_version (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    scene_code VARCHAR(64) NOT NULL COMMENT '场景编码',
    version VARCHAR(32) NOT NULL COMMENT '版本号',
    dmn_deploy_id VARCHAR(128) DEFAULT NULL COMMENT 'DMN部署ID',
    published_by VARCHAR(64) DEFAULT NULL COMMENT '发布人',
    published_at DATETIME NOT NULL COMMENT '发布时间',
    rollback_to VARCHAR(32) DEFAULT NULL COMMENT '回滚目标版本号',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/ARCHIVED等',
    UNIQUE KEY uk_scene_ver (scene_code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DMN规则版本发布表';

CREATE TABLE acc_user_kb_intent (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    intent_code VARCHAR(64) NOT NULL COMMENT '意图编码',
    intent_name VARCHAR(128) NOT NULL COMMENT '意图名称',
    category VARCHAR(32) NOT NULL COMMENT '意图分类',
    need_diagnosis TINYINT NOT NULL DEFAULT 1 COMMENT '是否需要诊断：0-不需要，1-需要',
    rule_scene_code VARCHAR(64) DEFAULT NULL COMMENT '关联DMN场景编码',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    UNIQUE KEY uk_intent (intent_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库意图表';

CREATE TABLE acc_user_kb_branch (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    intent_code VARCHAR(64) NOT NULL COMMENT '关联意图编码',
    branch_code VARCHAR(64) NOT NULL COMMENT '分支编码',
    branch_name VARCHAR(128) DEFAULT NULL COMMENT '分支名称',
    condition_json JSON COMMENT '分支匹配条件JSON（AND/OR+field/op/val）',
    priority INT NOT NULL DEFAULT 100 COMMENT '优先级（数值越小越优先）',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    UNIQUE KEY uk_branch (branch_code),
    KEY idx_intent (intent_code, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库意图分支表';

CREATE TABLE acc_user_kb_reply_script (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    branch_id BIGINT NOT NULL COMMENT '关联分支ID',
    seq_no INT NOT NULL COMMENT '脚本序号（分支内排序）',
    content_type VARCHAR(32) NOT NULL COMMENT '内容类型：text/markdown/buttons',
    text_template TEXT COMMENT '回复文本模板',
    payload_json JSON COMMENT '附加负载JSON',
    delay_seconds INT NOT NULL DEFAULT 0 COMMENT '发送前延迟秒数',
    UNIQUE KEY uk_branch_seq (branch_id, seq_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分支回复脚本表';

CREATE TABLE acc_user_kb_faq_alias (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    alias_text VARCHAR(256) NOT NULL COMMENT '别名/问法文本',
    intent_code VARCHAR(64) NOT NULL COMMENT '关联意图编码',
    match_type VARCHAR(16) NOT NULL DEFAULT 'CONTAINS' COMMENT '匹配类型：CONTAINS/EXACT等',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    KEY idx_alias (alias_text(64))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='FAQ别名问法映射表';

CREATE TABLE acc_user_kb_utterance_connector (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    connector_text VARCHAR(64) NOT NULL COMMENT '分句连接词文本',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    UNIQUE KEY uk_connector (connector_text)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='话语分句连接词表';

CREATE TABLE acc_user_kb_chitchat_pattern (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    category_code VARCHAR(32) NOT NULL COMMENT '闲聊分类编码',
    pattern VARCHAR(256) NOT NULL COMMENT '匹配模式文本',
    match_type VARCHAR(16) NOT NULL COMMENT '匹配类型：exact/contains/regex',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    KEY idx_cat (category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='闲聊匹配模式表';

CREATE TABLE acc_user_kb_chitchat_reply (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    category_code VARCHAR(32) NOT NULL COMMENT '闲聊分类编码',
    session_phase VARCHAR(16) NOT NULL COMMENT '会话阶段：opening/closing/idle',
    text_template TEXT NOT NULL COMMENT '回复文本模板',
    append_faq_chips TINYINT NOT NULL DEFAULT 0 COMMENT '是否追加FAQ快捷提问：0-否，1-是',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    UNIQUE KEY uk_cat_phase (category_code, session_phase)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='闲聊回复模板表';

CREATE TABLE acc_user_kb_human_route (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    route_code VARCHAR(64) NOT NULL COMMENT '转人工路由编码',
    intent_code VARCHAR(64) DEFAULT NULL COMMENT '匹配意图编码（可空表示默认）',
    channel_url VARCHAR(512) DEFAULT NULL COMMENT '人工客服渠道URL',
    work_hours_json JSON COMMENT '工作时间配置JSON',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    UNIQUE KEY uk_route (route_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转人工路由配置表';

CREATE TABLE acc_user_kb_scene_faq (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    scene_code VARCHAR(64) NOT NULL COMMENT '场景编码',
    faq_label VARCHAR(128) NOT NULL COMMENT 'FAQ显示标签',
    intent_code VARCHAR(64) NOT NULL COMMENT '关联意图编码',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    KEY idx_scene (scene_code, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场景FAQ入口表';

CREATE TABLE acc_user_kb_session_template (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    template_code VARCHAR(64) NOT NULL COMMENT '模板编码',
    text_template TEXT NOT NULL COMMENT '会话话术模板文本',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    UNIQUE KEY uk_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话话术模板表';

CREATE TABLE acc_user_kb_user_action (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    script_id BIGINT NOT NULL COMMENT '关联回复脚本ID',
    action_code VARCHAR(64) NOT NULL COMMENT '动作编码',
    action_type VARCHAR(16) NOT NULL COMMENT '动作类型：button/link等',
    label VARCHAR(64) NOT NULL COMMENT '按钮显示文案',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    KEY idx_script (script_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回复脚本用户动作表';

CREATE TABLE acc_user_kb_text_link (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    script_id BIGINT NOT NULL COMMENT '关联回复脚本ID',
    link_text VARCHAR(128) NOT NULL COMMENT '链接显示文本',
    link_type VARCHAR(16) NOT NULL COMMENT '链接类型：url/intent等',
    url VARCHAR(512) DEFAULT NULL COMMENT '跳转URL',
    intent_code VARCHAR(64) DEFAULT NULL COMMENT '跳转意图编码',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    KEY idx_script (script_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回复脚本文本链接表';

CREATE TABLE acc_user_kb_system_action (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    branch_id BIGINT NOT NULL COMMENT '关联分支ID',
    action_type VARCHAR(64) NOT NULL COMMENT '系统动作类型',
    params_json JSON COMMENT '动作参数JSON',
    exec_order INT NOT NULL DEFAULT 0 COMMENT '执行顺序',
    KEY idx_branch (branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分支系统动作表';

CREATE TABLE acc_ai_doc_document (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    title VARCHAR(1024) NOT NULL COMMENT '文档标题',
    source_url VARCHAR(2048) DEFAULT NULL COMMENT '文档来源URL',
    status VARCHAR(32) NOT NULL DEFAULT 'INIT' COMMENT '处理状态：INIT/INDEXED等',
    scope_code VARCHAR(64) DEFAULT NULL COMMENT '可见范围编码',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI RAG文档索引表';

CREATE TABLE acc_ai_doc_chunk (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    document_id BIGINT NOT NULL COMMENT '关联文档ID',
    chunk_order INT NOT NULL COMMENT '分块序号',
    text_content LONGTEXT NOT NULL COMMENT '分块文本内容',
    embedding_id VARCHAR(255) DEFAULT NULL COMMENT '向量嵌入ID',
    metadata_json JSON COMMENT '分块元数据JSON',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_doc (document_id, chunk_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI RAG文档分块表';

CREATE TABLE acc_ai_llm_trace (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    trace_id VARCHAR(64) NOT NULL COMMENT '追踪ID（业务关联用）',
    api_type VARCHAR(16) NOT NULL COMMENT 'API类型：chat/embedding等',
    model_name VARCHAR(128) DEFAULT NULL COMMENT '模型名称',
    latency_ms INT DEFAULT NULL COMMENT '调用延迟（毫秒）',
    status VARCHAR(16) NOT NULL COMMENT '调用状态：success/fail',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM调用追踪表';

CREATE TABLE acc_ai_model_route (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    task_code VARCHAR(64) NOT NULL COMMENT '任务编码：intent-recognition等',
    model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
    priority INT NOT NULL DEFAULT 0 COMMENT '路由优先级（越小越优先）',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    UNIQUE KEY uk_task_model (task_code, model_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI任务模型路由表';

CREATE TABLE acc_user_scene_dimension (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    scene_code VARCHAR(64) NOT NULL COMMENT '场景编码',
    fact_key VARCHAR(64) NOT NULL COMMENT 'DMN事实变量key',
    label VARCHAR(128) DEFAULT NULL COMMENT '维度显示名称',
    data_type VARCHAR(32) NOT NULL DEFAULT 'string' COMMENT '数据类型：string/number/boolean/enum',
    source_api VARCHAR(256) DEFAULT NULL COMMENT '数据来源API路径',
    enum_mapping_json JSON DEFAULT NULL COMMENT '枚举值映射JSON',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    KEY idx_scene (scene_code, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='诊断场景维度定义表';

CREATE TABLE acc_user_message_review (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    message_id VARCHAR(64) NOT NULL COMMENT '被审核消息ID',
    conversation_id VARCHAR(64) NOT NULL COMMENT '关联会话ID',
    review_status VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '审核状态：pending/reviewed/escalated',
    risk_level VARCHAR(16) DEFAULT NULL COMMENT '风险等级：low/medium/high/critical',
    emotion_tag VARCHAR(32) DEFAULT NULL COMMENT '情绪标签',
    review_note TEXT COMMENT '审核备注',
    reviewer_admin_id BIGINT DEFAULT NULL COMMENT '审核管理员ID',
    reviewed_at DATETIME DEFAULT NULL COMMENT '审核时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_conv (conversation_id),
    KEY idx_status (review_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息人工审核表';

CREATE TABLE acc_user_kb_chitchat_category (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    category_code VARCHAR(32) NOT NULL COMMENT '分类编码',
    category_name VARCHAR(128) NOT NULL COMMENT '分类名称',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    UNIQUE KEY uk_category (category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='闲聊分类表';

CREATE TABLE acc_user_kb_emotion_pattern (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    emotion_tag VARCHAR(32) NOT NULL COMMENT '情绪标签：ABUSE/ANGRY/SAD/URGENT/THREATEN等',
    match_type VARCHAR(16) NOT NULL DEFAULT 'CONTAINS' COMMENT '匹配类型：CONTAINS/REGEX',
    pattern VARCHAR(256) NOT NULL COMMENT '匹配关键词或正则表达式',
    language VARCHAR(16) NOT NULL DEFAULT 'ZH' COMMENT '语言：ZH/EN/PINYIN/UNIVERSAL',
    risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW' COMMENT '风险等级：HIGH/MEDIUM/LOW',
    suggest_human TINYINT NOT NULL DEFAULT 0 COMMENT '是否建议转人工：0-否，1-是',
    soothe_hint VARCHAR(512) DEFAULT NULL COMMENT '安抚话术提示',
    priority INT NOT NULL DEFAULT 100 COMMENT '匹配优先级（越小越先）',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    KEY idx_tag (emotion_tag, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='情绪识别模式表';

CREATE TABLE acc_user_knowledge_document (
    doc_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '文档ID',
    doc_title VARCHAR(1024) NOT NULL COMMENT '文档标题',
    upload_user VARCHAR(64) DEFAULT NULL COMMENT '上传用户',
    doc_url VARCHAR(2048) DEFAULT NULL COMMENT '原始文档URL',
    converted_doc_url VARCHAR(2048) DEFAULT NULL COMMENT '转换后文档URL',
    status VARCHAR(32) NOT NULL DEFAULT 'INIT' COMMENT '状态：INIT/UPLOADED/CONVERTING/CONVERTED/CHUNKED/VECTOR_STORED',
    accessible_by VARCHAR(64) DEFAULT NULL COMMENT '可见范围',
    description TEXT COMMENT '文档描述',
    knowledge_base_type VARCHAR(32) DEFAULT NULL COMMENT '知识库类型',
    extension JSON DEFAULT NULL COMMENT '扩展字段JSON',
    scene_code VARCHAR(64) DEFAULT NULL COMMENT '关联场景编码',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    lock_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营知识库文档表';

CREATE TABLE acc_user_knowledge_segment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '分片ID',
    text LONGTEXT COMMENT '分片文本内容',
    chunk_id VARCHAR(128) DEFAULT NULL COMMENT '分片业务ID',
    metadata JSON DEFAULT NULL COMMENT '分片元数据JSON',
    document_id BIGINT NOT NULL COMMENT '所属文档ID',
    chunk_order INT NOT NULL DEFAULT 0 COMMENT '分片顺序',
    embedding_id VARCHAR(255) DEFAULT NULL COMMENT '向量嵌入ID',
    status VARCHAR(32) DEFAULT NULL COMMENT '状态：INIT/VECTOR_STORED等',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    lock_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    KEY idx_doc (document_id, chunk_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营知识库文档分片表';

CREATE TABLE acc_user_device (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    device_id VARCHAR(64) NOT NULL COMMENT '设备唯一标识',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '绑定用户ID',
    device_model VARCHAR(128) DEFAULT NULL COMMENT '设备型号',
    sn VARCHAR(64) DEFAULT NULL COMMENT '设备序列号',
    imei VARCHAR(32) DEFAULT NULL COMMENT 'IMEI号',
    activation_time DATETIME DEFAULT NULL COMMENT '激活时间',
    data_plan VARCHAR(128) DEFAULT NULL COMMENT '流量套餐名称',
    data_used_gb DECIMAL(12,2) DEFAULT NULL COMMENT '已用流量（GB）',
    data_total_gb DECIMAL(12,2) DEFAULT NULL COMMENT '总流量（GB）',
    expire_time DATETIME DEFAULT NULL COMMENT '套餐过期时间',
    status VARCHAR(32) DEFAULT 'active' COMMENT '设备状态：active/disabled等',
    remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
    diag_activation_status VARCHAR(32) DEFAULT NULL COMMENT '诊断维度覆盖：激活状态',
    diag_realname_status VARCHAR(32) DEFAULT NULL COMMENT '诊断维度覆盖：实名状态',
    diag_online_status VARCHAR(32) DEFAULT NULL COMMENT '诊断维度覆盖：在线状态',
    diag_signal_value VARCHAR(32) DEFAULT NULL COMMENT '诊断维度覆盖：信号值',
    diag_device_status VARCHAR(32) DEFAULT NULL COMMENT '诊断维度覆盖：设备状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    lock_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    UNIQUE KEY uk_device (device_id),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设备表';

CREATE TABLE acc_user_table_meta (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    table_name VARCHAR(128) NOT NULL COMMENT '表名',
    description VARCHAR(512) DEFAULT NULL COMMENT '表描述',
    create_sql LONGTEXT COMMENT '建表SQL',
    columns_info JSON DEFAULT NULL COMMENT '字段信息JSON',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    lock_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    UNIQUE KEY uk_table (table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表格元数据表';

CREATE TABLE acc_user_consultation_profile (
    device_id VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '设备ID',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '用户ID',
    total_consult_count INT NOT NULL DEFAULT 0 COMMENT '累计咨询次数',
    network_consult_count INT NOT NULL DEFAULT 0 COMMENT '网络类咨询次数',
    human_transfer_count INT NOT NULL DEFAULT 0 COMMENT '转人工次数',
    high_freq_flag TINYINT NOT NULL DEFAULT 0 COMMENT '高频咨询标记：0-否，1-是',
    network_issue_flag TINYINT NOT NULL DEFAULT 0 COMMENT '网络问题标记：0-否，1-是',
    last_consult_at DATETIME DEFAULT NULL COMMENT '最近咨询时间',
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备咨询画像表';
