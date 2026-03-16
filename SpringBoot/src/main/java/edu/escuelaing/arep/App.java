package edu.escuelaing.arep;

import static edu.escuelaing.arep.HttpServer.get;
import static edu.escuelaing.arep.HttpServer.staticfiles;
import static edu.escuelaing.arep.HttpServer.start;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;

public class App {
    public static void main(String[] args) throws Exception {
        staticfiles("/webroot");

        // Lambda-style endpoints
        get("/App/hello", (req, resp) -> "Hello " + req.getValues("name"));
        get("/App/pi", (req, resp) -> String.valueOf(Math.PI));
        get("/App/euler", (req, resp) -> "e= " + Math.E);

        java.net.URL location = App.class.getProtectionDomain().getCodeSource().getLocation();
        File classesRoot = new File(location.toURI());
        if (classesRoot.isFile()) {
            classesRoot = new File("target/classes");
        }
        loadControllers(classesRoot, classesRoot);

        start();
    }

    private static void loadControllers(File root, File dir) throws Exception {
        File[] files = dir.listFiles();
        if (files == null)
            return;
        for (File file : files) {
            if (file.isDirectory()) {
                loadControllers(root, file);
            } else if (file.getName().endsWith(".class")) {
                String className = file.getAbsolutePath()
                        .substring(root.getAbsolutePath().length() + 1)
                        .replace(File.separatorChar, '.').replace(".class", "");
                try {
                    Class<?> c = Class.forName(className);
                    if (c.isAnnotationPresent(RestController.class)) {
                        registerController(c);
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    System.err.println("Could not load class: " + className);
                }
            }
        }
    }

    private static void registerController(Class<?> c) {
        Object instance = null;
        try {
            instance = c.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            // static-only controller
        }
        final Object obj = instance;

        for (Method m : c.getDeclaredMethods()) {
            if (m.isAnnotationPresent(GetMapping.class)) {
                String path = m.getAnnotation(GetMapping.class).value();
                final boolean isStatic = Modifier.isStatic(m.getModifiers());
                get(path, (req, res) -> {
                    try {
                        Object[] params = resolveParams(m, req);
                        Object result = m.invoke(isStatic ? null : obj, params);
                        return result != null ? result.toString() : "";
                    } catch (Exception e) {
                        return "Error: " + e.getMessage();
                    }
                });
            }
        }
    }

    private static Object[] resolveParams(Method m, HttpRequest req) {
        Parameter[] parameters = m.getParameters();
        Object[] values = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            RequestParam rp = parameters[i].getAnnotation(RequestParam.class);
            if (rp != null) {
                String val = req.getValues(rp.value());
                values[i] = (val.isEmpty() && !rp.defaultValue().isEmpty()) ? rp.defaultValue() : val;
            }
        }
        return values;
    }
}
