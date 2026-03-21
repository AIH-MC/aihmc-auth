package top.aihmc.auth.utils;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.time.Instant;

public class AuthValidationManager {
    private final ComponentLogger logger;
    private final Map<UUID, PlayerAuthData> playerDataMap = new HashMap<>();
    
    // 配置项
    private int registrationTimeoutSeconds = 300; // 5分钟
    private int loginTimeoutSeconds = 180; // 3分钟
    private int maxRetryAttempts = 3;
    private int cooldownMinutes = 10;
    
    // 从 config.yml 加载的服务器配置
    private String authServer;
    private String lobbyServer;
    
    public AuthValidationManager(ComponentLogger logger) {
        this.logger = logger;
    }
    
    public void loadConfig(Map<String, Object> configData) {
        Map<String, Object> servers = (Map<String, Object>) configData.get("servers");
        if (servers != null) {
            this.authServer = (String) servers.getOrDefault("auth", "auth");
            this.lobbyServer = (String) servers.getOrDefault("lobby", "lobby");
        }
        
        Map<String, Object> restrictions = (Map<String, Object>) configData.get("restrictions");
        if (restrictions != null) {
            this.registrationTimeoutSeconds = (int) restrictions.getOrDefault("registration-timeout-seconds", 300);
            this.loginTimeoutSeconds = (int) restrictions.getOrDefault("login-timeout-seconds", 180);
            this.maxRetryAttempts = (int) restrictions.getOrDefault("max-retry-attempts", 3);
            this.cooldownMinutes = (int) restrictions.getOrDefault("cooldown-minutes", 10);
        }
    }
    
    public boolean canAttemptAuth(UUID playerId) {
        PlayerAuthData data = playerDataMap.get(playerId);
        if (data == null) return true;
        
        if (data.isInCooldown()) {
            return false;
        }
        
        if (data.getRetryCount() >= maxRetryAttempts) {
            data.startCooldown(cooldownMinutes);
            return false;
        }
        
        return true;
    }
    
    public void recordAttempt(UUID playerId, boolean success) {
        PlayerAuthData data = playerDataMap.computeIfAbsent(playerId, k -> new PlayerAuthData());
        
        if (success) {
            data.reset();
        } else {
            data.incrementRetry();
            if (data.getRetryCount() >= maxRetryAttempts) {
                data.startCooldown(cooldownMinutes);
            }
        }
    }
    
    public boolean isRegistrationValid(UUID playerId, Instant startTime) {
        PlayerAuthData data = playerDataMap.get(playerId);
        if (data == null) return true;
        
        return !data.hasRegistrationTimedOut(registrationTimeoutSeconds, startTime);
    }
    
    public boolean isLoginValid(UUID playerId, Instant startTime) {
        PlayerAuthData data = playerDataMap.get(playerId);
        if (data == null) return true;
        
        return !data.hasLoginTimedOut(loginTimeoutSeconds, startTime);
    }
    
    public String getAuthServer() { return authServer; }
    public String getLobbyServer() { return lobbyServer; }
    public int getRegistrationTimeoutSeconds() { return registrationTimeoutSeconds; }
    public int getLoginTimeoutSeconds() { return loginTimeoutSeconds; }
    public int getMaxRetryAttempts() { return maxRetryAttempts; }
    public int getCooldownMinutes() { return cooldownMinutes; }
    
    public void clearPlayerData(UUID playerId) {
        playerDataMap.remove(playerId);
    }
    
    // 清理玩家的验证尝试记录
    public void clearAttempts(UUID playerId) {
        playerDataMap.remove(playerId);
    }

    // 清理所有玩家的验证记录（可选）
    public void clearAllAttempts() {
        playerDataMap.clear();
    }

    private static class PlayerAuthData {
        private int retryCount = 0;
        private Instant cooldownUntil;
        private Instant registrationStartTime;
        private Instant loginStartTime;
        
        public void incrementRetry() {
            retryCount++;
        }
        
        public void reset() {
            retryCount = 0;
            cooldownUntil = null;
            registrationStartTime = null;
            loginStartTime = null;
        }
        
        public int getRetryCount() { return retryCount; }
        
        public boolean isInCooldown() {
            return cooldownUntil != null && Instant.now().isBefore(cooldownUntil);
        }
        
        public void startCooldown(int minutes) {
            cooldownUntil = Instant.now().plusSeconds(minutes * 60L);
        }
        
        public boolean hasRegistrationTimedOut(int timeoutSeconds, Instant startTime) {
            if (registrationStartTime == null) {
                registrationStartTime = startTime;
            }
            return Instant.now().isAfter(registrationStartTime.plusSeconds(timeoutSeconds));
        }
        
        public boolean hasLoginTimedOut(int timeoutSeconds, Instant startTime) {
            if (loginStartTime == null) {
                loginStartTime = startTime;
            }
            return Instant.now().isAfter(loginStartTime.plusSeconds(timeoutSeconds));
        }
    }
}