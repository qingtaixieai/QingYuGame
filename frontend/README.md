# 前端开发入口

TypeScript / React / PixiJS。客户端负责输入和表现，后端是动作合法性、伤害和物品账务的权威。

2026-11-08按用户选择改造真实SceneView/BattleView，采用揭顶俯视像素场景。SceneArtwork只负责展示，不修改碰撞或服务端规则。旧独立选型页已退出构建并备份到本机忽略目录，当前唯一构建入口是index.html。前端负责人按地图、战斗、人物物品、社交、管理、美术和公共集成细分，见`config/module-ownership.json`。

```text
src/main.tsx                 挂载和HMR root
src/app/App.tsx              登录、注册、审批与账号门禁
src/app/styles.ts            显式CSS级联顺序
src/features/game/Game.tsx   世界状态、单一WebSocket、页面协调
src/features/admin/Admin.tsx 管理面板
src/features/social/        聊天、联系人/附近交互、表情选择与聊天审核
src/features/battle/         战斗几何与像素绘图
src/components/             品牌、纯展示组件
src/shared/                 公共协议、场景/制作/状态类型及显示映射
src/types.ts                旧导入路径的兼容再导出
```

现有MapView、SceneView、BattleView及各业务面板仍为明确叶子模块，避免为搬目录破坏全部引用。扩展时按职责进入features，共享类型必须进入shared；不能从协议反向导入组件类型。工程检查约束 shared不依赖UI、components不依赖features/app、features不依赖app。

GameProps和AdminProps是协调边界；游戏连接、请求序号及清理仍归Game单独负责，不能让各面板自建世界WebSocket。api.ts统一HTTP错误处理；场景/状态组件保留旧类型再导出兼容调用者。

开发：`npm ci`、`npm run dev`；构建：`npm run build`。可通过 `QINGYU_DEV_BACKEND` 指定本地后端。具体隔离环境和浏览器验收见[开发验收](../docs/engineering/开发验收.md)。

修改styles.ts的顺序可能影响移动端覆盖规则；本次拆分前后构建CSS哈希一致，后续视觉变更仍须实际页面验收。不能通过构建成功推断页面无遮挡或无卡顿。

社交功能实现集中在 `features/social/`，Game/Admin 直接引用该目录；根目录 Chat、SocialPanel、EmotePicker、ChatAdministration 仅保留兼容再导出。社交面板通过统一 api 发请求，聊天状态仍接收 Game 的 revision；不创建 WebSocket。场景数据直接依赖 shared/scene，物流交互复用现有 LogisticsPanel。emotes.ts 是地图和社交共用的像素资源，social.css 仍由原样式入口加载，保持级联顺序。
