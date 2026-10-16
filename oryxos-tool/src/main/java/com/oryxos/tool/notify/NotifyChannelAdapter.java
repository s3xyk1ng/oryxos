package com.oryxos.tool.notify;

/** 通知渠道适配器接口：不同通知渠道（Webhook/飞书/钉钉/企微）实现此接口。 */
public interface NotifyChannelAdapter {

    /**
     * 发送通知。
     *
     * @param target 通知目标（URL/频道名等）
     * @param content 通知内容
     * @return 发送结果描述
     */
    String send(String target, String content);

    /** 适配器类型名称。 */
    String getType();
}
