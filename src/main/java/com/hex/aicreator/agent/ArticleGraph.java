package com.hex.aicreator.agent;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.hex.aicreator.agent.context.MessageHandlerContext;
import com.hex.aicreator.agent.nodes.*;
import com.hex.aicreator.model.enums.SseMessageTypeEnum;
import com.hex.aicreator.model.state.ArticleState;
import com.hex.aicreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import com.hex.aicreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 文章智能体编排器
 * 使用 Spring AI Alibaba 的 StateGraph 编排多个 Agent
 */
@Service
@Slf4j
public class ArticleGraph {
    @Resource
    private Node1_TitleGenerator node1_titleGenerator;
    @Resource
    private Node2_OutlineGenerator node2_outlineGenerator;
    @Resource
    private Node3_ContentGenerator node3_contentGenerator;
    @Resource
    private Node4_ImageAnalyzer node4_imageAnalyzer;
    @Resource
    private Node5_ParallelImageGenerator node5_parallelImageGenerator;
    @Resource
    private Node6_ContentMerger node6_contentMerger;

    // region 状态键常量

    private static final String KEY_TASK_ID = "taskId";
    private static final String KEY_TOPIC = "topic";
    private static final String KEY_USER_DESCRIPTION = "userDescription";
    private static final String KEY_MAIN_TITLE = "mainTitle";
    private static final String KEY_SUB_TITLE = "subTitle";
    private static final String KEY_TITLE_OPTIONS = "titleOptions";
    private static final String KEY_OUTLINE = "outline";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_CONTENT_WITH_PLACEHOLDERS = "contentWithPlaceholders";
    private static final String KEY_IMAGE_REQUIREMENTS = "imageRequirements";
    private static final String KEY_IMAGES = "images";
    private static final String KEY_FULL_CONTENT = "fullContent";
    private static final String KEY_ENABLED_IMAGE_METHODS = "enabledImageMethods";

    // endregion

    /**
     * 阶段1：生成标题方案
     */
    public void executePhase1(ArticleState state, Consumer<String> streamHandler) {
        log.info("阶段1（多智能体编排）：开始生成标题方案, taskId={}", state.getTaskId());
        try {
            // 1. 构建初始状态
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(KEY_TASK_ID, state.getTaskId());
            inputs.put(KEY_TOPIC, state.getTopic());

            // 2. 编译图并执行
            StateGraph graph = buildPhase1Graph();
            CompiledGraph compiledGraph = graph.compile();
            Optional<OverAllState> result = compiledGraph.invoke(inputs);

            if (result.isPresent()) {
                OverAllState finalState = result.get();
                @SuppressWarnings("unchecked") List<ArticleState.TitleOption> titleOptions = finalState.value(KEY_TITLE_OPTIONS)
                        .map(v -> (List<ArticleState.TitleOption>) v).orElse(null);

                if (titleOptions != null) {
                    state.setTitleOptions(titleOptions);
                    streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
                    log.info("阶段1（多智能体编排）：标题方案生成完成, 数量={}", titleOptions.size());
                } else {
                    throw new RuntimeException("标题方案生成失败：结果为空");
                }
            } else {
                throw new RuntimeException("标题方案生成失败：执行结果为空");
            }
        } catch (Exception e) {
            log.error("阶段1（graph）：标题方案生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("标题方案生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 阶段2：生成大纲
     */
    public void executePhase2(ArticleState state, Consumer<String> streamHandler) {
        log.info("阶段2（多智能体编排）：开始生成大纲, taskId={}", state.getTaskId());

        // 设置流式处理器到 ThreadLocal
        MessageHandlerContext.set(streamHandler);

        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(KEY_TASK_ID, state.getTaskId());
            inputs.put(KEY_MAIN_TITLE, state.getTitle().getMainTitle());
            inputs.put(KEY_SUB_TITLE, state.getTitle().getSubTitle());
            inputs.put(KEY_USER_DESCRIPTION, state.getUserDescription());

            StateGraph graph = buildPhase2Graph();
            CompiledGraph compiledGraph = graph.compile();

            Optional<OverAllState> result = compiledGraph.invoke(inputs);

            if (result.isPresent()) {
                OverAllState finalState = result.get();
                ArticleState.OutlineResult outline = finalState.<ArticleState.OutlineResult>value(KEY_OUTLINE)
                        .orElse(null);
                if (outline != null) {
                    state.setOutline(outline);
                    streamHandler.accept(SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
                    log.info("阶段2（多智能体编排）：大纲生成完成, 章节数={}", outline.getSections().size());
                } else {
                    throw new RuntimeException("大纲生成失败：结果为空");
                }
            } else {
                throw new RuntimeException("大纲生成失败：执行结果为空");
            }

        } catch (Exception e) {
            log.error("阶段2（多智能体编排）：大纲生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("大纲生成失败: " + e.getMessage(), e);
        } finally {
            // 清理 ThreadLocal
            MessageHandlerContext.clear();
        }
    }

    /**
     * 阶段3：生成正文+配图
     */
    public void executePhase3(ArticleState state, Consumer<String> streamHandler) {
        log.info("阶段3（多智能体编排）：开始生成正文+配图, taskId={}", state.getTaskId());

        // 设置流式处理器到 ThreadLocal
        MessageHandlerContext.set(streamHandler);

        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(KEY_TASK_ID, state.getTaskId());
            inputs.put(KEY_MAIN_TITLE, state.getTitle().getMainTitle());
            inputs.put(KEY_SUB_TITLE, state.getTitle().getSubTitle());
            inputs.put(KEY_OUTLINE, state.getOutline());
            inputs.put(KEY_ENABLED_IMAGE_METHODS, state.getEnabledImageMethods());

            StateGraph graph = buildPhase3Graph();
            CompiledGraph compiledGraph = graph.compile();
            Optional<OverAllState> result = compiledGraph.invoke(inputs);

            if (result.isPresent()) {
                OverAllState finalState = result.get();

                // 提取四个节点属性
                String content = finalState.value(KEY_CONTENT).map(Object::toString).orElse(null);
                String contentWithPlaceholders = finalState.value(KEY_CONTENT_WITH_PLACEHOLDERS).map(Object::toString)
                        .orElse(null);

                Object imageRequirementsObj = finalState.value(KEY_IMAGE_REQUIREMENTS).orElse(null);
                List<ArticleState.ImageRequirement> imageRequirements = null;
                if (imageRequirementsObj != null) {
                    String json = GsonUtils.toJson(imageRequirementsObj);
                    imageRequirements = GsonUtils.fromJson(json, new TypeToken<List<ArticleState.ImageRequirement>>() {
                    }.getType());
                }

                Object imagesObj = finalState.value(KEY_IMAGES).orElse(null);
                List<ArticleState.ImageResult> images = null;
                if (imagesObj != null) {
                    String json = GsonUtils.toJson(imagesObj);
                    images = GsonUtils.fromJson(json, new TypeToken<List<ArticleState.ImageResult>>() {
                    }.getType());
                }
                String fullContent = finalState.value(KEY_FULL_CONTENT).map(Object::toString).orElse(null);

                // 更新状态
                if (content != null) {
                    state.setContent(content);
                    streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());
                }
                if (contentWithPlaceholders != null && imageRequirements != null) {
                    state.setContent(contentWithPlaceholders);
                    state.setImageRequirements(imageRequirements);
                    streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());
                }
                if (images != null) {
                    state.setImages(images);
                    streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());
                }
                if (fullContent != null) {
                    state.setFullContent(fullContent);
                    streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());
                }
                log.info("阶段3（多智能体编排）：正文+配图生成完成, 正文长度={}, 图片数={}", contentWithPlaceholders != null ? contentWithPlaceholders.length() : 0, images != null ? images.size() : 0);
            } else {
                throw new RuntimeException("正文+配图生成失败：执行结果为空");
            }

        } catch (Exception e) {
            log.error("阶段3（多智能体编排）：正文+配图生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("正文+配图生成失败: " + e.getMessage(), e);
        } finally {
            MessageHandlerContext.clear();
        }
    }

    // region 构建图

    /**
     * 构建阶段1图：标题生成
     */
    private StateGraph buildPhase1Graph() throws GraphStateException {
        return new StateGraph().addNode("title_generator", node_async(node1_titleGenerator))
                .addEdge(START, "title_generator").addEdge("title_generator", END);
    }

    /**
     * 构建阶段2图：大纲生成
     */
    private StateGraph buildPhase2Graph() throws GraphStateException {
        return new StateGraph().addNode("outline_generator", node_async(node2_outlineGenerator))
                .addEdge(START, "outline_generator").addEdge("outline_generator", END);
    }

    /**
     * 构建阶段3图：正文+配图生成（顺序执行）
     * 流程：正文生成 -> 配图需求分析 -> 并行配图生成 -> 图文合成
     */
    private StateGraph buildPhase3Graph() throws GraphStateException {
        return new StateGraph()
                // 节点定义
                .addNode("content_generator", node_async(node3_contentGenerator))
                .addNode("image_analyzer", node_async(node4_imageAnalyzer))
                .addNode("parallel_image_generator", node_async(node5_parallelImageGenerator))
                .addNode("content_merger", node_async(node6_contentMerger))
                // 边定义：顺序执行
                .addEdge(START, "content_generator").addEdge("content_generator", "image_analyzer")
                .addEdge("image_analyzer", "parallel_image_generator")
                .addEdge("parallel_image_generator", "content_merger").addEdge("content_merger", END);
    }
    // endregion
}
