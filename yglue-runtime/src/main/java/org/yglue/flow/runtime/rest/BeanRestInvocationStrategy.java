package org.yglue.flow.runtime.rest;

import org.springframework.context.ApplicationContext;
import org.yglue.flow.runtime.core.util.ExpressionEvaluator;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BeanRestInvocationStrategy implements RestInvocationStrategy {
    @Override
    public boolean supports(RestInvocationContext context) {
        Map<String, Object> cfg = context.getConfig();
        return cfg.containsKey("handler");
    }

    @Override
    public Object invoke(RestInvocationContext context) throws Exception {
        Map<String, Object> cfg = context.getConfig();
        String handler = String.valueOf(cfg.get("handler"));
        int idx = handler.lastIndexOf('.');
        if (idx <= 0) {
            throw new IllegalArgumentException("handler must be fqcn.method");
        }
        String className = handler.substring(0, idx);
        String methodName = handler.substring(idx + 1);
        ApplicationContext appCtx = context.getApplicationContext();
        Class<?> beanClass = Class.forName(className);
        Object bean = resolveBean(appCtx, beanClass);

        List<Object> arguments = new ArrayList<>();
        Object argsConfig = cfg.get("args");
        if (argsConfig instanceof List<?> list) {
            for (Object item : list) {
                arguments.add(ExpressionEvaluator.evaluate(item, context.getContext()));
            }
        }

        Method method = resolveMethod(bean.getClass(), methodName, arguments.size());
        return method.invoke(bean, convertArgs(method.getParameterTypes(), arguments));
    }

    private Object resolveBean(ApplicationContext appCtx, Class<?> beanClass) throws Exception {
        if (appCtx != null) {
            try {
                return appCtx.getBean(beanClass);
            } catch (Exception ignore) {
                // fall through to create new instance
            }
        }
        return beanClass.getDeclaredConstructor().newInstance();
    }

    private Method resolveMethod(Class<?> clazz, String name, int argc) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == argc) {
                return method;
            }
        }
        throw new IllegalArgumentException("Method not found: " + clazz.getName() + "." + name + "/" + argc);
    }

    private Object[] convertArgs(Class<?>[] types, List<Object> args) {
        Object[] out = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Object value = i < args.size() ? args.get(i) : null;
            out[i] = convert(value, types[i]);
        }
        return out;
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
