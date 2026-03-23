package com.hex.aicreator.controller;

import com.hex.aicreator.agent.service.ArticleAsyncService;
import com.hex.aicreator.annotation.AuthCheck;
import com.hex.aicreator.common.BaseResponse;
import com.hex.aicreator.common.DeleteRequest;
import com.hex.aicreator.common.ResultUtils;
import com.hex.aicreator.exception.ErrorCode;
import com.hex.aicreator.exception.ThrowUtils;
import com.hex.aicreator.manager.SseEmitterManager;
import com.hex.aicreator.model.dto.article.*;
import com.hex.aicreator.model.entity.User;
import com.hex.aicreator.model.state.ArticleState;
import com.hex.aicreator.model.vo.article.ArticleVO;
import com.hex.aicreator.service.ArticleService;
import com.hex.aicreator.service.UserService;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 文章表 控制层。
 *
 * @author <a href="https://github.com/ApprenticeDyc">DYC666</a>
 */
@RestController
@RequestMapping("/article")
@Tag(name = "文章接口")
@Slf4j
public class ArticleController {

    @Resource
    private ArticleService articleService;

    @Resource
    private ArticleAsyncService articleAsyncService;

    @Resource
    private SseEmitterManager sseEmitterManager;

    @Resource
    private UserService userService;

    /**
     * 生成文章接口
     */
    @PostMapping("/create")
    @Operation(summary = "创建文章任务")
    public BaseResponse<String> createArticle(@RequestBody ArticleCreateRequest request,
                                              HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTopic() == null || request.getTopic().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "选题不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        // 检查并消耗配额 + 创建文章任务（在同一事务中）
        String taskId = articleService.createArticleTaskWithQuotaCheck(
                request.getTopic(),
                loginUser);
        // 异步执行阶段1：生成标题方案
        articleAsyncService.workFLow_Phase1(
                taskId,
                request.getTopic()
        );
        return ResultUtils.success(taskId);
    }


    /**
     * SSE 进度推送
     */
    @GetMapping("/progress/{taskId}")
    @Operation(summary = "获取文章生成进度(SSE)")
    public SseEmitter getProgress(@PathVariable String taskId, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        User loginUser = userService.getLoginUser(httpServletRequest);
        articleService.getArticleDetail(taskId, loginUser);

        // 创建SSE Emitter并注册到Manager中, 跟客户端建立起SSE连接
        SseEmitter emitter = sseEmitterManager.createEmitter(taskId);

        log.info("SSE 连接已建立, taskId={}", taskId);
        return emitter;
    }

    /**
     * 获取文章详情
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "获取文章详情")
    @AuthCheck(mustRole = "user")
    public BaseResponse<ArticleVO> getArticle(@PathVariable String taskId,
                                              HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        User loginUser = userService.getLoginUser(httpServletRequest);
        ArticleVO articleVO = articleService.getArticleDetail(taskId, loginUser);
        return ResultUtils.success(articleVO);
    }


    /**
     * 确认标题并输入补充描述
     */
    @PostMapping("/confirm-title")
    @Operation(summary = "确认标题并输入补充描述")
    public BaseResponse<Void> confirmTitle(@RequestBody ArticleConfirmTitleRequest request,
                                           HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getSelectedMainTitle() == null || request.getSelectedMainTitle().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "主标题不能为空");
        ThrowUtils.throwIf(request.getSelectedSubTitle() == null || request.getSelectedSubTitle().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "副标题不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        // 确认标题
        articleService.confirmTitle(
                request.getTaskId(),
                request.getSelectedMainTitle(),
                request.getSelectedSubTitle(),
                request.getUserDescription(),
                loginUser
        );

        // 异步执行阶段2：生成大纲
        articleAsyncService.workFLow_Phase2(request.getTaskId());

        return ResultUtils.success(null);
    }

    /**
     * 确认大纲
     */
    @PostMapping("/confirm-outline")
    @Operation(summary = "确认大纲")
    public BaseResponse<Void> confirmOutline(@RequestBody ArticleConfirmOutlineRequest request,
                                             HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getOutline() == null || request.getOutline().isEmpty(),
                ErrorCode.PARAMS_ERROR, "大纲不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        // 确认大纲
        articleService.confirmOutline(
                request.getTaskId(),
                request.getOutline(),
                loginUser
        );

        // 异步执行阶段3：生成正文+配图
        articleAsyncService.workFLow_Phase3(request.getTaskId());

        return ResultUtils.success(null);
    }

    /**
     * AI 修改大纲
     */
    @PostMapping("/ai-modify-outline")
    @Operation(summary = "AI 修改大纲")
    public BaseResponse<List<ArticleState.OutlineSection>> aiModifyOutline(
            @RequestBody ArticleAiModifyOutlineRequest request,
            HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getModifySuggestion() == null || request.getModifySuggestion().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "修改建议不能为空");
        User loginUser = userService.getLoginUser(httpServletRequest);

        // AI 修改大纲
        List<ArticleState.OutlineSection> modifiedOutline = articleService.aiModifyOutline(
                request.getTaskId(),
                request.getModifySuggestion(),
                loginUser
        );

        return ResultUtils.success(modifiedOutline);
    }


    /**
     * 分页查询文章列表
     */
    @PostMapping("/list")
    @Operation(summary = "分页查询文章列表")
    @AuthCheck(mustRole = "user")
    public BaseResponse<Page<ArticleVO>> listArticle(@RequestBody ArticleQueryRequest request,
                                                     HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        Page<ArticleVO> articleVOPage = articleService.listArticleByPage(request, loginUser);
        return ResultUtils.success(articleVOPage);
    }

    /**
     * 删除文章
     */
    @PostMapping("/delete")
    @Operation(summary = "删除文章")
    @AuthCheck(mustRole = "user")
    public BaseResponse<Boolean> deleteArticle(@RequestBody DeleteRequest deleteRequest,
                                               HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpServletRequest);

        boolean result = articleService.deleteArticle(deleteRequest.getId(), loginUser);
        return ResultUtils.success(result);
    }
}

