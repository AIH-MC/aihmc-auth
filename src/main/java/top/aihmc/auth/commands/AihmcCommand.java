package top.aihmc.auth.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import top.aihmc.auth.utils.ConfigManager;
import top.aihmc.auth.utils.MessageManager;
import top.aihmc.auth.utils.AuthValidationManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AihmcCommand implements SimpleCommand {
    private final ProxyServer proxyServer;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final AuthValidationManager validationManager;
    
    private static final List<String> SUBCOMMANDS = Arrays.asList(
        "reload", "rl",
        "changepw", "changepassword", "cpw",
        "migrate", "mig",
        "rename", "rn",
        "rstname", "resetname", "rsn",
        "help"
    );
    
    public AihmcCommand(ProxyServer proxyServer, ConfigManager configManager, 
                       MessageManager messageManager, AuthValidationManager validationManager) {
        this.proxyServer = proxyServer;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.validationManager = validationManager;
    }
    
    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        
        if (args.length == 0) {
            // 显示帮助信息
            showHelp(invocation);
            return;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reload":
            case "rl":
                new Reload(proxyServer, configManager, messageManager, validationManager).execute(invocation);
                break;
                
            case "changepw":
            case "changepassword":
            case "cpw":
                executeChangePassword(invocation, args);
                break;
                
            case "migrate":
            case "mig":
                executeMigrate(invocation, args);
                break;
                
            case "rename":
            case "rn":
                executeRename(invocation, args);
                break;
                
            case "rstname":
            case "resetname":
            case "rsn":
                executeResetName(invocation, args);
                break;
                
            case "help":
                showHelp(invocation);
                break;
                
            default:
                invocation.source().sendMessage(
                    messageManager.getMessage("command-aihmc-usage")
                );
                break;
        }
    }
    
    private void executeChangePassword(Invocation invocation, String[] args) {
        if (args.length < 3) { // aihmc changepw <old> <new> = 需要3个参数
            invocation.source().sendMessage(
                messageManager.getMessage("command-changepw-usage")
            );
            return;
        }

        String[] changepwArgs = {args[1], args[2]}; // 提取密码参数
        Invocation changepwInvocation = createInvocation(invocation, changepwArgs);
        new ChangePassword(proxyServer, configManager, messageManager, validationManager)
            .execute(changepwInvocation);
    }

    private void executeMigrate(Invocation invocation, String[] args) {
        if (args.length < 3) { // aihmc migrate <old-uuid> <new-uuid> = 需要3个参数
            invocation.source().sendMessage(
                messageManager.getMessage("command-migrate-usage")
            );
            return;
        }

        String[] migrateArgs = {args[1], args[2]}; // 提取UUID参数
        Invocation migrateInvocation = createInvocation(invocation, migrateArgs);
        new Migrate(proxyServer, configManager, messageManager)
            .execute(migrateInvocation);
    }

    private void executeRename(Invocation invocation, String[] args) {
        if (args.length < 2) { // aihmc rename <new-name> = 需要2个参数
            invocation.source().sendMessage(
                messageManager.getMessage("command-rename-usage")
            );
            return;
        }

        String[] renameArgs = {args[1]}; // 提取新名字参数
        Invocation renameInvocation = createInvocation(invocation, renameArgs);
        new Rename(configManager, messageManager, validationManager)
            .execute(renameInvocation);
    }

    private void executeResetName(Invocation invocation, String[] args) {
        // rstname 没有参数
        Invocation rstnameInvocation = createInvocation(invocation, new String[0]);
        new ResetName(proxyServer, configManager, messageManager)
            .execute(rstnameInvocation);
    }

    // 创建新的 Invocation 实例
    private Invocation createInvocation(Invocation original, String[] newArgs) {
        return new Invocation() {
    @Override
            public com.velocitypowered.api.command.CommandSource source() {
                return original.source();
    }
    
    @Override
            public String alias() {
                return original.alias();
            }
    @Override
            public String[] arguments() {
                return newArgs;
            }
        };
    }

    private void showHelp(Invocation invocation) {
        Component helpMessage = messageManager.getMessage("command-aihmc-help");
        invocation.source().sendMessage(helpMessage);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("aihmc.player");
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        
        if (args.length == 0) {
            return CompletableFuture.completedFuture(SUBCOMMANDS);
        } else if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> suggestions = SUBCOMMANDS.stream()
                .filter(cmd -> cmd.startsWith(partial))
                .toList();
            return CompletableFuture.completedFuture(suggestions);
        }
        
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 0) {
            return SUBCOMMANDS;
        } else if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                .filter(cmd -> cmd.startsWith(partial))
                .toList();
}

        return Collections.emptyList();
    }
}

