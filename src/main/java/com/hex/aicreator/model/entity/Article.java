package com.hex.aicreator.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

import java.io.Serial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章表 实体类。
 *
 * @author <a href="https://github.com/ApprenticeDyc">DYC666</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("article")
public class Article implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 任务ID（UUID）
     */
    @Column("taskId")
    private String taskId;

    /**
     * 用户ID
     */
    @Column("userId")
    private Long userId;

    /**
     * 选题
     */
    private String topic;

    /**
     * 主标题
     */
    @Column("mainTitle")
    private String mainTitle;

    /**
     * 副标题
     */
    @Column("subTitle")
    private String subTitle;

    /**
     * 大纲（JSON格式）
     */
    private String outline;

    /**
     * 正文（Markdown格式）
     */
    private String content;

    /**
     * 完整图文（Markdown格式，含配图）
     */
    @Column("fullContent")
    private String fullContent;

    /**
     * 封面图 URL
     */
    @Column("coverImage")
    private String coverImage;

    /**
     * 配图列表（JSON数组，包含封面图 position=1）
     */
    private String images;

    /**
     * 状态：PENDING/PROCESSING/COMPLETED/FAILED
     */
    private String status;

    /**
     * 错误信息
     */
    @Column("errorMessage")
    private String errorMessage;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 完成时间
     */
    @Column("completedTime")
    private LocalDateTime completedTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;

}
