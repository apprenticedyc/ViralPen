package com.hex.aicreator.agent.nodes;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.hex.aicreator.agent.prompt.PromptTemplate;
import com.hex.aicreator.model.state.ArticleState;
import com.hex.aicreator.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 配图需求分析 Agent
 * 分析文章内容，生成配图需求列表
 *
 * @author AI Passage Creator
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class Node4_ImageAnalyzer implements NodeAction {

    private final DashScopeChatModel chatModel;

    public static final String INPUT_MAIN_TITLE = "mainTitle";
    public static final String INPUT_CONTENT = "content";
    public static final String INPUT_ENABLED_IMAGE_METHODS = "enabledImageMethods";
    public static final String OUTPUT_CONTENT_WITH_PLACEHOLDERS = "contentWithPlaceholders";
    public static final String OUTPUT_IMAGE_REQUIREMENTS = "imageRequirements";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String mainTitle = state.value(INPUT_MAIN_TITLE).map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少主标题参数"));

        String content = state.value(INPUT_CONTENT).map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少正文内容参数"));

        @SuppressWarnings("unchecked") List<String> enabledMethods = state.value(INPUT_ENABLED_IMAGE_METHODS).map(v -> {
            if (v instanceof List) {
                return (List<String>) v;
            }
            return null;
        }).orElse(null);

        log.info("Node4_ImageAnalyzer 开始执行: mainTitle={}, enabledMethods={}", mainTitle, enabledMethods);

        // 构建 prompt
        String prompt = PromptTemplate.AGENT4_IMAGE_REQUIREMENTS_PROMPT.replace("{mainTitle}", mainTitle)
                .replace("{content}", content);

        // 调用 LLM
        String response = call(prompt, ArticleState.Agent4Result.class);

        // 解析结果（新格式：包含 contentWithPlaceholders 和 imageRequirements）
        ArticleState.Agent4Result agent4Result = GsonUtils.fromJson(response, ArticleState.Agent4Result.class);

        log.info("Node4_ImageAnalyzer 执行完成: 配图需求数量={}, 已在正文中插入占位符", agent4Result.getImageRequirements()
                .size());

        // 返回结果：contentWithPlaceholders、content（更新为包含占位符）、imageRequirements
        return Map.of(OUTPUT_CONTENT_WITH_PLACEHOLDERS, agent4Result.getContentWithPlaceholders(), INPUT_CONTENT, agent4Result.getContentWithPlaceholders(), // 更新 content 为包含占位符的版本，传给下游节点
                OUTPUT_IMAGE_REQUIREMENTS, agent4Result.getImageRequirements());
    }

    /**
     * 调用 LLM（非流式）结构化输出
     */
    private String call(String prompt, Class clz) {
        DashScopeChatOptions options = DashScopeChatOptions.builder().enableThinking(true) // 开启思考
                .temperature(0.3)  // 更低的温度，更确定的输出
                .maxToken(5000) // 生成响应最多消耗token数
                .build();

        ReactAgent agent = ReactAgent.builder().name("title_generator").model(chatModel) // 指定LLM
                .outputType(clz) // 格式化输出
                .chatOptions(options).build();
        AssistantMessage response;
        try {
            response = agent.call(new UserMessage(prompt));
        } catch (GraphRunnerException e) {
            log.error("LLM 调用失败, prompt={}", prompt, e);
            throw new RuntimeException("TitleGeneratorAgent调用失败" + e.getMessage(), e);
        }
        return response.getText();
    }
}
