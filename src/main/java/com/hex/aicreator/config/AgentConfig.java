package com.hex.aicreator.config;

import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class AgentConfig {

    /**
     * 是否启用多智能体编排器
     * true: 使用新的 Spring AI Alibaba 多智能体编排
     * false: 使用原有的 ArticleAgentService
     */
    @Value("${article.agent.useGraph}")
    private boolean useGraph;

    /**
     * Agent 最大迭代次数
     */
    @Value("${article.agent.max-iterations}")
    private int maxIterations;

    /**
     * 提供内存状态保存器（单例）
     * 用于 Agent 对话记忆管理
     */
    @Bean
    public MemorySaver memorySaver() {
        return new MemorySaver();
    }
}
