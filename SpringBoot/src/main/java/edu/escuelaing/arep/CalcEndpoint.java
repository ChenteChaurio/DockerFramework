package edu.escuelaing.arep;

public class CalcEndpoint {

    public static void registerRoutes() {
        HttpServer.get("/App/calc", (req, res) -> {
            String leftParam = req.getValues("left");
            String rightParam = req.getValues("right");
            if (leftParam.isEmpty() || rightParam.isEmpty()) {
                return "Please provide 'left' and 'right' query params.";
            }
            int left = Integer.parseInt(leftParam);
            int right = Integer.parseInt(rightParam);
            return String.valueOf(left + right);
        });
    }
}
