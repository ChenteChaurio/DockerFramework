package edu.escuelaing.arep;

@RestController
public class HelloController {

    @GetMapping("/")
    public static String index() {
        return "Greetings from Spring Boot!";
    }

    @GetMapping("/pi")
    public static String webMethodPi() {
        return "Pi= " + Math.PI;
    }

    @GetMapping("/hello")
    public static String webMethodHello() {
        return "Hello World";
    }

}