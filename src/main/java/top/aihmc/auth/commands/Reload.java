package top.aihmc.auth.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import top.aihmc.auth.utils.ConfigManager;
import top.aihmc.auth.utils.MessageManager;
import top.aihmc.auth.utils.AuthValidationManager;

public class Reload implements SimpleCommand {
    private final ProxyServer proxyServer;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final AuthValidationManager validationManager;
    
    public Reload(ProxyServer proxyServer, ConfigManager configManager, 
                 MessageManager messageManager, AuthValidationManager validationManager) {
        this.proxyServer = proxyServer;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.validationManager = validationManager;
    }
    
    @Override
    public void execute(Invocation invocation) {
        // 检查权限
        if (!invocation.source().hasPermission("aihmc.admin")) {
            invocation.source().sendMessage(
                messageManager.getMessage("no-permission")
            );
            return;
        }
        
        try {
            // 重新加载配置
            invocation.source().sendMessage(
                messageManager.getMessage("reload-starting")
            );
            
            // 加载主配置文件
            configManager.load();
            invocation.source().sendMessage(
                messageManager.getMessage("reload-config-loaded")
            );
            
            // 加载消息配置文件
            messageManager.load();
            invocation.source().sendMessage(
                messageManager.getMessage("reload-messages-loaded")
            );
            
            // 重新加载验证管理器配置
            validationManager.loadConfig(configManager.getConfigData());
            invocation.source().sendMessage(
                messageManager.getMessage("reload-validation-updated")
            );
            
            // 发送成功消息
            invocation.source().sendMessage(
                messageManager.getMessage("reload-success")
            );
            
            // 可选：向控制台记录日志
            proxyServer.getConsoleCommandSource().sendMessage(
                messageManager.getMessage("reload-console-log",
                    "executor", invocation.source().toString())
            );
            
        } catch (Exception e) {
            // 处理异常
            invocation.source().sendMessage(
                messageManager.getMessage("reload-failed", "error", e.getMessage())
            );
            
            // 记录错误到控制台
            proxyServer.getConsoleCommandSource().sendMessage(
                messageManager.getMessage("reload-console-error", "error", e.getMessage())
            );
        }
    }
    
    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("aihmc.admin");
    }
}

