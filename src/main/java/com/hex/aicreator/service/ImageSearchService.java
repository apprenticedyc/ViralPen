package com.hex.aicreator.service;

import com.hex.aicreator.enums.ImageMethodEnum;

/**
 * 图片检索服务接口
 * 抽象图片检索逻辑，便于扩展多种图片来源（如 Pexels、Unsplash、AI 生图等）
 *
 */
public interface ImageSearchService {

    /**
     * 根据关键词检索图片
     *
     * @param keywords 搜索关键词
     * @return 图片 URL，检索失败返回 null
     */
    String searchImage(String keywords);

    /**
     * 获取图片检索方式
     *
     * @return 图片检索方式枚举
     */
    ImageMethodEnum getMethod();

    /**
     * 获取降级图片 URL
     *
     * @param position 位置序号（用于生成唯一的随机图片）
     * @return 降级图片 URL
     */
    String getFallbackImage(int position);
}
