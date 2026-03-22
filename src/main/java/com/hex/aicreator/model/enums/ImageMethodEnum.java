package com.hex.aicreator.model.enums;

import lombok.Getter;

/**
 *  配图方式枚举
 */
@Getter
public enum ImageMethodEnum {

    /**
     * Pexels 图库检索
     */
    PEXELS("PEXELS", "Pexels 图库", false, false),
    /**
     * Seedream AI生图
     */
    SEEDREAM("SEEDREAM", "SEEDREAM AI生图", true, false),

    /**
     * Picsum 随机图片（降级方案）
     */
    PICSUM("PICSUM", "Picsum 随机图片", false, true),


    /**
     * 表情包检索（Bing 图片搜索）
     */
    EMOJI_PACK("EMOJI_PACK", "表情包检索", false, false);


    /**
     * 方法值
     */
    private final String value;

    /**
     * 方法描述
     */
    private final String description;

    /**
     * 是否为 AI 生图方式
     * true: 使用 prompt 生成图片
     * false: 使用 keywords 检索图片
     */
    private final boolean aiGenerated;

    /**
     * 是否为降级方案
     */
    private final boolean fallback;

    ImageMethodEnum(String value, String description, boolean aiGenerated, boolean fallback) {
        this.value = value;
        this.description = description;
        this.aiGenerated = aiGenerated;
        this.fallback = fallback;
    }

    /**
     * 根据值获取枚举
     *
     * @param value 方法值
     * @return 枚举实例
     */
    public static ImageMethodEnum getByValue(String value) {
        if (value == null) {
            return null;
        }
        for (ImageMethodEnum methodEnum : values()) {
            if (methodEnum.getValue().equals(value)) {
                return methodEnum;
            }
        }
        return null;
    }

    /**
     * 获取默认的图库检索方式
     */
    public static ImageMethodEnum getDefaultSearchMethod() {
        return PEXELS;
    }

    /**
     * 获取默认的 AI 生图方式
     */
    public static ImageMethodEnum getDefaultAiMethod() {
        return SEEDREAM;
    }

    /**
     * 获取降级方案
     */
    public static ImageMethodEnum getFallbackMethod() {
        return PICSUM;
    }
}
