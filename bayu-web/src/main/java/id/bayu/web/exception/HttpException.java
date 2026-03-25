package id.bayu.web.exception;

/**
 * Throw this from controllers/services to return a specific HTTP status + message.
 * BayuHttpServer catches it and formats the response automatically.
 */
public class HttpException extends RuntimeException {

    private final int status;

    public HttpException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }

    public static HttpException badRequest(String message) { return new HttpException(400, message); }
    public static HttpException unauthorized(String message) { return new HttpException(401, message); }
    public static HttpException forbidden(String message) { return new HttpException(403, message); }
    public static HttpException notFound(String message) { return new HttpException(404, message); }
    public static HttpException conflict(String message) { return new HttpException(409, message); }
    public static HttpException internal(String message) { return new HttpException(500, message); }
}
