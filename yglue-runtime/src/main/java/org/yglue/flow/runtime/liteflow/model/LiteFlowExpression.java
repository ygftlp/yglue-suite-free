package org.yglue.flow.runtime.liteflow.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * LiteFlow 表达式抽象类
 *
 * @author yglue
 * @since 1.0
 */
public abstract class LiteFlowExpression {

    /**
     * 转换为 EL 表达式字符串
     */
    public abstract String toElString();

    /**
     * THEN 顺序执行表达式
     */
    public static class ThenExpression extends LiteFlowExpression {
        private List<LiteFlowExpression> expressions = new ArrayList<>();

        public ThenExpression() {
        }

        public ThenExpression(List<LiteFlowExpression> expressions) {
            this.expressions = expressions;
        }

        public ThenExpression add(LiteFlowExpression expression) {
            if (expression != null) {
                this.expressions.add(expression);
            }
            return this;
        }

        public List<LiteFlowExpression> getExpressions() {
            return expressions;
        }

        @Override
        public String toElString() {
            if (expressions.isEmpty()) {
                return "THEN()";
            }
            List<String> parts = new ArrayList<>();
            for (LiteFlowExpression expr : expressions) {
                parts.add(expr.toElString());
            }
            return "THEN(" + String.join(", ", parts) + ")";
        }
    }

    /**
     * WHEN 并行执行表达式
     */
    public static class WhenExpression extends LiteFlowExpression {
        private List<LiteFlowExpression> expressions = new ArrayList<>();

        public WhenExpression() {
        }

        public WhenExpression(List<LiteFlowExpression> expressions) {
            this.expressions = expressions;
        }

        public WhenExpression add(LiteFlowExpression expression) {
            if (expression != null) {
                this.expressions.add(expression);
            }
            return this;
        }

        public List<LiteFlowExpression> getExpressions() {
            return expressions;
        }

        @Override
        public String toElString() {
            if (expressions.isEmpty()) {
                return "WHEN()";
            }
            List<String> parts = new ArrayList<>();
            for (LiteFlowExpression expr : expressions) {
                parts.add(expr.toElString());
            }
            return "WHEN(" + String.join(", ", parts) + ")";
        }
    }

    /**
     * IF 条件表达式
     */
    public static class IfExpression extends LiteFlowExpression {
        private String conditionComponent;
        private LiteFlowExpression thenExpression;
        private LiteFlowExpression elseExpression;

        public IfExpression() {
        }

        public IfExpression(String conditionComponent, LiteFlowExpression thenExpression, LiteFlowExpression elseExpression) {
            this.conditionComponent = conditionComponent;
            this.thenExpression = thenExpression;
            this.elseExpression = elseExpression;
        }

        public String getConditionComponent() {
            return conditionComponent;
        }

        public void setConditionComponent(String conditionComponent) {
            this.conditionComponent = conditionComponent;
        }

        public LiteFlowExpression getThenExpression() {
            return thenExpression;
        }

        public void setThenExpression(LiteFlowExpression thenExpression) {
            this.thenExpression = thenExpression;
        }

        public LiteFlowExpression getElseExpression() {
            return elseExpression;
        }

        public void setElseExpression(LiteFlowExpression elseExpression) {
            this.elseExpression = elseExpression;
        }

        @Override
        public String toElString() {
            StringBuilder sb = new StringBuilder();
            sb.append("IF(").append(conditionComponent);

            if (thenExpression != null) {
                sb.append(", ").append(thenExpression.toElString());
            } else {
                sb.append(", THEN()");
            }

            if (elseExpression != null) {
                sb.append(", ").append(elseExpression.toElString());
            }

            sb.append(")");
            return sb.toString();
        }
    }

    /**
     * SWITCH 分支表达式
     */
    public static class SwitchExpression extends LiteFlowExpression {
        private String switchComponent;
        private List<LiteFlowExpression> branches = new ArrayList<>();

        public SwitchExpression() {
        }

        public SwitchExpression(String switchComponent) {
            this.switchComponent = switchComponent;
        }

        public String getSwitchComponent() {
            return switchComponent;
        }

        public void setSwitchComponent(String switchComponent) {
            this.switchComponent = switchComponent;
        }

        public List<LiteFlowExpression> getBranches() {
            return branches;
        }

        public SwitchExpression addBranch(LiteFlowExpression branch) {
            if (branch != null) {
                this.branches.add(branch);
            }
            return this;
        }

        @Override
        public String toElString() {
            StringBuilder sb = new StringBuilder();
            sb.append("SWITCH(").append(switchComponent).append(")");

            if (!branches.isEmpty()) {
                sb.append(".TO(");
                List<String> parts = new ArrayList<>();
                for (LiteFlowExpression branch : branches) {
                    parts.add(branch.toElString());
                }
                sb.append(String.join(", ", parts));
                sb.append(")");
            } else {
                sb.append(".TO()");
            }

            return sb.toString();
        }
    }

    /**
     * 节点组件引用
     */
    @Data
    public static class NodeExpression extends LiteFlowExpression {
        private String componentId;
        private String componentName;

        public NodeExpression(String componentId, String componentName) {
            this.componentId = componentId;
            this.componentName = componentName;
        }

        @Override
        public String toElString() {
            return String.format("%s.tag(\"%s\")", componentName, componentId);
        }
    }
}
