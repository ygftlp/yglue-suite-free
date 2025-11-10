package org.yglue.flow.sample.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.yglue.flow.sample.api.MathService;

@RestController
public class MathController {
    private final MathService math;

    public MathController(MathService math) { this.math = math; }

    @GetMapping("/api/math/add")
    public int add(@RequestParam int a, @RequestParam int b) { return math.add(a,b); }
}
