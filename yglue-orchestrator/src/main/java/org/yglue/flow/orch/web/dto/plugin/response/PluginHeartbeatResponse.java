package org.yglue.flow.orch.web.dto.plugin.response;

import java.util.List;

public class PluginHeartbeatResponse {
    private boolean needSync;
    private List<FlowSyncInstructionResponse> pendingFlows;

    public PluginHeartbeatResponse(boolean needSync, List<FlowSyncInstructionResponse> pendingFlows) {
        this.needSync = needSync;
        this.pendingFlows = pendingFlows;
    }

    public boolean isNeedSync() {
        return needSync;
    }

    public void setNeedSync(boolean needSync) {
        this.needSync = needSync;
    }

    public List<FlowSyncInstructionResponse> getPendingFlows() {
        return pendingFlows;
    }

    public void setPendingFlows(List<FlowSyncInstructionResponse> pendingFlows) {
        this.pendingFlows = pendingFlows;
    }
}
