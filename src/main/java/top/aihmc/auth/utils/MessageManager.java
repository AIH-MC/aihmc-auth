package top.aihmc.auth.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class MessageManager {
    private final Path messagePath;
    private Map<String, String> messages;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MessageManager(Path dataDirectory) {
        this.messagePath = dataDirectory.resolve("messages.yml");
        this.messages = Map.of(); // 默认空映射
    }

    public void load() {
        try {
            if (!Files.exists(messagePath.getParent())) {
                Files.createDirectories(messagePath.getParent());
            }

            if (!Files.exists(messagePath)) {
                try (InputStream in = getClass().getResourceAsStream("/messages.yml")) {
                    if (in != null) {
                        Files.copy(in, messagePath);
                    } else {
                        createDefaultMessages();
                    }
                }
            }

            Yaml yaml = new Yaml();
            try (FileReader reader = new FileReader(messagePath.toFile())) {
                Map<String, Object> data = yaml.load(reader);
                this.messages = (Map<String, String>) data.get("messages");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createDefaultMessages() throws IOException {
        String defaultMessages = """
                messages:
                             figura-register-success: "<green>Figura注册成功，现在可以自由的使用4D/5D皮肤了！"
                             # 验证相关消息
                             registration-timeout: "<red>注册超时！请在 %time% 秒内完成注册"
                             login-timeout: "<red>登录超时！请在 %time% 秒内完成登录"
                             max-retry-reached: "<red>重试次数过多，请等待 %minutes% 分钟后再试"
                             in-cooldown: "<red>请等待冷却时间结束（剩余：%remaining% 分钟）"

                             # 指令反馈
                             command-reload-success: "<green>配置文件已重新加载"
                             command-reload-failed: "<red>配置文件重新加载失败"
                             command-changepw-success: "<green>密码修改成功"
                             command-changepw-failed: "<red>密码修改失败"
                             command-migrate-success: "<green>数据迁移成功"
                             command-migrate-failed: "<red>数据迁移失败"
                             command-rename-success: "<green>重命名成功"
                             command-rename-failed: "<red>重命名失败"
                             command-rstname-success: "<green>重置名称成功"
                             command-rstname-failed: "<red>重置名称失败"

                             # AIHMC命令相关消息
                             command-aihmc-help: |
                               <red>=== AIH-MC Auth 帮助 ===
                               <yellow>主命令：/aihmc <子命令>
                               <gray>可用子命令：
                               <green>/aihmc reload <gray>- 重新加载插件配置
                               <green>/aihmc changepw <旧密码> <新密码> <gray>- 修改密码
                               <green>/aihmc migrate <原UUID> <新UUID> <gray>- 数据迁移（管理员）
                               <green>/aihmc rename <新玩家名> <gray>- 重命名玩家
                               <green>/aihmc rstname <gray>- 重置玩家名称
                               <gray>独立命令：
                               <green>/login <密码> <gray>(或 /l) - 登录
                               <green>/register <密码> <确认密码> <gray>(或 /reg) - 注册

                             command-aihmc-usage: "<yellow>用法：/aihmc <子命令>"
                             command-changepw-usage: "<yellow>用法：/aihmc changepw <旧密码> <新密码>"
                             command-migrate-usage: "<yellow>用法：/aihmc migrate <原UUID> <新UUID>"
                             command-rename-usage: "<yellow>用法：/aihmc rename <新玩家名>"

                             # 通用消息
                             no-permission: "<red>你没有权限执行此命令"
                             admin-only: "<red>只有管理员才能使用此命令"
                             player-only: "<red>此命令只能由玩家执行"
                             usage: "<yellow>用法：%usage%"
                             error: "<red>发生错误：%error%"

                             # 认证拦截消息
                             auth-security-title: "<red>【AIH-MC 安全拦截】"
                             auth-name-conflict: "<gray>检测到玩家名冲突！"
                             auth-name-registered: "<gray>名称 <yellow>%name%</yellow> 已在 <gold>%source%</gold> 平台注册"
                             auth-use-platform-login: "<gray>请使用 <gold>%source%</gold> 平台登录"
                             auth-not-your-account: "<gray>如果不是你的账号，请先前往 <green> https://name.aihmc.top </green> 测试你的要名字是否可用，然后再进服注册登录"
                             auth-server-unavailable: "<red>认证服务器暂时无法访问"

                             # 登录相关消息
                             command-login-success: "<green>登录成功"
                             command-login-network-error: "<red>登录失败：网络或服务器错误"
                             command-login-failed: "<red>登录失败"

                             # 注册相关消息
                             command-register-success: "<green>注册成功"
                             command-register-network-error: "<red>注册失败：网络或服务器错误"
                             command-register-failed: "<red>注册失败"
                             passwords-not-match: "<red>两次输入的密码不一致"
                             register-usage: "<yellow>用法：/register <密码> <确认密码>"

                             # Title提示消息
                             auth-title-login-required: "<red>请登录</red>"
                             auth-subtitle-login-command: "<yellow>/login 密码</yellow>"
                             auth-title-register-required: "<red>请注册</red>"
                             auth-subtitle-register-command: "<yellow>/register 密码 确认密码</yellow>"

                             # 重载相关消息
                             reload-starting: "<yellow>正在重新加载配置文件..."
                             reload-config-loaded: "<green>✓ 配置文件已加载"
                             reload-messages-loaded: "<green>✓ 消息文件已加载"
                             reload-validation-updated: "<green>✓ 验证配置已更新"
                             reload-success: "<green>配置文件重新加载成功！"
                             reload-failed: "<red>配置文件重新加载失败：%error%"
                             reload-console-log: "<gray>[AIH-MC Auth] 配置文件已由 %executor% 重新加载"
                             reload-console-error: "<red>[AIH-MC Auth] 配置文件重载失败: %error%"

                             # 修改密码相关消息
                             changepw-usage: "<yellow>用法：/changepw <旧密码> <新密码>"
                             password-too-short: "<red>新密码长度至少为6位"
                             command-changepw-network-error: "<red>密码修改失败：网络或服务器错误"
                             changepw-log-success: "<gray>[AIH-MC Auth] %player% 修改密码成功"

                             # 数据迁移相关消息
                             migrate-usage: "<yellow>用法：/migrate <原UUID> <新UUID>"
                             uuid-cannot-be-empty: "<red>UUID不能为空"
                             uuid-invalid-length: "<red>UUID长度不正确"
                             uuid-invalid-format: "<red>UUID格式不正确，请使用标准的UUID格式（如：12345678-1234-1234-1234-123456789012）"
                             uuid-same-error: "<red>原UUID和新UUID不能相同"
                             command-migrate-network-error: "<red>数据迁移失败：网络或服务器错误"
                             migrate-console-success: "<gray>[AIH-MC Auth] 数据迁移: %old_uuid% -> %new_uuid% 成功"
                             migrate-console-failed: "<red>[AIH-MC Auth] 数据迁移失败: %error%"
                             migrate-console-network-error: "<red>[AIH-MC Auth] 数据迁移网络错误: %error%"

                             # 迁移踢出玩家相关消息
                             migrate-kick-player: "<yellow>你的账号数据已迁移"
                             migrate-kick-reason: "<green>请使用新的UUID重新登录服务器"
                             migrate-kick-console-log: "<gray>[AIH-MC Auth] 踢出玩家 %player% (UUID: %uuid%)，请重新登录"

                             # 重置名称相关消息
                             resetname-processing: "<yellow>正在重置名称，请稍候..."
                             command-rstname-server-error: "<red>重置名称失败：服务器内部错误"
                             command-rstname-network-error: "<red>重置名称失败：网络或服务器错误"
                             resetname-console-success: "<gray>[AIH-MC Auth] 玩家 %player% (UUID: %uuid%) 重置名称成功"
                           """;

        Files.writeString(messagePath, defaultMessages);

        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(defaultMessages);
        this.messages = (Map<String, String>) data.get("messages");
    }

    public Component getMessage(String key) {
        String message = messages.getOrDefault(key, "<red>未找到消息: " + key);
        return miniMessage.deserialize(message);
    }

    public Component getMessage(String key, String... placeholders) {
        String message = messages.getOrDefault(key, "<red>未找到消息: " + key);

        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                String placeholder = "%" + placeholders[i] + "%";
                String value = placeholders[i + 1];
                message = message.replace(placeholder, value);
            }
        }

        return miniMessage.deserialize(message);
    }
}
