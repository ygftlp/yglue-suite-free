package org.yglue.flow.orch.web.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yglue.flow.orch.domain.StatEvent;
import org.yglue.flow.orch.service.StatService;
import org.yglue.flow.orch.web.dto.stat.request.StatEventRequest;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectKey}/stats")
public class StatController {

    private final StatService statService;

    public StatController(StatService statService) {
        this.statService = statService;
    }

    @PostMapping("/events")
    public StatEvent push(@PathVariable("projectKey") String projectKey, @RequestBody @Valid StatEventRequest req) {
        return statService.recordEvent(projectKey, req);
    }

    @GetMapping("/events")
    public List<StatEvent> list(@PathVariable("projectKey") String projectKey) {
        return statService.list(projectKey);
    }
}
