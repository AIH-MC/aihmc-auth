package top.aihmc.auth.utils;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private final Path configPath;
    private final ComponentLogger logger;
    private String apiUrl;
    private String accessToken;
    private Map<String, Object> configData;
    
    // 新增字段
    private Map<String, Object> servers;
    private Map<String, Object> restrictions;
    private List<String> allowedCommands;
    private int registrationTimeoutSeconds;
    private int loginTimeoutSeconds;
    private int maxRetryAttempts;
    private int cooldownMinutes;

    // 新增标题设置字段
    private Map<String, Object> titleSettings;
    private int titleFadeInSeconds;
    private int titleStaySeconds;
    private int titleFadeOutSeconds;
    private int titleRepeatIntervalSeconds;

    public ConfigManager(Path dataDirectory, ComponentLogger logger) {
        this.configPath = dataDirectory.resolve("config.yml");
        this.logger = logger;
    }

    public void load() {
        try {
            if (!Files.exists(configPath.getParent())) Files.createDirectories(configPath.getParent());
            if (!Files.exists(configPath)) {
                try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    if (in != null) Files.copy(in, configPath);
                    else Files.createFile(configPath);
                }
            }

            Yaml yaml = new Yaml();
            try (FileReader reader = new FileReader(configPath.toFile())) {
                this.configData = yaml.load(reader);  // 保存完整的配置数据
                this.apiUrl = (String) configData.getOrDefault("api-url", "");
                this.accessToken = (String) configData.getOrDefault("access-token", "");
                
                // 新增：加载其他配置字段
                if (configData.containsKey("servers")) {
                    this.servers = (Map<String, Object>) configData.get("servers");
                }
                
                if (configData.containsKey("restrictions")) {
                    this.restrictions = (Map<String, Object>) configData.get("restrictions");
                    
                    // 加载allowed-commands
                    if (restrictions.containsKey("allowed-commands")) {
                        this.allowedCommands = (List<String>) restrictions.get("allowed-commands");
                    }
                    
                    // 加载超时和限制设置
                    if (restrictions.containsKey("registration-timeout-seconds")) {
                        this.registrationTimeoutSeconds = (int) restrictions.get("registration-timeout-seconds");
                    }
                    if (restrictions.containsKey("login-timeout-seconds")) {
                        this.loginTimeoutSeconds = (int) restrictions.get("login-timeout-seconds");
                    }
                    if (restrictions.containsKey("max-retry-attempts")) {
                        this.maxRetryAttempts = (int) restrictions.get("max-retry-attempts");
                    }
                    if (restrictions.containsKey("cooldown-minutes")) {
                        this.cooldownMinutes = (int) restrictions.get("cooldown-minutes");
                    }
                }

                // 新增：加载标题设置
                if (configData.containsKey("title-settings")) {
                    this.titleSettings = (Map<String, Object>) configData.get("title-settings");

                    // 加载标题时间设置
                    if (titleSettings.containsKey("fade-in-seconds")) {
                        this.titleFadeInSeconds = (int) titleSettings.get("fade-in-seconds");
                    }
                    if (titleSettings.containsKey("stay-seconds")) {
                        this.titleStaySeconds = (int) titleSettings.get("stay-seconds");
                    }
                    if (titleSettings.containsKey("fade-out-seconds")) {
                        this.titleFadeOutSeconds = (int) titleSettings.get("fade-out-seconds");
                    }
                    if (titleSettings.containsKey("repeat-interval-seconds")) {
                        this.titleRepeatIntervalSeconds = (int) titleSettings.get("repeat-interval-seconds");
                    }
                }
            }
        } catch (IOException e) {
            logger.error("无法加载配置文件", e);
        }
    }
    
    // 新增getter方法
    public Map<String, Object> getServers() {
        return servers;
    }
    
    public String getLobbyServer() {
        return servers != null ? (String) servers.getOrDefault("lobby", "lobby") : "lobby";
    }
    
    public String getAuthServer() {
        return servers != null ? (String) servers.getOrDefault("auth", "auth") : "auth";
    }
    
    public Map<String, Object> getRestrictions() {
        return restrictions;
    }

    public List<String> getAllowedCommands() {
        return allowedCommands;
    }

    public int getRegistrationTimeoutSeconds() {
        return registrationTimeoutSeconds;
    }

    public int getLoginTimeoutSeconds() {
        return loginTimeoutSeconds;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public int getCooldownMinutes() {
        return cooldownMinutes;
    }

    // 新增：获取标题设置的getter方法
    public Map<String, Object> getTitleSettings() {
        return titleSettings;
    }

    public int getTitleFadeInSeconds() {
        return titleFadeInSeconds > 0 ? titleFadeInSeconds : 1; // 默认值
    }

    public int getTitleStaySeconds() {
        return titleStaySeconds > 0 ? titleStaySeconds : 30; // 默认值
    }

    public int getTitleFadeOutSeconds() {
        return titleFadeOutSeconds > 0 ? titleFadeOutSeconds : 1; // 默认值
    }

    public int getTitleRepeatIntervalSeconds() {
        return titleRepeatIntervalSeconds > 0 ? titleRepeatIntervalSeconds : 30; // 默认值
    }

    public Map<String, Object> getConfigData() {
        return configData;
    }

    public String getApiUrl() { return apiUrl; }
    public String getAccessToken() { return accessToken; }
}
