package com.hex.aicreator.aiImageGenerate;

import com.volcengine.ark.runtime.model.images.generation.GenerateImagesRequest;
import com.volcengine.ark.runtime.model.images.generation.ImagesResponse;
import com.volcengine.ark.runtime.model.images.generation.ResponseFormat;
import com.volcengine.ark.runtime.service.ArkService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.util.concurrent.TimeUnit;

/***
 * 参考API文档: https://www.volcengine.com/docs/82379/1541523?lang=zh
 */
public class ImageGenerationsExample {
    public static void main(String[] args) {
        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        ArkService service = ArkService.builder()
                .baseUrl("https://ark.cn-beijing.volces.com/api/v3")
                .dispatcher(dispatcher).connectionPool(connectionPool).apiKey("24b0a80b-5f53-43d7-a3f6-7c25336b5a42")
                .build();
        // 构建生成图像请求对象，设置相关参数。
        GenerateImagesRequest generateRequest = GenerateImagesRequest.builder()
                .model("doubao-seedream-5-0-260128") // 指定使用的模型名称，必填项。
                .prompt("卡通版洞主")
                .sequentialImageGeneration("disabled") // 控制是否关闭组图功能。
                .outputFormat("png") // 指定生成图像的文件格式。
                .responseFormat(ResponseFormat.Url) // 指定生成图像的返回格式 url或base64
                .stream(false) // 控制是否开启流式输出模式，非流式输出模式，等待所有图片全部生成结束后再一次性返回所有信息。
                .watermark(false).build();
        ImagesResponse imagesResponse = service.generateImages(generateRequest);
        System.out.println(imagesResponse.getData().get(0).getUrl());
        service.shutdownExecutor();
    }
}