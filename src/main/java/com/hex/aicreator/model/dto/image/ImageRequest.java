package com.hex.aicreator.model.dto.image;

import lombok.Builder;
import lombok.Data;

/**
 * 图片请求对象
 * 统一封装图片获取所需的各种参数，便于扩展
 *
 */
@Data
@Builder
public class ImageRequest {

    /**
     * 搜索关键词（用于图库检索）
     */
    private String keywords;

    /**
     * 生图提示词（用于 AI 生图）
     */
    private String prompt;

    /**
     * 图片位置序号
     */
    private Integer position;

    /**
     * 图片类型（cover/section）
     */
    private String type;

    /**
     * 宽高比（如 16:9, 1:1）
     */
    private String aspectRatio;
}