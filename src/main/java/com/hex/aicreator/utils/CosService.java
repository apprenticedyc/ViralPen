package com.hex.aicreator.utils;

import com.hex.aicreator.config.CosConfig;
import com.hex.aicreator.model.dto.image.ImageData;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
@Slf4j
public class CosService {

    @Resource
    private CosConfig cosConfig;

    private COSClient cosClient;

    private final OkHttpClient httpClient = new OkHttpClient();

    @PostConstruct
    public void init() {
        COSCredentials cred = new BasicCOSCredentials(cosConfig.getSecretId(), cosConfig.getSecretKey());
        Region region = new Region(cosConfig.getRegion());
        ClientConfig clientConfig = new ClientConfig(region);
        clientConfig.setHttpProtocol(HttpProtocol.https);
        cosClient = new COSClient(cred, clientConfig);
    }

    /**
     * 上传 ImageData 到 COS（统一入口）
     * 根据数据类型自动选择上传方式
     *
     * @param imageData 图片数据对象
     * @param folder    文件夹
     * @return COS 图片 URL，上传失败返回 null
     */
    public String uploadImageData(ImageData imageData, String folder) {
        if (imageData == null || !imageData.isValid()) {
            log.warn("ImageData 无效，无法上传");
            return null;
        }

        try {
            return switch (imageData.getDataType()) {
                case BYTES -> uploadBytes(imageData.getBytes(), imageData.getMimeType(), folder);
                case URL -> uploadFromUrl(imageData.getUrl(), folder);
                case DATA_URL -> uploadFromDataUrl(imageData, folder);
            };
        } catch (Exception e) {
            log.error("上传 ImageData 到 COS 失败, dataType={}", imageData.getDataType(), e);
            return null;
        }
    }

    /**
     * 上传字节数据到 COS
     */
    public String uploadBytes(byte[] bytes, String mimeType, String folder) {
        if (bytes == null || bytes.length == 0) {
            log.warn("字节数据为空，无法上传");
            return null;
        }

        try {
            // 生成文件名
            String extension = getExtensionFromMimeType(mimeType);
            String fileName = folder + "/" + UUID.randomUUID() + extension;

            // 上传到 COS
            try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(bytes.length);
                metadata.setContentType(mimeType != null ? mimeType : "image/png");

                PutObjectRequest putObjectRequest = new PutObjectRequest(cosConfig.getBucket(), fileName, inputStream, metadata);

                cosClient.putObject(putObjectRequest);

                String cosUrl = buildCosUrl(fileName);
                log.info("字节数据上传成功, size={} bytes, url={}", bytes.length, cosUrl);
                return cosUrl;
            }
        } catch (Exception e) {
            log.error("上传字节数据到 COS 失败", e);
            return null;
        }
    }

    /**
     * 从外部 URL 下载并上传到 COS
     */
    public String uploadFromUrl(String imageUrl, String folder) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            log.warn("图片 URL 为空，无法上传");
            return null;
        }

        try {
            // 下载图片
            Request request = new Request.Builder().url(imageUrl).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("下载图片失败: {}, code={}", imageUrl, response.code());
                    return null;
                }

                byte[] imageBytes = response.body().bytes();
                String contentType = response.header("Content-Type", "image/jpeg");

                // 上传字节数据
                return uploadBytes(imageBytes, contentType, folder);
            }
        } catch (IOException e) {
            log.error("从 URL 上传图片到 COS 失败: {}", imageUrl, e);
            return null;
        }
    }

    /**
     * 从 base64 data URL 解码并上传到 COS
     */
    public String uploadFromDataUrl(ImageData imageData, String folder) {
        byte[] bytes = imageData.getImageBytes();
        if (bytes == null || bytes.length == 0) {
            log.warn("解码 data URL 失败，无法上传");
            return null;
        }

        return uploadBytes(bytes, imageData.getMimeType(), folder);
    }

    /**
     * 上传图片到 COS
     *
     * @param imageUrl 图片 URL
     * @param folder   文件夹
     * @return COS 图片 URL
     */
    public String uploadImage(String imageUrl, String folder) {
        try {
            // 下载图片
            Request request = new Request.Builder().url(imageUrl).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("下载图片失败: {}", imageUrl);
                    return imageUrl; // 降级：直接返回原始 URL
                }

                byte[] imageBytes = response.body().bytes();

                // 生成文件名
                String fileName = folder + "/" + UUID.randomUUID() + ".jpg";

                // 上传到 COS
                try (InputStream inputStream = new ByteArrayInputStream(imageBytes)) {
                    ObjectMetadata metadata = new ObjectMetadata();
                    metadata.setContentLength(imageBytes.length);
                    metadata.setContentType("image/jpeg");

                    PutObjectRequest putObjectRequest = new PutObjectRequest(cosConfig.getBucket(), fileName, inputStream, metadata);

                    cosClient.putObject(putObjectRequest);

                    // 返回访问 URL
                    return String.format("https://%s.cos.%s.myqcloud.com/%s", cosConfig.getBucket(), cosConfig.getRegion(), fileName);
                }
            }
        } catch (IOException e) {
            log.error("上传图片到 COS 失败", e);
            return imageUrl; // 降级：直接返回原始 URL
        }
    }

    /**
     * 直接使用图片 URL（不上传到 COS）
     *
     * @param imageUrl 图片 URL
     * @return 图片 URL
     */
    public String useDirectUrl(String imageUrl) {
        return imageUrl;
    }

    /**
     * 根据 MIME 类型获取文件扩展名
     */
    private String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null) {
            return ".png";
        }
        return switch (mimeType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/svg+xml" -> ".svg";
            default -> ".png";
        };
    }

    /**
     * 构建 COS 访问 URL
     */
    private String buildCosUrl(String fileName) {
        return String.format("https://%s.cos.%s.myqcloud.com/%s", cosConfig.getBucket(), cosConfig.getRegion(), fileName);
    }
}
