package com.hex.aicreator.agent.context;

import java.util.function.Consumer;

/**
 * 使用 ThreadLocal 保存 MessageHandler，因为无法序列化所以无法放入 StateGraph 状态
 */
public class MessageHandlerContext {

    private static final ThreadLocal<Consumer<String>> MESSAGE_HANDLER = new ThreadLocal<>();

    /**
     * 设置ThreadLocal值
     */
    public static void set(Consumer<String> handler) {
        MESSAGE_HANDLER.set(handler);
    }

    /**
     * 获取ThreadLocal值
     */
    public static Consumer<String> get() {
        return MESSAGE_HANDLER.get();
    }

    /**
     * 清理上下文
     * 务必在使用完毕后调用，避免内存泄漏
     */
    public static void clear() {
        MESSAGE_HANDLER.remove();
    }
}
