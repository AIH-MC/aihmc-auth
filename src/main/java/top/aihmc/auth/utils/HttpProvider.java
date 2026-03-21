package top.aihmc.auth.utils;

import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class HttpProvider {

    private static final Gson GSON = new Gson();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 统一响应包装类
     */
    public static class ApiResponse<T> {
        public final int statusCode;
        public final T body;

        public ApiResponse(int statusCode, T body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    /**
     * 发起 GET 请求
     */
    public static <T> CompletableFuture<ApiResponse<T>> get(String url, Class<T> responseClass, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json");

        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }

        return sendRequest(builder.build(), responseClass);
    }

    /**
     * 发起 POST 请求
     */
    public static <T> CompletableFuture<ApiResponse<T>> post(String url, Object body, Class<T> responseClass,
            String token) {
        String jsonBody = GSON.toJson(body).toString();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .version(HttpClient.Version.HTTP_1_1)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");

        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }

        return sendRequest(builder.build(), responseClass);
    }

    private static <T> CompletableFuture<ApiResponse<T>> sendRequest(HttpRequest request, Class<T> responseClass) {
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {

                    T parsedBody = null;
                    if (response.body() != null && !response.body().isEmpty()) {
                        try {
                            parsedBody = GSON.fromJson(response.body(), responseClass);
                        } catch (Exception e) {
                            System.err.println("JSON解析失败: " + e.getMessage());
                        }
                    }
                    return new ApiResponse<>(response.statusCode(), parsedBody);
                });
    }
}