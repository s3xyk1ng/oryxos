package com.oryxos.core.model;

/**
 * Sandbox 动作定义。
 */
public class SandboxAction {

    public enum ActionType {
        FILE_READ, FILE_WRITE, SHELL_COMMAND, HTTP_REQUEST
    }

    private final ActionType type;
    private final String target;

    public SandboxAction(ActionType type, String target) {
        this.type = type;
        this.target = target;
    }

    public ActionType getType() { return type; }
    public String getTarget() { return target; }
}
