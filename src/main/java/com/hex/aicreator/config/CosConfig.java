package com.hex.aicreator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云 COS 配置
 *
 */
@Configuration
@ConfigurationProperties(prefix = "tencent.cos")
@Data
public class CosConfig {

    /**
     * Secret ID
     */
    private String secretId;

    /**
     * Secret Key
     */
    private String secretKey;

    /**
     * 地域
     */
    private String region;

    /**
     * 存储桶
     */
    private String bucket;
}
