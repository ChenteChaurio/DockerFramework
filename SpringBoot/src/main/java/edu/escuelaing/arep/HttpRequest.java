package edu.escuelaing.arep;

import java.util.Map;

public class HttpRequest {
    private Map<String, String> queryParams;
    private String path;

    public HttpRequest(Map<String, String> queryParams) {
        this.queryParams = queryParams;
        this.path = "";
    }

    public HttpRequest(String path, Map<String, String> queryParams) {
        this.path = path;
        this.queryParams = queryParams;
    }

    public String getValues(String varname) {
        return queryParams.getOrDefault(varname, "");
    }

    public String getPath() {
        return path;
    }
}
