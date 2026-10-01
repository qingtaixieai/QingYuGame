# 首版验证记录

日期：2026年9月20日。已部署至 https://qingtaixieai.com 并通过公网验收。

## 已验证

- 前端TypeScript检查与生产构建通过。
- 后端Maven verify通过，生成可执行JAR；3项领域测试通过，其中覆盖100种地图种子的聚落连通性和逐步合法通行。
- 使用真实PostgreSQL 17.6及Spring Boot服务进行10个不同账号联测，14项检查通过。
- 联测包括匿名访问拒绝、缺少防伪请求头拒绝、跨源修改拒绝、未审批拦截、管理员接口越权拦截、在线状态同步、并发移动、位置落库、不可通行目的地拒绝、离线保留、撤权断连、重新审批、密码重置使原凭据和会话失效、原子世界重建及旧世界版本拒绝。
- PostgreSQL及后端均重启后，地图内容指纹、角色坐标和准入权限与重启前一致；浏览器仍可恢复已登录角色。
- 浏览器实际验证：登录、地图渲染、地点列表、定位和缩放、前往城镇、当前位置更新、世界重建消息、后台显示与重建输入确认禁用状态。
- 浏览器控制台检查未发现地图运行错误。
- 前端生产依赖经npm官方源审计，报告0项已知漏洞。这不等于完整安全审计。
- 发布包约21.5MB，包含前端构建结果、Java运行包和部署模板；不含本地测试数据库、账号密码或SSH私钥。

## 公网部署验证

- 已核验Ubuntu 22.04及现有服务。容器镜像源连接受限，实际采用Java 21、PostgreSQL 17与systemd原生部署，复用宿主机Nginx。
- HTTPS证书签发成功，HTTP跳转HTTPS；证书续期定时器与Nginx重载钩子已配置，尚未演练未来的自动续期。
- 公网7项检查通过：安全会话、匿名访问拒绝、注册待审批与批准、两条WSS连接共享在线状态、移动同步、位置重新读取保持、撤权断连并拒绝重新进入。两名验收账号已清理。
- 独立浏览器实际通过生产登录、地图画布渲染、移动及后台显示检查。使用PixiJS官方免动态求值兼容模块，修复生产CSP下的初始化问题，未放宽为unsafe-eval。
- qingyu及PostgreSQL服务运行正常；游戏服务和证书续期定时器已启用。后端和数据库仅监听本机。
- 公网报告：`.local/production-report.json`；画面记录：`.local/production-preview.png`与`.local/production-admin.png`。初始未登录的`/api/me`返回401是预期行为。

## 验证边界

- 没有验证千人/万人负载；当前验证范围为10账号原型，非长期压力与故障演练。

原始本地报告在被版本控制忽略的 `.local/integration-report.json`；后端测试结果在 `backend/target/surefire-reports/`。

## 背包与采集验证（2026年9月21日）

- 新后端Maven verify通过；前端TypeScript及生产构建通过。
- 新增本地11项联测通过：资源地形、远程采集/越权拒绝、并发捡拾只奖励一次、随机平原刷新、森林独占及广播、移动取消无奖励、断线完成与树桩计时、管理员查看离线背包、原地树木恢复、撤权释放资源、重建保留库存并取消旧行动。
- 将未完成行动保存后实际重启后端，验证恢复后只奖励1份，无重复发放。
- 正式HTTPS环境4项联测通过：石头入账及防重复、共享3秒行动和独占、离线完成及树桩、管理员查看背包与普通玩家越权拒绝。临时测试账号已清理。
- 实际浏览器验证背包横向列表、管理后台查看库存、地图正常渲染；生产页面未捕获运行异常。
- 本地真实界面验证砍伐头顶进度条、木头增加、树桩和恢复倒计时；截图为`.local/resources-action.png`及`.local/resources-stump.png`。
- 公网报告为`.local/production-resources-report.json`，本地报告为`.local/resource-report.json`，正式画面为`.local/resources-production.png`及`.local/resources-admin-production.png`。
- 测试不是压力测试。当前仍为单一世界进程，不能直接多实例同时运行资源结算。

## 表情系统验证（2026年9月22日交付）

- 后端Maven clean verify、前端TypeScript与生产构建通过。
- 本地8项联测通过：身份与代码校验、双端共享3秒气泡及限流、按玩家替换、不影响其他玩家、双端过期、砍伐不取消不重计时、移动不中断、8种表情及撤权清除（按脚本分组记录）。
- 公网双账号验证通过：待审批/匿名禁止发送、WSS同步、替换与过期；临时测试账号已清理。
- 真实界面检查表情盘、头顶气泡与砍伐进度条分离；正式浏览器验证8个按钮、发送成功、发送后收起、未绑定E快捷键，无运行异常。
- 证据：`.local/production-emote-report.json`、`.local/emote-browser-report.json`、`.local/emote-wheel-production.png`和`.local/emote-bubble-production.png`。
- 更新保留现有世界、库存与账号，部署前保存数据库及旧程序。

## 聊天室验证（2026年9月22日交付）

- 后端Maven clean verify、前端TypeScript与生产构建通过。
- 本地18组接口联测通过：准入权限、公聊与已读游标、私聊隔离（含管理员）、离线消息与表情、非法类型、图片格式与5MB限制、图片位复用及读取权限、GIF申请/批准/拒绝、聊天禁言与地图表情分离、临时限制到期、IP限制、单浏览器会话、账号/IP游戏封禁、自锁防护及40条历史分页。
- 真实双人浏览器验证通过：公私聊红点、旅人私聊入口、八种表情入口与爱心消息、上传发送、申请换图、后台批准、历史旧图提示、禁言/解除、地图表情保留、新浏览器顶下旧登录、同浏览器多标签页。未捕获前端运行异常。
- 实际重启后端后，私聊内容、原始GIF字节、已读游标、禁言及原会话仍可恢复。
- 公网四组联测通过：HTTPS权限/WSS私聊/已读及管理员隔离；multipart上传、私聊图片权限、审批及GIF字节一致；账号禁言/解除；新登录断开旧连接并废止旧会话。仅在临时账号之间发私聊，未向正式公聊发送测试消息。
- 正式浏览器确认聊天入口、公私聊切换、八表情菜单、后台管理表单，无运行异常；截图为`.local/chat-public-production.png`和`.local/chat-admin-production.png`。
- 部署前备份数据库、旧JAR、Nginx和网页入口；V3迁移完成，服务健康，临时公网账号及其消息/图片/封禁记录已清理。未重建正式世界。
- 报告：`.local/chat-report.json`、`chat-browser-report.json`、`chat-restart-report.json`、`production-chat-report.json`、`chat-production-browser-report.json`。
- 此次为功能与权限验证，未进行大规模压力测试或完整安全审计。

## 全屏地图与基础战斗验证（2026年9月23日）

- 前端TypeScript和生产构建通过，后端Maven verify通过；V4迁移在本地PostgreSQL 17上执行并经后端重启校验。
- 本地三账号联测通过：向离线角色开战、世界角色战斗状态、移动与采集等世界指令拒绝、单格单战场、下一轮增援、公开红格、离开红格只触发一次攻击、边缘撤离及战后恢复世界行动。
- 实际浏览器检查全屏地图、侧栏收起和战斗中切换面板；在战场与大世界之间切换后仍保留战斗，大世界仅可查看。验证攻击目标选择及红色轮廓，手机尺寸下检查战场与侧栏布局。
- 保留一场战斗与待执行攻击，实际重启后端，确认参战角色、回合和红格恢复；本地临时角色与战场已按精确ID清理。
- 已更新 https://qingtaixieai.com ：部署前备份正式数据库、旧JAR和网页入口至`/opt/qingyu/backups/battle-20260923-195801/`，未重建世界。正式数据库Flyway版本为4，服务与HTTPS健康检查正常，页面已引用本次构建资源。
- 公网三名临时账号联测通过离线同格开战、战斗中拒绝大世界行动、增援下一轮与公开红格；验收战场和三名账号已按精确ID清理，正式环境当前无活动战斗。
- 线上浏览器可视检查工具超时，未将其记为通过；本地真实浏览器已检查完整交互与手机尺寸，公网接口及静态资源已单独核对。
- 当前仍是2至10人试玩目标，没有生命、伤害、胜负结算，也未进行多人高负载压测。

## 六边战场重设计验证（2026年9月24日）

- 后端Maven verify、前端TypeScript及生产构建通过，V5在本地与正式数据库迁移成功。
- 六账号本地11组联测通过：同格全员/离线/内部不重叠随机出生；已有路径遇新战斗停止；普通寻路避让与大世界行动锁定；对应边下一轮增援；外圈移动不自动退出且不能当轮强退；普通撤离下回合生效；实际攻击打断、预告不打断；借机攻击不重复；强退转入邻接战场对面边并等下轮；角格双方向与不可通行校验；最后角色留在原格。
- 实际重启本地后端，比较撤离方向、坐标、先攻、入场轮次、当前回合和行动点，保持一致。报告`.local/battle-edges-report.json`及`.local/battle-edges-restart.json`。
- 真实浏览器检查桌面与390×844竖屏：顺序条、点击定位、格子选择、移动、世界/战斗视角切换、断线重连；修复头像受通用按钮布局影响及规则排列的装饰。热更新阶段出现过一次重复createRoot开发警告，重新加载后未新增该警告。
- 已部署至qingtaixieai.com；数据库与旧版备份在`/opt/qingyu/backups/battle-20260924-004606/`，未重建世界。
- 公网三账号联测通过：离线目标与内部随机出生、世界格封锁与战斗行动锁、按来向外圈增援及下一轮准入。使用远离现有玩家的空格，精确清理验收账号及战场，报告`.local/production-battle-report.json`。
- 正式页面通过HTTP资源核对；本次视觉验收在本地真实浏览器完成，未声称线上可视验收或大规模负载测试通过。树石仅装饰，血量/伤害/胜负仍未加入。


## 2026-09-24 战斗操作与公共渡船

- 后端5个单元测试通过：旧大陆生成检查、100种种子扩图后旧地形/道路/地点不变、岛屿与大陆分离、岛内连通、海路合法且幂等，以及多格寻路绕过占用格。
- 新增12组本地接口联测通过：旧存档迁移保留坐标；空格预设进入不触发/离开命中；多格途中触发并按路径扣点；攻击者保持相邻不触发而走远触发；空格走远落空；后进入者在到期时被命中；空格到期落空；占用/超预算/边界拒绝；共同投入原子扣物品；多人登船与船上限制；离线随船与禁止中途下船；空船循环和岛屿返程。
- 既有六边战斗11组回归通过，包括离线参战、战斗格封锁、增援、延迟撤离、攻击打断、强制撤离、相邻战场转移及最后角色保留世界格。
- 停止并重新启动本地 Java 进程后，工程材料、船体、乘客和空格预设仍在数据库中；航行计时续接。
- Browser 实际桌面及390×844手机检查：多格移动后6点降为3点、可达格即时减少；攻击模式显示6个相邻格，选定空格后提示消失但红格保留；非行动者整卡灰显、当前卡1.1倍且换人过渡0.3秒；底部独立操作栏无横向溢出；实际点击完成木头/石头投入、登船、查看航行倒计时。
- 修复验收中发现的港口与战斗面板遮挡、退出地图时 ResizeObserver 读取空容器问题。前端生产构建通过；仍有既有Pixi主包体积提示，未声称通过大规模并发压测。
- 生产发布及仓库同步状态待下方补记。

- 已于2026-09-24部署至 https://qingtaixieai.com ，数据库 V6 迁移成功。正式备份位于 `/opt/qingyu/backups/expedition-20260924-130446/`。
- 正式扩图前后逐格核对旧地形、道路、已有地点与全部账号坐标一致，新增一处港口和一处岛屿浅滩。
- 正式HTTPS五组接口检查通过：离线目标与内部出生；空格预设加多格移动的扣点和单次落空；固定海路及两岸；战斗封锁与世界行动限制；方向增援。临时账号及战场均已清理。公共造船进度未被生产验收修改，完整造船/航行与页面视觉验收在本地完成。

## 2026-09-24 移动预览、重复预设与航行流畅度

- 前端 TypeScript/生产构建通过，后端 Maven test 与发布打包通过。默认命令使用旧 Java 17，已改用本机 JDK 25 编译 Java 21 目标；打包前停止占用旧 JAR 的本地进程，随后恢复本地服务。
- `expedition-smoke.mjs` 14组检查通过，新增验证：未触发红格被替换后再次扣2点、旧费用不返还、不足2点拒绝并保留旧红格；同回合预设后移动触发，再预设并移动触发，共执行两次。既有六边战场11组回归通过。
- `ferry-motion.test.mjs` 4项通过：双向跨格连续性、迟到快照与新快照位置一致、仅预测到本航程目的港、未建造与退化路线静止。
- Browser 桌面实测：首次点目的地显示2格/2点预览且人物不动、点其他格只取消、重新两次点击后才移动并扣点；选中 SVG 地块的默认方形 outline 为 none，仍保留六边形选中与键盘焦点样式。
- 实际点击人物名字覆盖的邻格，红格落在鼠标所在的(1,-1)，没有跟随人物脚下(1,0)；再点人物身体所在格，旧红格取消、唯一新红格在(1,0)，点数由4降为2。
- 390×844 CSS视口检查：页面无横向溢出，路线说明可换行，底部操作栏位于视口内。测试后恢复原窗口尺寸。
- 正常航行参数下，本地页面连续10秒采样600个帧间隔，95分位16.8ms、最大17.1ms，无超过50ms的间隔。该结果仅代表本机该次观察，不是所有设备或网络状况的性能保证。
- 本次发布及仓库同步状态待下方补记。

- 已部署至 https://qingtaixieai.com ，备份 /opt/qingyu/backups/battle-20260924-183130/。正式服务健康检查通过，公网首页返回200且引用本次新JS/CSS；发布前后账号数均为7。本次无数据库迁移。
- 本地视觉测试账号、战场已清理，临时渡船状态已还原。正式环境未创建测试角色或修改玩家材料；交互与性能验证在本地完成。

## 2026-09-30 人物装备、生命与岛屿小怪

- 后端Maven verify通过：共9个测试（地图/扩图5、武器形状和AI评分4），前端TypeScript与生产构建通过。构建仍提示主包超过500KB，未阻止发布。
- `character-smoke.mjs`的15组本地接口检查通过：初始化与管理员权限、发放换装守恒、战斗锁装和先攻、斧头整组触发、替换收费及失败保留旧预设、长枪部分范围保留与整体出圈结算、流星锤第二环盲区、倒地中断移动及不溢出、救起完整回合、真死灵魂与信息隔离、原战场增援复活、回城取消/重计/满状态恢复、绑定休整、小怪主动遭遇与合法AI攻击、停靠安全圈外复活。
- 既有六边战场11组回归通过，覆盖全员出生、格子封锁、按方向增援、等待与强制撤离、打断和不重复触发、邻接战场转移及最后角色留在原世界格。
- `character-restart.mjs`实际停止/重启Java后通过，血量、倒地条、灵魂固定死亡坐标、装备、先攻与多格预设完整保留。测试角色及战场精确清理。
- 本地真实浏览器验证：六属性、方格物品详情和换装、战斗第二环范围、生命与灵魂回城；844×390横屏顶栏收起/展开、页面无横向溢出；390×844触屏竖屏仅显示旋转提示。截图`.local/character-panel.png`、`.local/character-landscape.png`。界面验证在本地进行，未将公网接口核对记为线上视觉检查。
- UI测试期间一处直接数据库造战场的临时脚本未包事务，出现半成品战场导致唯一索引冲突；已清理精确测试ID并将造数据改为事务，最新后端运行及接口回归未再出现该错误。正常游戏写入本来已由WorldService事务串行执行。
- 已发布https://qingtaixieai.com。上线前数据库与旧程序备份`/opt/qingyu/backups/battle-20260930-121129/`。V7成功，原9账号对应9个人物档案，新增1只小怪、3种武器。数据库中的完整世界文档与备份逐字节一致。
- 公网HTTPS健康检查、HTML及引用JS/CSS与本地构建逐字节比较通过；服务错误级日志查询无记录。未对正式角色实施测试攻击或发测试物品。
- 未进行大规模压力测试。目标仍为2～10人试玩；属性成长、更多装备槽/技能/怪物、随机词条和掉落不属于本轮实现。

## 2026-09-30 武器移动判定与手机界面重排

- 后端Maven verify通过12项单元测试，含长枪前后/侧移、流星锤第二环全部12位置、斧头固定朝向部分重叠。V8追加迁移成功，保留旧预设语义，新预设保存移动规则和起点。
- 更新后的15组人物接口检查及11组战斗回归通过。一次连续造账号触发本地注册限流429，待窗口恢复后完整回归通过；没有修改限流策略。
- `map-gestures-test.mjs`验证单击、拖动、双指缩放比例与中心、双指抬起/取消不误点、事件清理。浏览器工具不支持`Input.dispatchTouchEvent`，未宣称已在真机或浏览器完成真实双指触摸验证。
- 重启Java进程后，武器预设的规则、原点、范围和既有生命/先攻等存档保持一致。
- 实际浏览器844×390检查：青屿工具展开/收起及缩放、全屏地图初始附近视野、250px侧栏四页、装备详情/换装、缩小表情盘、战斗图标分组与置灰占位。地图和战斗无页面横向溢出。
- 页面实际点击：长枪预设2格扣2点；移动预览提示触发原红格，第二次点同格完成侧移，原目标12→9生命、红格清空、行动点4→3；方向/位置与接口一致。
- 城镇格子内实际开始休整，显示倒计时与取消；平时没有右下角生命栏。测试角色倒地弹框需要二次确认死亡，成为灵魂后右下角显示回城/定位/原地复活。手机竖屏遮罩隐藏游戏操作，测试结束恢复视口与触摸模拟设置。
- 修复验收发现的悬停详情遮住物品造成点击不稳定：悬停为原生简介，点击后固定显示详情并滚动到可见位置；实际流星锤换装成功。未捕获前端错误日志。
- 本地截图：`.local/mobile-battle-refresh.png`、`.local/mobile-equipment-refresh.png`。当地界面测试与公网静态资源核对分别记录。
- 已部署https://qingtaixieai.com，备份`/opt/qingyu/backups/battle-20260930-233715/`。公网健康、HTML/JS/CSS构建字节核对通过；数据库V8，旧世界文档与发布前备份完全一致，原9账号/人物保留，未对正式玩家做攻击或装备测试。
- 手机浏览器能否真正隐藏地址栏/系统栏取决于浏览器全屏支持与用户授权；页面已提供入口与失败提示。真实手机双指体验仍待试玩。

## 2026-10-01: Stable mobile header and ferry animation

- Authorized: hide the top bar while preserving its occupied height and every other layout; use the Qingyu tree for sidebar collapse and a distinct magnifier for the existing map tool toggle.
- Removed zero-height/header display:none and hidden-state rail padding. Hidden header uses visibility:hidden/pointer-events:none. Fixed chat sidebar safe-area selector specificity so it uses the same left inset as the rail.
- Ferry diagnosis from code: every network snapshot replaced the render clock offset, allowing network jitter to rewind interpolated movement; unchanged resource snapshots caused full resource reconciliation; two independent resize owners (Pixi resizeTo and ResizeObserver) could resize the same canvas. These are identified contributors, not a measured diagnosis of all physical-phone dropped frames.
- Rendering now advances continuously using performance time and slews clock correction at up to 10% per frame; resumes at server time after suspension. Resource references and empty paths remain stable when unchanged. One ResizeObserver owns canvas sizing. Server route/timing rules unchanged.
- Verification: frontend production build; 6 ferry motion tests (including jitter, suspension and drift); gesture checks. Browser at 844x390: topbar toggled with exact equal rects for rail, sidebar, history, composer, canvas and tools. Chat/side navigation, map tools, local boat sailing and 1280x800 canvas resize checked. Browser timing API unavailable in evaluate, so no FPS benchmark claimed; real phone remains to be checked by user.
- Local temporary UI account removed, ferry fixture restored. Screenshots .local/mobile-stable-header.png and .local/mobile-boat-motion.png.
- Frontend-only release: server not restarted, database untouched. Backup /opt/qingyu/backups/frontend-20261001-122248/. Public index + entry JS/CSS byte-matched local build, HTTPS health OK.

## 2026-10-01 手机聊天与战斗头顶信息修正

- 用户逐项确认本轮范围：仅修复手机聊天、顶部树标、侧栏按钮与战斗头顶布局；同步装备设计，不实现主副手和换装。
- 手机横屏消息区扩大，缩小标题/标签/会话说明，将输入和发送放同一行；正文12px，桌面聊天规则不变。844x390实测消息区190px（前版约71px），输入工具区约72px。
- 顶部原游戏树标左移作为唯一展开/收起入口，隐藏原品牌重复树标，侧栏恢复方框箭头；地图工具保持放大镜，顶栏隐藏占位保持。
- 战场姓名、生命数值与血条分层；桌面DOM实际文字边界互不重叠，横屏截图检查通过。当前战场无采集进度条，未新增虚假的长行动机制；大世界既有行动条不变。
- 前端生产构建通过；浏览器验证手机聊天发送、公私聊切换，桌面1280x800聊天各区矩形与修改前完全相同；顶部切换前后导航/面板/战场/顺序条矩形相同。界面截图.local/mobile-chat-compact.png与battle-labels-fixed.png。本地临时账号、消息、怪物与战场已清理。
- 已前端发布，未重启后端或改生产数据库。备份 /opt/qingyu/backups/frontend-20261001-182703/，公网HTML和入口JS/CSS与本地构建逐字节一致。真实手机待用户体验，非所有设备保证。
- 本地Word设计文档同步已确认换装规则，标记待实现；Word内容检查通过，环境缺失LibreOffice且原生Word启动失败，分页视觉核验未完成。

## 2026-10-02 主副手、盾牌、绷带与底部战斗栏

- 后端新增 V9 迁移并在本地 PostgreSQL 17.6 成功从 V8 升至 V9。新增主手/副手、单手盾、双手盾、绷带、战斗反应点和举盾状态；旧迁移未修改。
- 后端 Maven `test package` 通过，12项单元测试通过。首次打包因本地旧 JAR 正在运行被锁定，确认进程为本项目后停止并重新打包成功。
- 前端 `npm run build` 通过，仍有既有主包超过500KB提示；未作为阻断。
- 旧 `character-smoke.mjs` 15项通过，覆盖人物属性、装备旧接口兼容、战斗锁装、多格武器、倒地/灵魂/救援、回城休整和小怪AI，确认既有流程未被本轮破坏。
- 新增 `hands-shields-smoke.mjs` 并通过5项：单手盾副手装备和物品守恒；单手盾被动护甲与举盾最低1伤；双手盾可将护甲后1伤挡为0；战斗绷带消耗2AP和1物品治疗4HP；战斗外同格绷带治疗不耗战术AP。
- 本地浏览器实际检查 844x390 横屏战斗栏：页面无横向溢出；底栏从侧栏右侧开始；中间栏 `scrollWidth` 842、`clientWidth` 793，存在可见横向滚动余量；结束回合固定在右侧；按钮文字没有内部溢出。截图 `.local/mobile-hotbar-v9.png`。
- 根目录 Word 设计文档《多人开放大世界战棋设计文档_20261001更新版.docx》已追加2026年10月2日实现状态小节，内容结构检查确认写入。DOCX渲染仍因本机缺少 `soffice.exe` 失败，未完成分页视觉验收。
- 已部署至 https://qingtaixieai.com ，备份 `/opt/qingyu/backups/battle-20261002-004439/`。公网 `/api/health` 返回 ok，正式数据库 Flyway 版本为 9，首页引用本次构建资源 `index-DYogDxcv.js` 与 `index-CXJGBui9.css`。未对正式玩家发放物品或进行攻击测试。
- 本轮尚未同步GitHub、未做多人压力测试。临时UI测试账号已按 `ui_%` 精确清理。
