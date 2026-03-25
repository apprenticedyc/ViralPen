package com.hex.aicreator.agent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.hex.aicreator.agent.context.MessageHandlerContext;
import com.hex.aicreator.agent.tools.ImageGenerationTool;
import com.hex.aicreator.model.enums.SseMessageTypeEnum;
import com.hex.aicreator.model.state.ArticleState;
import com.hex.aicreator.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 并行图片生成器
 * 根据 imageSource 分组，并行执行不同类型的图片生成任务
 *
 * @author AI Passage Creator
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class Node5_ParallelImageGenerator implements NodeAction {
    private final Executor articleExecutor;
    private final ImageGenerationTool imageGenerationTool;
    public static final String INPUT_IMAGE_REQUIREMENTS = "imageRequirements";
    public static final String OUTPUT_IMAGES = "images";

    @Override
    public Map<String, Object> apply(OverAllState state) {
        @SuppressWarnings("unchecked") List<ArticleState.ImageRequirement> imageRequirements = state.<List<?>>value(INPUT_IMAGE_REQUIREMENTS)
                .map(this::convertToImageRequirements).orElseGet(ArrayList::new);
        log.info("Node5_ParallelImageGenerator 开始执行: 配图需求数量={}", imageRequirements.size());

        // 从 ThreadLocal 取出消息处理器
        Consumer<String> messageHandler = MessageHandlerContext.get();
        // 按 imageSource 分组
        Map<String, List<ArticleState.ImageRequirement>> groupedBySource = imageRequirements.stream()
                .collect(Collectors.groupingBy(ArticleState.ImageRequirement::getImageSource));
        log.info("Node5_ParallelImageGenerator 按 imageSource 分组完成: 分组数量={}", groupedBySource.size());
        // 并行执行不同类型的图片生成, 其中messageHandler被多个线程共享, 需要保证线程安全
        List<ArticleState.ImageResult> allImages = executeParallel(groupedBySource, messageHandler);

        // 按 position 排序
        allImages.sort(Comparator.comparingInt(image -> image.getPosition() == null ? 0 : image.getPosition()));

        log.info("Node5_ParallelImageGenerator 执行完成: 成功生成 {} 张图片", allImages.size());
        return Map.of(OUTPUT_IMAGES, allImages);
    }

    /**
     * 并行执行图片生成任务
     * 不同 imageSource 类型并行执行，同一类型内部串行执行
     */
    private final Object messageHandlerLock = new Object();

    private List<ArticleState.ImageResult> executeParallel(
            Map<String, List<ArticleState.ImageRequirement>> groupedBySource, Consumer<String> messageHandler) {

        if (groupedBySource == null || groupedBySource.isEmpty()) {
            return Collections.emptyList();
        }

        List<CompletableFuture<List<ArticleState.ImageResult>>> futures = groupedBySource.entrySet().stream()
                .map(entry -> CompletableFuture.supplyAsync(
                        ///按照 imageSource 分组后，同一类型的图片串行生成
                        /// 1. entry.getKey() 是 imageSource
                        /// 2. entry.getValue() 是该 imageSource 对应的图片需求列表
                        /// articleExecutor 是自定义线程池,代替默认的 ForkJoinPool.commonPool(), 避免与其他任务争抢线程资源
                        () -> generateBySource(entry.getKey(), entry.getValue(), messageHandler), articleExecutor))
                .toList();

        // allOf聚合所有异步任务
        // join 同步等待所有任务完成, 这里不需要获取结果, 因为后续会再次 join 获取结果
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        // 收集所有异步任务结果
        List<ArticleState.ImageResult> result = new ArrayList<>();
        for (CompletableFuture<List<ArticleState.ImageResult>> future : futures) {
            List<ArticleState.ImageResult> list = future.join();
            result.addAll(list);
        }
        return result;
    }

    /**
     * 根据来源异步串行生成图片
     */
    private List<ArticleState.ImageResult> generateBySource(String imageSource,
                                                            List<ArticleState.ImageRequirement> requirements,
                                                            Consumer<String> messageHandler) {
        log.info("开始处理 {} 类型的图片，数量: {}", imageSource, requirements.size());
        List<ArticleState.ImageResult> imageResults = new ArrayList<>(requirements.size());

        for (ArticleState.ImageRequirement req : requirements) {
            try {
                ImageGenerationTool.ImageGenerationResult result = imageGenerationTool.generateImage(req.getImageSource(), req.getKeywords(), req.getPrompt(), req.getPosition(), req.getType(), req.getSectionTitle(), req.getPlaceholderId());
                if (!result.isSuccess()) {
                    log.warn("图片生成失败: imageSource={}, position={}, error={}", imageSource, req.getPosition(), result.getError());
                    continue;
                }
                ArticleState.ImageResult imageResult = convertToImageResult(result);
                imageResults.add(imageResult);
                sendImageCompleteMessage(messageHandler, imageResult);
                log.info("图片生成成功: imageSource={}, position={}", imageSource, req.getPosition());
            } catch (Exception e) {
                log.error("图片生成异常: imageSource={}, position={}", imageSource, req.getPosition(), e);
            }
        }

        log.info("完成处理 {} 类型的图片", imageSource);
        return imageResults;
    }

    /**
     * 发送单张配图完成消息
     * 💥💥💥💥💥💥 因为 messageHandler 会被多个线程池的线程共享，所以需要同步控制
     */
    private void sendImageCompleteMessage(Consumer<String> messageHandler, ArticleState.ImageResult imageResult) {
        String message = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix() + GsonUtils.toJson(imageResult);
        synchronized (messageHandlerLock) {
            messageHandler.accept(message);
        }
    }

    /**
     * 转换 ImageGenerationResult 为 ArticleState.ImageResult
     */
    private ArticleState.ImageResult convertToImageResult(ImageGenerationTool.ImageGenerationResult genResult) {
        return ArticleState.ImageResult.builder().position(genResult.getPosition()).url(genResult.getUrl())
                .method(genResult.getMethod()).keywords(genResult.getKeywords())
                .sectionTitle(genResult.getSectionTitle()).description(genResult.getDescription())
                .placeholderId(genResult.getPlaceholderId()).build();
    }

    /**
     * 转换列表为 ImageRequirement 列表
     */
    private List<ArticleState.ImageRequirement> convertToImageRequirements(List<?> list) {
        return list.stream().filter(ArticleState.ImageRequirement.class::isInstance)
                .map(ArticleState.ImageRequirement.class::cast).collect(Collectors.toCollection(ArrayList::new));
    }
}
