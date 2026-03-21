package top.aihmc.auth.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import top.aihmc.auth.utils.ConfigManager;
import top.aihmc.auth.utils.HttpProvider;
import top.aihmc.auth.utils.MessageManager;

import java.util.concurrent.CompletableFuture;
import java.util.UUID;
import java.util.List;

public class ResetName implements SimpleCommand {
    
    private final ProxyServer proxyServer;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final String apiBaseUrl;
    private final String accessToken;
    
    public ResetName(ProxyServer proxyServer, ConfigManager configManager, 
                     MessageManager messageManager) {
        this.proxyServer = proxyServer;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.apiBaseUrl = configManager.getApiUrl();
        this.accessToken = configManager.getAccessToken();
    }
    
    @Override
    public void execute(Invocation invocation) {
        // 检查权限
        if (!invocation.source().hasPermission("aihmc.player")) {
            invocation.source().sendMessage(
                messageManager.getMessage("no-permission")
            );
            return;
        }
        
        // 确保命令由玩家执行
        if (!(invocation.source() instanceof com.velocitypowered.api.proxy.Player)) {
            invocation.source().sendMessage(
                messageManager.getMessage("player-only")
            );
            return;
        }
        
        com.velocitypowered.api.proxy.Player player = 
            (com.velocitypowered.api.proxy.Player) invocation.source();
        UUID playerId = player.getUniqueId();
        
        // 构建 API URL
        String puuid = playerId.toString().replace("-", "");
        String url = apiBaseUrl + "/profile/rstname/" + puuid;
        
        // 发送重置名称请求（使用 messages.yml 中的消息）
        invocation.source().sendMessage(
            messageManager.getMessage("resetname-processing")
        );
        
        CompletableFuture<HttpProvider.ApiResponse<Void>> future = 
            HttpProvider.get(url, Void.class, accessToken);
        
        future.thenAccept(response -> {
            Component message;
            
            if (response.statusCode == 200) {
                message = messageManager.getMessage("command-rstname-success");
                
                // 记录成功日志
                proxyServer.getConsoleCommandSource().sendMessage(
                    messageManager.getMessage("resetname-console-success",
                        "player", player.getUsername(),
                        "uuid", playerId.toString())
                );
            } else if (response.statusCode == 500) {
                message = messageManager.getMessage("command-rstname-server-error");
            } else {
                message = messageManager.getMessage("command-rstname-failed",
                    "code", String.valueOf(response.statusCode));
            }
            
            invocation.source().sendMessage(message);
        }).exceptionally(ex -> {
            // 网络或处理错误
            invocation.source().sendMessage(
                messageManager.getMessage("command-rstname-network-error")
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
}
