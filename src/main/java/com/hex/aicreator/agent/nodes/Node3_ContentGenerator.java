package com.hex.aicreator.agent.nodes;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.hex.aicreator.agent.context.MessageHandlerContext;
import com.hex.aicreator.agent.prompt.PromptTemplate;
import com.hex.aicreator.model.enums.SseMessageTypeEnum;
import com.hex.aicreator.model.state.ArticleState;
import com.hex.aicreator.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 正文生成 Agent
 * 根据大纲生成文章正文内容（支持流式输出）
 *
 * @author AI Passage Creator
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class Node3_ContentGenerator implements NodeAction {

    private final DashScopeChatModel chatModel;


    public static final String INPUT_MAIN_TITLE = "mainTitle";
    public static final String INPUT_SUB_TITLE = "subTitle";
    public static final String INPUT_OUTLINE = "outline";
    public static final String OUTPUT_CONTENT = "content";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String mainTitle = state.value(INPUT_MAIN_TITLE)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少主标题参数"));
        
        String subTitle = state.value(INPUT_SUB_TITLE)
                .map(Object::toString)
                .orElse("");

        // 从全局状态中获取大纲参数
        @SuppressWarnings("unchecked")
        ArticleState.OutlineResult outline = state.value(INPUT_OUTLINE)
                .map(v -> {
                    if (v instanceof ArticleState.OutlineResult) {
                        return (ArticleState.OutlineResult) v;
                    }
                    return GsonUtils.fromJson(GsonUtils.toJson(v), ArticleState.OutlineResult.class);
                })
                .orElseThrow(() -> new IllegalArgumentException("缺少大纲参数"));
        log.info("Node3_ContentGenerator 开始执行: mainTitle={}", mainTitle);
        
        // 构建 prompt
        String outlineText = GsonUtils.toJson(outline.getSections());
        String prompt = PromptTemplate.AGENT3_CONTENT_PROMPT
                .replace("{mainTitle}", mainTitle)
                .replace("{subTitle}", subTitle)
                .replace("{outline}", outlineText);

        // 获取流式处理器
        Consumer<String> messageHandler = MessageHandlerContext.get();
        
        // 调用 LLM（流式输出）
        String content = stream(prompt, messageHandler, String.class);
        
        log.info("Node3_ContentGenerator 执行完成: 正文长度={}", content.length());
        
        return Map.of(OUTPUT_CONTENT, content);
    }

    /**
     * 调用 LLM（流式输出）
     */
    private String stream(String prompt, Consumer<String> messageHandler, Class clz) {
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
                // 处理响应流中元素, 来一个拼接一个, 并通过messageHandler推送给前端
                .doOnNext(output -> {
                    if (output instanceof StreamingOutput streamingOutput) {
                        OutputType type = streamingOutput.getOutputType();
                        // 处理模型推理的流式输出
                        if (type == OutputType.AGENT_MODEL_STREAMING) {
                            String text = streamingOutput.message().getText();
                            contentBuilder.append(text);
                            // 带前缀发送流式消息
                            messageHandler.accept(SseMessageTypeEnum.AGENT3_STREAMING.getStreamingPrefix() + text);
                        } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                            log.info("\n模型流式输出完成");
                        }
                    }
                })
                // 错误处理, 记录日志
                .doOnError(error -> log.error("Node3_ContentGenerator 流式调用失败", error))
                // 阻塞直到整个响应流结束, 以确保contentBuilder 中内容完整
                .blockLast();
        return contentBuilder.toString();
    }
}
