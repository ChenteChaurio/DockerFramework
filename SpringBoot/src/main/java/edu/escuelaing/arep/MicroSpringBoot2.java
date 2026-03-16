package edu.escuelaing.arep;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

public class MicroSpringBoot2 {
    static Map<String, Method> controllerMethods = new HashMap<>();

    public static void main(String[] args)
            throws ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        System.out.println("Loading controller classes...");

        Class<?> c = Class.forName(args[0]);

        if (c.isAnnotationPresent(RestController.class)) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(GetMapping.class)) {
                    GetMapping a = m.getAnnotation(GetMapping.class);
                    controllerMethods.put(a.value(), m);
                }
            }
        }

        String[] parts = args[1].split("\\?", 2);
        String path = parts[0];
        Map<String, String> queryParams = new HashMap<>();
        if (parts.length == 2) {
            for (String pair : parts[1].split("&")) {
                String[] kv = pair.split("=");
                if (kv.length == 2)
                    queryParams.put(kv[0], kv[1]);
            }
        }

        System.out.println("Executing web method for path: " + path);

        Method m = controllerMethods.get(path);

        Object instance = null;
        if (!Modifier.isStatic(m.getModifiers())) {
            try {
                instance = c.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                System.err.println("Could not create instance: " + e.getMessage());
            }
        }

        Parameter[] parameters = m.getParameters();
        Object[] methodArgs = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            RequestParam rp = parameters[i].getAnnotation(RequestParam.class);
            if (rp != null) {
                methodArgs[i] = queryParams.getOrDefault(rp.value(), rp.defaultValue());
            }
        }

        System.out.println(m.invoke(instance, methodArgs));
    }
}
