# 后端开发入口

Java21 / Spring Boot / JDBC / PostgreSQL / Flyway。保持单进程部署，领域之间使用Java调用，不能擅自改为多个服务。

|责任|代码入口（src/main/java/game/world）|对接对象|
|---|---|---|
|账户/会话|AccountController、AccountService|RequestGuard、客户端App|
|地图/场景|WorldController、SceneRuntime、SceneService、TownService|WorldService、客户端Game/SceneView|
|战斗动作|BattleController、BattleService、CombatActions|WorldService串行化，玩家和NPC共用|
|战报|BattleJournal|BattleService传同一JdbcTemplate，返回Event兼容类型|
|NPC战斗导航|NpcCombatNavigation|纯输入/距离图，BattleService执行动作|
|角色/物品|CharacterController、CharacterService、EquipmentService、LootService|战斗与背包界面|
|经济/交接|EconomyController、ShopService、ProductionService、HandoverService|世界、角色、后勤|
|AI与后勤|NpcDirector、LogisticsDirector等|只决策，通过公共动作结算|
|管理|AdminController、ChatController的管理路由|账号管理员权限|
|集成|WorldService、SocketConfig|保持锁、事务、广播时序|

HTTP控制器只校验/鉴权和转发，不写SQL或结算。GameApiErrors限定六个拆分控制器，ApiErrors继续覆盖聊天等旧入口，不能合并后改变错误文案。

接口：[正文](../docs/前后端接口文档.md)、[路由集合](../contracts/http-routes.json)。内部方法改变必须检查调用方，不修改DTO序列化字段来迎合重构命名。

在本目录执行 `mvn verify`。完整数据库测试需要隔离库环境变量，见[开发验收](../docs/engineering/开发验收.md)。`HttpContractIntegrationTest`验证真实路由/权限/CSRF；`ControllerErrorContractTest`验证Advice作用域；战报和NPC导航分别有独立单测。其他领域沿用集成回归。

当前仍以同一Java包组织互相协作的领域类；职责分离已建立，不把文件分包误称为解耦。世界调度和回合状态机有意保持原事务边界，后续若拆分需要独立时序/并发方案。
