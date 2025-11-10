package org.yglue.flow.orch.web.dto.endpoint.response;

import java.util.List;

public class ProjectComponentGroupResponse {
    private String type;
    private String displayName;
    private int count;
    private List<ProjectComponentItemResponse> items;

    public ProjectComponentGroupResponse() {
    }

    public ProjectComponentGroupResponse(String type,
                                         String displayName,
                                         List<ProjectComponentItemResponse> items) {
        this.type = type;
        this.displayName = displayName;
        this.items = items;
        this.count = items == null ? 0 : items.size();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<ProjectComponentItemResponse> getItems() {
        return items;
    }

    public void setItems(List<ProjectComponentItemResponse> items) {
        this.items = items;
    }
}

