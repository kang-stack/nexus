# cat-customer-agent

智能客服（V2.1 基线）— Java 21 + Spring Boot 3.5 + Vue 3。

## 模块结构

| 目录 | 说明 |
|------|------|
| `bootstrap/` | Spring Boot 启动入口（端口 8899） |
| `common-module/` | 公共技术组件（core、mybatis、redis、security、web、integration） |
| `user-module/` | 用户侧业务（common、knowledge、session、message、portal、agent…） |
| `ai-module/` | AI 能力（ai-spi、ai-rag、ai-intent 占位） |
| `cs-module/` | 客服业务扩展（cs-assistant） |
| `frontend/` | Vue 3 管理端与用户聊天前端 |

## 快速启动

```bash
# 后端
mvn -pl bootstrap -am compile
cd bootstrap && mvn spring-boot:run

# 前端
cd frontend && npm install && npm run dev
```

- 默认 profile：`dev`
- AI 配置：`bootstrap/src/main/resources/application-ai.yml`
- 数据库：Flyway 自动迁移（`bootstrap/src/main/resources/db/migration/`）

## 设计文档

项目仓库不含过程文档。设计与执行计划见本机目录：

`C:/workspace/workrecord/cat-customer-agent/docs/`
