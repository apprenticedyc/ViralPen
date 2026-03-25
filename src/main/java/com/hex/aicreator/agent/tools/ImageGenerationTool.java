package com.hex.aicreator.agent.tools;

import com.hex.aicreator.model.dto.image.ImageRequest;
import com.hex.aicreator.model.enums.ImageMethodEnum;
import com.hex.aicreator.service.impl.image.ImageServiceStrategy;
import com.hex.aicreator.utils.CosService;
import com.hex.aicreator.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * 图片生成工具
 * 封装 ImageServiceStrategy，供 Agent 调用
 */
@Component
@Slf4j
public class ImageGenerationTool {

    @Resource
    private ImageServiceStrategy imageServiceStrategy;

    @Resource
    private CosService cosService;

    /**
     * 根据需求生成或搜索图片
     */
    @Tool(description = "根据需求生成或搜索图片。支持多种图片来源")
    public String generateImage(@ToolParam(description = "图片来源类型") String imageSource,
                                @ToolParam(description = "搜索关键词") String keywords,
                                @ToolParam(description = "AI 生图提示词或图表代码") String prompt,
                                @ToolParam(description = "图片位置序号") Integer position,
                                @ToolParam(description = "图片类型：cover 或 section") String type,
                                @ToolParam(description = "对应的章节标题") String sectionTitle) {

        log.info("ImageGenerationTool 开始执行: imageSource={}, position={}", imageSource, position);

        try {
            ImageRequest imageRequest = ImageRequest.builder().keywords(keywords).prompt(prompt).position(position)
                    .type(type).build();

            // 使用统一上传到 COS 的方法
            ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(imageSource, imageRequest);
            String cosUrl = result.getUrl();
            ImageMethodEnum method = result.getMethod();

            ImageGenerationResult generationResult = ImageGenerationResult.builder().position(position).url(cosUrl)
                    .method(method.getValue()).keywords(keywords).sectionTitle(sectionTitle).description(type)
                    .success(true).build();
            log.info("ImageGenerationTool 执行成功: position={}, method={}", position, method.getValue());
            return GsonUtils.toJson(generationResult);

        } catch (Exception e) {
            log.error("ImageGenerationTool 执行失败: imageSource={}, position={}", imageSource, position, e);
            ImageGenerationResult failResult = ImageGenerationResult.builder().position(position).success(false)
                    .error(e.getMessage()).sectionTitle(sectionTitle).description(type).build();
            return GsonUtils.toJson(failResult);
        }
    }

    /**
     * 直接生成图片（不通过 Agent，供内部调用）
     */
    public ImageGenerationResult generateImage(String imageSource, String keywords, String prompt,
                                               Integer position, String type, String sectionTitle,
                                               String placeholderId) {
        try {
            ImageRequest imageRequest = ImageRequest.builder().keywords(keywords).prompt(prompt).position(position)
                    .type(type).build();

            ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(imageSource, imageRequest);
            String cosUrl = result.getUrl();
            ImageMethodEnum method = result.getMethod();

            ImageGenerationResult generationResult = ImageGenerationResult.builder().position(position).url(cosUrl)
                    .method(method.getValue()).keywords(keywords).sectionTitle(sectionTitle).description(type)
                    .placeholderId(placeholderId).success(true).build();
            return generationResult;

        } catch (Exception e) {
            log.error("图片生成失败: imageSource={}, position={}", imageSource, position, e);

            ImageGenerationResult failResult = ImageGenerationResult.builder().position(position).success(false)
                    .error(e.getMessage()).sectionTitle(sectionTitle).placeholderId(placeholderId).build();
            return failResult;
        }
    }

    /**
     * 图片生成结果
     */
    @Builder
    @Data
    public static class ImageGenerationResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer position;
        private String url;
        private String method;
        private String keywords;
        private String sectionTitle;
        private String description;
        private String placeholderId;
        private boolean success;
        private String error;
    }
}
