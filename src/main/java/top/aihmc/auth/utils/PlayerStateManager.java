package top.aihmc.auth.utils;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerStateManager {
    // 记录已登录的玩家
    private final Set<UUID> loggedInPlayers = new HashSet<>();

    // 记录正版玩家（通过 forceOnlineMode 验证）
    private final Set<UUID> premiumPlayers = new HashSet<>();

    // 新增：记录已注册的离线玩家
    private final Set<UUID> registeredPlayers = new HashSet<>();
    private final Set<String> registeredUsernames = new HashSet<>();

    public void markAsLoggedIn(UUID playerId) {
        loggedInPlayers.add(playerId);
    }

    public void markAsPremium(UUID playerId) {
        premiumPlayers.add(playerId);
        loggedInPlayers.add(playerId); // 正版玩家自动视为已登录
    }

    // 新增：标记玩家为已注册（但未登录）
    public void markAsRegistered(String username) {
        registeredUsernames.add(username.toLowerCase());
    }

    public boolean isLoggedIn(UUID playerId) {
        return loggedInPlayers.contains(playerId);
    }

    public boolean isPremium(UUID playerId) {
        return premiumPlayers.contains(playerId);
    }

    // 新增：检查玩家是否已注册
    public boolean isRegistered(String username) {
        return registeredUsernames.contains(username.toLowerCase());
    }

    public void logout(UUID playerId, String username) {
        loggedInPlayers.remove(playerId);
        premiumPlayers.remove(playerId);
        registeredUsernames.remove(username.toLowerCase());
    }

    public void clearAll() {
        loggedInPlayers.clear();
        premiumPlayers.clear();
        registeredPlayers.clear(); // 新增：清理已注册玩家
    }
}
