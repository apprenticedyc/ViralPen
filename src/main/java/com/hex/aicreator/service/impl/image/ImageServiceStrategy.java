package com.hex.aicreator.service.impl.image;

import com.hex.aicreator.model.enums.ImageMethodEnum;
import com.hex.aicreator.model.dto.image.ImageData;
import com.hex.aicreator.model.dto.image.ImageRequest;
import com.hex.aicreator.service.ImageSearchService;
import com.hex.aicreator.utils.CosService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 图片服务策略选择器
 * 根据图片来源类型选择对应的图片服务实现
 *
 * 设计说明：
 * - 自动注册所有 ImageSearchService 实现
 * - 根据 ImageMethodEnum 的元数据自动选择正确的参数
 * - 支持服务可用性检查和自动降级
 * - 统一处理图片上传到 COS
 *
 */
@Service
@Slf4j
public class ImageServiceStrategy {

    @Resource
    private List<ImageSearchService> imageSearchServices;

    @Resource
    private CosService cosService;

    /**
     * 图片服务映射：ImageMethodEnum -> ImageSearchService
     */
    private final Map<ImageMethodEnum, ImageSearchService> serviceMap = new EnumMap<>(ImageMethodEnum.class);

    @PostConstruct
    public void init() {
        // 将所有 ImageSearchService 实现注册到映射表
        for (ImageSearchService service : imageSearchServices) {
            ImageMethodEnum method = service.getMethod();
            serviceMap.put(method, service);
        }
    }

    /**
     * 获取图片并上传到 COS（推荐方法）
     * 统一处理所有图片来源的上传逻辑
     *
     * @param imageSource 图片来源
     * @param request     图片请求对象
     * @return 图片获取结果（包含 COS URL）
     */
    public ImageResult getImageAndUpload(String imageSource, ImageRequest request) {
        ImageMethodEnum method = getImageMethodEnum(imageSource);
        ImageSearchService service = serviceMap.get(method);
        try {
            // 1. 最核心的一步: 获取图片数据, 不管是哪个服务, 都返回统一的 ImageData 对象
            ImageData imageData = service.getImageData(request);
            if (imageData == null || !imageData.isValid()) {
                log.warn("图片数据获取失败, 使用降级方案, method={}", method);
                return handleFallbackWithUpload(request.getPosition());
            }

            // 2. 上传到 COS
            String folder = getCOSFolder(method);
            String cosUrl = cosService.uploadImageData(imageData, folder);
            if (cosUrl != null && !cosUrl.isEmpty()) {
                log.info("图片获取并上传成功, method={}, cosUrl={}", method, cosUrl);
                return new ImageResult(cosUrl, method);
            } else {
                log.warn("图片上传 COS 失败, 使用降级方案, method={}", method);
                return handleFallbackWithUpload(request.getPosition());
            }
        } catch (Exception e) {
            log.error("获取图片并上传异常, method={}", method, e);
            return handleFallbackWithUpload(request.getPosition());
        }
    }

    /**
     * 根据图片方法获取 COS 文件夹
     */
    private String getCOSFolder(ImageMethodEnum method) {
        return switch (method) {
            case PEXELS -> "pexels";
            case SEEDREAM -> "seedream";
            case EMOJI_PACK -> "emoji-pack";
            case PICSUM -> "picsum";
        };
    }

    /**
     * 解析图片来源
     */
    private ImageMethodEnum getImageMethodEnum(String imageSource) {
        ImageMethodEnum method = ImageMethodEnum.getByValue(imageSource);
        if (method == null) {
            log.warn("未知的图片来源: {}, 默认使用 {}", imageSource, ImageMethodEnum.getDefaultSearchMethod());
            return ImageMethodEnum.getDefaultSearchMethod();
        }
        return method;
    }

    /**
     * 处理降级逻辑（含上传）
     */
    private ImageResult handleFallbackWithUpload(Integer position) {
        int pos = position != null ? position : 1;
        String fallbackUrl = getFallbackImage(pos);

        // 将降级图片也上传到 COS
        ImageData fallbackData = ImageData.fromUrl(fallbackUrl);
        String cosUrl = cosService.uploadImageData(fallbackData, "fallback");

        // 如果上传失败，直接使用原始 URL
        String finalUrl = (cosUrl != null && !cosUrl.isEmpty()) ? cosUrl : fallbackUrl;
        return new ImageResult(finalUrl, ImageMethodEnum.getFallbackMethod());
    }

    /**
     * 获取指定方法的图片服务
     */
    public ImageSearchService getService(ImageMethodEnum method) {
        return serviceMap.get(method);
    }

    /**
     * 获取降级图片
     */
    public String getFallbackImage(int position) {
        ImageSearchService defaultService = serviceMap.get(ImageMethodEnum.getDefaultSearchMethod());
        if (defaultService != null) {
            return defaultService.getFallbackImageURL(position);
        }
        // position只是用来确保每次随机图片不同防止一篇文都是一张图
        return String.format("https://picsum.photos/800/600?random=%d", position);
    }

    /**
     * 获取所有已注册的图片服务类型
     */
    public List<ImageMethodEnum> getRegisteredMethods() {
        return List.copyOf(serviceMap.keySet());
    }

    /**
     * 图片获取结果
     */
    public static class ImageResult {
        private final String url;
        private final ImageMethodEnum method;

        public ImageResult(String url, ImageMethodEnum method) {
            this.url = url;
            this.method = method;
        }

        public String getUrl() {
            return url;
        }

        public ImageMethodEnum getMethod() {
            return method;
        }

        public boolean isSuccess() {
            return url != null && !url.isEmpty();
        }
    }
}
