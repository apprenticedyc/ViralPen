package com.hex.aicreator.service.impl.image;

import com.hex.aicreator.config.image.SeedreamConfig;
import com.hex.aicreator.model.enums.ImageMethodEnum;
import com.hex.aicreator.service.ImageSearchService;
import com.volcengine.ark.runtime.model.images.generation.GenerateImagesRequest;
import com.volcengine.ark.runtime.model.images.generation.ImagesResponse;
import com.volcengine.ark.runtime.service.ArkService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Seedream AI图片生成服务
 *
 */
@Service
@Slf4j
public class SeedreamService implements ImageSearchService {

    @Resource
    private SeedreamConfig seedreamConfig;

    /**
     * 调用问生图 API, 返回图片URL
     */
    @Override
    public String generateImageURL(String prompt) {
        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        // 1. 构建 ArkService 实例，设置基础URL、调度器、连接池和API Key等参数。
        ArkService service = ArkService.builder().baseUrl(seedreamConfig.getBaseUrl())
                .dispatcher(dispatcher).connectionPool(connectionPool)
                .apiKey(seedreamConfig.getApiKey())
                .build();

        // 2. 构建生成图像请求对象，设置相关参数。
        GenerateImagesRequest generateRequest = GenerateImagesRequest.builder()
                .model(seedreamConfig.getModel()) // 指定使用的模型名称，必填项。
                .prompt(prompt) // 提示词, 必填
                .sequentialImageGeneration(seedreamConfig.getSequentialImageGeneration()) // 控制是否关闭组图功能。
                .outputFormat(seedreamConfig.getOutputFormat()) // 指定生成图像的文件格式。
                .responseFormat(seedreamConfig.getResponseFormat()) // 指定生成图像的返回格式
                .stream(seedreamConfig.isStream()) // 控制是否开启流式输出模式
                .watermark(seedreamConfig.isWatermark()) // 是否添加水印
                .build();
        ImagesResponse imagesResponse = service.generateImages(generateRequest);
        service.shutdownExecutor();

        // 取组图的第一张图返回
        return imagesResponse.getData().get(0).getUrl();
    }

    @Override
    public ImageMethodEnum getMethod() {
        return ImageMethodEnum.SEEDREAM;
    }
}
