package io.casehub.connectors.lifecycle.deployment;

import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

public class ConnectorBeanCreator implements BeanCreator<Object> {

    @Override
    public Object create(SyntheticCreationalContext<Object> context) {
        var params = context.getParams();
        var connectorId = (String) params.get("connectorId");
        var className = (String) params.get("className");
        var argKinds = (String[]) params.get("argKinds");
        var argNames = (String[]) params.get("argNames");
        var fieldNames = (String[]) params.get("fieldNames");
        var fieldDefaults = (String[]) params.get("fieldDefaults");

        try {
            var clazz = Thread.currentThread().getContextClassLoader().loadClass(className);

            if (argKinds == null || argKinds.length == 0) {
                return clazz.getDeclaredConstructor().newInstance();
            }

            var facade = context.getInjectedReference(ProviderCredentialFacade.class);

            Map<String, String> defaults = buildDefaultsMap(fieldNames, fieldDefaults);

            var argValues = new Object[argKinds.length];
            var argTypes = new Class<?>[argKinds.length];

            for (int i = 0; i < argKinds.length; i++) {
                if ("field".equals(argKinds[i])) {
                    var value = facade.resolve(connectorId, argNames[i])
                            .orElse(defaults.getOrDefault(argNames[i], ""));
                    argValues[i] = value;
                    argTypes[i] = String.class;
                } else {
                    var beanClass = Thread.currentThread().getContextClassLoader().loadClass(argNames[i]);
                    argValues[i] = context.getInjectedReference(beanClass);
                    argTypes[i] = beanClass;
                }
            }

            Constructor<?> constructor = findConstructor(clazz, argTypes);
            return constructor.newInstance(argValues);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create connector bean: " + className
                    + " for connector: " + connectorId, e);
        }
    }

    private static Map<String, String> buildDefaultsMap(String[] fieldNames, String[] fieldDefaults) {
        if (fieldNames == null || fieldDefaults == null) {
            return Map.of();
        }
        var defaults = new HashMap<String, String>();
        for (int i = 0; i < fieldNames.length; i++) {
            if (!fieldDefaults[i].isEmpty()) {
                defaults.put(fieldNames[i], fieldDefaults[i]);
            }
        }
        return defaults;
    }

    private static Constructor<?> findConstructor(Class<?> clazz, Class<?>[] argTypes) throws NoSuchMethodException {
        try {
            return clazz.getDeclaredConstructor(argTypes);
        } catch (NoSuchMethodException e) {
            for (var c : clazz.getDeclaredConstructors()) {
                if (c.getParameterCount() == argTypes.length && isAssignable(c.getParameterTypes(), argTypes)) {
                    return c;
                }
            }
            throw e;
        }
    }

    private static boolean isAssignable(Class<?>[] paramTypes, Class<?>[] argTypes) {
        for (int i = 0; i < paramTypes.length; i++) {
            if (!paramTypes[i].isAssignableFrom(argTypes[i])) {
                return false;
            }
        }
        return true;
    }
}
