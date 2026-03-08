package org.yglue.flow.sample;

import org.springframework.stereotype.Service;

/**
 * 测试服务类
 * 用于演示如何在规则中调用Spring Bean的方法
 */
@Service("testService")
public class TestService {
    
    /**
     * 测试方法B
     * 
     * @param a 输入参数
     * @param userDTO 用户对象
     * @return 处理结果
     */
    public String testB(String a, Object userDTO) {
        // 简单的处理逻辑
        return "Processed: " + a + ", User: " + userDTO;
    }
}