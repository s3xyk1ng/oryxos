package com.oryxos.core.react;

import com.oryxos.core.model.ToolCallRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ChatResponse;

/** Tool call 解析器：从 ChatResponse 中提取 tool call 请求。 */
public class ToolCallParser {

    /** 从 ChatResponse 中提取 tool call 请求。 */
    public static List<ToolCallRequest> extract(ChatResponse response) {
        List<ToolCallRequest> requests = new ArrayList<>();
        if (response == null || response.getResult() == null) return requests;

        var assistantMessage = response.getResult().getOutput();
        if (assistantMessage.getToolCalls() != null) {
            for (var tc : assistantMessage.getToolCalls()) {
                ToolCallRequest request = new ToolCallRequest();
                request.setId(tc.id());
                request.setToolName(tc.name());
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> args =
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readValue(tc.arguments(), Map.class);
                    request.setArguments(args);
                } catch (Exception e) {
                    request.setArguments(Map.of());
                }
                requests.add(request);
            }
        }
        return requests;
    }
}
