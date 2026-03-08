package org.yglue.flow.runtime.liteflow;

import org.junit.jupiter.api.Test;
import org.yglue.flow.runtime.liteflow.adapter.ServiceNodeComponent;

import static org.junit.jupiter.api.Assertions.*;

class ServiceNodeComponentTest {

    @Test
    void testServiceNodeComponentCreation() {
        // 创建ServiceNodeComponent实例
        ServiceNodeComponent component = new ServiceNodeComponent();
        
        // 验证组件不为空
        assertNotNull(component);
    }
}