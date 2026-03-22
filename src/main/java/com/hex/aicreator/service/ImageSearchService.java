package com.hex.aicreator.service;

import com.hex.aicreator.model.enums.ImageMethodEnum;
import com.hex.aicreator.model.dto.image.ImageData;
import com.hex.aicreator.model.dto.image.ImageRequest;

import static com.hex.aicreator.constant.ArticleConstant.PICSUM_URL_TEMPLATE;

/**
 * 图片服务接口
 * 抽象图片获取逻辑，便于扩展多种图片来源（如 Pexels、Unsplash、AI 生图等）
 *
 * 扩展新的图片服务时：
 * 1. 实现此接口
 * 2. 在 ImageMethodEnum 中添加对应的枚举值
 * 3. 添加对应的配置类（如需要）
 *
 */
public interface ImageSearchService {
    /**
     * 获取图片数据（用于统一上传到 COS）
     * 默认实现：根据服务类型选择合适的参数获取图片 URL，然后转换为 ImageData
     * 返回非URL格式的图片数据（如字节数据）的服务可以重写此方法以提高效率
     * @param request 图片请求对象
     * @return ImageData 对象，包含图片字节或 URL
     */
    default ImageData getImageData(ImageRequest request) {
        String url = getMethod().isAiGenerated() ? generateImageURL(request.getPrompt()) : searchImageURL(request.getKeywords());
        return ImageData.fromUrl(url);
    }

    /**
     * 根据关键词/提示词获取图片
     *
     * @param keywords 搜索关键词（图库检索）或生图提示词（AI 生图）
     * @return 图片 URL，获取失败返回 null
     */
    default String searchImageURL(String keywords) {
        return null;
    }

    /**
     * 根据提示词生成图片
     *
     * @param prompt 提示词
     * @return 图片 URL，获取失败返回 null
     */
    default String generateImageURL(String prompt) {
        return null;
    }

    /**
     * 获取图片服务类型
     *
     * @return 图片服务类型枚举
     */
    ImageMethodEnum getMethod();

    /**
     * 获取降级图片 URL
     *
     * @param position 位置序号（用于生成唯一的随机图片）
     * @return 降级图片 URL
     */
    default String getFallbackImageURL(int position) {
        return String.format(PICSUM_URL_TEMPLATE, position);
    }
}
