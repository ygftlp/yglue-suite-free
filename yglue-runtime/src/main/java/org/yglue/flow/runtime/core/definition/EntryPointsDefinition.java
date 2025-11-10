package org.yglue.flow.runtime.core.definition;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class EntryPointsDefinition {

    private List<RestEntryPoint> rests = new ArrayList<>();

    public List<RestEntryPoint> getRests() {
        return rests;
    }

    public void setRests(List<RestEntryPoint> rests) {
        this.rests = rests == null ? new ArrayList<>() : new ArrayList<>(rests);
    }

    public List<RestEntryPoint> restsView() {
        return Collections.unmodifiableList(rests);
    }
}
