package com.hex.aicreator.model.enums;

import lombok.Getter;

/**
 * 任务执行阶段枚举
 */
@Getter
public enum ArticlePhaseEnum {

    PENDING("PENDING", "等待处理"),
    TITLE_GENERATING("TITLE_GENERATING", "生成标题中"),
    TITLE_SELECTING("TITLE_SELECTING", "等待选择标题"),
    OUTLINE_GENERATING("OUTLINE_GENERATING", "生成大纲中"),
    OUTLINE_EDITING("OUTLINE_EDITING", "等待编辑大纲"),
    CONTENT_GENERATING("CONTENT_GENERATING", "生成正文中");

    /**
     * 阶段值
     */
    private final String value;

    /**
     * 阶段描述
     */
    private final String description;

    ArticlePhaseEnum(String value, String description) {
        this.value = value;
        this.description = description;
    }

    /**
     * 根据值获取枚举
     *
     * @param value 阶段值
     * @return 枚举实例
     */
    public static ArticlePhaseEnum getByValue(String value) {
        if (value == null) {
            return null;
        }
        for (ArticlePhaseEnum phaseEnum : values()) {
            if (phaseEnum.getValue().equals(value)) {
                return phaseEnum;
            }
        }
        return null;
    }
}
