package com.hex.aicreator.agent.nodes;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.google.gson.reflect.TypeToken;
import com.hex.aicreator.agent.prompt.PromptTemplate;
import com.hex.aicreator.model.state.ArticleState;
import com.hex.aicreator.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 标题生成 Agent
 * 根据选题生成 3-5 个爆款标题方案
 *
 * @author AI Passage Creator
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class Node1_TitleGenerator implements NodeAction {

    private final DashScopeChatModel chatModel;

    // 输入输出常量
    public static final String INPUT_TOPIC = "topic";
    public static final String OUTPUT_TITLE_OPTIONS = "titleOptions";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String topic = state.value(INPUT_TOPIC)
                .map(Object::toString)
                // 没取到值就抛出异常
                .orElseThrow(() -> new IllegalArgumentException("缺少选题参数"));
        
        // 构建 prompt
        String prompt = PromptTemplate.AGENT1_TITLE_PROMPT
                .replace("{topic}", topic);
        // 调用 LLM
        String content = call(prompt, new TypeToken<List<ArticleState.TitleOption>>() {
        }.getClass());
        
        // 解析结果
        List<ArticleState.TitleOption> titleOptions = GsonUtils.fromJson(
                content,  new TypeToken<List<ArticleState.TitleOption>>() {
                }.getType()
        );
        log.info("Node1_TitleGenerator 执行完成: 生成了 {} 个标题方案", titleOptions.size());
        return Map.of(OUTPUT_TITLE_OPTIONS, titleOptions);
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
