package com.hex.aicreator.service;

import com.hex.aicreator.enums.ArticleTaskStatusEnum;
import com.hex.aicreator.model.dto.article.ArticleQueryRequest;
import com.hex.aicreator.model.entity.User;
import com.hex.aicreator.model.state.ArticleState;
import com.hex.aicreator.model.vo.article.ArticleVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.hex.aicreator.model.entity.Article;

/**
 * 文章表 服务层。
 *
 * @author <a href="https://github.com/ApprenticeDyc">DYC666</a>
 */
public interface ArticleService extends IService<Article> {
    /**
     * 创建文章任务
     *
     * @param topic     选题
     * @param loginUser 当前登录用户
     * @return 任务ID
     */
    String createArticleTask(String topic, User loginUser);
//
//    /**
//     * 创建文章任务（带配额检查）
//     * 将配额扣减和任务创建放在同一事务中，确保原子性
//     *
//     * @param topic     选题
//     * @param loginUser 当前登录用户
//     * @return 任务ID
//     */
//    String createArticleTaskWithQuotaCheck(String topic, User loginUser);

    /**
     * 根据任务ID获取文章成品
     *
     * @param taskId 任务ID
     * @return 文章实体
     */
    Article getByTaskId(String taskId);

    /**
     * 获取文章详情
     *
     * @param taskId    任务ID
     * @param loginUser 当前登录用户
     * @return 文章VO
     */
    ArticleVO getArticleDetail(String taskId, User loginUser);

    /**
     * 分页查询文章列表, 可指定文章生成状态
     *
     * @param request   查询请求
     * @param loginUser 当前登录用户
     * @return 分页结果
     */
    Page<ArticleVO> listArticleByPage(ArticleQueryRequest request, User loginUser);

    /**
     * 删除文章（带权限校验）
     *
     * @param id        文章ID
     * @param loginUser 当前登录用户
     * @return 是否成功
     */
    boolean deleteArticle(Long id, User loginUser);

    /**
     * 更新文章状态
     *
     * @param taskId       任务ID
     * @param status       状态枚举
     * @param errorMessage 错误信息（可选）
     */
    void updateArticleStatus(String taskId, ArticleTaskStatusEnum status, String errorMessage);

    /**
     * 保存文章内容
     *
     * @param taskId 任务ID
     * @param state  文章状态对象
     */
    void saveArticleContent(String taskId, ArticleState state);

}
