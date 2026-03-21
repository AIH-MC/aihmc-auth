package top.aihmc.auth;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.slf4j.Logger;
import top.aihmc.auth.utils.*;
import top.aihmc.auth.listeners.AuthListener;
import top.aihmc.auth.listeners.PlayerListener;
import top.aihmc.auth.commands.AihmcCommand;
import top.aihmc.auth.commands.Login;
import top.aihmc.auth.commands.Register;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import java.nio.file.Path;

@Plugin(id = "aihmc-auth", name = "AIHMC Auth", description = "A Velocity Auth Plugin for AIHMC", version = "1.0.0", authors = {
        "Erythrocyte3803"
})
public final class VelocityPlugin {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final ComponentLogger componentLogger;
    private PlayerStateManager playerStateManager;

    // 管理器实例
    private ConfigManager configManager;
    private MessageManager messageManager;
    private AuthValidationManager validationManager;
    private CommandManager commandManager;

    @Inject
    public VelocityPlugin(ProxyServer proxyServer, Logger logger, ComponentLogger componentLogger) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.componentLogger = componentLogger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("AIHMC Auth 插件正在启动...");

        // 获取插件数据目录
        Path dataDirectory = Path.of("plugins/aihmc-auth");

        // 初始化管理器
        this.configManager = new ConfigManager(dataDirectory, componentLogger);
        this.messageManager = new MessageManager(dataDirectory);
        this.validationManager = new AuthValidationManager(componentLogger);
        this.playerStateManager = new PlayerStateManager();

        // 加载配置
        configManager.load();
        messageManager.load();

        // 加载验证配置
        validationManager.loadConfig(configManager.getConfigData());

    // ============ 新增：API 健康检查 ============
    try {
        String apiUrl = configManager.getApiUrl();
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            throw new IllegalStateException("API URL 未配置");
        }
        
        String accessToken = configManager.getAccessToken();
        String checkTokenUrl = apiUrl + "/check_token";
        logger.info("正在检查 API 连接: " + checkTokenUrl);
        
        // 修改这里：添加 token 参数
        var future = HttpProvider.get(checkTokenUrl, Object.class, accessToken);
        
        // 同步等待响应，设置超时时间为 10 秒
        var response = future.get(10, TimeUnit.SECONDS);
        
        if (response.statusCode != 200) {
            logger.error("API 健康检查失败! 状态码: " + response.statusCode);
            logger.error("插件将自动关闭，请检查 API 服务是否正常运行");
            
            // 延迟关闭插件，确保日志被打印
            proxyServer.getScheduler().buildTask(this, () -> {
                logger.error("由于 API 连接失败，插件正在关闭...");
                disablePlugin();
            }).delay(1, TimeUnit.SECONDS).schedule();
            
            return; // 停止继续初始化
        }
        
        logger.info("API 健康检查通过! 状态码: " + response.statusCode);
    } catch (TimeoutException e) {
        logger.error("API 连接超时（10秒）: " + e.getMessage());
        logger.error("插件将自动关闭，请检查网络连接");
        
        proxyServer.getScheduler().buildTask(this, () -> {
            logger.error("由于 API 连接超时，插件正在关闭...");
            disablePlugin();
        }).delay(1, TimeUnit.SECONDS).schedule();
        
        return;
    } catch (InterruptedException | ExecutionException e) {
        logger.error("API 连接异常: " + e.getMessage());
        logger.error("插件将自动关闭，请检查 API 服务");
        
        proxyServer.getScheduler().buildTask(this, () -> {
            logger.error("由于 API 连接异常，插件正在关闭...");
            disablePlugin();
        }).delay(1, TimeUnit.SECONDS).schedule();
        
        return;
    } catch (Exception e) {
        logger.error("启动异常: " + e.getMessage());
        logger.error("插件将自动关闭，请检查配置");
        
        proxyServer.getScheduler().buildTask(this, () -> {
            logger.error("由于启动异常，插件正在关闭...");
            disablePlugin();
        }).delay(1, TimeUnit.SECONDS).schedule();
        
        return;
    }
    // ============ 结束健康检查 ============
        // 初始化命令管理器
        this.commandManager = new CommandManager(proxyServer, configManager, messageManager, validationManager);

        // 注册事件监听器
        proxyServer.getEventManager().register(this,
                new AuthListener(proxyServer, configManager, messageManager, playerStateManager));
        proxyServer.getEventManager().register(this,
                new PlayerListener(proxyServer, configManager, messageManager, validationManager, playerStateManager));

        // 注册命令
        registerCommands();

        logger.info("AIHMC Auth 插件已成功启动!");
        logger.info("API URL: " + configManager.getApiUrl());
        logger.info("验证超时设置 - 注册: " + validationManager.getRegistrationTimeoutSeconds() + "秒, 登录: "
                + validationManager.getLoginTimeoutSeconds() + "秒");
        logger.info("重试限制: " + validationManager.getMaxRetryAttempts() + "次, 冷却: "
                + validationManager.getCooldownMinutes() + "分钟");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("AIHMC Auth 插件正在关闭...");
        // 清理资源
    }

    private void registerCommands() {
        // 注册主命令 /aihmc
        proxyServer.getCommandManager().register(
                proxyServer.getCommandManager().metaBuilder("aihmc")
                        .aliases("auth")
                        .plugin(this)
                        .build(),
                new AihmcCommand(proxyServer, configManager, messageManager, validationManager));

        // 注册其他命令别名
        registerAliasCommands();
    }

    private void registerAliasCommands() {
        // 创建 PlayerListener 实例（现在需要 PlayerStateManager）
        PlayerListener playerListener = new PlayerListener(proxyServer, configManager, messageManager,
                validationManager, playerStateManager);

        // 只允许 login 和 register 有独立别名
        // 注册 /login 命令别名
        proxyServer.getCommandManager().register(
                proxyServer.getCommandManager().metaBuilder("login")
                        .aliases("l")
                        .plugin(this)
                        .build(),
                new Login(proxyServer, configManager, messageManager, validationManager, playerListener));

        // 注册 /register 命令别名
        proxyServer.getCommandManager().register(
                proxyServer.getCommandManager().metaBuilder("register")
                        .aliases("reg")
                        .plugin(this)
                        .build(),
                new Register(proxyServer, configManager, messageManager, validationManager, playerListener));

        // 注意：还需要更新 AihmcCommand 和其中的子命令（如 ChangePassword, Rename 等）
        // 如果这些命令需要访问 PlayerListener，也需要传递 playerListener 参数
    }

    // Getter 方法供其他类使用
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public AuthValidationManager getValidationManager() {
        return validationManager;
    }

    public ProxyServer getProxyServer() {
        return proxyServer;
    }

    public Logger getLogger() {
        return logger;
    }

    private void disablePlugin() {
        logger.warn("插件功能已被禁用，所有命令和监听器将不会生效");
        // 可以在这里添加更多的清理逻辑，如果需要的话
    }
}