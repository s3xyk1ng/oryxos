package com.oryxos.tool.notify;

/**
 * 通知渠道适配器接口：保持中立，不绑定任何具体渠道类型。
 * 适配器自己判断 target.getType() 是否匹配，不匹配返回 "unsupported type: xxx"。
 */
public interface NotifyChannelAdapter {

    /**
     * 发送通知。
     *
     * @param target  通知目标（类型 + 配置）
     * @param content 通知内容
     * @return 发送结果描述；类型不匹配时返回 "unsupported type: xxx"
     */
    String send(NotifyTarget target, String content);
}
