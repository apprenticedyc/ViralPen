package com.hex.aicreator.agent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.hex.aicreator.agent.tools.ImageGenerationTool;
import com.hex.aicreator.model.state.ArticleState;
import com.hex.aicreator.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 图文合成 Agent
 * 将配图插入到正文的相应位置
 *
 * @author AI Passage Creator
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class Node6_ContentMerger implements NodeAction {

    public static final String INPUT_CONTENT = "content";
    public static final String INPUT_IMAGES = "images";
    public static final String OUTPUT_FULL_CONTENT = "fullContent";

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String content = state.value(INPUT_CONTENT).map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少正文内容参数"));

        @SuppressWarnings("unchecked")
        List<ArticleState.ImageResult> images = state.<List<ArticleState.ImageResult>>value(INPUT_IMAGES)
               .orElseGet(ArrayList::new);

        log.info("Node6_ContentMerger 开始执行: 正文长度={}, 图片数量={}", content.length(), images.size());

        String fullContent = mergeImagesIntoContent(content, images);

        log.info("Node6_ContentMerger 执行完成: 完整内容长度={}", fullContent.length());

        return Map.of(OUTPUT_FULL_CONTENT, fullContent);
    }

    /**
     * 将配图插入正文（使用占位符替换）
     */
    private String mergeImagesIntoContent(String content, List<ArticleState.ImageResult> images) {
        if (images == null || images.isEmpty()) {
            return content;
        }
        String fullContent = content;
        // 遍历所有配图，根据占位符替换为实际图片
        for (ArticleState.ImageResult image : images) {
            String placeholder = image.getPlaceholderId();
            log.info("处理图片: position={}, placeholderId={}, url={}", image.getPosition(), placeholder, image.getUrl());

            if (placeholder != null && !placeholder.isEmpty()) {
                String description = image.getDescription() != null ? image.getDescription() : "配图";
                String imageMarkdown = "![" + description + "](" + image.getUrl() + ")";

                if (fullContent.contains(placeholder)) {
                    fullContent = fullContent.replace(placeholder, imageMarkdown);
                    log.info("成功替换占位符: {} -> {}", placeholder, imageMarkdown.substring(0, Math.min(50, imageMarkdown.length())));
                } else {
                    log.warn("正文中未找到占位符: {}", placeholder);
                }
            } else {
                log.warn("图片 position={} 的 placeholderId 为空", image.getPosition());
            }
        }
        return fullContent;
    }
}
