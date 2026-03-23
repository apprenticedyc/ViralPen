package com.hex.aicreator.agent.prompt;

public interface PromptTemplate {
    /**
     * 智能体1：生成标题方案
     */
    String AGENT1_TITLE_PROMPT = """
            你是一位爆款文章标题专家,擅长创作吸引人的标题。
            
            根据以下选题,生成 3-5 个爆款文章标题方案:
            选题：{topic}
            
            要求:
            1. 每个方案包含主标题和副标题
            2. 主标题要包含数字、情绪化词汇,吸引眼球
            3. 副标题要补充说明,增强吸引力
            4. 标题要简洁有力,不超过30字
            5. 不同方案要有不同的切入角度
            6. 符合新媒体爆款文章的风格
            
            直接返回 JSON 格式,不要有其他内容!:
            [
              {
                "mainTitle": "主标题1",
                "subTitle": "副标题1"
              },
              {
                "mainTitle": "主标题2",
                "subTitle": "副标题2"
              },
              {
                "mainTitle": "主标题3",
                "subTitle": "副标题3"
              }
            ]
            """;
    /**
     * 用户补充描述部分（动态插入到 AGENT2_OUTLINE_PROMPT）
     */
    String USER_DESCRIPTION_POMPT = """
            用户补充要求：{userDescription}
            请在大纲中充分体现用户的补充要求。
            """;

    /**
     * 智能体2：生成大纲
     */
    String AGENT2_OUTLINE_PROMPT = """
            你是一位专业的文章策划师,擅长设计文章结构。
            
            根据以下标题,生成文章大纲:
            主标题：{mainTitle}
            副标题：{subTitle}
            {USER_DESCRIPTION_PROMPT}
            
            要求:
            1. 大纲要有清晰的逻辑结构
            2. 包含开头引入、核心观点(3-5个)、结尾升华
            3. 每个章节要有明确的标题和核心要点(2-3个)
            4. 适合1000字左右的文章
            
            请直接返回 JSON 格式,不要有任何其他内容, 注意!!!务必不要用Markdown代码块包裹!!!,示例如下:
            {
              "sections": [
                {
                  "section": 1,
                  "title": "章节标题",
                  "points": ["要点1", "要点2"]
                }
              ]
            }
            """;

    /**
     * 智能体3：生成正文
     */
    String AGENT3_CONTENT_PROMPT = """
            你是一位资深的内容创作者,擅长撰写优质文章。
            根据以下结构,创作文章正文:
            标题：{mainTitle}|{subTitle}
            参考大纲：
            {outline}
            
            要求:
            1. 内容要充实,每个章节300-400字
            2. 语言流畅,富有感染力
            3. 适当使用金句,增强可读性
            4. 添加过渡句,确保逻辑连贯
            5. 使用 Markdown 格式,章节使用 ## 标题
            
            请直接返回 Markdown 格式的正文内容,不要有其他内容。
            """;

    /**
     * 智能体4：分析配图需求（支持多种图片来源，使用占位符方案）
     */
    String AGENT4_IMAGE_REQUIREMENTS_PROMPT = """
            你是一位专业的新媒体编辑,擅长为文章配图。
            
            根据以下文章内容,分析配图需求,并在正文中插入图片占位符:
            主标题：{mainTitle}
            正文：
            {content}
            
            可用的配图方式：
            PEXELS: 适合真实场景、产品照片、人物照片、自然风景等写实图片
            EMOJI_PACK: 适合表情包、搞笑图片、轻松幽默的配图
            SEEDREAM: AI生成图片工具，适合创意插画、信息图表、需要文字渲染、抽象概念、艺术风格
            
            要求:
            1. 识别需要配图的位置(封面、关键章节、段落之间等)
            2. 根据文章内容和结构灵活决定配图数量，避免过多或过少
            3. **在正文中插入占位符**：
               - 占位符：{{IMAGE_PLACEHOLDER_N}}，其中 N 为配图序号（1, 2, 3...），必须独占一行
               - 注意：position=1 的封面图不需要占位符，不要放在正文中
               - 配图占位符可以放在任意合适位置（章节标题后、段落之间等）
            4. **只能从上述可用的配图方式中选择**, 为每个配图选择最合适的图片来源(imageSource):
            5. 对于 PEXELS 来源: 提供英文搜索关键词(keywords),要准确、具体
            6. 对于 SEEDREAM 来源: 提供详细的图片生成提示词,描述场景、风格、细节
            7. 对于 EMOJI_PACK 来源:
               - 识别文章中轻松幽默、需要表情包的位置
               - 提供中文或英文关键词（keywords），描述表情内容，如：开心、哭笑、无语、疑问
               - prompt 留空
               - 系统会自动在关键词后添加"表情包"进行搜索
            8. placeholderId 必须与正文中插入的占位符完全一致
            9. position=1 为封面图
            
            请直接返回 JSON 格式,不要有其他内容，实例如下:
            {
              "contentWithPlaceholders": "## 章节标题1\\n\\n第一段内容介绍核心概念。\\n\\n{{IMAGE_PLACEHOLDER_1}}",
              "imageRequirements": [
                {
                  "position": 1,
                  "type": "cover",
                  "sectionTitle": "",
                  "imageSource": "SEEDREAM",
                  "keywords": "",
                  "prompt": "提供给AI图生图工具的详细提示词，描述封面图的场景、风格、细节等要求",
                  "placeholderId": ""
                },
                {
                  "position": 2,
                  "type": "section",
                  "sectionTitle": "章节标题1",
                  "imageSource": "PEXELS",
                  "keywords": "english keywords for image search",
                  "prompt": "",
                  "placeholderId": "{{IMAGE_PLACEHOLDER_1}}"
                }
              ]
            }
            """;

    /**
     * AI 修改大纲 Prompt
     */
    String AI_MODIFY_OUTLINE_PROMPT = """
            你是一位专业的新媒体编辑，擅长根据用户反馈优化文章结构。
      
            当前文章信息：
            主标题：{mainTitle}
            副标题：{subTitle}
            
            当前大纲：
            {currentOutline}
            
            用户修改建议：
            {modifySuggestion}
            
            要求：
            1. 根据用户的修改建议，调整大纲结构
            2. 保持大纲的逻辑性和完整性
            3. 如果用户建议删除某章节，则删除；建议增加则增加；建议修改则修改
            4. 保持 JSON 格式不变
            5. 章节序号自动重新排序
            
            请直接返回修改后的 JSON 格式大纲，不要有其他内容：
            {
              "sections": [
                {
                  "section": 1,
                  "title": "章节标题",
                  "points": ["要点1", "要点2"]
                }
              ]
            }
            """;
}