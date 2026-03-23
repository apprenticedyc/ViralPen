package com.hex.aicreator.agent.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.hex.aicreator.agent.prompt.PromptTemplate;
import com.hex.aicreator.model.dto.image.ImageRequest;
import com.hex.aicreator.model.enums.ImageMethodEnum;
import com.hex.aicreator.model.enums.SseMessageTypeEnum;
import com.hex.aicreator.model.state.ArticleState;
import com.hex.aicreator.service.impl.image.ImageServiceStrategy;
import com.hex.aicreator.utils.CosService;
import com.hex.aicreator.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Hex
 * @since 2026/3/20
 * Description
 */
@Service
@Slf4j
public class ArticleAgentService {
    @Resource
    private DashScopeChatModel chatModel;
    @Resource
    private ImageServiceStrategy imageServiceStrategy;
    @Resource
    private CosService cosService;

    /**
     * 阶段1：生成标题方案（3-5个）
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executePhase1(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体1：生成标题方案
            log.info("阶段1：开始生成标题方案, taskId={}", state.getTaskId());
            agent1GenerateTitleOptions(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
            log.info("阶段1：标题方案生成完成, taskId={}, optionsCount={}", state.getTaskId(), state.getTitleOptions()
                    .size());
        } catch (Exception e) {
            log.error("阶段1：标题方案生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("标题方案生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 阶段2：生成大纲（用户选择标题后）
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executePhase2(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体2：生成大纲（流式输出）
            log.info("阶段2：开始生成大纲, taskId={}", state.getTaskId());
            agent2GenerateOutline(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
            log.info("阶段2：大纲生成完成, taskId={}", state.getTaskId());
        } catch (Exception e) {
            log.error("阶段2：大纲生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("大纲生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 阶段3：生成正文+配图（用户确认大纲后）
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executePhase3(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体3：生成正文（流式输出）
            log.info("阶段3：开始生成正文, taskId={}", state.getTaskId());
            agent3GenerateContent(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());

            // 智能体4：分析配图需求
            log.info("阶段3：开始分析配图需求, taskId={}", state.getTaskId());
            agent4AnalyzeImageRequirements(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());

            // 智能体5：生成配图
            log.info("阶段3：开始生成配图, taskId={}", state.getTaskId());
            agent5GenerateImages(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());

            // 图文合成：将配图插入正文
            log.info("阶段3：开始图文合成, taskId={}", state.getTaskId());
            mergeImagesIntoContent(state);
            streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());

            log.info("阶段3：正文生成完成, taskId={}", state.getTaskId());
        } catch (Exception e) {
            log.error("阶段3：正文生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("正文生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 智能体1：生成标题方案（3-5个）
     */
    private void agent1GenerateTitleOptions(ArticleState state) {
        String prompt = PromptTemplate.AGENT1_TITLE_PROMPT.replace("{topic}", state.getTopic());

        String content = call(prompt, new TypeToken<List<ArticleState.TitleOption>>() {
        }.getClass());
        List<ArticleState.TitleOption> titleOptions = parseJsonListResponse(content, new TypeToken<List<ArticleState.TitleOption>>() {
        }, "标题方案");
        state.setTitleOptions(titleOptions);
        log.info("智能体1：标题方案生成成功, optionsCount={}", titleOptions.size());
    }


    /**
     * 智能体2：生成大纲（流式输出）
     */
    private void agent2GenerateOutline(ArticleState state, Consumer<String> streamHandler) {
        // 1. 构建用户补充描述的 prompt 部分（如果有的话）
        String userDescriptionPrompt = state.getUserDescription() == null ? "" : PromptTemplate.USER_DESCRIPTION_POMPT.replace("{userDescription}", state.getUserDescription());

        // 2. 构建完整的 prompt，动态插入标题和用户补充描述提示词
        String prompt = PromptTemplate.AGENT2_OUTLINE_PROMPT.replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle())
                .replace("{USER_DESCRIPTION_PROMPT}", userDescriptionPrompt);
        String content = stream(prompt, streamHandler, SseMessageTypeEnum.AGENT2_STREAMING, ArticleState.OutlineResult.class);
        ArticleState.OutlineResult outlineResult = parseJsonResponse(content, ArticleState.OutlineResult.class, "大纲");
        state.setOutline(outlineResult);
        log.info("智能体2：大纲生成成功, sections={}", outlineResult.getSections().size());
    }


    /**
     * 智能体3：生成正文（流式输出）
     */
    private void agent3GenerateContent(ArticleState state, Consumer<String> streamHandler) {
        String outlineText = GsonUtils.toJson(state.getOutline().getSections());
        String prompt = PromptTemplate.AGENT3_CONTENT_PROMPT.replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle()).replace("{outline}", outlineText);

        String content = stream(prompt, streamHandler, SseMessageTypeEnum.AGENT3_STREAMING, String.class);
        state.setContent(content);
        log.info("智能体3：正文生成成功, length={}", content.length());
    }

    /**
     * 智能体4：分析配图需求（在正文中插入占位符）
     */
    private void agent4AnalyzeImageRequirements(ArticleState state) {
        String prompt = PromptTemplate.AGENT4_IMAGE_REQUIREMENTS_PROMPT.replace("{mainTitle}", state.getTitle()
                .getMainTitle()).replace("{content}", state.getContent());

        String content = call(prompt, ArticleState.Agent4Result.class);
        ArticleState.Agent4Result agent4Result = parseJsonResponse(content, ArticleState.Agent4Result.class, "配图需求");

        // 更新正文为包含占位符的版本
        state.setContent(agent4Result.getContentWithPlaceholders());
        state.setImageRequirements(agent4Result.getImageRequirements());
        log.info("智能体4：配图需求分析成功, count={}, 已在正文中插入占位符", agent4Result.getImageRequirements()
                .size());
    }

    /**
     * 智能体5：生成配图（串行执行，支持混用多种配图方式，统一上传到 COS）
     */
    private void agent5GenerateImages(ArticleState state, Consumer<String> streamHandler) {
        List<ArticleState.ImageResult> imageResults = new ArrayList<>();

        for (ArticleState.ImageRequirement requirement : state.getImageRequirements()) {
            String imageSource = requirement.getImageSource();
            log.info("智能体5：开始获取配图, position={}, imageSource={}, keywords={}", requirement.getPosition(), imageSource, requirement.getKeywords());

            // 构建图片请求对象
            ImageRequest imageRequest = ImageRequest.builder().keywords(requirement.getKeywords())
                    .prompt(requirement.getPrompt()).position(requirement.getPosition()).type(requirement.getType())
                    .build();

            // 使用策略模式获取图片并统一上传到 COS
            ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(imageSource, imageRequest);
            String cosUrl = result.getUrl();
            ImageMethodEnum method = result.getMethod();

            // 创建配图结果（URL 已经是 COS 地址）
            ArticleState.ImageResult imageResult = buildImageResult(requirement, cosUrl, method);
            imageResults.add(imageResult);

            // 推送单张配图完成
            String imageCompleteMessage = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix() + GsonUtils.toJson(imageResult);
            streamHandler.accept(imageCompleteMessage);

            log.info("智能体5：配图获取并上传成功, position={}, method={}, cosUrl={}", requirement.getPosition(), method.getValue(), cosUrl);
        }

        state.setImages(imageResults);
        log.info("智能体5：所有配图生成并上传完成, count={}", imageResults.size());
    }

    /**
     * 图文合成：根据占位符将配图插入正文
     */
    private void mergeImagesIntoContent(ArticleState state) {
        String content = state.getContent();
        List<ArticleState.ImageResult> images = state.getImages();
        if (images == null || images.isEmpty()) {
            state.setFullContent(content);
            return;
        }
        String fullContent = content;

        // 遍历所有配图，根据占位符替换为实际图片
        for (ArticleState.ImageResult image : images) {
            String placeholder = image.getPlaceholderId();
            if (placeholder != null && !placeholder.isEmpty()) {
                String imageMarkdown = "![" + image.getDescription() + "](" + image.getUrl() + ")";
                fullContent = fullContent.replace(placeholder, imageMarkdown);
            }
        }
        state.setFullContent(fullContent);
        log.info("图文合成完成, fullContentLength={}", fullContent.length());
    }


    // startregion辅助方法

    /**
     * 调用 LLM（非流式）结构化输出
     */
    private String call(String prompt, Class clz) {
        DashScopeChatOptions options = DashScopeChatOptions.builder().enableThinking(true) // 开启思考
                .temperature(0.3)  // 更低的温度，更确定的输出
                .maxToken(5000) // 生成响应最多消耗token数
                .build();

        ReactAgent agent = ReactAgent.builder().name("content_generator").model(chatModel) // 指定LLM
                .outputType(clz) // 格式化输出
                .chatOptions(options).build();
        AssistantMessage response;
        try {
            response = agent.call(new UserMessage(prompt));
        } catch (GraphRunnerException e) {
            log.error("LLM 调用失败, prompt={}", prompt, e);
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }
        return response.getText();
    }

    /**
     * 调用 LLM（流式输出）
     */
    private String stream(String prompt, Consumer<String> streamHandler, SseMessageTypeEnum messageType, Class clz) {
        DashScopeChatOptions options = DashScopeChatOptions.builder().enableThinking(true) // 开启思考
                .temperature(0.3)  // 更低的温度，更确定的输出
                .maxToken(5000) // 生成响应最多消耗token数
                .build();

        StringBuilder contentBuilder = new StringBuilder();
        ReactAgent agent = ReactAgent.builder().name("content_generator").model(chatModel) // 指定LLM
                .outputType(clz) // 格式化输出
                .chatOptions(options).build();

        Flux<NodeOutput> response;
        try {
            response = agent.stream(new UserMessage(prompt));
        } catch (GraphRunnerException e) {
            log.error("LLM 调用失败, prompt={}", prompt, e);
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }
        response
                // 处理响应流中元素, 来一个拼接一个, 并通过streamHandler推送给前端
                .doOnNext(output -> {
                    if (output instanceof StreamingOutput streamingOutput) {
                        OutputType type = streamingOutput.getOutputType();
                        // 处理模型推理的流式输出
                        if (type == OutputType.AGENT_MODEL_STREAMING) {
                            String text = streamingOutput.message().getText();
                            contentBuilder.append(text);
                            // 实时推送进度
                            streamHandler.accept(messageType.getStreamingPrefix() + text);
                        } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                            log.info("\n模型流式输出完成");
                        }
                    }
                })
                // 错误处理, 记录日志
                .doOnError(error -> log.error("LLM 流式调用失败, messageType={}", messageType, error))
                // 阻塞直到整个响应流结束, 以确保contentBuilder 中内容完整
                .blockLast();
        return contentBuilder.toString();
    }

    /**
     * AI 修改大纲
     *
     * @param mainTitle        主标题
     * @param subTitle         副标题
     * @param currentOutline   当前大纲
     * @param modifySuggestion 用户修改建议
     * @return 修改后的大纲
     */
    public List<ArticleState.OutlineSection> aiModifyOutline(String mainTitle, String subTitle,
                                                             List<ArticleState.OutlineSection> currentOutline,
                                                             String modifySuggestion) {
        String currentOutlineJson = GsonUtils.toJson(currentOutline);

        String prompt = PromptTemplate.AI_MODIFY_OUTLINE_PROMPT.replace("{mainTitle}", mainTitle)
                .replace("{subTitle}", subTitle).replace("{currentOutline}", currentOutlineJson)
                .replace("{modifySuggestion}", modifySuggestion);

        String content = call(prompt, ArticleState.OutlineResult.class);
        ArticleState.OutlineResult outlineResult = parseJsonResponse(content, ArticleState.OutlineResult.class, "修改后的大纲");

        log.info("AI修改大纲成功, sectionsCount={}", outlineResult.getSections().size());
        return outlineResult.getSections();
    }


    /**
     * 解析 JSON 响应
     */
    private <T> T parseJsonResponse(String content, Class<T> clazz, String name) {
        try {

            return GsonUtils.fromJson(content, clazz);
        } catch (JsonSyntaxException e) {
            log.error("{}解析失败, content={}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }

    /**
     * 解析 JSON 列表响应
     */
    private <T> T parseJsonListResponse(String content, TypeToken<T> typeToken, String name) {
        try {
            return GsonUtils.fromJson(content, typeToken);
        } catch (JsonSyntaxException e) {
            log.error("{}解析失败, content={}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }

    /**
     * 构建配图结果对象
     */
    private ArticleState.ImageResult buildImageResult(ArticleState.ImageRequirement requirement, String imageUrl,
                                                      ImageMethodEnum method) {
        ArticleState.ImageResult imageResult = new ArticleState.ImageResult();
        imageResult.setPosition(requirement.getPosition());
        imageResult.setUrl(imageUrl);
        imageResult.setMethod(method.getValue());
        imageResult.setKeywords(requirement.getKeywords());
        imageResult.setSectionTitle(requirement.getSectionTitle());
        imageResult.setDescription(requirement.getType());
        imageResult.setPlaceholderId(requirement.getPlaceholderId());  // 记录占位符ID
        return imageResult;
    }

    /**
     * 在章节标题后插入对应图片
     */
    private void insertImageAfterSection(StringBuilder fullContent, List<ArticleState.ImageResult> images,
                                         String sectionTitle) {
        for (ArticleState.ImageResult image : images) {
            if (image.getPosition() > 1 && image.getSectionTitle() != null && sectionTitle.contains(image.getSectionTitle()
                    .trim())) {
                fullContent.append("\n![").append(image.getDescription()).append("](").append(image.getUrl())
                        .append(")\n");
                break;
            }
        }
    }
    // endregion
}
