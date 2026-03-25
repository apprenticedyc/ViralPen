# AI 爆款文章创作器 ✍️

<div align="center">

**AI 爆款文章创作器**

基于多智能体协作，自动完成从选题、大纲、正文到配图的全流程图文创作

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring AI Alibaba](https://img.shields.io/badge/Spring%20AI%20Alibaba-1.1.0-FF6A00?style=flat-square&logo=spring&logoColor=white)
![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D?style=flat-square&logo=vuedotjs&logoColor=white)
![JDK](https://img.shields.io/badge/JDK-21+-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

</div>

## 🏗 项目简介

AI 爆款文章创作器是一个基于 **Spring AI Alibaba** 构建的智能图文创作平台，通过 **5 个智能体协作** 完成从选题到图文文章的全自动创作，每个阶段都支持用户介入，实现人机协作的创作体验。

```
阶段1: 选题 → 生成 3-5 个标题方案 → 用户选择
阶段2: 标题 → 生成大纲 → 用户编辑 / AI 优化大纲
阶段3: 大纲 → 生成正文 → 分析配图需求 → 生成配图 → 图文合成
```

## 🎯 核心价值

| 特性 | 说明                             | 价值        |
|------|--------------------------------|-----------|
| 🤖 多智能体协作 | 5 个 Agent 分工协作，StateGraph 编排流程 | 专业分工，生成质量更高 |
| 🎨 多元配图 | 4 种配图策略 + 自动降级                 | 丰富创作素材    |
| 📡 实时流式输出 | SSE 推送大纲/正文创作过程                | 所见即所得     |
| 🧑‍💻 人机协作 | 三阶段创作，每步可介入                    | 生成内容可控    |
| 💎 VIP 会员体系 | 限定功能+额外配额                      | 商业变现      |

## ✨ 功能特性

### 智能体协作

| 智能体     | 功能 | 说明                       |
|---------|------|--------------------------|
| Agent 1 | 标题生成 | 根据选题生成 3-5 个标题方案供用户选择    |
| Agent 2 | 大纲生成 | 根据标题生成文章大纲（流式输出）         |
| Agent 3 | 正文生成 | 根据大纲生成 Markdown 正文（流式输出） |
| Agent 4 | 配图分析 | 分析正文内容，生成配图需求+占位符        |
| Agent 5 | 配图生成 | 获取图片并上传到 COS             |
| 合成节点    | 合并图文 | 将配图插入正文占位符生成完整图文         |

### 配图方式（策略模式）
采用策略模式实现多种配图方式，支持灵活扩展：

| 方式       | 说明        | 数据来源 | 权限 |
|----------|-----------|---------|------|
| Pexels   | 高质量图库检索   | 关键词检索 | 全部用户 |
| 表情包      | Bing 图片搜索 | 关键词检索 | 全部用户 |
| Seedream | 即梦 AI 生图  | AI Prompt 生成 | VIP |
| Picsum   | 随机图片      | 降级方案 | 自动触发 |

> 当主配图方式失败时，系统自动降级到 Picsum 随机图片，确保文章生成不中断。
### SSE 实时通信

基于 Server-Sent Events 实现实时进度推送：

| 消息类型 | 说明 |
|---------|------|
| `AGENT1_COMPLETE` | 标题方案生成完成 |
| `AGENT2_STREAMING` | 大纲流式输出中 |
| `AGENT2_COMPLETE` | 大纲生成完成 |
| `AGENT3_STREAMING` | 正文流式输出中 |
| `AGENT3_COMPLETE` | 正文生成完成 |
| `AGENT4_COMPLETE` | 配图需求分析完成 |
| `IMAGE_COMPLETE` | 单张配图生成完成 |
| `AGENT5_COMPLETE` | 所有配图生成完成 |
| `MERGE_COMPLETE` | 图文合成完成 |
| `ERROR` | 错误通知 |
## 🛠 技术栈

### 后端

| 技术 | 版本 | 
|------|------|
| Spring Boot | 3.5.9 | 
| Spring AI Alibaba | 1.1.0 | 
| MyBatis-Flex | 1.11.1 |
| MySQL | 8.0 | 
| Redisson | 3.50.0 | 
| Knife4j | 4.4.0 |

## 🚀 快速开始

### 环境要求

- JDK 21+
- Node.js 18+
- MySQL 8.0+
- Redis 7.x

### 1. 数据库初始化

```bash
mysql -uroot -p < sql/create_table.sql
```

### 2. 配置API、数据库连接

```bash
cp src/main/resources/application-dev.example.yml src/main/resources/application-dev.yml
```

编辑 `application-dev.yml`：

```yaml
spring:
  # MySQL 数据库配置
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/ai_passage_creator
    username: 你的用户名
    password: 你的密码
  # 阿里云百炼平台api-key
  ai:
    alibaba:
      dashscope:
        api-key: 你的apikey  # 必填
# 字节火山 APIKey
seedream:
  api-key: 你的apikey 
  model: doubao-seedream-5-0-260128
pexels:
  api-key: 你的apikey  # 必填  
# 腾讯云 COS 配置
tencent:
  cos:
    secret-id: 参考腾讯云COS配置说明
    secret-key: 同上
    region: 同上
    bucket: 同上
```

### 🔑 API Key 获取

| 服务      | 获取地址 | 说明   |
|---------|--------|------|
| 阿里百炼    | https://bailian.console.aliyun.com | 必需   |
| Pexels  | https://www.pexels.com/api/ | 必需   |
| 腾讯云 COS | https://console.cloud.tencent.com | 图片上传 |
| 字节火山    | https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey | AI生图 |


### 3. 启动后端

```bash
mvn spring-boot:run
```

接口文档：http://localhost:8123/api/doc.html

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```
前端页面：http://localhost:5173
## 📁 项目结构

```
ai-creator/
├── src/main/java/com/yupi/template/
│   ├── agent/                       # 智能体模块
│   │   ├── agents/                  # 各智能体实现
│   │   ├── parallel/                # 并行配图生成
│   │   ├── config/                  # 智能体配置
│   │   ├── context/                 # 流式处理上下文
│   │   ├── tools/                   # 智能体工具
│   │   └── ArticleAgentOrchestrator.java
│   ├── config/                      # 配置类（COS、Pexels 等）
│   ├── controller/                  # 控制器
│   ├── exception/                   # 异常处理
│   ├── manager/                     # 管理器（SseEmitterManager）
│   ├── mapper/                      # MyBatis Mapper
│   ├── model/                       # 数据模型（entity/dto/vo/enums）
│   ├── service/                     # 业务服务
│   │   ├── impl/                    # 服务实现
│   │   ├── ImageServiceStrategy.java# 配图策略选择器
│   │   ├── CosService.java          # COS 上传
│   │   ├── PexelsService.java       # Pexels 图库
│   │   └── ...                      # 其他服务
│   └── utils/                       # 工具类
├── frontend/                        # 前端项目（Vue 3）
├── sql/                             # 数据库脚本
├── docker-compose.yml               # Docker 编排
└── pom.xml                          # Maven 配置
```

<div align="center">


## ⭐ Star History

[![Star History Chart](https://api.star-history.com/svg?repos=apprenticedyc/ViralPen&type=Date)](https://star-history.com/#apprenticedyc/ViralPen&Date)

---

**如果这个项目对你有帮助，请给一个 Star ⭐**

Made with ❤️ by [ApprenticeDyc](https://github.com/apprenticedyc)

</div>
