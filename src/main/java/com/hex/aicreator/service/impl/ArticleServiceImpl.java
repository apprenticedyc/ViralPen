package com.hex.aicreator.service.impl;

import cn.hutool.core.util.IdUtil;
import com.google.gson.reflect.TypeToken;
import com.hex.aicreator.agent.service.ArticleAgentService;
import com.hex.aicreator.model.enums.ArticlePhaseEnum;
import com.hex.aicreator.model.enums.ArticleTaskStatusEnum;
import com.hex.aicreator.exception.BusinessException;
import com.hex.aicreator.exception.ErrorCode;
import com.hex.aicreator.exception.ThrowUtils;
import com.hex.aicreator.model.dto.article.ArticleQueryRequest;
import com.hex.aicreator.model.entity.User;
import com.hex.aicreator.model.state.ArticleState;
import com.hex.aicreator.model.vo.article.ArticleVO;
import com.hex.aicreator.service.QuotaService;
import com.hex.aicreator.utils.GsonUtils;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.hex.aicreator.model.entity.Article;
import com.hex.aicreator.mapper.ArticleMapper;
import com.hex.aicreator.service.ArticleService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.hex.aicreator.constant.UserConstant.ADMIN_ROLE;
import static com.hex.aicreator.constant.UserConstant.VIP_ROLE;

/**
 * 文章表 服务层实现。
 *
 * @author <a href="https://github.com/ApprenticeDyc">DYC666</a>
 */
@Slf4j
@Service
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {
    @Resource
    private ArticleAgentService articleAgentService;
    @Resource
    private QuotaService quotaService;

    @Override
    public String createArticleTask(String topic, User loginUser) {
        // 生成任务ID
        String taskId = IdUtil.simpleUUID();
        // 创建文章记录
        Article article = new Article();
        article.setTaskId(taskId);
        article.setUserId(loginUser.getId());
        article.setTopic(topic);
        article.setStatus(ArticleTaskStatusEnum.PENDING.getValue());
        article.setCreateTime(LocalDateTime.now());
        this.save(article);
        log.info("文章任务已创建, taskId={}, userId={}", taskId, loginUser.getId());
        return taskId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createArticleTaskWithQuotaCheck(String topic, User loginUser) {
        // 在同一事务中：先扣配额，再创建任务
        // 如果任务创建失败，配额会自动回滚
        quotaService.checkAndConsumeQuota(loginUser);
        return createArticleTask(topic, loginUser);
    }

    @Override
    public void updateArticleStatus(String taskId, ArticleTaskStatusEnum status, String errorMessage) {
        Article article = getByTaskId(taskId);

        if (article == null) {
            log.error("文章记录不存在, taskId={}", taskId);
            return;
        }

        article.setStatus(status.getValue());
        article.setErrorMessage(errorMessage);
        this.updateById(article);

        log.info("文章状态已更新, taskId={}, status={}", taskId, status.getValue());
    }

    @Override
    public void saveArticleContent(String taskId, ArticleState state) {
        Article article = getByTaskId(taskId);

        if (article == null) {
            log.error("文章记录不存在, taskId={}", taskId);
            return;
        }

        article.setMainTitle(state.getTitle().getMainTitle());
        article.setSubTitle(state.getTitle().getSubTitle());
        article.setOutline(GsonUtils.toJson(state.getOutline().getSections()));
        article.setContent(state.getContent());
        article.setFullContent(state.getFullContent());

        // 保存封面图 URL（从 images 列表中提取 position=1 的 URL）
        if (state.getImages() != null && !state.getImages().isEmpty()) {
            ArticleState.ImageResult cover = state.getImages().stream()
                    .filter(img -> img.getPosition() != null && img.getPosition() == 1).findFirst().orElse(null);
            if (cover != null && cover.getUrl() != null) {
                article.setCoverImage(cover.getUrl());
            }
        }
        article.setImages(GsonUtils.toJson(state.getImages()));
        article.setCompletedTime(LocalDateTime.now());

        this.updateById(article);
        log.info("文章保存成功, taskId={}", taskId);
    }

    //startregion 增删改查
    @Override
    public Article getByTaskId(String taskId) {
        return this.getOne(QueryWrapper.create().eq("taskId", taskId));
    }

    @Override
    public ArticleVO getArticleDetail(String taskId, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");

        // 校验权限：只能查看自己的文章（管理员除外）
        checkArticlePermission(article, loginUser);

        return ArticleVO.objToVo(article);
    }

    @Override
    public Page<ArticleVO> listArticleByPage(ArticleQueryRequest request, User loginUser) {
        long current = request.getPageNum();
        long size = request.getPageSize();

        // 构建查询条件
        QueryWrapper queryWrapper = QueryWrapper.create().eq("isDelete", 0).orderBy("createTime", false);

        // 非管理员只能查看自己的文章
        if (!ADMIN_ROLE.equals(loginUser.getUserRole())) {
            queryWrapper.eq("userId", loginUser.getId());
        } else if (request.getUserId() != null) {
            queryWrapper.eq("userId", request.getUserId());
        }

        // 按状态筛选
        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            queryWrapper.eq("status", request.getStatus());
        }

        // 分页查询
        Page<Article> articlePage = this.page(new Page<>(current, size), queryWrapper);

        // 转换为 VO
        return convertToVOPage(articlePage);
    }

    @Override
    public boolean deleteArticle(Long id, User loginUser) {
        Article article = this.getById(id);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR);

        // 校验权限：只能删除自己的文章（管理员除外）
        checkArticlePermission(article, loginUser);

        // 逻辑删除
        return this.removeById(id);
    }

    @Override
    public void confirmTitle(String taskId, String mainTitle, String subTitle, String userDescription, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");

        // 校验当前阶段（必须是 TITLE_SELECTING）
        ArticlePhaseEnum currentPhase = ArticlePhaseEnum.getByValue(article.getPhase());
        ThrowUtils.throwIf(currentPhase != ArticlePhaseEnum.TITLE_SELECTING, ErrorCode.OPERATION_ERROR, "当前阶段不允许此操作");

        // 保存用户选择的标题和补充描述
        article.setMainTitle(mainTitle);
        article.setSubTitle(subTitle);
        article.setUserDescription(userDescription);
        article.setPhase(ArticlePhaseEnum.OUTLINE_GENERATING.getValue());

        this.updateById(article);
        log.info("用户确认标题, taskId={}, mainTitle={}", taskId, mainTitle);
    }

    @Override
    public void confirmOutline(String taskId, List<ArticleState.OutlineSection> outline, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");

        // 校验当前阶段（必须是 OUTLINE_EDITING）
        ArticlePhaseEnum currentPhase = ArticlePhaseEnum.getByValue(article.getPhase());
        ThrowUtils.throwIf(currentPhase != ArticlePhaseEnum.OUTLINE_EDITING, ErrorCode.OPERATION_ERROR, "当前阶段不允许此操作");

        // 保存用户编辑后的大纲
        article.setOutline(GsonUtils.toJson(outline));
        article.setPhase(ArticlePhaseEnum.CONTENT_GENERATING.getValue());

        this.updateById(article);
        log.info("用户确认大纲, taskId={}, sectionsCount={}", taskId, outline.size());
    }

    @Override
    public void updatePhase(String taskId, ArticlePhaseEnum phase) {
        Article article = getByTaskId(taskId);
        if (article == null) {
            log.error("文章记录不存在, taskId={}", taskId);
            return;
        }

        article.setPhase(phase.getValue());
        this.updateById(article);
        log.info("文章阶段已更新, taskId={}, phase={}", taskId, phase.getValue());
    }

    @Override
    public void saveTitleOptions(String taskId, List<ArticleState.TitleOption> titleOptions) {
        Article article = getByTaskId(taskId);
        if (article == null) {
            log.error("文章记录不存在, taskId={}", taskId);
            return;
        }

        article.setTitleOptions(GsonUtils.toJson(titleOptions));
        this.updateById(article);
        log.info("标题方案已保存, taskId={}, optionsCount={}", taskId, titleOptions.size());
    }

    @Override
    public List<ArticleState.OutlineSection> aiModifyOutline(String taskId, String modifySuggestion, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        // 校验 VIP 权限（普通用户不能使用 AI 修改大纲）
        ThrowUtils.throwIf(!isVipOrAdmin(loginUser), ErrorCode.NO_AUTH_ERROR,
                "AI 修改大纲功能仅限 VIP 会员使用");

        // 校验当前阶段（必须是 OUTLINE_EDITING）
        ArticlePhaseEnum currentPhase = ArticlePhaseEnum.getByValue(article.getPhase());
        ThrowUtils.throwIf(currentPhase != ArticlePhaseEnum.OUTLINE_EDITING, ErrorCode.OPERATION_ERROR, "当前阶段不允许此操作");

        // 获取当前大纲
        List<ArticleState.OutlineSection> currentOutline = GsonUtils.fromJson(article.getOutline(), new TypeToken<List<ArticleState.OutlineSection>>() {
        });

        // 调用 AI 修改大纲
        List<ArticleState.OutlineSection> modifiedOutline = articleAgentService.aiModifyOutline(article.getMainTitle(), article.getSubTitle(), currentOutline, modifySuggestion);

        // 保存修改后的大纲
        article.setOutline(GsonUtils.toJson(modifiedOutline));
        this.updateById(article);

        log.info("AI修改大纲完成, taskId={}, sectionsCount={}", taskId, modifiedOutline.size());
        return modifiedOutline;
    }


    //end region

    //startregion 辅助方法

    /**
     * 校验文章权限
     *
     * @param article   文章
     * @param loginUser 当前用户
     */
    private void checkArticlePermission(Article article, User loginUser) {
        if (!article.getUserId().equals(loginUser.getId()) && !ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    /**
     * 将文章分页结果转换为 VO 分页
     *
     * @param articlePage 文章分页
     * @return VO 分页
     */
    private Page<ArticleVO> convertToVOPage(Page<Article> articlePage) {
        Page<ArticleVO> articleVOPage = new Page<>();
        articleVOPage.setPageNumber(articlePage.getPageNumber());
        articleVOPage.setPageSize(articlePage.getPageSize());
        articleVOPage.setTotalRow(articlePage.getTotalRow());

        List<ArticleVO> articleVOList = articlePage.getRecords().stream().map(ArticleVO::objToVo)
                .collect(Collectors.toList());
        articleVOPage.setRecords(articleVOList);

        return articleVOPage;
    }


    /**
     * 判断是否为 VIP 或管理员
     */
    private boolean isVipOrAdmin(User user) {
        return ADMIN_ROLE.equals(user.getUserRole()) ||
                VIP_ROLE.equals(user.getUserRole());
    }
    //endregion

}
