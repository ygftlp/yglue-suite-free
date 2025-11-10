package org.yglue.flow.orch.web.dto.flow.response;

import java.util.List;

public class EntryPointsResponse {

    private List<RestEntryPointResponse> rests;

    public EntryPointsResponse() {
    }

    public EntryPointsResponse(List<RestEntryPointResponse> rests) {
        this.rests = rests;
    }

    public List<RestEntryPointResponse> getRests() {
        return rests;
    }

    public void setRests(List<RestEntryPointResponse> rests) {
        this.rests = rests;
    }
}
