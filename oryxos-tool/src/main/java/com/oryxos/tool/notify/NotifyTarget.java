package com.oryxos.tool.notify;

import java.util.Map;

/**
 * 通知目标结构体：携带渠道类型和配置信息。
 * type 决定路由到哪个适配器，config 携带渠道特有配置（不同 type 解读不同 key）。
 */
public record NotifyTarget(String type, Map<String, String> config) {
}
