package top.aihmc.plugin;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ConfigManager {
    private final Path configPath;
    private final ComponentLogger logger;
    private String apiUrl;
    private String accessToken;

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
                Map<String, Object> data = yaml.load(reader);
                this.apiUrl = (String) data.getOrDefault("api-url", "");
                this.accessToken = (String) data.getOrDefault("access-token", "");
            }
        } catch (IOException e) {
            logger.error("无法加载配置文件", e);
        }
    }

    public String getApiUrl() { return apiUrl; }
    public String getAccessToken() { return accessToken; }
}