package com.hex.aicreator.enums;

import lombok.Getter;

/**
 *  配图方式枚举
 */
@Getter
public enum ImageMethodEnum {

    /**
     * Pexels 图库检索
     */
    PEXELS("PEXELS", "Pexels 图库"),

    /**
     * Picsum 随机图片（降级方案）
     */
    PICSUM("PICSUM", "Picsum 随机图片");

    /**
     * 方法值
     */
    private final String value;

    /**
     * 方法描述
     */
    private final String description;

    ImageMethodEnum(String value, String description) {
        this.value = value;
        this.description = description;
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
}
