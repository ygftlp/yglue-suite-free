package org.yglue.flow.sample.api;

import org.yglue.flow.annotations.FlowApi;
import org.yglue.flow.annotations.FlowOperation;
import org.springframework.stereotype.Service;

@FlowApi(name = "MathService", description = "常见数学能力：加/减/乘/除")
@Service
public class MathService {

    @FlowOperation(name = "add", description = "两数相加")
    public int add(int a, int b) { return a + b; }

    @FlowOperation(name = "sub", description = "两数相减")
    public int sub(int a, int b) { return a - b; }

    @FlowOperation(name = "mul", description = "两数相乘")
    public int mul(int a, int b) { return a * b; }

    @FlowOperation(name = "div", description = "两数相除（整数除法）")
    public int div(int a, int b) { return a / b; }
}
