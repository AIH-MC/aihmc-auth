package top.aihmc.auth.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import top.aihmc.auth.utils.*;
import com.google.gson.Gson;

import java.util.concurrent.CompletableFuture;
import java.util.List;

public class Migrate implements SimpleCommand {
    private final ProxyServer proxyServer;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Gson gson = new Gson();

    public Migrate(ProxyServer proxyServer, ConfigManager configManager, MessageManager messageManager) {
        this.proxyServer = proxyServer;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();

        // 检查权限
        if (!invocation.source().hasPermission("aihmc.admin")) {
            invocation.source().sendMessage(messageManager.getMessage("no-permission"));
            return;
        }

        // 检查参数数量
        if (args.length < 2) {
            invocation.source().sendMessage(
                messageManager.getMessage("migrate-usage")
            );
            return;
        }

        String oldUuidStr = args[0];
        String newUuidStr = args[1];

        // 验证UUID不能为空
        if (oldUuidStr.isEmpty() || newUuidStr.isEmpty()) {
            invocation.source().sendMessage(
                messageManager.getMessage("uuid-cannot-be-empty")
            );
            return;
        }

        // 验证UUID格式
        try {
            // 移除可能存在的连字符
            oldUuidStr = oldUuidStr.replace("-", "");
            newUuidStr = newUuidStr.replace("-", "");

            // 检查长度
            if (oldUuidStr.length() != 32 || newUuidStr.length() != 32) {
                invocation.source().sendMessage(
                    messageManager.getMessage("uuid-invalid-length")
                );
                return;
            }

            // 检查两个UUID是否相同
            if (oldUuidStr.equals(newUuidStr)) {
                invocation.source().sendMessage(
                    messageManager.getMessage("uuid-same-error")
                );
                return;
            }

            // 转换为标准UUID格式
            String formattedOldUuid = formatUuid(oldUuidStr);
            String formattedNewUuid = formatUuid(newUuidStr);

            // 发起API请求
            performMigration(invocation, formattedOldUuid, formattedNewUuid);

        } catch (Exception e) {
            invocation.source().sendMessage(
                messageManager.getMessage("uuid-invalid-format")
            );
        }
    }

    private void performMigration(Invocation invocation, String oldUuid, String newUuid) {
        String apiBaseUrl = configManager.getApiUrl();
        String accessToken = configManager.getAccessToken();
        
        // 构建请求URL
        String url = apiBaseUrl + "/profile/linkprofile";
        
        // 构建请求体
        MigrationRequest requestBody = new MigrationRequest(oldUuid, newUuid);
        
        // 发起POST请求
        CompletableFuture<HttpProvider.ApiResponse<MigrationResponse>> future = 
            HttpProvider.post(url, requestBody, MigrationResponse.class, accessToken);
        
        future.thenAccept(response -> {
            if (response.statusCode == 200) {
                // 迁移成功
                invocation.source().sendMessage(
                    messageManager.getMessage("command-migrate-success")
                );
                
                // 向控制台发送成功日志
                proxyServer.getConsoleCommandSource().sendMessage(
                    messageManager.getMessage("migrate-console-success",
                        "old_uuid", oldUuid,
                        "new_uuid", newUuid)
                );

                // 检查并踢出原UUID对应的在线玩家
                kickOldUuidPlayer(oldUuid);

            } else if (response.statusCode == 400 || response.statusCode == 404) {
                // 处理错误响应
                if (response.body != null && response.body.detail != null) {
                    String errorMessage = response.body.detail.message;
                    invocation.source().sendMessage(
                        Component.text("§c数据迁移失败: " + errorMessage)
                    );
                    
                    // 向控制台发送失败日志
                    proxyServer.getConsoleCommandSource().sendMessage(
                        messageManager.getMessage("migrate-console-failed",
                            "error", errorMessage)
                    );
                } else {
                    invocation.source().sendMessage(
                        messageManager.getMessage("command-migrate-failed")
                    );
                }
            } else {
                // 网络错误或其他错误
                invocation.source().sendMessage(
                    messageManager.getMessage("command-migrate-network-error")
                );
                
                // 向控制台发送网络错误日志
                proxyServer.getConsoleCommandSource().sendMessage(
                    messageManager.getMessage("migrate-console-network-error",
                        "error", "HTTP " + response.statusCode)
                );
            }
        }).exceptionally(ex -> {
            // 处理异常
            invocation.source().sendMessage(
                messageManager.getMessage("command-migrate-network-error")
            );
            
            // 向控制台发送异常日志
            proxyServer.getConsoleCommandSource().sendMessage(
                messageManager.getMessage("migrate-console-network-error",
                    "error", ex.getMessage())
            );
            return null;
        });
    }

    /**
     * 踢出原UUID对应的在线玩家（如果在线）
     */
    private void kickOldUuidPlayer(String oldUuid) {
        try {
            // 尝试查找原UUID对应的玩家
            proxyServer.getPlayer(java.util.UUID.fromString(oldUuid)).ifPresent(player -> {
                // 构建踢出消息
                Component kickMessage = messageManager.getMessage("migrate-kick-player")
                    .append(Component.newline())
                    .append(messageManager.getMessage("migrate-kick-reason"));

                // 踢出玩家
                player.disconnect(kickMessage);

                // 发送踢出日志到控制台
                proxyServer.getConsoleCommandSource().sendMessage(
                    messageManager.getMessage("migrate-kick-console-log",
                        "player", player.getUsername(),
                        "uuid", oldUuid)
                );
            });
        } catch (IllegalArgumentException e) {
            // UUID格式无效，不踢出玩家
            proxyServer.getConsoleCommandSource().sendMessage(
                Component.text("§c[AIH-MC Auth] 无效的UUID格式，无法踢出玩家: " + oldUuid)
            );
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("aihmc.admin");
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(List.of());
    }
    /**
     * 将32位无连字符UUID转换为标准UUID格式
     */
    private String formatUuid(String uuidWithoutDashes) {
        return uuidWithoutDashes.substring(0, 8) + "-" +
               uuidWithoutDashes.substring(8, 12) + "-" +
               uuidWithoutDashes.substring(12, 16) + "-" +
               uuidWithoutDashes.substring(16, 20) + "-" +
               uuidWithoutDashes.substring(20, 32);
    }
    /**
     * 迁移请求体
     */
    private static class MigrationRequest {
        String uuid;
        String new_uuid;

        public MigrationRequest(String uuid, String new_uuid) {
            this.uuid = uuid;
            this.new_uuid = new_uuid;
        }
    }

    /**
     * 迁移响应体
     */
    private static class MigrationResponse {
        Detail detail;

        static class Detail {
            String message;
        }
    }
}
