package com.oryxos.core.agent;

import com.oryxos.core.model.Profile;
import com.oryxos.core.react.LlmPort;

/**
 * LLM 端口工厂：根据 Profile 创建对应的 LlmPort 实例。
 * 实现在 oryxos-provider 模块。
 */
public interface LlmPortFactory {

    /**
     * 为指定 Profile 创建 LLM 端口。
     */
    LlmPort createForProfile(Profile profile);
}
