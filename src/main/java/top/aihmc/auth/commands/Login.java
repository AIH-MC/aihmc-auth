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

public class Login implements SimpleCommand {
    private final ProxyServer proxyServer;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final AuthValidationManager validationManager;
    private final PlayerListener playerListener;
    
    public Login(ProxyServer proxyServer, ConfigManager configManager, 
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
        if (args.length < 1) {
            invocation.source().sendMessage(
                messageManager.getMessage("usage", "usage", "/login <密码>")
            );
            return;
        }
        
        if (invocation.source() instanceof Player player) {
            String password = args[0];
            
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
            if (!validationManager.isLoginValid(player.getUniqueId(), startTime)) {
                invocation.source().sendMessage(
                    messageManager.getMessage("login-timeout",
                        "time", String.valueOf(validationManager.getLoginTimeoutSeconds()))
                );
                validationManager.recordAttempt(player.getUniqueId(), false);
                return;
            }
            
            // 执行登录请求
            performLogin(player, password, invocation);
        } else {
            invocation.source().sendMessage(
                messageManager.getMessage("player-only")
            );
        }
    }
    
    private void performLogin(Player player, String password, Invocation invocation) {
        // 构建请求体
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.username = player.getUsername();
        loginRequest.passwd = password;
        
        // 构建请求URL
        String apiUrl = configManager.getApiUrl();
        String accessToken = configManager.getAccessToken();
        String loginUrl = apiUrl + "/offline/login";
        
        // 发送登录请求
        CompletableFuture<HttpProvider.ApiResponse<LoginResponse>> loginFuture = 
            HttpProvider.post(loginUrl, loginRequest, LoginResponse.class, accessToken);
        
        loginFuture.thenAccept(response -> {
            if (response.statusCode == 200) {
                // 登录成功
                validationManager.recordAttempt(player.getUniqueId(), true);
                
                // 标记玩家为已登录
                playerListener.markPlayerAsLoggedIn(player);

                invocation.source().sendMessage(
                    messageManager.getMessage("command-login-success")
                );

                // 登录成功后传送到大厅服务器
                playerListener.redirectToLobby(player);
            } else {
                // 登录失败，完全使用API返回的消息
                validationManager.recordAttempt(player.getUniqueId(), false);
                
                // 默认使用配置中的通用失败消息
                Component errorMessage = messageManager.getMessage("command-login-failed");
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
                messageManager.getMessage("command-login-network-error")
            );
            return null;
        });
    }
    
    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("aihmc.player");
    }
    
    // 登录请求体
    private static class LoginRequest {
        String username;
        String passwd;
        
        // Gson需要默认构造函数
        public LoginRequest() {}
    }
    
    // 登录响应体
    private static class LoginResponse {
        Detail detail;
        
        static class Detail {
            String message;
        }
        
        // Gson需要默认构造函数
        public LoginResponse() {}
    }
}

