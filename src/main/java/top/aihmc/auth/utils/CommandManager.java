package top.aihmc.auth.utils;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import java.time.Instant;

public class CommandManager {
    private final ProxyServer proxyServer;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final AuthValidationManager validationManager;
    
    public CommandManager(ProxyServer proxyServer, ConfigManager configManager, 
                         MessageManager messageManager, AuthValidationManager validationManager) {
        this.proxyServer = proxyServer;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.validationManager = validationManager;
    }
    
    public SimpleCommand createReloadCommand() {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                if (!invocation.source().hasPermission("aihmc.admin")) {
                invocation.source().sendMessage(
                        messageManager.getMessage("no-permission")
                );
                    return;
                }
                
                // 重新加载配置的逻辑
                configManager.load();
                messageManager.load();
                validationManager.loadConfig(configManager.getConfigData());

                invocation.source().sendMessage(
                    messageManager.getMessage("command-reload-success")
                );
            }
            
            @Override
            public boolean hasPermission(Invocation invocation) {
                return invocation.source().hasPermission("aihmc.admin");
            }
        };
    }
    
    public SimpleCommand createChangePasswordCommand() {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                if (!invocation.source().hasPermission("aihmc.player")) {
                invocation.source().sendMessage(
                        messageManager.getMessage("no-permission")
                );
                    return;
                }
                
                // 修改密码逻辑
                String[] args = invocation.arguments();
                if (args.length < 2) {
                    invocation.source().sendMessage(
                        messageManager.getMessage("usage", "usage", "/changepw <旧密码> <新密码>")
                    );
                    return;
                }
                
                // TODO: 实现密码修改API调用
                        invocation.source().sendMessage(
                    messageManager.getMessage("command-changepw-success")
                        );
                    }
                    
            @Override
            public boolean hasPermission(Invocation invocation) {
                return invocation.source().hasPermission("aihmc.player");
            }
        };
    }
    
    public SimpleCommand createMigrateCommand() {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                if (!invocation.source().hasPermission("aihmc.admin")) {
                invocation.source().sendMessage(
                        messageManager.getMessage("no-permission")
                );
                    return;
            }
            
                // 数据迁移逻辑
                // TODO: 实现数据迁移API调用
                invocation.source().sendMessage(
                    messageManager.getMessage("command-migrate-success")
                );
            }

            @Override
            public boolean hasPermission(Invocation invocation) {
                return invocation.source().hasPermission("aihmc.admin");
    }
        };
}

    public SimpleCommand createRegisterCommand() {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                if (!invocation.source().hasPermission("aihmc.player")) {
                    invocation.source().sendMessage(
                        messageManager.getMessage("no-permission")
                    );
                    return;
                }

                String[] args = invocation.arguments();
                if (args.length < 2) {
                    invocation.source().sendMessage(
                        messageManager.getMessage("usage", "usage", "/register <密码> <确认密码>")
                    );
                    return;
                }

                // 检查验证超时和重试限制
                if (invocation.source() instanceof com.velocitypowered.api.proxy.Player player) {
                    if (!validationManager.canAttemptAuth(player.getUniqueId())) {
                        invocation.source().sendMessage(
                            messageManager.getMessage("in-cooldown")
                        );
                        return;
                    }

                    // 记录开始时间
                    Instant startTime = Instant.now();

                    // 检查是否超时
                    if (!validationManager.isRegistrationValid(player.getUniqueId(), startTime)) {
                        invocation.source().sendMessage(
                            messageManager.getMessage("registration-timeout",
                                "time", String.valueOf(validationManager.getRegistrationTimeoutSeconds()))
                        );
                        validationManager.recordAttempt(player.getUniqueId(), false);
                        return;
                    }

                    // TODO: 实现注册API调用
                    // 如果成功
                    validationManager.recordAttempt(player.getUniqueId(), true);
                    invocation.source().sendMessage(
                        messageManager.getMessage("command-register-success")
                    );
                } else {
                    invocation.source().sendMessage(
                        messageManager.getMessage("player-only")
                    );
                }
            }

            @Override
            public boolean hasPermission(Invocation invocation) {
                return invocation.source().hasPermission("aihmc.player");
            }
        };
    }

    public SimpleCommand createLoginCommand() {
        return new SimpleCommand() {
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

                if (invocation.source() instanceof com.velocitypowered.api.proxy.Player player) {
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

                    // TODO: 实现登录API调用
                    // 如果成功
                    validationManager.recordAttempt(player.getUniqueId(), true);
                } else {
                    invocation.source().sendMessage(
                        messageManager.getMessage("player-only")
                    );
                }
            }

            @Override
            public boolean hasPermission(Invocation invocation) {
                return invocation.source().hasPermission("aihmc.player");
            }
        };
    }

    public SimpleCommand createRenameCommand() {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                if (!invocation.source().hasPermission("aihmc.player")) {
                    invocation.source().sendMessage(
                        messageManager.getMessage("no-permission")
                    );
                    return;
                }

                // 重命名逻辑
                // TODO: 实现重命名API调用
                invocation.source().sendMessage(
                    messageManager.getMessage("command-rename-success")
                );
            }

            @Override
            public boolean hasPermission(Invocation invocation) {
                return invocation.source().hasPermission("aihmc.player");
            }
        };
    }

    public SimpleCommand createResetNameCommand() {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                if (!invocation.source().hasPermission("aihmc.player")) {
                    invocation.source().sendMessage(
                        messageManager.getMessage("no-permission")
                    );
                    return;
                }

                // 重置名称逻辑
                // TODO: 实现重置名称API调用
                invocation.source().sendMessage(
                    messageManager.getMessage("command-rstname-success")
                );
            }

            @Override
            public boolean hasPermission(Invocation invocation) {
                return invocation.source().hasPermission("aihmc.player");
            }
        };
    }
}
