package org.yglue.flow.runtime.core.executors;

import org.springframework.context.ApplicationContext;
import org.yglue.flow.runtime.core.NodeExecutionContext;
import org.yglue.flow.runtime.core.FlowExecutor;
import org.yglue.flow.runtime.core.util.ExpressionEvaluator;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CallNodeExecutor implements FlowExecutor {

    private final ApplicationContext applicationContext;

    public CallNodeExecutor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object execute(NodeExecutionContext context) throws Exception {
        Map<String, Object> config = context.getNode().getConfig();
        String target = String.valueOf(config.get("target"));
        int idx = target.lastIndexOf('.');
        if (idx < 0) {
            throw new IllegalArgumentException("call target must be fqcn.method");
        }
        String className = target.substring(0, idx);
        String methodName = target.substring(idx + 1);
        Object bean = getBeanOrCreate(className);

        List<Object> arguments = new ArrayList<>();
        Object argsConfig = config.get("args");
        if (argsConfig instanceof List<?> list) {
            for (Object item : list) {
                arguments.add(ExpressionEvaluator.evaluate(item, context.getContext()));
            }
        }

        Method method = resolveMethod(bean.getClass(), methodName, arguments.size());
        Object result = method.invoke(bean, convertArgs(method.getParameterTypes(), arguments));
        Object alias = config.get("as");
        if (alias != null) {
            context.getContext().set(String.valueOf(alias), result);
        }
        return result;
    }

    private Object getBeanOrCreate(String className) throws Exception {
        Class<?> clazz = Class.forName(className);
        if (applicationContext != null) {
            try {
                return applicationContext.getBean(clazz);
            } catch (Exception ignore) {
            }
        }
        return clazz.getDeclaredConstructor().newInstance();
    }

    private Method resolveMethod(Class<?> clazz, String name, int argc) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == argc) {
                return method;
            }
        }
        throw new IllegalArgumentException("Method not found: " + clazz.getName() + "." + name + "/" + argc);
    }

    private Object[] convertArgs(Class<?>[] parameterTypes, List<Object> args) {
        Object[] converted = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Object value = i < args.size() ? args.get(i) : null;
            converted[i] = convert(value, parameterTypes[i]);
        }
        return converted;
    }

    private Object convert(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(String.valueOf(value));
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(String.valueOf(value));
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(String.valueOf(value));
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        return value;
    }
}
