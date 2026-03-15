package top.aihmc.plugin;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpHandler {
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void postPlayerData(String baseUrl, String token, Player player, ComponentLogger logger) {
        String uuid = player.getUniqueId().toString();
        String trimmedUuid = uuid.replace("-", "");
        String name = player.getUsername();
        String json = String.format("{\"uuid\":\"%s\", \"username\":\"%s\"}", trimmedUuid, name);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/figura/register"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        // 提交成功，给玩家发送绿色消息
                        player.sendMessage(
                                Component.text("[AIHMC] ", NamedTextColor.GOLD)
                                        .append(Component.text("您的 Figura 后端数据已成功注册，现在可以用 Figura 模型了！", NamedTextColor.GREEN)));
                    } else {
                        logger.warn("API 提交失败，状态码: {}", response.statusCode());
                        // 提交失败，给玩家发送警告消息
                        Component.text("[AIHMC] ", NamedTextColor.GOLD)
                                        .append(Component.text("您的 Figura 后端数据注册失败，请联系管理员！", NamedTextColor.RED));
                    }
                })
                .exceptionally(ex -> {
                    logger.error("API 请求发生错误: {}", ex.getMessage());
                    return null;
                });
    }
}