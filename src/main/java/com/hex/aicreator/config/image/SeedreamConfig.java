package com.hex.aicreator.config.image;

import com.volcengine.ark.runtime.model.images.generation.ResponseFormat;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Seedream 文生图API调用参数配置
 * 配置文件中前缀为 "seedream"的属性会自动绑定到该类的字段上
 */
@Configuration
@ConfigurationProperties(prefix = "seedream")
@Data
public class SeedreamConfig {

    /**
     * API Key
     */
    private String apiKey;

    /**
     * 模型名称
     */
    private String model;

    /**
     * API基础URL
     */
    private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";

    /**
     * 输出图片格式
     */
    private String outputFormat = "png";

    /**
     * 生成图像的返回格式
     * Url: 返回图片URL地址
     * Base64: 返回图片Base64编码字符串
     */
    private String responseFormat = ResponseFormat.Url;
    /**
     * 是否关闭组图功能。
     - auto：自动判断模式，模型会根据用户提供的提示词自主判断是否返回组图以及组图包含的图片数量。
     - disabled：关闭组图功能，模型只会生成一张图。
     */
    private String sequentialImageGeneration = "disabled";

    /**
     * 是否开启流式输出模式
     * true：开启流式输出模式，模型会在生成每张图完成后立即返回该图的信息，而不是等待所有图片全部生成结束后再一次性返回所有信息。
     * false：非流式输出模式，等待所有图片全部生成结束后再一次性返回所有信息。
     */
    private boolean stream = false;

    /**
     * 是否在生成的图片中添加水印
     */
    private boolean watermark = false;
}
