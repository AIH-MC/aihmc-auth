package top.aihmc.auth.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import top.aihmc.auth.utils.ConfigManager;
import top.aihmc.auth.utils.MessageManager;
import top.aihmc.auth.utils.HttpProvider;
import top.aihmc.auth.utils.AuthValidationManager;
import top.aihmc.auth.listeners.PlayerListener;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class Register implements SimpleCommand {
    private final ProxyServer proxyServer;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final AuthValidationManager validationManager;
    private final PlayerListener playerListener;
    
    public Register(ProxyServer proxyServer, ConfigManager configManager, 
                   MessageManager messageManager, AuthValidationManager validationManager,
                   PlayerListener playerListener) {
        this.proxyServer = proxyServer;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.validationManager = validationManager;
        this.playerListener = playerListener;
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
                messageManager.getMessage("register-usage")
            );
            return;
        }
        
        if (invocation.source() instanceof Player player) {
            String password = args[0];
            String confirmPassword = args[1];
            
            // 检查两次密码是否一致
            if (!password.equals(confirmPassword)) {
                invocation.source().sendMessage(
                    messageManager.getMessage("passwords-not-match")
                );
                return;
            }
            
            // 检查验证超时和重试限制
            if (!validationManager.canAttemptAuth(player.getUniqueId())) {
                invocation.source().sendMessage(
                    messageManager.getMessage("in-cooldown")
                );
                return;
            }
            
            // 记录开始时间
            Instant startTime = Instant.now();
            
            // 检查是否超时
            if (!validationManager.isRegistrationValid(player.getUniqueId(), startTime)) {
                invocation.source().sendMessage(
                    messageManager.getMessage("registration-timeout",
                        "time", String.valueOf(validationManager.getRegistrationTimeoutSeconds()))
                );
                validationManager.recordAttempt(player.getUniqueId(), false);
                return;
            }
            
            // 执行注册请求
            performRegister(player, password, invocation);
        } else {
            invocation.source().sendMessage(
                messageManager.getMessage("player-only")
            );
        }
    }
    
    private void performRegister(Player player, String password, Invocation invocation) {
        // 构建请求体
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.username = player.getUsername();
        registerRequest.passwd = password;
        registerRequest.repasswd = password;  // 确认密码使用相同的值，因为前端已经验证过了
        registerRequest.ip = player.getRemoteAddress().getAddress().getHostAddress();
        
        // 构建请求URL
        String apiUrl = configManager.getApiUrl();
        String accessToken = configManager.getAccessToken();
        String registerUrl = apiUrl + "/offline/reg";
        
        // 发送注册请求
        CompletableFuture<HttpProvider.ApiResponse<RegisterResponse>> registerFuture = 
            HttpProvider.post(registerUrl, registerRequest, RegisterResponse.class, accessToken);
        
        registerFuture.thenAccept(response -> {
            if (response.statusCode == 200) {
                // 注册成功
                validationManager.recordAttempt(player.getUniqueId(), true);
                
                // 标记玩家为已登录（自动登录）
                playerListener.markPlayerAsLoggedIn(player);
                // 发送成功消息
            invocation.source().sendMessage(
                    messageManager.getMessage("command-register-success")
            );

                // 注册成功后传送到大厅服务器
                playerListener.redirectToLobby(player);

                // 向日志记录注册成功
                player.getCurrentServer().ifPresent(serverConnection -> {
                    serverConnection.getServer().sendMessage(
                        Component.text("[AIH-MC Auth] " + player.getUsername() + " 注册成功")
                    );
        });
            } else {
                // 注册失败，完全使用API返回的消息
                validationManager.recordAttempt(player.getUniqueId(), false);

                // 默认使用配置中的通用失败消息
                Component errorMessage = messageManager.getMessage("command-register-failed");
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
                messageManager.getMessage("command-register-network-error")
            );
            return null;
        });
    }
    
    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("aihmc.player");
    }

    // 注册请求体
    private static class RegisterRequest {
        String username;
        String passwd;
        String repasswd;
        String ip;
        
        // Gson需要默认构造函数
        public RegisterRequest() {}
    }
    
    // 注册响应体
    private static class RegisterResponse {
        Detail detail;
        
        static class Detail {
            String message;
        }
        
        // Gson需要默认构造函数
        public RegisterResponse() {}
    }
}

