# AIH-MC Auth Velocity
自己服务器定制的聚合登录插件，同时支持正版登录+多个外置登录，并且前两者认证成功后可以跳过指令登录注册直接游玩。离线登录玩家自动触发指令登录，并且离线登录玩家无法使用正版登录或外置登录已有玩家名。所有数据都在后端持久保存。目前支持指令登录注册，修改离线登录密码，数据迁移，改名。仅有 **Velocity** 版本，需要服务端启动时候通过 [authlib-injector](https://authlib-injector.yushi.moe/) 挂载后端地址的 /auth 节点，velocity 里面要设置 online-mode=false
# 注意事项
1. 该插件需要配合专用后端使用，插件本身只负责请求后端接口。
2. 只有外置登录或正版登录改名才能生效。
# 相关仓库
**插件仓库（当前）：**[https://github.com/AIH-MC/aihmc-auth](https://github.com/AIH-MC/aihmc-auth)

**后端仓库：**[https://github.com/AIH-MC/aihmc-auth-backend](https://github.com/AIH-MC/aihmc-auth-backend)
