package org.yglue.flow.orch.web.dto.plugin.response;

import java.time.Instant;

public class PluginInstanceResponse {
    private String instanceKey;
    private String ideType;
    private String ideVersion;
    private String status;
    private Instant lastHeartbeat;
    private String lastIp;
    private int pendingFlows;

    public String getInstanceKey() {
        return instanceKey;
    }

    public void setInstanceKey(String instanceKey) {
        this.instanceKey = instanceKey;
    }

    public String getIdeType() {
        return ideType;
    }

    public void setIdeType(String ideType) {
        this.ideType = ideType;
    }

    public String getIdeVersion() {
        return ideVersion;
    }

    public void setIdeVersion(String ideVersion) {
        this.ideVersion = ideVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public String getLastIp() {
        return lastIp;
    }

    public void setLastIp(String lastIp) {
        this.lastIp = lastIp;
    }

    public int getPendingFlows() {
        return pendingFlows;
    }

    public void setPendingFlows(int pendingFlows) {
        this.pendingFlows = pendingFlows;
    }
}
