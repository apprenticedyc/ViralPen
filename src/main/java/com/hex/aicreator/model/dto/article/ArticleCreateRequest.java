package com.hex.aicreator.model.dto.article;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建文章请求
 *
 */
@Data
public class ArticleCreateRequest implements Serializable {

    /**
     * 选题
     */
    private String topic;

    private static final long serialVersionUID = 1L;
}
