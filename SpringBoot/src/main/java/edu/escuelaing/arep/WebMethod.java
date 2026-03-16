package edu.escuelaing.arep;

public interface WebMethod {
    // public String execute();
    public String execute(HttpRequest request, HttpResponse response);
}
