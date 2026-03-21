package top.aihmc.auth.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import top.aihmc.auth.utils.ConfigManager;
import top.aihmc.auth.utils.MessageManager;
import top.aihmc.auth.utils.AuthValidationManager;
import top.aihmc.auth.utils.PlayerStateManager;
import top.aihmc.auth.utils.HttpProvider;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PlayerListener {
    private final ProxyServer proxyServer;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final AuthValidationManager validationManager;
    private final PlayerStateManager playerStateManager;

    // 用于跟踪已显示Title的玩家，避免重复显示
    private final Set<UUID> playersWithTitleShown = new HashSet<>();
    // 用于记录已经提交过Figura注册的玩家，避免重复提交
    private final Set<UUID> figuraRegisteredPlayers = new HashSet<>(); // 新增字段

    public PlayerListener(ProxyServer proxyServer, ConfigManager configManager,
            MessageManager messageManager, AuthValidationManager validationManager,
            PlayerStateManager playerStateManager) {
        this.proxyServer = proxyServer;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.validationManager = validationManager;
        this.playerStateManager = playerStateManager;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String serverName = event.getServer().getServerInfo().getName();
        UUID playerId = player.getUniqueId();

        submitFiguraRegistration(player);

        // 从配置获取服务器名称
        Map<String, Object> configData = configManager.getConfigData();
        String authServer = getServerName(configData, "auth");

        // 如果玩家连接到 auth 服务器且是正版玩家，重定向到 lobby
        if (serverName.equals(authServer) && playerStateManager.isPremium(playerId)) {
            // 延迟一小段时间确保玩家完全连接
            proxyServer.getScheduler()
                    .buildTask(proxyServer.getPluginManager().getPlugin("aihmc-auth").orElse(null), () -> {
                        redirectToLobby(player);
                    }).delay(500, java.util.concurrent.TimeUnit.MILLISECONDS).schedule();
        }

        // 如果玩家连接到 auth 服务器且未登录
        if (serverName.equals(authServer) && !playerStateManager.isLoggedIn(playerId)) {
            // 延迟一小段时间确保玩家完全连接
            proxyServer.getScheduler()
                    .buildTask(proxyServer.getPluginManager().getPlugin("aihmc-auth").orElse(null), () -> {
                        showAuthTitle(player);
                    }).delay(1000, java.util.concurrent.TimeUnit.MILLISECONDS).schedule();
        }
    }

    private void showAuthTitle(Player player) {
        UUID playerId = player.getUniqueId();
        String username = player.getUsername();

        // 如果已经显示过Title，不再重复显示
        if (playersWithTitleShown.contains(playerId)) {
            return;
        }

        // 检查玩家是否已注册
        boolean isRegistered = playerStateManager.isRegistered(username);

        Component title, subtitle;
        if (isRegistered) {
            // 已注册但未登录：显示登录提示
            title = messageManager.getMessage("auth-title-login-required");
            subtitle = messageManager.getMessage("auth-subtitle-login-command");
        } else {
            // 未注册：显示注册提示
            title = messageManager.getMessage("auth-title-register-required");
            subtitle = messageManager.getMessage("auth-subtitle-register-command");
        }
        // 从配置中获取标题时长设置
        int fadeInSeconds = configManager.getTitleFadeInSeconds();
        int staySeconds = configManager.getTitleStaySeconds();
        int fadeOutSeconds = configManager.getTitleFadeOutSeconds();
        int repeatIntervalSeconds = configManager.getTitleRepeatIntervalSeconds();
        // 创建Title对象
        Title titleObj = Title.title(
                title,
                subtitle,
                Title.Times.times(
                        Duration.ofSeconds(fadeInSeconds), // 渐入时间（从配置读取）
                        Duration.ofSeconds(staySeconds), // 保持时间（从配置读取）
                        Duration.ofSeconds(fadeOutSeconds) // 渐出时间（从配置读取）
                ));

        // 显示Title
        player.showTitle(titleObj);

        // 标记为已显示
        playersWithTitleShown.add(playerId);

        // 添加一个周期性任务来重新显示Title（每30秒一次）
        proxyServer.getScheduler()
                .buildTask(proxyServer.getPluginManager().getPlugin("aihmc-auth").orElse(null), () -> {
                    // 如果玩家仍然未登录且在auth服务器上，重新显示Title
                    if (!playerStateManager.isLoggedIn(playerId) &&
                            player.getCurrentServer().isPresent() &&
                            getServerName(configManager.getConfigData(), "auth").equals(
                                    player.getCurrentServer().get().getServerInfo().getName())) {

                        player.showTitle(titleObj);
                    } else {
                        // 如果玩家已登录或离开auth服务器，取消后续任务
                        playersWithTitleShown.remove(playerId);
                    }
                }).delay(repeatIntervalSeconds, java.util.concurrent.TimeUnit.SECONDS)
                .repeat(repeatIntervalSeconds, java.util.concurrent.TimeUnit.SECONDS)
                .schedule();
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        String targetServer = event.getOriginalServer().getServerInfo().getName();
        UUID playerId = player.getUniqueId();

        // 从配置获取服务器名称
        Map<String, Object> configData = configManager.getConfigData();
        String lobbyServer = getServerName(configData, "lobby");
        String authServer = getServerName(configData, "auth");

        // 如果玩家已登录（包括正版玩家），允许连接到任何服务器
        if (playerStateManager.isLoggedIn(playerId)) {
            // 如果玩家离开auth服务器，清除Title显示记录
            if (player.getCurrentServer().isPresent() &&
                    player.getCurrentServer().get().getServerInfo().getName().equals(authServer)) {
                playersWithTitleShown.remove(playerId);
            }
            return;
        }

        // 如果玩家未登录，只能连接到 auth 服务器
        if (!targetServer.equals(authServer)) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());

            // 如果玩家正在尝试离开 auth 服务器，发送提示消息
            player.getCurrentServer().ifPresent(currentServer -> {
                if (currentServer.getServerInfo().getName().equals(authServer)) {
                    player.sendMessage(
                            messageManager.getMessage("error", "error", "请先登录后再切换到其他服务器"));
                }
            });

            // 如果玩家还没有服务器，重定向到 auth 服务器
            if (!player.getCurrentServer().isPresent()) {
                proxyServer.getServer(authServer).ifPresent(server -> {
                    player.createConnectionRequest(server).connect();
                });
            }
        }
    }

    @Subscribe
    public void onCommandExecute(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) {
            return;
        }

        // 直接从 configManager 获取已加载的白名单
        List<String> allowedCommands = configManager.getAllowedCommands();

        // 获取原始指令（去除空格并转小写）
        String command = event.getCommand().toLowerCase().trim();
        // 确保匹配以 / 开头，因为配置中通常带有 /
        if (!command.startsWith("/")) {
            command = "/" + command;
        }
        String baseCommand = command.split(" ")[0];

        // 如果玩家未登录且命令不在白名单中，则拦截
        if (!playerStateManager.isLoggedIn(player.getUniqueId()) && !allowedCommands.contains(baseCommand)) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(
                    messageManager.getMessage("error", "error", "请先登录后再使用此命令"));
        }
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();

        // 如果玩家未登录，禁止聊天
        if (!playerStateManager.isLoggedIn(player.getUniqueId())) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            player.sendMessage(
                    messageManager.getMessage("error", "error", "请先登录后再聊天"));
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String username = player.getUsername();
        // 玩家断开连接时清理登录状态和Title记录
        playerStateManager.logout(playerId, username);
        validationManager.clearAttempts(playerId);
        playersWithTitleShown.remove(playerId);
        figuraRegisteredPlayers.remove(playerId);
    }

    // 公共方法供命令类调用
    public void markPlayerAsLoggedIn(Player player) {
        UUID playerId = player.getUniqueId();
        playerStateManager.markAsLoggedIn(playerId);
        // 清除Title显示记录
        playersWithTitleShown.remove(playerId);
        // 清除玩家的Title显示
        player.clearTitle();
    }

    private void submitFiguraRegistration(Player player) {
        UUID playerId = player.getUniqueId();
        String username = player.getUsername();

        // 检查是否已经提交过，避免重复提交
        if (figuraRegisteredPlayers.contains(playerId)) {
            return;
        }

        // 构建请求数据
        Map<String, String> figuraRequest = new HashMap<>();
        figuraRequest.put("uuid", playerId.toString().replace("-",""));
        figuraRequest.put("username", username);

        // 构建请求URL和token
        String apiUrl = configManager.getApiUrl();
        String accessToken = configManager.getAccessToken();
        String figuraUrl = apiUrl + "/figura/register";

        // 发送Figura注册请求
        CompletableFuture<HttpProvider.ApiResponse<FiguraResponse>> figuraFuture = HttpProvider.post(figuraUrl,
                figuraRequest, FiguraResponse.class, accessToken);

        figuraFuture.thenAccept(response -> {
            if (response.statusCode == 200) {
                // 注册成功，标记为已注册
                figuraRegisteredPlayers.add(playerId);

                // 向玩家发送成功消息
                player.sendMessage(
                        messageManager.getMessage("figura-register-success"));

                // 可选：记录到日志
                proxyServer.getConsoleCommandSource().sendMessage(
                        Component.text("[AIH-MC Auth] " + username + " 的Figura注册已提交成功"));
            } else {
                // 注册失败，记录但不影响玩家登录
                proxyServer.getConsoleCommandSource().sendMessage(
                        Component.text("[AIH-MC Auth] " + username + " 的Figura注册提交失败，状态码: " + response.statusCode));

                // 检查是否有具体的错误消息
                if (response.body != null && response.body.detail != null && response.body.detail.message != null) {
                    proxyServer.getConsoleCommandSource().sendMessage(
                            Component.text("[AIH-MC Auth] 错误信息: " + response.body.detail.message));
                }
            }
        }).exceptionally(ex -> {
            // 网络或处理异常
            proxyServer.getConsoleCommandSource().sendMessage(
                    Component.text("[AIH-MC Auth] " + username + " 的Figura注册提交异常: " + ex.getMessage()));
            return null;
        });
    }

    public boolean isPlayerLoggedIn(Player player) {
        return playerStateManager.isLoggedIn(player.getUniqueId());
    }

    public void redirectToLobby(Player player) {
        Map<String, Object> configData = configManager.getConfigData();
        String lobbyServer = getServerName(configData, "lobby");

        proxyServer.getServer(lobbyServer).ifPresent(server -> {
            player.createConnectionRequest(server).connect();
        });
        
    }

    // 辅助方法
    private String getServerName(Map<String, Object> configData, String serverType) {
        if (configData != null && configData.containsKey("servers")) {
            Map<String, Object> servers = (Map<String, Object>) configData.get("servers");
            return (String) servers.getOrDefault(serverType, serverType);
        }
        return serverType;
    }

    private static class FiguraResponse {
        Detail detail;

        static class Detail {
            String message;
        }

        // Gson需要默认构造函数
        public FiguraResponse() {
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getAllowedCommands() {
        Map<String, Object> configData = configManager.getConfigData();
        if (configData != null && configData.containsKey("restrictions")) {
            Map<String, Object> restrictions = (Map<String, Object>) configData.get("restrictions");
            if (restrictions.containsKey("allowed-commands")) {
                return (List<String>) restrictions.get("allowed-commands");
            }
        }
        return Arrays.asList("/login", "/register", "/l", "/reg");
    }
}