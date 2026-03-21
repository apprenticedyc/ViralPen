package com.hex.aicreator.agent.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.hex.aicreator.agent.prompt.PromptTemplate;
import com.hex.aicreator.enums.ImageMethodEnum;
import com.hex.aicreator.enums.SseMessageTypeEnum;
import com.hex.aicreator.model.state.ArticleState;
import com.hex.aicreator.utils.CosService;
import com.hex.aicreator.utils.GsonUtils;
import com.hex.aicreator.service.ImageSearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
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
    private ImageSearchService imageSearchService;
    @Resource
    private CosService cosService;

    /**
     * 执行完整的文章生成流程
     *
     * @param state         全局状态
     * @param streamHandler 流式输出处理器
     */
    public void generateArticleWorkFlow(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体1：生成标题
            log.info("智能体1：开始生成标题, taskId={}", state.getTaskId());
            agent1GenerateTitle(state);
            //accept(...)是抽象的消息消费(处理)逻辑,具体逻辑由调用方实现
            streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());

            // 智能体2：生成大纲（流式输出）
            log.info("智能体2：开始生成大纲, taskId={}", state.getTaskId());
            agent2GenerateOutline(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT2_COMPLETE.getValue());

            // 智能体3：生成正文（流式输出）
            log.info("智能体3：开始生成正文, taskId={}", state.getTaskId());
            agent3GenerateContent(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());

            // 智能体4：分析配图需求
            log.info("智能体4：开始分析配图需求, taskId={}", state.getTaskId());
            agent4AnalyzeImageRequirements(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());

            // 智能体5：生成配图
            log.info("智能体5：开始生成配图, taskId={}", state.getTaskId());
            agent5GenerateImages(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());

            // 图文合成：将配图插入正文
            log.info("开始图文合成, taskId={}", state.getTaskId());
            mergeImagesIntoContent(state);
            streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());

            log.info("文章生成完成, taskId={}", state.getTaskId());
        } catch (Exception e) {
            log.error("文章生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("文章生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 智能体1：生成标题
     */
    private void agent1GenerateTitle(ArticleState state) {
        String prompt = PromptTemplate.AGENT1_TITLE_PROMPT.replace("{topic}", state.getTopic());
        String content = call(prompt, ArticleState.TitleResult.class);
        ArticleState.TitleResult titleResult = parseJsonResponse(content, ArticleState.TitleResult.class, "标题");
        state.setTitle(titleResult);
        log.info("智能体1：标题生成成功, mainTitle={}", titleResult.getMainTitle());
    }


    /**
     * 智能体2：生成大纲（流式输出）
     */
    private void agent2GenerateOutline(ArticleState state, Consumer<String> streamHandler) {
        String prompt = PromptTemplate.AGENT2_OUTLINE_PROMPT.replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle());

        String content = stream(prompt, streamHandler, SseMessageTypeEnum.AGENT2_STREAMING);
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

        String content = stream(prompt, streamHandler, SseMessageTypeEnum.AGENT3_STREAMING);
        state.setContent(content);
        log.info("智能体3：正文生成成功, length={}", content.length());
    }

    /**
     * 智能体4：分析配图需求
     */
    private void agent4AnalyzeImageRequirements(ArticleState state) {
        String prompt = PromptTemplate.AGENT4_IMAGE_REQUIREMENTS_PROMPT.replace("{mainTitle}", state.getTitle()
                .getMainTitle()).replace("{content}", state.getContent());

        String content = call(prompt, new TypeToken<List<ArticleState.ImageRequirement>>() {
        }.getClass());
        List<ArticleState.ImageRequirement> imageRequirements = parseJsonListResponse(content, new TypeToken<>() {
        }, "配图需求");
        state.setImageRequirements(imageRequirements);
        log.info("智能体4：配图需求分析成功, count={}", imageRequirements.size());
    }

    /**
     * 智能体5：生成配图（串行执行）
     */
    private void agent5GenerateImages(ArticleState state, Consumer<String> streamHandler) {
        List<ArticleState.ImageResult> imageResults = new ArrayList<>();

        for (ArticleState.ImageRequirement requirement : state.getImageRequirements()) {
            log.info("智能体5：开始检索配图, position={}, keywords={}", requirement.getPosition(), requirement.getKeywords());

            // 调用图片检索服务
            String imageUrl = imageSearchService.searchImage(requirement.getKeywords());

            // 降级策略
            ImageMethodEnum method = imageSearchService.getMethod();
            if (imageUrl == null) {
                imageUrl = imageSearchService.getFallbackImage(requirement.getPosition());
                method = ImageMethodEnum.PICSUM;
                log.warn("智能体5：图片检索失败, 使用降级方案, position={}", requirement.getPosition());
            }

            // 使用图片直接 URL（MVP 阶段不上传到 COS，简化流程）
            String finalImageUrl = cosService.useDirectUrl(imageUrl);

            // 创建配图结果
            ArticleState.ImageResult imageResult = buildImageResult(requirement, finalImageUrl, method);
            imageResults.add(imageResult);

            // 推送单张配图完成消息
            String imageCompleteMessage = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix() + GsonUtils.toJson(imageResult);
            streamHandler.accept(imageCompleteMessage);

            log.info("智能体5：配图检索成功, position={}, method={}", requirement.getPosition(), method.getValue());
        }

        state.setImages(imageResults);
        log.info("智能体5：所有配图生成完成, count={}", imageResults.size());
    }

    /**
     * 图文合成：将配图插入正文对应位置
     */
    private void mergeImagesIntoContent(ArticleState state) {
        // 正文内容
        String content = state.getContent();
        List<ArticleState.ImageResult> images = state.getImages();
        // 没图片直接返回正文
        if (images == null || images.isEmpty()) {
            state.setFullContent(content);
            return;
        }

        StringBuilder fullContent = new StringBuilder();

        // "逐行"扫描正文，在章节标题后插入对应图片
        String[] lines = content.split("\n");
        for (String line : lines) {
            fullContent.append(line).append("\n");
            // 检查是否是章节标题（以 ## 开头）
            if (line.startsWith("## ")) {
                // 截取章节标题
                String sectionTitle = line.substring(3).trim();
                // 将图片插在章节后面
                insertImageAfterSection(fullContent, images, sectionTitle);
            }
        }
        state.setFullContent(fullContent.toString());
        log.info("图文融合完成, fullContentLength={}", fullContent.length());
    }


    // startregion辅助方法

    /**
     * 调用 LLM（非流式）
     */
    private String call(String prompt, Class clz) {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .enableThinking(true) // 开启思考
                .temperature(0.3)  // 更低的温度，更确定的输出
                .maxToken(1000) // 生成响应最多消耗token数
                .build();

        BeanOutputConverter outputConverter = new BeanOutputConverter<>(clz);
        String format = outputConverter.getFormat();
        ReactAgent agent = ReactAgent.builder()
                .name("content_generator")
                .model(chatModel)
                // 格式化输出
                .outputSchema(format)
                .chatOptions(options)
                .build();
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
    private String stream(String prompt, Consumer<String> streamHandler, SseMessageTypeEnum messageType) {
        StringBuilder contentBuilder = new StringBuilder();
        Flux<ChatResponse> streamResponse = chatModel.stream(new Prompt(new UserMessage(prompt)));

        streamResponse
                // 处理响应流中元素, 来一个拼接一个, 并通过streamHandler推送给前端
                .doOnNext(chatResponse -> {
                    String content = chatResponse.getResult().getOutput().getText();
                    if (content != null && !content.isEmpty()) {
                        contentBuilder.append(content);
                        streamHandler.accept(messageType.getStreamingPrefix() + content);
                    }
                })
                // 错误处理, 记录日志
                .doOnError(error -> log.error("LLM 流式调用失败, messageType={}", messageType, error))
                // 阻塞直到整个响应流结束, 以确保contentBuilder 中内容完整
                .blockLast();
        return contentBuilder.toString();
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
     * 构建配图结果
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
