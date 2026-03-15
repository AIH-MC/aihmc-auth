package top.aihmc.plugin;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import java.nio.file.Path;

@Plugin(id = "aihmc-plugin", name = "AIHMC Plugin", description = "A Velocity Plugin for AIHMC", version = "1.0.0", // 也可以使用
                                                                                                                    // Constants.VERSION
        authors = { "erythrocyte3803" })
public final class VelocityPlugin {

    private final ProxyServer server;
    private final ComponentLogger logger;
    private final Path dataDirectory;
    private ConfigManager configManager;

    @Inject
    public VelocityPlugin(ProxyServer server, ComponentLogger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    /**
     * 插件初始化：加载配置，注册指令
     */
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // 初始化配置管理器并加载（包含自动释放逻辑）
        this.configManager = new ConfigManager(dataDirectory, logger);
        this.configManager.load();

        // 注册指令 /aihmc
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("aihmc").build(),
                new AIHMCCommand());

        logger.info("AIHMC Plugin 初始化完成！");
    }

    /**
     * 监听玩家登录：实现自动 POST 提交
     */
    @Subscribe
    public void onPlayerPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();

        // 直接把 player 对象传进去，这样回调里就能用到
        HttpHandler.postPlayerData(
                configManager.getApiUrl(),
                configManager.getAccessToken(),
                player,
                logger);
    }

    /**
     * 指令处理类：实现重载逻辑与权限控制
     */
    private final class AIHMCCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                configManager.load();
                source.sendMessage(
                        Component.text("AIHMC 配置重载成功！", NamedTextColor.GREEN));
                return;
            }

            source.sendMessage(
                    Component.text("使用方法: /aihmc reload", NamedTextColor.YELLOW));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            // 权限节点检查
            return invocation.source().hasPermission("aihmc.admin");
        }
    }
}