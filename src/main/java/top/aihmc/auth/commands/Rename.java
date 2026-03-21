package top.aihmc.auth.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import top.aihmc.auth.utils.ConfigManager;
import top.aihmc.auth.utils.HttpProvider;
import top.aihmc.auth.utils.MessageManager;
import top.aihmc.auth.utils.AuthValidationManager;
import net.kyori.adventure.text.Component;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Rename implements SimpleCommand {
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final AuthValidationManager validationManager;

    public Rename(ConfigManager configManager, MessageManager messageManager, AuthValidationManager validationManager) {
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.validationManager = validationManager;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!invocation.source().hasPermission("aihmc.player")) {
            invocation.source().sendMessage(
                messageManager.getMessage("no-permission")
            );
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 1) {
            invocation.source().sendMessage(
                messageManager.getMessage("usage", "usage", "/rename <新玩家名>")
            );
            return;
        }

        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(
                messageManager.getMessage("player-only")
            );
            return;
        }

        // 检查验证限制
        if (!validationManager.canAttemptAuth(player.getUniqueId())) {
            invocation.source().sendMessage(
                messageManager.getMessage("in-cooldown")
            );
            return;
        }

        String newUsername = args[0];
        
        // 验证用户名格式（允许Unicode字符U+0080到U+D7FF）
        if (!isValidUsername(newUsername)) {
            invocation.source().sendMessage(
                messageManager.getMessage("error", "error", "无效的用户名格式（仅允许字母、数字、下划线和部分Unicode字符）")
            );
            return;
        }

        // 准备API请求
        String apiUrl = configManager.getApiUrl() + "/profile/rename";
        String accessToken = configManager.getAccessToken();
        String playerUuid = player.getUniqueId().toString();

        // 构建请求体
        RenameRequest requestBody = new RenameRequest(playerUuid, newUsername);

        // 发送API请求
        CompletableFuture<HttpProvider.ApiResponse<RenameResponse>> apiResponse = 
            HttpProvider.post(apiUrl, requestBody, RenameResponse.class, accessToken);

        apiResponse.thenAccept(response -> {
            if (response.statusCode == 200) {
                // 改名成功
                validationManager.recordAttempt(player.getUniqueId(), true);
                invocation.source().sendMessage(
                    messageManager.getMessage("command-rename-success")
                );
            } else {
                // 改名失败，尝试从API响应中获取错误消息
                String errorMessage = "未知错误";
                if (response.body != null && response.body.detail != null && response.body.detail.message != null) {
                    errorMessage = response.body.detail.message;
                }
                
                validationManager.recordAttempt(player.getUniqueId(), false);
                invocation.source().sendMessage(
                    messageManager.getMessage("command-rename-failed")
                        .append(Component.text(": " + errorMessage))
                );
            }
        }).exceptionally(ex -> {
            // 网络或服务器错误
            validationManager.recordAttempt(player.getUniqueId(), false);
            invocation.source().sendMessage(
                messageManager.getMessage("error", "error", "网络或服务器错误")
            );
            return null;
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("aihmc.player");
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }

    // 验证用户名格式（允许字母、数字、下划线和Unicode字符U+0080到U+D7FF）
    private boolean isValidUsername(String username) {
        if (username == null || username.length() < 1 || username.length() > 16) {
            return false;
        }
        
        // 允许：
        // 1. 字母、数字、下划线：\w
        // 2. Unicode字符U+0080到U+D7FF：[\u0080-\ud7ff]
        // 注意：U+D800到U+DFFF是代理对区域，不应该单独出现
        return username.matches("^[\\w\\u0080-\\ud7ff]+$");
    }

    // 请求体类
    private static class RenameRequest {
        public final String uuid;
        public final String username;

        public RenameRequest(String uuid, String username) {
            this.uuid = uuid;
            this.username = username;
        }
    }

    // 响应体类
    private static class RenameResponse {
        public RenameResponseDetail detail;
        
        public static class RenameResponseDetail {
            public String message;
        }
    }
}

