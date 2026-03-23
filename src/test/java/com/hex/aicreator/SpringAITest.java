package com.hex.aicreator;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.hex.aicreator.model.state.ArticleState;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

@SpringBootTest
public class SpringAITest {

    @Resource
    private DashScopeChatModel chatModel;

    @Test
    public void testChat() throws InterruptedException {
        // 创建带有特定选项的 Prompt
        DashScopeChatOptions runtimeOptions_1 = DashScopeChatOptions.builder()
                .model("MiniMax-M2.1")
                .enableThinking(true) // 开启思考
//                .enableSearch(true) // 开启搜索
                .temperature(0.3)  // 更低的温度，更确定的输出
                .maxToken(500) // 生成响应最多消耗token数
                .build();

        Prompt prompt = new Prompt(
                new UserMessage("介绍下你自己,你是啥模型"),
                runtimeOptions_1
        );
        ChatResponse response = chatModel.call(prompt);
        System.out.println(response);

        DashScopeChatOptions runtimeOptions_2 = DashScopeChatOptions.builder().enableThinking(true)
                .model("MiniMax-M2.1").temperature(0.3)  // 更低的温度，更确定的输出
                .maxToken(500).build();
        Prompt prompt2 = new Prompt(new UserMessage("你是啥模型"), runtimeOptions_2);
        // 流式调用
        Flux<ChatResponse> stream = chatModel.stream(prompt2);
        stream.subscribe(
                // 对返回流式内容做处理
                chunk -> {
                    String content = chunk.getResult().getOutput().getText();
                    System.out.print(content);
                }
                // 产生错误回调
                , error -> System.err.println("错误: " + error.getMessage())
                // 整个响应流结束后回调
                , () -> System.out.println(" 流式响应完成"));

        // 等待流式响应完成（subscribe异步对流处理，需要阻塞主线程不然直接退出了）
        Thread.sleep(10000);  // 等待10秒，根据实际响应时间调整
    }

    @Test
    public void testStructedOutput() throws GraphRunnerException {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .enableThinking(true) // 有些模型必须强制手动开启思考模式
                .model("tongyi-xiaomi-analysis-flash") // 指定模型型号
                .temperature(0.3) // 温度决定输出随机性
                .maxToken(2000) // 生成响应最大消耗的token
                .build();

        // 创建 DashScope API 实例
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey("sk-7220ede328d14663b123ce3ccf7d54ac")
                .build();

        ChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(options) // 将定义的选项作为默认配置
                .build();

        // 使用BeanOutputConverter生成输出结构
        BeanOutputConverter<ArticleState.TitleOption> outputConverter = new BeanOutputConverter<>(ArticleState.TitleOption.class);
        String format = outputConverter.getFormat();
        ReactAgent agent = ReactAgent.builder().name("contact_extractor").model(chatModel).outputSchema(format).build();

        AssistantMessage result = agent.call("从以下信息提取标题：新华全媒头条丨守护“城市奔跑者”奋斗路——全方位夯实新就业群体权益保障根基");

        System.out.println(result.getText());

    }

    @Test
    public void testStream() throws GraphRunnerException {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .enableThinking(true) // 有些模型必须强制手动开启思考模式
                .model("tongyi-xiaomi-analysis-flash") // 指定模型型号
                .temperature(0.3) // 温度决定输出随机性
                .maxToken(2000) // 生成响应最大消耗的token
                .build();

        // 创建 DashScope API 实例
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey("sk-7220ede328d14663b123ce3ccf7d54ac")
                .build();

        ChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(options) // 将定义的选项作为默认配置
                .build();

        ReactAgent agent = ReactAgent.builder()
                .name("streaming_agent")
                .model(chatModel)
                .build();

        Flux<NodeOutput> stream = agent.stream("帮我写一首关于春天的诗");

        stream.doOnNext(
                output -> {
                    if (output instanceof StreamingOutput streamingOutput) {
                        OutputType type = streamingOutput.getOutputType();

                        // 处理模型推理的流式输出
                        if (type == OutputType.AGENT_MODEL_STREAMING) {
                            // 流式增量内容，逐步显示
                            System.out.print(streamingOutput.message().getText());
                        } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                            // 模型推理完成，可获取完整响应
                            System.out.println("\n模型输出完成");
                        }
                    }
                }
        ).blockLast();

    }
}
