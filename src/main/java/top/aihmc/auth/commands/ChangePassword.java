package top.aihmc.auth.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import top.aihmc.auth.utils.ConfigManager;
import top.aihmc.auth.utils.MessageManager;
import top.aihmc.auth.utils.HttpProvider;
import top.aihmc.auth.utils.AuthValidationManager;
import java.util.concurrent.CompletableFuture;

public class ChangePassword implements SimpleCommand {
    private final ProxyServer proxyServer;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final AuthValidationManager validationManager;
    
    public ChangePassword(ProxyServer proxyServer, ConfigManager configManager, 
                         MessageManager messageManager, AuthValidationManager validationManager) {
        this.proxyServer = proxyServer;
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
        if (args.length < 2) {
            invocation.source().sendMessage(
                messageManager.getMessage("changepw-usage")
            );
            return;
        }
        
        if (invocation.source() instanceof Player player) {
            String oldPassword = args[0];
            String newPassword = args[1];
            
            // 检查新密码长度和复杂度（可选）
            if (newPassword.length() < 6) {
                invocation.source().sendMessage(
                    messageManager.getMessage("password-too-short")
                );
                return;
            }
            
            // 检查冷却时间
            if (!validationManager.canAttemptAuth(player.getUniqueId())) {
                invocation.source().sendMessage(
                    messageManager.getMessage("in-cooldown")
                );
                return;
            }
            
            // 执行修改密码请求
            performChangePassword(player, oldPassword, newPassword, invocation);
        } else {
            invocation.source().sendMessage(
                messageManager.getMessage("player-only")
            );
        }
    }
    
    private void performChangePassword(Player player, String oldPassword, String newPassword, Invocation invocation) {
        // 构建请求体
        ChangePasswordRequest changePasswordRequest = new ChangePasswordRequest();
        changePasswordRequest.username = player.getUsername();
        changePasswordRequest.passwd = oldPassword;
        changePasswordRequest.newpasswd = newPassword;
        
        // 构建请求URL
        String apiUrl = configManager.getApiUrl();
        String accessToken = configManager.getAccessToken();
        String changePasswordUrl = apiUrl + "/offline/chpasswd";
        
        // 发送修改密码请求
        CompletableFuture<HttpProvider.ApiResponse<ChangePasswordResponse>> changePasswordFuture = 
            HttpProvider.post(changePasswordUrl, changePasswordRequest, ChangePasswordResponse.class, accessToken);
        
        changePasswordFuture.thenAccept(response -> {
            if (response.statusCode == 200) {
                // 修改密码成功
                validationManager.recordAttempt(player.getUniqueId(), true);
                
                // 发送成功消息
                invocation.source().sendMessage(
                    messageManager.getMessage("command-changepw-success")
                );
                
                // 向日志记录密码修改成功
                player.getCurrentServer().ifPresent(serverConnection -> {
                    // 手动替换占位符，因为服务器消息需要纯文本
                    String logMessageTemplate = "[AIH-MC Auth] %player% 修改密码成功";
                    String logMessage = logMessageTemplate.replace("%player%", player.getUsername());
                    serverConnection.getServer().sendMessage(
                        Component.text(logMessage)
                    );
                });
                
                // 可选：修改密码后踢出玩家重新登录
                // player.disconnect(Component.text("密码已修改，请重新登录"));
            } else {
                // 修改密码失败，完全使用API返回的消息
                validationManager.recordAttempt(player.getUniqueId(), false);
                
                // 默认使用配置中的通用失败消息
                Component errorMessage = messageManager.getMessage("command-changepw-failed");
                if (response.body != null && response.body.detail != null && response.body.detail.message != null) {
                    // API返回了具体错误消息，直接使用它（不需要MessageManager转换）
                    errorMessage = Component.text(response.body.detail.message);
                }
                
                invocation.source().sendMessage(errorMessage);
            }
        }).exceptionally(ex -> {
            // 网络或处理异常，使用配置中的网络错误消息
            validationManager.recordAttempt(player.getUniqueId(), false);
            invocation.source().sendMessage(
                messageManager.getMessage("command-changepw-network-error")
            );
            return null;
        });
    }
    
    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("aihmc.player");
    }
    
    // 修改密码请求体
    private static class ChangePasswordRequest {
        String username;
        String passwd;
        String newpasswd;
        
        // Gson需要默认构造函数
        public ChangePasswordRequest() {}
    }
    
    // 修改密码响应体
    private static class ChangePasswordResponse {
        Detail detail;
        
        static class Detail {
            String message;
        }
        
        // Gson需要默认构造函数
        public ChangePasswordResponse() {}
    }
}

