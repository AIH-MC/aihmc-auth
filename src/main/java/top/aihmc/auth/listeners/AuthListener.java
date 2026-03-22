package top.aihmc.auth.listeners;

import com.google.gson.Gson;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import top.aihmc.auth.utils.ConfigManager;
import top.aihmc.auth.utils.HttpProvider;
import top.aihmc.auth.utils.MessageManager;
import top.aihmc.auth.utils.PlayerStateManager;  // Added import

import java.util.concurrent.CompletableFuture;

public class AuthListener {
    
    private final Gson gson = new Gson();
    private final ProxyServer proxyServer;
    private final ConfigManager configManager;
    private final MessageManager messageManager; // 添加 MessageManager 字段
    private final PlayerStateManager playerStateManager; // Added PlayerStateManager field

    // 修改构造函数来注入 MessageManager
    public AuthListener(ProxyServer proxyServer, ConfigManager configManager,
                       MessageManager messageManager, PlayerStateManager playerStateManager) { // Modified constructor
        this.proxyServer = proxyServer;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.playerStateManager = playerStateManager; // Initialize PlayerStateManager
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        String username = event.getUsername();
        
        // 将 Velocity 默认生成的带连字符 UUID 转为无连字符格式，适配 Yggdrasil API
        String puuid = event.getUniqueId().toString().replace("-", "");
        
        // 从配置读取 API 域名
        String apiBaseUrl = configManager.getApiUrl();

        // 构建完整的 URL
        String urlName = apiBaseUrl + "/auth/api/users/profiles/minecraft/" + username;
        String urlProfile = apiBaseUrl + "/auth/sessionserver/session/minecraft/profile/" + puuid;
        String urlCheck = apiBaseUrl + "/offline/check/" + username; // 新增：检查离线玩家是否已注册
        String apiCheck = apiBaseUrl;
        String accessToken = configManager.getAccessToken();

        // 使用现有的 HttpProvider 工具类发起请求
        CompletableFuture<HttpProvider.ApiResponse<AuthBody>> nameReq = 
                HttpProvider.get(urlName, AuthBody.class, null);
        CompletableFuture<HttpProvider.ApiResponse<AuthBody>> profileReq = 
                HttpProvider.get(urlProfile, AuthBody.class, null);
        CompletableFuture<HttpProvider.ApiResponse<CheckResponse>> checkReq =
                HttpProvider.get(urlCheck, CheckResponse.class, accessToken); // 新增：检查离线玩家
        CompletableFuture<HttpProvider.ApiResponse<CheckResponse>> apicheckReq =
                HttpProvider.get(apiCheck, CheckResponse.class, accessToken); 

        // 等待所有结果返回并执行逻辑分流
        PreLoginEvent.PreLoginComponentResult result = CompletableFuture.allOf(nameReq, profileReq, checkReq, apicheckReq)
                .thenApply(v -> {
                    HttpProvider.ApiResponse<AuthBody> nameRes = nameReq.join();
                    HttpProvider.ApiResponse<AuthBody> profileRes = profileReq.join();
                    HttpProvider.ApiResponse<CheckResponse> checkRes = checkReq.join(); // 新增：获取检查结果
                    HttpProvider.ApiResponse<CheckResponse> apicheckRes = apicheckReq.join();
                    if (apicheckRes.statusCode != 200){
                        return PreLoginEvent.PreLoginComponentResult.denied(
                            messageManager.getMessage("backend-error")
                        );
                    }
                    if (nameRes.statusCode == 200 && profileRes.statusCode == 204) {
                        String source = (nameRes.body != null && nameRes.body.source != null) ? 
                                nameRes.body.source : "未知认证源";
                        
                        // 提取用户名
                        String conflictName = (nameRes.body != null && nameRes.body.name != null) ?
                                nameRes.body.name : "未知玩家";

                        // 使用 MessageManager 获取消息组件
                        return PreLoginEvent.PreLoginComponentResult.denied(
                                messageManager.getMessage("auth-security-title")
                                        .append(Component.newline())
                                        .append(messageManager.getMessage("auth-name-conflict"))
                                        .append(Component.newline())
                                        .append(messageManager.getMessage("auth-name-registered",
                                                "name", conflictName, "source", source))
                                        .append(Component.newline())
                                        .append(messageManager.getMessage("auth-use-platform-login",
                                                "source", source))
                                        .append(Component.newline())
                                        .append(messageManager.getMessage("auth-not-your-account"))
                        );
                    }

                    if (nameRes.statusCode == 200 && profileRes.statusCode == 200) {
                        // 正版玩家，标记状态
                        playerStateManager.markAsPremium(event.getUniqueId()); // Added premium player marking
                        return PreLoginEvent.PreLoginComponentResult.forceOnlineMode();
                    }

                    // forceOfflineMode - 离线玩家处理
                    if (checkRes.statusCode == 200) {
                        // 状态码 200：玩家已注册，标记为已注册
                        playerStateManager.markAsRegistered(username);
                    } else {
                        // 状态码不是200：玩家未注册，不标记
                        // 后续会在PlayerListener中显示注册提示
                    }

                    return PreLoginEvent.PreLoginComponentResult.forceOfflineMode();
                })
                .exceptionally(ex -> {
                    // 使用 MessageManager 获取错误消息
                    return PreLoginEvent.PreLoginComponentResult.denied(
                            messageManager.getMessage("auth-server-unavailable")
                    );
                })
                .join();

        event.setResult(result);
    }

    // --- 内部数据模型 ---
    private static class AuthBody {
        String id;
        String name;
        String source;
        
        // Gson 需要默认构造函数
        public AuthBody() {}
        
        // Getter 和 Setter（可选，Gson 可以通过反射访问字段）
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    // 新增：检查响应模型
    private static class CheckResponse {
        Detail detail;

        static class Detail {
            String message;
        }

        public CheckResponse() {}

        public Detail getDetail() { return detail; }
        public void setDetail(Detail detail) { this.detail = detail; }
    }
}

