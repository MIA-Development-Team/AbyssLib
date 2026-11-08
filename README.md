# AbyssLib (26.1.2)

Altnoir 系列模组的公共前置库（NeoForge **26.1.2.94** / Java 25 / moddev 2.0.143）。

- 包名：`com.altnoir.abysslib`
- 作者：Altnoir
- 分支：`26.1.2-NeoForge`（独立 git worktree：`D:\Minecraft\ModDev\AbyssLib-26.1.2`）
- 版本：`1.0-03`（以根目录 `gradle.properties` 为准）

> 与 1.21.1 线（`1.21.1-NeoForge`）的差异：**API 不通用**。
> - 26.1 使用 `Identifier`（无 `ResourceLocation`）、`GuiGraphicsExtractor`/`RenderPipelines` 渲染管线；
> - 分区栏为 MIA-26.1 模型（分区持 `ResourceKey<CreativeModeTab>` + `Identifier` + 可选 bannerSprite），不是 1.21.1 的 populator + BannerStyle 模型；
> - 访问创造栏私有成员用 **mixin `@Shadow`**（不用 AT）；
> - 当前包含 `Reginth`（注册框架）、`ReLink`（模型加载器）与 `Atlas`（结构扩展），均已接入 26.1.2 构建。

## 模块化结构

与 1.21.1 线同形制：一个 Gradle 多项目，拆成「功能模块 + 聚合包」。

| 模块 | artifactId（group `com.altnoir`） | modid | 内容 |
|---|---|---|---|
| **AbyssLib**（聚合） | `abysslib-26.1.2` | `abysslib` | 内嵌 Reginth、ReLink、Atlas 三个模块的 jar（jarJar），**装这一个就等于装全部**；另含 `AbyssLib` 门面工具 |
| **AbyssLib-Reginth** | `abysslib-reginth-26.1.2` | `abysslib_reginth` | 注册框架 **`Reginth`**（源码级内嵌自上游 Registrate `MC26.1-1.5.7`，包名与类名已改）+ 分区式创造栏 |
| **AbyssLib-ReLink** | `abysslib-relink-26.1.2` | `abysslib_relink` | 移植自 Athena 的 CTM / 动态模型加载器，以及发光与 Athena 格式兼容扩展 |
| **AbyssLib-Atlas** | `abysslib-atlas-26.1.2` | `abysslib_atlas` | 结构上限扩展与大型结构放置 |

> **artifactId 里的 `-26.1.2` 是 MC 线标识，必须保留**。本库按 MC 版本分线，1.21.1 线的坐标是
> `com.altnoir:abysslib-1.21.1` / `com.altnoir:abysslib-reginth-1.21.1`。两条线用**同一个 group 和同一个
> `mod_version`**，如果 artifactId 不区分 MC 版本，两条线会落到**完全相同的坐标**上——后发布的会
> **静默覆盖**先发布的，消费方拿到的就是错 MC 版本的 jar。后缀由根 `gradle.properties` 的
> `mod_mc_suffix` 一处定义，`settings.gradle` 据此命名 Gradle 项目（项目名 == artifactId）。

- 目录：`module.reginth/`、`module.relink/`、`module.atlas/`、`module.main/`（详见仓库里的 `settings.gradle`）。
- **不再依赖 `maven.gegy.dev` 的 Registrate**，也不再 `jarJar` / `api` 暴露它 —— 类就在本库 jar 里，天然单副本、无版本冲突。
- **Simple Bedrock Model / mae 不由本库内置**；需要 SBM 的模组自行以本地 libs + jarJar 提供。

## 构建 / 发布

```bash
./gradlew build          # 四个模块的二进制 jar 和 sources.jar 位于各自 build/libs/
./gradlew publish        # 四个坐标及对应源码包一起发布到 maven.ringlo.im/releases（需要凭证）
./gradlew :abysslib-26.1.2:runClient    # run 配置只定义在聚合模块
./gradlew :abysslib-26.1.2:runServer
```

> artifactId 由 Gradle 项目名决定（`settings.gradle` 里按 `mod_mc_suffix` 改名），坐标为
> `com.altnoir:abysslib-26.1.2:<版本>`、`com.altnoir:abysslib-reginth-26.1.2:<版本>`、
> `com.altnoir:abysslib-relink-26.1.2:<版本>`、`com.altnoir:abysslib-atlas-26.1.2:<版本>`。
> 各模块 `build.gradle` 里**没有** `base { archivesName = ... }`——默认值就是项目名，jar 名与坐标不会对不上。

> **跑 run 需要 Java 21 工具链**：ModDevGradle 的 `downloadAssets` 任务要求 Java 21，而工程本身target Java 25。
> 本机已在 `GRADLE_USER_HOME/gradle.properties` 里用 `org.gradle.java.installations.paths` 指向 JDK-21；
> 换机器时若报 "Cannot find a Java installation … languageVersion=21"，照此补一行即可。

## ReLink 按位置动态发光

客户端可在模型烘焙前按方块 ID 注册状态提供器，控制局部发光叠加层：

```java
DynamicEmissive.register(Identifier.fromNamespaceAndPath("mymod", "coil"),
        (level, pos, state) -> ClientCoilState.isActive(pos));
```

普通方块模型和 ReLink CTM 模型均可使用。每张需要发光的基础贴图旁放置同名的 `_active_e` 遮罩
（如 `coil.png` 对应 `coil_active_e.png`），并确保遮罩被加入方块贴图图集。遮罩透明部分不会覆盖底图；
CTM 应为实际使用的各张连接贴图分别提供对齐的遮罩。动态叠加层不依赖全局 `_e` 选项，
只改变外观，不改变世界光照。

提供器在区块构建工作线程调用，状态读取必须线程安全。消费方负责同步服务端状态、在结果改变时重建
受影响的区块网格，并在区块卸载、切换维度或断线时清理其客户端缓存。未注册的方块保持原有行为。

## 通用工具（AbyssLib 静态方法）

`AbyssLib` 聚合模块自带一组 `Identifier`/注册表路径工具：

| 方法 | 说明 |
|---|---|
| `AbyssLib.loc(path)` | `abysslib:<path>`（仅本库资源；消费方用 `modloc(自身MOD_ID, path)`） |
| `AbyssLib.modloc(namespace, path)` | 任意 `namespace:path` |
| `AbyssLib.mcloc(path)` | 原版 `minecraft:path` |
| `AbyssLib.parse(str)` / `tryParse(str)` | 严格 / 宽松解析 |
| `getItemPath(item)` / `getBlockPath(block)` / `getBlockKey(block)` | 物品/方块的注册名 |

## Reginth（通用注册框架实例）

**改名说明**：26.1 线原来自有的 `ALRegistrate` / `ALBlockBuilder` / `ALItemBuilder` 已删除，
统一为源码内嵌的注册框架 `Reginth` / `ReginthBlockBuilder` / `ReginthItemBuilder`
（包名 `com.altnoir.abysslib.reginth`，与 1.21.1 线一致）。

入口类持有绑定自己 mod id 的实例，并**自行挂事件总线**：

```java
@Mod(MyMod.MOD_ID)
public class MyMod {
    public static final String MOD_ID = "mymod";
    private static final Reginth REGINTH = Reginth.create(MOD_ID);

    public MyMod(IEventBus modEventBus) {
        REGINTH.registerEventListeners(modEventBus);   // 手动挂载（重要）
        MyItems.register();
        MyBlocks.register();
        MyItemGroups.register();
    }

    public static Reginth reginth() {
        return REGINTH;
    }
}
```

> **与 1.21.1 线的差异**：26.1 线的 `create()` **不会**自动挂事件总线（上游与 1.21.1 线会在 `create` 里
> 查 `ModList` 自动挂载）。所以上面那行 `registerEventListeners` 是**必须**的；也不要两处都挂（会重复注册）。

行为要点：
- 先 `REGINTH.defaultCreativeSection(section)` 设定默认分区，之后 `REGINTH.item(...)` 注册的物品会自动 `.tab(section.tab())` 并把注册名 `add` 进该分区；`block(...)` 本身不自动归类（其方块物品走 item 链触发同一逻辑）。
- `ReginthItemBuilder.ignore()` / `ReginthBlockBuilder.ignore()` 把条目从默认创造栏剔除。
- `object("name").creativeTab(tab -> SectionedCreativeModeTab.configure(...)).register()` 或 `creativeTab(...)` 便捷方法注册自定义标签页。

## 分区式创造栏（CreativeTabSection + SectionedCreativeModeTab）

分区以**注册名**收集（照 MIA-26.1），展示时经注册表惰性解析：

```java
public final class MyItemGroups {
    private static final Reginth REGINTH = MyMod.reginth();

    public static final ResourceKey<CreativeModeTab> TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, AbyssLib.modloc(MyMod.MOD_ID, "main"));

    // (tab, id, title[, bannerSprite]) —— bannerSprite 指向
    // assets/<ns>/textures/gui/sprites/<path>.png（162x18）；省略则画默认绿色横幅
    public static final CreativeTabSection TS_ITEMS = new CreativeTabSection(
            TAB_KEY, AbyssLib.modloc(MyMod.MOD_ID, "main/items"),
            Component.translatable("itemGroup.mymod.section.items"));
    public static final CreativeTabSection TS_BLOCKS = new CreativeTabSection(
            TAB_KEY, AbyssLib.modloc(MyMod.MOD_ID, "main/blocks"),
            Component.translatable("itemGroup.mymod.section.blocks"));

    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> TAB = REGINTH
            .object("main")
            .creativeTab(tab -> SectionedCreativeModeTab.configure(
                    tab.icon(MyItems.SOME_ITEM::asStack),
                    TS_ITEMS, TS_BLOCKS))
            .register();

    public static void register() {
    }
}
```

注册归类（在 init 类 static 初始化最前调用一次，再注册物品）：

```java
public final class MyItems {
    static {
        MyMod.reginth().defaultCreativeSection(MyItemGroups.TS_ITEMS);
    }

    public static final ItemEntry<Item> SOME_ITEM = MyMod.reginth().item("some_item", Item::new).register();
}
```

客户端标题渲染**开箱即用、无需任何客户端代码**：`AbyssLib-Reginth` 模块自带的两个 client mixin
（`CreativeModeInventoryScreen.extractBackground` TAIL 注入 + `CustomCreativeSlot.isHighlightable` 屏蔽标题槽）
会自动处理任何 `SectionedCreativeModeTab`。

## 消费方 build.gradle 接入

```gradle
repositories {
    maven { url = file("../AbyssLib-26.1.2/repo") }   // 本地发布仓库（先 ./gradlew publish）
    // 不再需要 maven.gegy.dev：Registrate 已源码内嵌，本库 POM 里没有外部 Registrate 依赖
}

dependencies {
    // 全套。坐标形如 com.altnoir:abysslib-<MC 版本>:<版本>，MC 后缀不能漏。
    // 用 ${minecraft_version} 插值可以避免手写错：
    implementation("com.altnoir:abysslib-${minecraft_version}:${abysslib_version}")
    // 或只依赖注册框架模块：implementation("com.altnoir:abysslib-reginth-${minecraft_version}:${abysslib_version}")
    // 需要 SBM 的模组自行声明（本地 libs + jarJar / compileOnly），本库不提供。
}
```

> **坐标改过名（1.0.0，破坏性）**：以前是 `com.altnoir.abysslib:AbyssLib:<版本>`，
> 现在是 `com.altnoir:abysslib-26.1.2:<版本>`。只有 `build.gradle` 里那一行要改——
> modid（`abysslib`）、Java 包名（`com.altnoir.abysslib.**`）、`mods.toml` 里的 `modId` / `versionRange`
> 全都不受影响。

mods.toml 声明：

```toml
[[dependencies.你的modid]]
modId = "abysslib"          # 只装单模块时用 abysslib_reginth
type = "required"
versionRange = "[1.0,)"
ordering = "AFTER"
side = "BOTH"
```

## 备注

- 渲染/屏蔽靠 `abysslib_reginth.mixins.json`（`compatibilityLevel JAVA_25`，两个 mixin 均在 `client` 段）。
  模块里还留着 `META-INF/accesstransformer.cfg`（`CreativeModeInventoryScreen.selectedTab/scrollOffs`）——
  实际用的是 mixin `@Shadow`，该 AT **可能是历史遗留**（未清理，保留以零风险；确认无用后可删）。
- 分区每占用一整个标题行（displayItems 内为真实 EMPTY 占位）；滚动行号公式与 26.1 `ItemPickerMenu` 一致。
- 数据生成注意：多模组并存时 Registrate 的 unassociated BLOCK_TAGS 生成器存在并发竞态，世界生成标签建议用自定义 DataProvider 在 addTags 阶段直填。

## 许可与第三方声明

本库独立编写的代码采用 **MIT**，见根目录 [`LICENSE`](LICENSE)（`Copyright (c) 2026 Altnoir`）。
内嵌第三方源码及其修改分别遵循原许可证，完整来源、修改说明和适用范围见
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。

| 内容 | 来源与许可 | 源码范围说明 |
|---|---|---|
| 注册框架底层及其修改 | Registrate `MC26.1-1.5.7`，**MPL-2.0** | [`reginth/LICENSE`](module.reginth/src/main/java/com/altnoir/abysslib/reginth/LICENSE)；其中列明的独立接入文件继续使用 MIT |
| CTM / 模型加载实现 | Athena 1.21.1 线，**MIT**，`Copyright (c) 2024 Terrarium Earth` | [`model/LICENSE`](module.relink/src/main/java/com/altnoir/abysslib/model/LICENSE) |

各模块的二进制 JAR 和源码 JAR 均携带 `META-INF/LICENSE`、
`META-INF/THIRD_PARTY_NOTICES.md` 与 `META-INF/licenses/` 下的第三方许可证全文。
Reginth 与聚合包的许可元数据标为 `MIT AND MPL-2.0`，两种许可分别适用于各自文件，
并非可任选一种替代；独立原创文件继续使用 MIT。

**取得对应修改后源码**：Reginth 二进制 JAR 的
`META-INF/sources/com/altnoir/abysslib/reginth/` 内直接包含本次构建的注册框架 Java 源码及许可范围声明。
聚合包用户先打开 `META-INF/jarjar/` 内的 Reginth JAR，再提取上述目录即可。
也可取得与二进制版本一致的 Maven 源码包：
`com.altnoir:abysslib-reginth-26.1.2:<版本>:sources`。
其他模块也生成和发布同版本 `sources` 包；聚合模块的源码包仅包含聚合模块自身源码。

再分发时应保留第三方版权、许可声明及 MPL 覆盖源码的获取方式。
不要移除构建脚本中的声明打包和 Reginth 源码随附配置。
