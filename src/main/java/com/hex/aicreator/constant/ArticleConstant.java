package com.hex.aicreator.constant;

/**
 * @author Hex
 * @since 2026/3/20
 * Description
 */
public interface ArticleConstant {

    /**
     * SSE 连接超时时间（毫秒）：30分钟
     */
    long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    /**
     * SSE 重连时间（毫秒）：3秒
     */
    long SSE_RECONNECT_TIME_MS = 3000L;

    // region Pexels 相关常量

    /**
     * Pexels API 地址
     */
    String PEXELS_API_URL = "https://api.pexels.com/v1/search";

    /**
     * Pexels 每页返回数量
     */
    int PEXELS_PER_PAGE = 1;

    /**
     * Pexels 图片方向：横向
     */
    String PEXELS_ORIENTATION_LANDSCAPE = "landscape";

    // endregion

    // region Picsum 相关常量

    /**
     * Picsum 随机图片 URL 模板
     */
    String PICSUM_URL_TEMPLATE = "https://picsum.photos/800/600?random=%d";

    // endregion

    // region Bing 表情包相关常量

    /**
     * Bing 图片搜索地址
     */
    String BING_IMAGE_SEARCH_URL = "https://cn.bing.com/images/async";

    /**
     * 表情包关键词后缀（程序固定拼接）
     */
    String EMOJI_PACK_SUFFIX = "表情包";

    /**
     * Bing 图片搜索每批最大数量
     */
    int BING_MAX_IMAGES = 30;
    // endregion

}
