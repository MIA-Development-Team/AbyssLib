# AbyssLib

Altnoir 系列模组的公共前置库。**NeoForge 1.21.1 / Java 21** · 包名 `com.altnoir.abysslib` · 许可 MIT

本库是**模块化**的：三个功能模块各自是一个可单独安装的模组，另有一个聚合包把三者装在一起。

| 模块 | 坐标（group `com.altnoir.abysslib`） | modid | 自有命名空间 | 内容 | 详见 |
|---|---|---|---|---|---|
| **AbyssLib**（聚合） | `AbyssLib` | `abysslib` | — | 内嵌下面三个模块的 jar，**装这一个就等于装全部** | [§0.1](#01-装哪个-jar) |
| **AbyssLib-Reginth** | `AbyssLib-Reginth` | `abysslib_reginth` | — | 注册框架 `Reginth`（fork 自 Registrate）+ 分区式创造栏 | [§2](#2-注册框架-reginth) · [§4](#4-分区式创造栏) |
| **AbyssLib-ReLink** | `AbyssLib-ReLink` | `abysslib_relink` | **`relink:`** | 贴图材质链接：CTM / 动态模型 / 两套发光（移植自 Athena，**并兼容 `athena:` 旧写法**） | [§3](#3-内置模型加载器) |
| **AbyssLib-Atlas** | `AbyssLib-Atlas` | `abysslib_atlas` | **`atlas:`** | 结构扩展：放宽原版上限 + per-chunk 放置 + `atlas:jigsaw` | [§9](#9-原版结构扩展per-chunk-放置与-atlasjigsaw) |

> **命名空间 ≠ modid**：modid（`abysslib_relink`）只用于模组加载与依赖声明；
> 资源包/数据包看到的是**命名空间**（`relink:` / `atlas:`）。
> **三个模块都能单独安装**（玩家侧不需要 Reginth）。ReLink / Atlas 只在**编译期**用 Reginth 的
> 类型写 datagen 助手的签名，运行时一处都不引用它，所以没有内嵌——详见 [§0.2](#02-reginth-是可选依赖)。
> 需要 **Simple Bedrock Model / mae** 的模组请自行声明（jitpack 坐标 + 各自 jarJar / compileOnly），本库不提供。

---

## 目录

- [0. 快速开始](#0-快速开始)
  - [0.1 装哪个 jar](#01-装哪个-jar)
  - [0.2 Reginth 是可选依赖](#02-reginth-是可选依赖)
  - [0.3 统一配置入口](#03-统一配置入口聚合包)
- [1. 分层与构建](#1-分层与构建)
- [2. 注册框架 Reginth](#2-注册框架-reginth)
  - [2.1 建立实例](#21-建立实例)
  - [2.2 注册方块、物品、实体等](#22-注册方块物品实体等)
  - [2.3 通用 ResourceLocation 工具](#23-通用-resourcelocation-工具)
- [3. 内置模型加载器](#3-内置模型加载器)
  - [3.1 三种摆放方式](#31-三种摆放方式)
  - [3.2 内置类型一览](#32-内置类型一览)
  - [3.3 发光方案 A：整模型满亮](#33-发光方案-a整模型满亮)
  - [3.4 发光方案 B：OptiFine 式叠加层](#34-发光方案-boptifine-式叠加层)
  - [3.5 用 datagen 生成定义](#35-用-datagen-生成定义推荐做法)
  - [3.6 兼容上游 Athena 写法](#36-兼容上游-athena-写法)
- [4. 分区式创造栏](#4-分区式创造栏)
  - [4.1 建标签页与分区](#41-建标签页与分区)
  - [4.2 横幅样式](#42-横幅样式albannerstyle)
- [5. 消费方接入](#5-消费方接入)
- [6. 迁移指南](#6-迁移指南)
- [7. 排错](#7-排错)
- [8. 分支、版本与许可](#8-分支版本与许可)
- [9. 原版结构扩展（per-chunk 放置与 atlas:jigsaw）](#9-原版结构扩展per-chunk-放置与-atlasjigsaw)
  - [9.1 三个新增的类型](#91-三个新增的类型)
  - [9.2 用 datagen 生成（推荐，走 reginth）](#92-用-datagen-生成推荐走-reginth)
  - [9.3 手写 JSON 的等价形式](#93-手写-json-的等价形式)
  - [9.4 字段与约束](#94-字段与约束)
  - [9.5 长道路与地形贴合](#95-长道路与地形贴合)

---

## 0. 快速开始

**消费方 `build.gradle`**：

```gradle
repositories {
    maven { url = file("../AbyssLib/repo") }   // 本地发布仓库（先在 AbyssLib 下 ./gradlew publish）
}

dependencies {
    // 全套（聚合包）：注册框架 + 模型加载器 + 结构扩展都在这一份里
    implementation("com.altnoir.abysslib:AbyssLib:1.0.0")

    // 或按需只引某一个功能模块（可单独安装、不含 Reginth）：
    // implementation("com.altnoir.abysslib:AbyssLib-Reginth:1.0.0")
    // implementation("com.altnoir.abysslib:AbyssLib-ReLink:1.0.0")
    // implementation("com.altnoir.abysslib:AbyssLib-Atlas:1.0.0")
    // 要在单独模块上用 datagen 助手，再加一条（见 §0.3）：
    // compileOnly("com.altnoir.abysslib:AbyssLib-Reginth:1.0.0")
}
```

### 0.1 装哪个 jar

| 你的需求 | 依赖坐标 | 运行时需要的 mod |
|---|---|---|
| 全套功能 | `AbyssLib` | 只装 `AbyssLib`（三个模块已内嵌） |
| 只要注册框架 / 分区创造栏 | `AbyssLib-Reginth` | `AbyssLib-Reginth` |
| 只要 CTM / 动态模型 / 发光 | `AbyssLib-ReLink` | `AbyssLib-ReLink` |
| 只要结构扩展 | `AbyssLib-Atlas` | `AbyssLib-Atlas` |

> 表中"运行时需要的 mod"只是**最少**要求：单装 ReLink / Atlas **不需要**再装 `AbyssLib-Reginth`
> （它们不内嵌、运行时不引用 Reginth）。`AbyssLib-Reginth` 是**开发期可选依赖**，见 [§0.2](#02-reginth-是可选依赖)。

### 0.2 Reginth 是可选依赖

**运行时**：`AbyssLib-ReLink` 与 `AbyssLib-Atlas` 的 jar 里**没有任何**引用 reginth 的类会随游戏加载——
编译期扫过 `.class` 常量池，全模块只有两个类出现 `abysslib/reginth` 引用：

| 模块 | 唯一引用 reginth 的类 | 用途 |
|---|---|---|
| ReLink | `ALModelDefinitionProvider` | CTM / 动态模型**定义 datagen** 的基类 |
| Atlas | `ALStructureDatagen` | `atlas:jigsaw` / `per_chunk` / `grid_profile` 的 datagen 注册入口 |

这两个类都不带类级注解、不被 `META-INF/services` 引用、也不在 `neoforge.mods.toml` 里声明依赖，
**只有你自己在 `GatherDataEvent` 里 `new` 它们时才需要 Reginth**。所以：

| 你的情况 | 要不要引 Reginth |
|---|---|
| 用聚合包 `AbyssLib` | **不用**。聚合包 `jarJar` 内嵌全部三个模块，`AbyssLib` 的 POM 里 Reginth 是传递依赖，datagen 开箱可用 |
| 单装 ReLink / Atlas，**要** datagen 助手 | 加一条 `compileOnly("com.altnoir.abysslib:AbyssLib-Reginth:1.0.0")` |
| 单装 ReLink / Atlas，**不要** datagen（自己手写 JSON） | 什么也不用加 |

> 手写 JSON 的等价形式见 [§3.5](#35-用-datagen-生成定义推荐做法) 与 [§9.3](#93-手写-json-的等价形式)；
> 两条通道产出的文件格式完全一致，都是普通资源/数据包文件。

> **为什么不做成内嵌**：ReLink / Atlas 对 Reginth 只有**方法签名级**的 datagen 依赖，内嵌后聚合包里
> Reginth 会出现 3 份（聚合包自身 1 份 + 两个模块各 1 份），聚合包体积从 356 KB 涨到 740 KB。
> 运行时 jarJar 会按 GAV 去重、不会真的加载多份类，但那 384 KB 是白白的下载与磁盘占用。
> 对比 AnvilLib：它对 `anvillib-util` 是**运行时核心依赖**（`AbstractRegistrum` 有 63 处引用），
> 无法改成 `compileOnly`，所以它只能承受重复内嵌——本库的情况不同，可以去掉。

**`neoforge.mods.toml`**：装聚合包装 `abysslib`；只装单个模块时把 `modId` 换成对应模块的 modid。

```toml
[[dependencies.你的modid]]
modId = "abysslib"          # 或用 abysslib_reginth / abysslib_relink / abysslib_atlas
type = "required"
versionRange = "[1.0,)"
ordering = "AFTER"
side = "BOTH"
```

### 0.3 统一配置入口（聚合包）

装**聚合包**时，模组列表里 `AbyssLib` 的「配置」按钮会打开一个**统一入口**：
里面列出所有「已加载且可配置」的 AbyssLib 模块，点哪个就进哪个模块**自己的标准配置界面**。

```
模组列表 → AbyssLib → 配置 → [ AbyssLib - ReLink ]   ← 点它
                              [ 完成 ]
                                    ↓
                          ReLink 自己的配置界面（发光叠加层等）
```

- **只列本库的模块**（不枚举别人的 mod），且只列**有配置的**：`AbyssLib-Reginth` / `AbyssLib-Atlas`
  目前没有配置项，所以不会出现在列表里（免得点进一个空界面）。
- 目前只有 ReLink 有配置项：`config/abysslib_relink-client.toml`。
- 点进去后改的东西写的是**那个模块自己的文件** —— 这是刻意的：FML 的配置文件按文件名全局独占，
  两个 mod 抢同一个文件名会直接崩游戏，所以这里只做"入口统一"，不做"文件合并"，
  也就不存在"两个地方能改同一件事"的隐患。
- **只装单个模块**（没装聚合包）时本入口不存在，直接用那个模块自己的配置按钮即可。


**模组入口**：

```java
public class MyMod {
    public static final String MOD_ID = "mymod";
    private static final Reginth REGINTH = Reginth.create(MOD_ID);

    public static Reginth reginth() {
        return REGINTH;
    }

    public static ResourceLocation loc(String path) {
        return AbyssLib.modloc(MOD_ID, path);
    }
}
```

**注册一个方块**（自动生成 blockstate / 战利品表 / 语言键）：

```java
public final class MyBlocks {
    public static final BlockEntry<Block> RUBY_BLOCK = MyMod.reginth()
            .block("ruby_block", Block::new)
            .simpleItem()          // 同时生成方块物品
            .register();

    public static void register() {}   // 空方法：供入口调用以触发类加载
}
```

> 注册类都要在入口构造函数里调一次 `MyBlocks.register();`，靠类初始化完成注册——这是 Registrate 体系的既有约定。

---

## 1. 分层与构建

**仓库结构**：一个 Gradle 多项目构建，四个子项目 = 四个模组（每个子项目产出自己的 jar 与 Maven 坐标）。

```
AbyssLib/
├── settings.gradle          # include 四个 module.<name> 并把项目名改成 artifactId
├── build.gradle             # 各子项目共享的构建约定（编码、mods.toml 展开、许可打包、发布）
├── module.reginth/          # AbyssLib-Reginth  (abysslib_reginth)
├── module.relink/           # AbyssLib-ReLink   (abysslib_relink)  命名空间 relink:
├── module.atlas/            # AbyssLib-Atlas    (abysslib_atlas)   命名空间 atlas:
└── module.main/             # AbyssLib（聚合）   (abysslib)
```

**代码分布**（包名统一 `com.altnoir.abysslib.**`）：

| 模块 | 源码 | 说明 |
|---|---|---|
| **Reginth** | `reginth/`、`creative/`、`client/creative/` | 注册框架（搬运自 Registrate，类型名不改以便日后与上游 diff）；`Reginth` / `AbstractReginth` / `builders/` / `providers/` / `util/`。`ReginthBlockBuilder` / `ReginthItemBuilder` 是**本库新增**（上游没有） |
| **ReLink** | `model/`、`datagen/`、`mixin/model/`、`client/ALClientConfig` | 模型加载器（全部 `AL*` 前缀）+ 发光叠加层配置 + 定义 datagen。入口类 `AbyssLibReLink`（`@Mod(dist = CLIENT)`） |
| **Atlas** | `structure/`、`mixin/structure/` | 结构放宽自检 + `atlas:per_chunk` / `atlas:grid_profile` / `atlas:jigsaw`。入口类 `AbyssLibAtlas`（双端） |
| **Main** | `AbyssLib.java` | 聚合模块：**无业务代码**，只 `jarJar` 内嵌三个模块，并保留消费方在用的门面工具（`AbyssLib.modloc` 等） |

**构建与发布**：

```bash
./gradlew build      # 四个 jar 都在各自 module.*/build/libs/
./gradlew publish    # 四个坐标一起发布到 repo/
```

> **子模块之间没有内嵌**：ReLink / Atlas 用 `compileOnly project(':AbyssLib-Reginth')` 引 Reginth，
> 因为它们的 datagen 助手只在**方法签名**里用了 Reginth 的 `BlockEntry` / `AbstractReginth`，
> 运行时代码一处都不引用。聚合包再 `jarJar` 内嵌全部三个模块，所以聚合包里 Reginth **只有 1 份**。
> 各 jar 体积：聚合 356 KB / Reginth 210 KB / ReLink 131 KB / Atlas 42 KB。

> **消费方 datagen 前置条件**：只有当你（在消费方工程里）调用 `ALModelDefinitionProvider`
> 或 `ALStructureDatagen` 时才需要 Reginth 在编译期可见。用聚合包时它随 POM 传递、无需额外声明；
> 单装 ReLink / Atlas 时自己加一条 `compileOnly`，或干脆手写 JSON。详见 [§0.2](#02-reginth-是可选依赖)。

**客户端边界**：ReLink 与 Reginth 的客户端部分都是 `@Mod(value = ..., dist = Dist.CLIENT)`，
**专用服务端不会加载这些类**。mixin 分两份：`abysslib_relink.mixins.json`（仅 `client` 段）、
`abysslib_atlas.mixins.json`（`mixins` 4 条 + `client` 2 条）。


---

## 2. 注册框架 Reginth

### 2.1 建立实例

```java
public class MyMod {
    private static final Reginth REGINTH = Reginth.create(MyMod.MOD_ID);

    public static Reginth reginth() {
        return REGINTH;
    }
}
```

`Reginth` 继承 `AbstractReginth<Reginth>`，上游 Registrate 的 API **全部保留**——
`entry(...)` / `generic(...)` / `block(...)` / `item(...)` / `entity(...)` / `addDataGenerator(...)` /
`defaultCreativeTab(...)` 等，只是包名与类名换成了本库命名空间。用法与上游文档一致。

> **铁律**：`Reginth` 的类就在 `abysslib.jar` 里，**不存在外部 Maven 坐标**。
> 所以消费方**不要**再声明或 `jarJar` Registrate / Reginth——运行时天然单副本，
> 跨模组传 `ItemEntry` / `BlockEntry` 也不会类型分裂。

### 2.2 注册方块、物品、实体等

`Reginth.block(...)` / `Reginth.item(...)` 返回的是本库的 `ReginthBlockBuilder` / `ReginthItemBuilder`，
比上游多两件默认行为：**自动生成 blockstate / 战利品表 / 语言键**（物品是模型 / 语言键），以及**创造栏分区支持**。

```java
// 方块（自动 blockstate + loot + lang，并自动创建方块物品）
public static final BlockEntry<Block> RUBY_BLOCK = REGINTH
        .block("ruby_block", Block::new)
        .simpleItem()
        .register();

// 物品（自动模型 + lang）
public static final ItemEntry<Item> RUBY = REGINTH
        .item("ruby", Item::new)
        .register();

// 实体
public static final EntityEntry<MyEntity> MY_ENTITY = REGINTH
        .entity("my_entity", MyEntity::new, MobCategory.CREATURE)
        .properties(p -> p.sized(0.5F, 0.6F))
        .renderer(() -> MyRenderer::new)
        .register();
```

需要更细的控制（自定义方块物品、自定义战利品表、不要物品等）时，直接用上游 builder 的既有 API，
例如 `block(...).loot(...)` / `.item(...)` / `.properties(...)` / `.blockstate(...)` / `.model(...)`。

> **`Supplier` 用哪个**：Reginth API 里需要 `Supplier` 的位置要用
> `com.altnoir.abysslib.reginth.util.nullness.NonNullSupplier`，不是 `java.util.function.Supplier`。

### 2.3 通用 ResourceLocation 工具

`AbyssLib` 入口类自带一组静态工具，消费方无需各自复制：

| 方法 | 说明 |
|---|---|
| `AbyssLib.modloc(namespace, path)` | 任意 `namespace:path` |
| `AbyssLib.mcloc(path)` | 原版 `minecraft:path` |
| `AbyssLib.parse(str)` / `tryParse(str)` | 解析 `"ns:path"`（严格抛错 / 宽松返回 null） |
| `AbyssLib.getItemPath(item)` / `getBlockPath(block)` / `getBlockKey(block)` | 物品/方块的注册名 path 或 ResourceLocation |

典型用法就是入口里的 `loc` 委托（见 [§0](#0-快速开始)）。

---

## 3. 内置模型加载器

[Athena](https://github.com/terrarium-earth/Athena)（MIT，Terrarium Earth）的 1.21.1 NeoForge 部分
**已源码级并入本库**：消费方可以直接写连接纹理（CTM）/ 拼接 / 柱状等动态模型，
**无需安装 Athena 模组，也无需自行打包**。上游来源与许可见 [§8 分支与许可](#8-分支版本与许可)。

**命名统一**：类名（`AL*`）、包名（`com.altnoir.abysslib.model.**`）、资源 id 都用本库自己的命名空间 **`relink`**
（声明键 `relink:loader`、类型 `relink:ctm`、定义目录 `assets/<ns>/relink/`）。
下文出现的 `athena:*` / `earth.terrarium.athena` 一律指**上游**写法。

> **上游 Athena 写法照样能跑**：本库带兼容层，会一并认 `athena:loader` / `athena:ctm` /
> `assets/<ns>/athena/` / `"loader": "athena:athena"`，既有资源**不改一行**也能用。见 [§3.6](#36-兼容上游-athena-写法)。

> **不要**再安装上游 Athena 模组：本库已提供完整实现，且两者会抢同一个几何加载器 id（`athena:athena`）。
> 检测到上游在场时本库会**自动关闭** `athena:*` 兼容层以免冲突（见 [§3.6](#36-兼容上游-athena-写法)）。

### 3.1 三种摆放方式

定义要声明**模型类型** `"relink:loader"`（如 `"relink:ctm"`），有三种等效摆放位置：

**① blockstate 根**（上游 wiki 的规范写法，推荐照抄）

`assets/<你的modid>/blockstates/<方块名>.json`：

```json
{
  "variants": { "": { "model": "minecraft:block/air" } },

  "relink:loader": "relink:ctm",
  "ctm_textures": {
    "center":     "chipped:block/amethyst_block/ctm/cut_amethyst_block_column_ctm/3",
    "empty":      "chipped:block/amethyst_block/ctm/cut_amethyst_block_column_ctm/0",
    "horizontal": "chipped:block/amethyst_block/ctm/cut_amethyst_block_column_ctm/2",
    "vertical":   "chipped:block/amethyst_block/ctm/cut_amethyst_block_column_ctm/1",
    "particle":   "chipped:block/amethyst_block/cut_amethyst_block_column"
  }
}
```

`variants` 里的 `model` 只是占位（会被本库模型替换），wiki 统一写 `minecraft:block/air`。

**② 模型文件**（wiki 未收录）：把上面那个对象放进 `assets/<ns>/models/**.json`，
并补一个 `"loader": "relink:model"`，blockstate 里以**字符串**引用该模型。

```json
{
  "loader": "relink:model",
  "relink:loader": "relink:ctm",
  "ctm_textures": { "center": "…", "empty": "…", "horizontal": "…", "vertical": "…", "particle": "…" }
}
```

**③ 定义目录**（datagen 默认产出）：不带 `loader`，把该对象放到
`assets/<ns>/relink/<方块注册名>.json`。运行时优先读它。

> ⚠️ **1.21.1 原版限制**：blockstate 的 `variants.*.model` **只能写字符串**。写"内联模型对象"会在加载时报
> `Expected model to be a string, was an object`（实测确认）。需要 `"loader"` 的写法必须放进**模型文件**。

> **匹配语义**：本库用**方块 id**（`blockstates/<名字>.json` 的名字）查找定义，**不是**方块所用模型的 id。
> 物品模型变体（`inventory`）自动跳过，物品栏图标仍走普通模型。

**wiki 未记录、但代码支持**的字段：`connect_to`（连接条件树：`not` / `and` / `or` / `xor` /
`state`（可带 `properties`）/ `tag` / `sameBlock` / `sameState`）、
`render_type`（`solid` / `cutout` / `cutout_mipped` / `translucent`）、
`tint`（数字索引，或 `{r,g,b,a}` / `[r,g,b,a]` 固定色）。

**自定义 Java 类型**：`FactoryManager.register(ResourceLocation, ALModelFactory)`，
实现 `ALBlockModel`（`getQuads` / `getTextures` / 可选 `getDefaultQuads` / `getAttributes`）。

### 3.2 内置类型一览

| 类型 | 本库标识符 | 用途 | `ctm_textures` 键 |
|---|---|---|---|
| Full Cube CTM | `relink:ctm` | 整面连接纹理 | `center` / `empty` / `horizontal` / `vertical` / `particle` |
| Carpet CTM | `relink:carpet_ctm` | 地毯 / 薄板连接 | 同上五项 |
| Pane CTM | `relink:pane_ctm` | 玻璃板连接（含竖向剔除） | 同上五项 |
| Giant / Mural | `relink:giant`（别名 `relink:mural`） | 多格拼接大图 | `"1"`…`"width*height"` + `particle`，另需 `width` / `height` |
| Pillar | `relink:pillar` | 带 `AXIS` 属性的柱 | `self` / `top` / `center` / `bottom` / `particle` |
| Limited Pillar | `relink:limited_pillar` | 仅竖向的柱 | 同上五项 |
| Pane Pillar | `relink:pane_pillar` | 玻璃板柱 | 同上五项（另读 `edge` / `side_edge`） |

> 上游 wiki "Mural" 页给的标识符其实是 `athena:giant`（不是 `mural`）；本库两者都注册，与上游一致。
> `relink:ctm` 还额外支持"按方向分别给贴图 + `default` 回退"的写法（wiki 未记录）。

> **属性键对所有内置类型都生效**：上游只有 `athena:ctm` 解析 `tint` / `render_type`，
> 本库统一套了属性装饰器，因此 `carpet_ctm` / `pane_ctm` / `giant` / `pillar` / `limited_pillar` / `pane_pillar`
> 也能写 `render_type` / `tint` / `relink:emissive`（例如玻璃板 CTM 需要 `"render_type": "translucent"`）。

### 3.3 发光方案 A：整模型满亮

两种写法，效果相同——所有面**强制 15/15 光照并关闭 AO 与方向性明暗**，
于是不受环境光照影响、贴图什么颜色就显示什么颜色，暗处看起来就是发光。

**① 让原版/已有模型直接发亮**（最省事，不需要任何贴图字段）：blockstate 里**只写** `relink:emissive`

```json
{
  "variants": { "": { "model": "minecraft:block/stone" } },
  "relink:emissive": true
}
```

不写 `relink:loader` 时本库**不替换模型**：保留 `variants` 指向的原版模型（石头、楼梯、台阶，或你自己的模型），
只把每个面**复制一份**并写满光照——形状、贴图、`tintindex` / 生物群系染色全部照旧。
该键也可写进 `assets/<ns>/relink/<方块>.json` 定义文件（同样不需要 `relink:loader`）。

**② 本库模型 + 发光**：与 `relink:loader` 同级写（三种摆放方式都适用）

```json
{
  "variants": { "": { "model": "minecraft:block/stone" } },
  "relink:loader": "relink:ctm",
  "relink:emissive": true,
  "ctm_textures": { "center": "…", "empty": "…", "horizontal": "…", "vertical": "…", "particle": "…" }
}
```

实现走 NeoForge 原生机制（不需要光影）：`FaceBakery` 把 15/15 写进 quad 顶点光照，
渲染时 `QuadLighter` / `applyBakedLighting` 取 `max(烘焙光照, 世界光照)` → 永远满亮。

注意事项：

- **只影响外观**，不会照亮周围。要真正发光请另在 Java 侧用
  `BlockBehaviour.Properties.lightLevel(state -> 15)`（`block(...).properties(...)` 直接可写）。
- **写法①是"包裹"、写法②是"重建"**：① 保留模型原本的 `tintindex` / 生物群系染色（不会出现 §7 的灰度问题）；
  ② 由本库生成面，颜色需自己用 `tint` 指定。① 改写的是 quad 的**副本**，原版模型实例（可能被多个方块/状态共享）不受污染。
- 可与 `tint` / `render_type` 叠加（例如 `"render_type": "translucent"` + 发光玻璃）。
- 发光面是"平"的（无 AO、无方向明暗层次），这是刻意的。
- 光影（Iris/Oculus）下是否被当作 emissive 由光影包决定；原版渲染路径下必定满亮。

### 3.4 发光方案 B：OptiFine 式叠加层

想复刻 OptiFine "画一张 `iron_ore_e.png` 就发光"的体验时用这个（**默认关闭**）。

**1) 配置** `config/abysslib_relink-client.toml`（游戏内 **模组列表 → AbyssLib → Config** 也能改）：

```toml
emissiveLayer = false        # 是否启用发光叠加层
emissiveSuffix = "_e"        # 叠加层贴图后缀；留空 = 关闭
emissiveExclude = []         # 不应用叠加层的贴图 / 命名空间前缀（防止第三方 _e 贴图被误用）
```

**2) 画叠加贴图**：基贴图 `iron_ore.png` → 同名加后缀 `iron_ore_e.png`，**只画发光像素**，其余留透明。
放在与基贴图同级，例如原版铁矿 `assets/minecraft/textures/block/iron_ore_e.png`，
自己的方块 `assets/<你的modid>/textures/block/xxx_e.png`。

**3) 生效**：打开 `emissiveLayer` 后自动应用，不需要 F3+T，也不需要写任何模型/blockstate JSON。
改动配置后的行为分级（日志会打印一行说明）：

- **关闭开关 / 改后缀 / 改排除表** → 自动重建区块网格（等价 F3+A，代价很小）；
- **从关闭切到开启** → 自动重载一次客户端资源（模型需要重新包一层，无法只靠重建网格完成）。

渲染语义（对齐 OptiFine）：

- 叠加层**满亮、不受环境光照影响**；
- **跟随基贴图的渲染层**；基贴图只有 `SOLID` 层时改用 `CUTOUT`（这样叠加图里的透明像素会被 alpha 剔除，而不是画成黑块）；
- 叠加贴图的 alpha 参与混合（半透明像素 = 半亮），可做柔光边缘；
- 基贴图 quad 完全不动（叠加层是**副本**），不影响共用同一模型/贴图的其它方块；
- **只对方块生效**（物品 / 生物 / 方块实体暂不支持）；
- 与 §3.3 的 `relink:emissive` 互不冲突，可叠加。

### 3.5 用 datagen 生成定义（推荐做法）

> **前置条件**：`ALModelDefinitionProvider` 的签名里用了 Reginth 的 `BlockEntry`，所以**你的工程编译期
> 需要 Reginth 可见**。用聚合包 `AbyssLib` 时它随 POM 传递，开箱可用；单装 `AbyssLib-ReLink` 时请自行加
> `compileOnly("com.altnoir.abysslib:AbyssLib-Reginth:1.0.0")`，否则改用 [§3.1](#31-三种摆放方式) 的手写 JSON。
> 见 [§0.2](#02-reginth-是可选依赖)。

`ALModelDefinitionProvider` 生成的是**定义目录**形式（`assets/<modid>/relink/<方块>.json`），
**不改写 blockstate**，因此可以和你现有的 `RegistrateBlockstateProvider`（如 PoopSky 的 `BlockStateGen`）
**共存、互不覆盖**。

> 结构 / 世界生成（`atlas:jigsaw`、`atlas:per_chunk`、`atlas:grid_profile`）的 datagen 走**另一条通道**
> （reginth 的 `getDataGenInitializer()`），见 [§9](#9-原版结构扩展per-chunk-放置与-atlasjigsaw)。本节只讲**模型定义**。

```java
// 1) 常规模型 / blockstate / 物品模型：照旧用现有 helper
//    （这份模型同时充当"模型加载器未生效时的回退外观"，也让物品栏图标正常）
simpleBlockWithItem(MyBlocks.GLOWING_ORE.get(),
        models().cubeAll("glowing_ore", modLoc("block/glowing_ore")));

// 2) CTM 定义（新增一个 provider）
public class CtmModelGen extends ALModelDefinitionProvider {
    public CtmModelGen(PackOutput output, ExistingFileHelper helper) {
        super(output, MyMod.MOD_ID, helper);
    }

    @Override
    protected void registerDefinitions() {
        // CTM 五连贴图自动推导：particle = 本体贴图，其余 = <dir>/ctm/<name>_ctm/0..3
        //（0=empty, 1=vertical, 2=horizontal, 3=center，与常见 CTM 材质包一致）
        ctm(MyBlocks.GLOWING_ORE).baseTexture("block/glowing_ore").emissive().save();

        // 柱类：显式给五张
        pillar(MyBlocks.POOP_PILLAR).pillarTextures(
                "block/pillar/self", "block/pillar/top", "block/pillar/center",
                "block/pillar/bottom", "block/poop_block").save();

        // 多格拼接：尺寸 + 编号贴图
        giant(MyBlocks.MURAL).size(2, 3).numberedTextures("block/mural/poop")
                .texture("particle", "block/poop_block").save();
    }
}

// 3) 挂到 GatherDataEvent（客户端资源）
generators.addProvider(event.includeClient(), new CtmModelGen(packOutput, existingFileHelper));
```

**入口方法**（都接受 `Block` 或 `BlockEntry`）：

| 入口 | 生成类型 | 必填字段（缺了 datagen 直接抛错） |
|---|---|---|
| `ctm(block)` | `relink:ctm` | particle / center / empty / vertical / horizontal |
| `carpetCtm(block)` | `relink:carpet_ctm` | 同上五张 |
| `paneCtm(block)` | `relink:pane_ctm` | 同上五张（可选 `paneEdges(edge, sideEdge)`） |
| `giant(block)` / `mural(block)` | 多格拼接 | `size(w,h)` + `"1".."w*h"` + particle |
| `pillar(block)` / `limitedPillar(block)` / `panePillar(block)` | 柱类 | particle / self / top / center / bottom |

**链式方法**（`ALModelDefinition`）：`baseTexture(...)`（可带 `ctmDir`）、`ctmDir(...)`、
`texture(key, rl)`、`pillarTextures(...)`、`paneEdges(...)`、`size(w,h)`、`numberedTextures(dir)`、
`emissive()`、`renderType("cutout"|"translucent"|…)`、`tint(0)` / `tint(r,g,b,a)`、
`property(key, json)`（逃生口，例如 `connect_to` 条件树）、`save()`。

- 生成路径 `assets/<你的modid>/relink/<方块注册名>.json`，与手写文件**完全等价**（运行时优先读它）；
- 生成时检查引用到的贴图是否存在，缺图汇总成一条 WARN（**不阻断**，因为贴图也可能来自资源包/其它模组）；
- 同一个方块重复声明会 WARN，并以最后一次为准。

### 3.6 兼容上游 Athena 写法

本库的加载器移植自 [Athena](https://github.com/terrarium-earth/Athena)，但把命名空间换成了 `relink`。
为了**让既有 Athena 格式资源不改一行就能用**，本库默认启用兼容层，额外认这些上游写法：

| 上游写法 | 兼容方式 |
|---|---|
| `"athena:loader": "athena:ctm"` | 读取 `relink:loader` 时会回退读 `athena:loader` |
| `athena:ctm` / `carpet_ctm` / `pane_ctm` / `giant` / `mural` / `pillar` / `limited_pillar` / `pane_pillar` | 同名注册一份 `athena:` 别名（上游的 8 个类型与本库完全一致） |
| `"loader": "athena:athena"` | 几何加载器 id 也注册了 `athena:athena` 别名 |
| 定义目录 `assets/<ns>/athena/**.json` | 一并扫描（同名条目以 `relink/` 的为准） |
| `athena:emissive` | **不兼容** —— 上游没有这个键，整模型发光是本库扩展 |

**与上游 Athena 共存**：上游注册的几何加载器 id 也是 `athena:athena`，两边同时注册会冲突。
因此本库用 `ModList.get().isLoaded("athena")` 判定：**检测到上游已加载就整体关闭兼容层**，
那些资源交给上游处理，并在日志里说明：

```
AbyssLib/ReLink: 未检测到上游 Athena -> 启用 athena:* 兼容层（Athena 格式的旧资源无需改写）
AbyssLib/ReLink: 检测到上游 Athena 已加载 -> 已禁用 athena:* 兼容层，athena 格式资源交给上游处理
```

兼容是**单向**的：本库认 `athena:`，Athena 不认 `relink:`。


---

## 4. 分区式创造栏

给创造栏标签页加"分区"（带标题横幅的分组）。**横幅渲染开箱即用**：随 `AbyssLibReginth` 自动注册，
消费方无需任何客户端代码。

### 4.1 建标签页与分区

```java
public final class MyItemGroups {
    private static final Reginth REGINTH = MyMod.reginth();

    public static final ALCreativeTabSection TS_ITEMS = new ALCreativeTabSection("itemGroup.mymod.section.items");
    public static final ALCreativeTabSection TS_BLOCKS = new ALCreativeTabSection("itemGroup.mymod.section.blocks");

    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> TAB = REGINTH.generic("main",
            Registries.CREATIVE_MODE_TAB, () ->
                    ALSectionedCreativeModeTab.configure(
                            CreativeModeTab.builder()
                                    .title(Component.translatable("itemGroup.mymod"))
                                    .icon(MyItems.SOME_ITEM::asStack),
                            MyItemGroups::populate,
                            TS_ITEMS, TS_BLOCKS
                    ).build()
    ).register();

    private static void populate(CreativeModeTab.ItemDisplayParameters parameters) {
        for (Item item : MyItems.getAllItems()) {
            TS_ITEMS.add(item);
        }
    }

    public static void register() {}
}
```

`ALSectionedCreativeModeTab.configure(...)` 有两个重载：

```java
configure(builder, populator, sections...)                    // 用默认横幅样式
configure(builder, bannerStyle, populator, sections...)        // 指定横幅样式
```

**分区内容的两种填充方式**（等价，可混用）：

```java
// 方式一：populate 回调里手动 add（上面的 MyItemGroups::populate）
TS_ITEMS.add(item);          // ItemLike
TS_ITEMS.add(itemStack);     // ItemStack
TS_ITEMS.add(() -> stack);   // 惰性 Supplier<ItemStack>
TS_ITEMS.clear();            // 清空

// 方式二：注册期自动归类（设置默认分区后，之后注册的方块/物品自动归入）
REGINTH.defaultCreativeSection(TS_ITEMS);

MyMod.reginth().item("some_item", Item::new).register();                 // → 自动进 TS_ITEMS
MyMod.reginth().item("no_tab_item", Item::new).ignore().register();      // → 排除，不进任何分区
MyMod.reginth().item("extra_item", Item::new)
        .addTabSection(MyItemGroups.TS_BLOCKS).register();               // → 额外进 TS_BLOCKS
```

> **注意**：分区会在每次 `buildContents` 清空后由标签页的 populate 重新填充。
> 因此依赖"注册期自动归类"的条目必须能被 populate 覆盖到（如遍历 `getAllItems()` 重新 add），
> 或直接用链式 API 手动归类——这与纯 populate 驱动的写法等价。

### 4.2 横幅样式（ALBannerStyle）

分区横幅是每个分区标题上方的那条色带/贴图。**样式按标签页各自独立**，建标签页时作为
`configure(...)` 的第二个参数传入；不传则用默认样式 `ALBannerStyle.DEFAULT`（绿色系纯色，整行）。

样式统一用**格数**（1~9）描述长度：每格 = 18px（创造栏一格宽），**9 = 整行 162px**。

| API | 说明 |
|---|---|
| `ALBannerStyle.colors(背景, 暗边框, 亮边框, 文字)` | 纯色，9 格整行（颜色为 ARGB，如 `0xFF123456`） |
| `ALBannerStyle.colors(格数, 背景, 暗边框, 亮边框, 文字)` | 纯色 + 指定格数 |
| `ALBannerStyle.texture(格数)` | 内置预设贴图（见下表） |
| `ALBannerStyle.texture(格数, "路径")` | 自定义贴图（支持 `"ns:path"` 或 ResourceLocation），拉伸到指定格数 |
| `样式.withUnits(格数)` | 在已有样式上改格数（纯色 / 贴图都有） |

**格数与像素宽**：`1→18`、`2→36`、`3→54`、`4→72`、`5→90`、`6→108`、`7→126`、`8→144`、`9→162`；
越界抛 `IllegalArgumentException`。

**内置预设贴图**位于本库 jar 的 `assets/abysslib/textures/gui/section/banner_1~9.png`，
N 号贴图宽 `N×18`、高 18，与格数精确对应，`texture(N)` 自动引入、像素级 1:1：

| `texture(n)` | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|---|---|---|---|---|---|---|---|---|---|
| 对应像素宽 | 18 | 36 | 54 | 72 | 90 | 108 | 126 | 144 | 162 |

```java
// 方式一：内置预设贴图，只写格数（texture(4) → banner_4.png，72px）
ALSectionedCreativeModeTab.configure(
        CreativeModeTab.builder().title(…).icon(…),
        ALBannerStyle.texture(4),
        MyItemGroups::populate, TS_ITEMS)

// 方式二：纯色 + 自定义格数
ALSectionedCreativeModeTab.configure(
        CreativeModeTab.builder().title(…).icon(…),
        ALBannerStyle.colors(6, 0xFF123456, 0xFF789ABC, 0xFFABCDEF, 0xFFFFFFFF),
        MyItemGroups::populate, TS_BLOCKS)

// 方式三：自定义贴图 + 格数
ALBannerStyle.texture(3, "mymod:textures/gui/creative/banner")
```

要点：

- **横幅与物品同行接续**：横幅 N 格时，该分区标题行行首 N 格被横幅占据，物品从右侧第 N+1 格开始同行排布
  （满 9 格换行）；N = 9 即横幅独占一整行、物品从下一行开始，与默认外观一致。
- 同一标签页内的所有分区**共用**该标签页的样式与格数（暂不支持一个标签页里分区各异）。
- 贴图模式标题文字固定白色带阴影（保证任何贴图上可读）；纯色模式用样式里的文字色。
- 自定义贴图建议为 18 的倍数宽、18 高；非匹配尺寸会整张拉伸到横幅宽度。

---

## 5. 消费方接入

`build.gradle`：

```gradle
repositories {
    maven { url = file("../AbyssLib/repo") }   // 本地发布仓库（先 ./gradlew publish）
    // 1.4.0 起注册框架已源码内置，不再需要 mvn.devos.one（Registrate）仓库。
    // 需要 SBM 的模组请自行再加 jitpack（SBM 不再由 AbyssLib 提供）
    // maven { url = "https://jitpack.io" }
}

dependencies {
    // 全套：注册框架 + 模型加载器 + 结构扩展都在聚合包里；无需声明 Registrate / Reginth 等额外依赖，
    // 也不要再 jarJar 它们。
    implementation("com.altnoir.abysslib:AbyssLib:1.0.0")

    // 只想要某一个功能时，换成对应模块（见 §0.1）。注意这些模块**不含** Reginth：
    // implementation("com.altnoir.abysslib:AbyssLib-Reginth:1.0.0")
    // implementation("com.altnoir.abysslib:AbyssLib-ReLink:1.0.0")
    // implementation("com.altnoir.abysslib:AbyssLib-Atlas:1.0.0")

    // 单装 ReLink / Atlas 时，若要用库提供的 datagen 助手（§3.5 / §9.2）再补这一条；
    // 不用 datagen（手写 JSON）就什么都不用加，运行时也不需要装 AbyssLib-Reginth。
    // compileOnly("com.altnoir.abysslib:AbyssLib-Reginth:1.0.0")
}
```

`neoforge.mods.toml`：见 [§0](#0-快速开始)（required 依赖 `abysslib`）。

> **不再需要 `mvn.devos.one` 仓库**。旧版本（≤1.3.0）需要它解析 AbyssLib `api` 传递出的 Registrate 构件；
> 1.4.0 起 Reginth 的类直接随 jar 发布，那条仓库可以删掉。
> 同理，把 AbyssLib 设成 `{ transitive = false }` 也不再需要补 Registrate 的 `runtimeOnly`。

> **间接用到上游 Registrate 的情况**：若你依赖的第三方 mod（如 Create）把上游
> `com.tterrag.registrate.*` 类型写进了公开 API，你的代码**只要触碰那些字段**，javac 就需要该类型。
> 这不是 AbyssLib 的问题，解决办法是在你的工程里加
> `compileOnly("com.tterrag.registrate:Registrate:<上游版本>")`；dev 运行时若同样报缺类再加一条 `runtimeOnly`。
> 判断方法：`javap -cp <mod.jar> <类名>` 看其公开签名里有没有 `com.tterrag.registrate.*`。

---

## 6. 迁移指南

### 6.1 从上游 Athena 资源迁移（**1.0.0 起可选**）

自 1.0.0 起本库自带 [§3.6](#36-兼容上游-athena-写法) 的兼容层，**既有 Athena 格式资源不改也能跑**，
所以这一步是**可选**的——只是推荐改成本库命名空间，以免将来兼容层调整时被动。

```powershell
Get-ChildItem -Recurse -Filter *.json | ForEach-Object {
  $t = Get-Content $_.FullName -Raw
  $n = $t -replace 'athena:athena', 'relink:model'   # 先处理几何加载器 id（避免被下一步拆坏）
  $n = $n -replace 'athena:', 'relink:'              # 键名 athena:loader + 类型值 athena:ctm 等
  if ($t -ne $n) { Set-Content $_.FullName $n -Encoding utf8NoBOM }
}
```

目录 `assets/<ns>/athena/` 若有使用，改名为 `assets/<ns>/relink/` 即可（内容不变）。

| 上游（Athena） | 本库（AbyssLib） |
|---|---|
| 声明键 `"athena:loader"` | `"relink:loader"` |
| 模型类型 `athena:ctm` / `athena:carpet_ctm` / `athena:pane_ctm` / `athena:giant` / `athena:mural` / `athena:pillar` / `athena:limited_pillar` / `athena:pane_pillar` | 同名换前缀：`relink:ctm` … |
| 几何加载器 `"loader": "athena:athena"` | `"loader": "relink:model"` |
| 定义目录 `assets/<ns>/athena/**.json` | `assets/<ns>/relink/**.json` |
| **其余内容**（`variants`、`ctm_textures`、`width`、`height`、文件位置） | **保持不变** |
| Java 包 `earth.terrarium.athena.**` | `com.altnoir.abysslib.model.**` |
| `athena:emissive` | **不存在**（上游没有这个键，整模型发光是本库扩展，只有 `relink:emissive`） |

### 6.2 迁移到 Reginth（1.4.0 起，破坏性）

1.4.0 把外部 `com.tterrag.registrate:Registrate` 换成了源码内置的 `com.altnoir.abysslib.reginth`。
**包前缀变了，且所有 `Registrate*` 类名都改成了 `Reginth*`**：

| 旧 | 新 |
|---|---|
| 包 `com.tterrag.registrate.**` | 包 `com.altnoir.abysslib.reginth.**` |
| `AbstractRegistrate` | `AbstractReginth` |
| `Registrate` | `Reginth` |
| `RegistrateBlockstateProvider` / `RegistrateItemModelProvider` / `RegistrateLangProvider` / `RegistrateRecipeProvider` / `RegistrateDataProvider` / `RegistrateTagsProvider` / `RegistrateLootTableProvider` / `RegistrateAdvancementProvider` / … | 同上规则：`Registrate` → `Reginth`，其余不变 |
| `ALRegistrate`（本库旧类，**已删除**） | `Reginth`（分区创造栏逻辑已并入其中） |
| `ALBlockBuilder` / `ALItemBuilder`（旧名） | `ReginthBlockBuilder` / `ReginthItemBuilder`（位于 `…reginth.builders`） |

**不含 `Registrate` 的类型名一律不动**：`ProviderType`、`DataGenContext`、`BlockEntry`、`ItemEntry`、
`RegistryEntry`、`NonNullFunction` / `NonNullSupplier`、`*Builder` 等。

批量改写（只动 import 行）：

```powershell
Get-ChildItem -Recurse src -Filter *.java | ForEach-Object {
  $t = Get-Content $_.FullName -Raw
  $n = $t -replace 'com\.tterrag\.registrate\.AbstractRegistrate', 'com.altnoir.abysslib.reginth.AbstractReginth'
  $n = $n -replace 'com\.tterrag\.registrate\.Registrate\b',        'com.altnoir.abysslib.reginth.Reginth'
  $n = $n -replace 'com\.tterrag\.registrate',                      'com.altnoir.abysslib.reginth'
  $n = $n -replace '\bALRegistrate\b', 'Reginth'
  if ($t -ne $n) { Set-Content $_.FullName $n -Encoding utf8NoBOM }
}
```

再把 `abysslib_version` 提到 `1.4.0`。注意 `Registrate*Provider` 这类**类名**也要跟着改成 `Reginth*Provider`
（上面的脚本只处理 import 行，代码体里的类型引用需一并替换；`\bRegistrate` → `Reginth` 的词边界替换即可）。

### 6.3 迁移到 1.0.0（模块化 + 命名空间改名，破坏性）

1.0.0 做了三件事：**（1）拆成模块；（2）模型/结构两条功能的命名空间从 `abysslib:` 改名；
（3）配置文件按 modid 重新命名。**

**（1）依赖坐标**——聚合包坐标不变，消费方通常只需改版本号：

```gradle
implementation("com.altnoir.abysslib:AbyssLib:1.0.0")   // 原来是 1.4.x
```

`neoforge.mods.toml` 里装聚合包时 `modId = "abysslib"` **不用改**；只装单个模块才换成
`abysslib_reginth` / `abysslib_relink` / `abysslib_atlas`。

**（2）命名空间改名**（资源键、注册表 id、定义目录）——`abysslib:` 不再被读取：

| 旧（0.x 时代的旧编号，≤1.4.x） | 新（1.0.0 起） |
|---|---|
| `"abysslib:loader"` | `"relink:loader"` |
| `abysslib:ctm` / `carpet_ctm` / `pane_ctm` / `giant` / `mural` / `pillar` / `limited_pillar` / `pane_pillar` | 同名换前缀：`relink:*` |
| `"loader": "abysslib:model"` | `"loader": "relink:model"` |
| `"abysslib:emissive"` | `"relink:emissive"` |
| 定义目录 `assets/<ns>/abysslib/**.json` | `assets/<ns>/relink/**.json` |
| `abysslib:jigsaw` | `atlas:jigsaw` |
| `abysslib:per_chunk` | `atlas:per_chunk` |
| `abysslib:grid_profile` | `atlas:grid_profile` |
| 数据包目录 `data/<包名>/abysslib/grid_profile/` | `data/<包名>/atlas/grid_profile/` |

批量改写（**只动 JSON 键与 id，不动 Java 包名**）：

```powershell
Get-ChildItem -Recurse -Include *.json | ForEach-Object {
  $t = Get-Content $_.FullName -Raw
  $n = $t
  foreach ($k in 'loader','emissive','model','ctm','carpet_ctm','pane_ctm','giant','mural','pillar','limited_pillar','pane_pillar') {
    $n = $n -replace "abysslib:$k", "relink:$k"
  }
  foreach ($k in 'jigsaw','per_chunk','grid_profile') { $n = $n -replace "abysslib:$k", "atlas:$k" }
  if ($t -ne $n) { Set-Content $_.FullName $n -Encoding utf8NoBOM }
}
# 定义目录改名
Get-ChildItem -Recurse -Directory -Filter abysslib | Where-Object { $_.Parent.Name -eq 'assets' } |
  ForEach-Object { Rename-Item $_.FullName -NewName 'relink' }
```

> 注意 [§6.1](#61-从上游-athena-资源迁移100-起可选)：**上游 Athena 写法（`athena:*`）不需要改**，
> 兼容层照旧认。
> 反过来，如果你的资源里写过 `relink:` 之前的老名字，那就按上表改。

**（3）配置文件改名** —— 配置文件按 `modid` 命名，旧的 `config/abysslib-client.toml` 不再被读取：

| 旧 | 新 |
|---|---|
| `config/abysslib-client.toml` | `config/abysslib_relink-client.toml` |

（配置项本身没变：`emissiveLayer` / `emissiveSuffix` / `emissiveExclude`。
把旧文件重命名过去即可；留着旧文件无害，只是不再生效。）

**（4）其它不变**：`com.altnoir.abysslib.**` 包名、`Reginth` API、`abysslib:grid_profile` 之外的
数据包注册表机制、`AbyssLib.modloc(...)` 等门面工具都不变。

---

## 7. 排错

**模型没被接管？** 把日志级别开到 DEBUG，接管时会打印：

```
AbyssLib/ReLink: replaced top-level model '<方块id>#<变体>' with model type relink:<类型>
```

没有这行说明 loader 声明没被找到——检查 `"relink:loader"` 键名、方块 id 与 blockstate 文件名是否对应。
（生产环境日志为 INFO，默认不打印。）

**发光叠加层没生效？** DEBUG 下会打印：

```
AbyssLib/ReLink: emissive overlay enabled for '<blockstate>' (base=..., overlay=..., separatePass=...)
```

没有这行说明基贴图没找到同后缀贴图——检查后缀、贴图路径/命名空间、是否被 `emissiveExclude` 排除。

**结构扩展的自检**（Atlas 模块，INFO 级，启动时必打两条）：
```
[AbyssLib/Atlas] 原版结构限制放宽（mod 加载完成）-> jigsaw: distance=256, depth=128 [codec=OK, verifyRange=待运行时, ...]
[AbyssLib/Atlas] 原版结构限制放宽（世界数据包加载完成）-> ... [codec=OK, verifyRange=OK, ...]
```

第二条里 `verifyRange=OK` 才算真的生效（第一条时数据包还没解析，显示"待运行时"是正常的）。

**贴图变成灰度 / 纯色？** 本库模型是**自己生成面**的，不读原版模型 JSON 里的 `tintindex`：

- 用**灰度贴图**的方块（`grass_block_top`、`oak_leaves`、红石线…）必须在定义里显式给颜色：
  `"tint": 0`（数字 = 原版 `BlockColor`/`ItemColor` 的 tint 索引，**生物群系染色走这条**）或
  `"tint": [r,g,b,a]`（固定色）。**不写 tint 就是贴图原样**，灰度贴图自然显示成灰度。
- 同时开了 `"relink:emissive": true` 会更明显：不受光照、无方向明暗，看上去就是一张平的灰图。

**原版方块（草方块/泥土/石头…）被替换成奇怪贴图？** 先确认 `build/resources/main/assets/` 下有没有
调试用的 `minecraft/**` 覆盖残留，然后重新 `gradlew build`。本库源码只含 `assets/abysslib/**`
（Reginth 的创造栏横幅）与 `assets/abysslib_relink/**`（ReLink 的配置译名），
**从不覆盖原版资源**（各模块 `jar` 任务都硬排除了 `assets/minecraft/**`）。

**配置入口里看不到某个模块？** 正常。统一配置入口（见 [§0.3](#03-统一配置入口聚合包)）只列出**有配置的**模块：
`AbyssLib-Reginth` / `AbyssLib-Atlas` 目前没有配置项，所以不会出现（避免点进空界面）。
若连 `AbyssLib` 的「配置」按钮都没有，说明你装的是单个模块而不是聚合包 —— 直接用那个模块自己的配置按钮即可。

**专用服务端报客户端类加载？** 不应发生。分区横幅在 `AbyssLibReginth`、模型加载器在 `AbyssLibReLink`
（都是 `@Mod(dist = CLIENT)`）里初始化，ReLink 的 mixin 配置也只有 `client` 段。

**单装 ReLink / Atlas 时 `RUN` 崩 / 报 `NoClassDefFoundError: com/altnoir/abysslib/reginth/...`？**
不应发生。这两个模块的运行时代码一个类都不引用 reginth，已实测：单独装 `AbyssLib-ReLink`（服务端 + 客户端）
与 `AbyssLib-Atlas`（服务端）均能正常启动，0 报错、0 缺类、0 mixin 失败。若确实遇到：

1. 确认你写的类有没有 `implements DataProvider` / `extends ALModelDefinitionProvider` / 调 `ALStructureDatagen` ——
   这些只在 `GatherDataEvent`（`runData`）里才会被加载，游戏内不会；
2. 确认没有把 `AbyssLib-Reginth` 写成 `implementation` 却又在 `neoforge.mods.toml` 里要求它 ——
   玩家侧不需要装它，`mods.toml` 也不应声明这个依赖；
3. 清一次 `run/` 与 `build/` 再试（旧 jar 的残留会误导）。

**datagen（`runData`）报 `找不到符号: BlockEntry` / `AbstractReginth`？** 说明你的工程编译期看不见 Reginth。
用聚合包时不该出现；单装 ReLink / Atlas 时补 `compileOnly("com.altnoir.abysslib:AbyssLib-Reginth:1.0.0")`，
或改用手写 JSON（见 [§0.2](#02-reginth-是可选依赖)）。

---

## 8. 分支、版本与许可

**版本**：

> **编号说明**：模块化之前的几次发布用**另一套旧编号**（1.2.0 / 1.3.0 / 1.4.0 / 1.4.5），按现在看都属于 **0.x 时代**；
> 模块化这一次是**首个正式版 `1.0.0`**。旧版本目录已从发布仓库移除，需要旧 jar 请用 tag `pre-modular-1.4.5` 重建。

| 版本 | 变更 |
|---|---|
| *0.x 时代*（旧编号） | 单模组时期：1.2.0 jarJar 内置 Registrate + 分区创造栏 → 1.3.0 源码内置模型加载器（命名空间 `abysslib`）→ 1.4.0 源码内置注册框架 `Reginth`、移除外部依赖与 jarJar（**破坏性**）→ 1.4.5 结构放宽 + per-chunk |
| **1.0.0** | **首个正式版 / 模块化**：拆成 `AbyssLib-Reginth` / `AbyssLib-ReLink` / `AbyssLib-Atlas` 三个**可单独安装**的模组 + 聚合包 `AbyssLib`；模型命名空间 `abysslib:` → **`relink:`**、结构命名空间 `abysslib:` → **`atlas:`**（**破坏性**，见 [§6.3](#63-迁移到-100模块化--命名空间改名破坏性)）；新增上游 Athena 写法兼容层（见 [§3.6](#36-兼容上游-athena-写法)）；ReLink / Atlas 不再内嵌 Reginth（改为 `compileOnly`，datagen 助手变成**可选依赖**，见 [§0.2](#02-reginth-是可选依赖)），聚合包 740 KB → **356 KB** |

**分支**（按 MC 线分开维护）：

| 分支 / 目录 | 目标 | 关键差异 |
|---|---|---|
| `1.21.1-NeoForge`（本文档）`D:\Minecraft\ModDev\AbyssLib` | NeoForge 1.21.1 / Java 21 | **模块化多项目**；源码内置 `Reginth`（1.4.0 起）与模型加载器（1.3.0 起）；命名空间 `relink:` / `atlas:`（1.0.0 起） |
| `26.1.2-NeoForge`（worktree）`D:\Minecraft\ModDev\AbyssLib-26.1.2` | NeoForge 26.1.2.94 / Java 25 | 仍是**单项目**；以外部依赖方式使用 Registrate `MC26.1-1.5.7`；分区栏为 MIA-26.1 模型；**暂未内置模型加载器，也未内置注册框架，尚未模块化** |

**许可**：本库自身代码为 **MIT**，见 [`LICENSE`](LICENSE)（`Copyright (c) 2026 Altnoir`）。

内置的第三方源码各有其原始版权人，署名与许可：

| 内置内容 | 来源 | 许可 |
|---|---|---|
| `Reginth`（注册框架） | Registrate `MC1.21-1.3.0+67`（tterrag1098） | MIT |
| CTM / 动态模型加载器 | Athena 1.21.1（Terrarium Earth） | MIT |

---

## 9. 原版结构扩展（per-chunk 放置与 atlas:jigsaw）

面向"要生成**超过原版 128 格**的大结构"的消费方：长道路、巨型地牢、跨群系的连续结构。
不覆盖任何原版文件，也不需要安装其它结构库。

> 本库另外放宽了原版上限（jigsaw `max_distance_from_center` 128→256、`size` 20→128、结构方块 48→128）。
> 那属于"**能不能声明**"；本节解决的是"**能不能长出来**"——两者是互补的两层，缺一层都不成立。

### 9.1 三个新增的类型

| 类型 | 标识符 | 作用 |
|---|---|---|
| `StructureType` | `atlas:jigsaw` | 与原版 jigsaw 同形（字段一致，多一个 `grid_profile`），但**锚点与随机种子固定到结构中心**；并把 piece 切成"每个 chunk 只带自己那片"，从而让足迹内每个 chunk 各自落地 |
| `StructurePlacementType` | `atlas:per_chunk` | **逐 chunk 放置**：足迹范围内每个 chunk 都持有结构起点。原版只有中心 chunk 有起点，而相邻 chunk 靠 references 得知结构存在、那个半径是**硬编码 ±8 chunk = 128 格**（`MAX_TOTAL_STRUCTURE_RANGE = 128` 的来历），超出的 piece 不落块 |
| 数据包注册表 | `atlas:grid_profile` | 上述两者的**唯一参数来源**（`spacing` / `separation` / `spread_type` / `salt` / `footprint_chunks`）。placement 与结构都只引用同一个 id，因此"两边参数不一致导致结构碎裂"在结构上不可能发生 |

> **必须成对使用**：`atlas:jigsaw` 配原版 `random_spread` → 只有中心 chunk 有起点，远处 piece 依旧不落块；
> `atlas:per_chunk` 配原版 `minecraft:jigsaw` → 每个 chunk 各算一份布局（原版以当前 chunk 为锚点）→ 结构碎裂。
> 另外 `atlas:per_chunk` 继承自原版 `RandomSpreadStructurePlacement`，所以 `/locate structure` 正常工作。

### 9.2 用 datagen 生成（推荐，走 reginth）

> **前置条件**：`ALStructureDatagen#register` 的签名里用了 Reginth 的 `AbstractReginth` / `DataProviderInitializer`，
> 所以**你的工程编译期需要 Reginth 可见**。用聚合包 `AbyssLib` 时它随 POM 传递，开箱可用；单装
> `AbyssLib-Atlas` 时请自行加 `compileOnly("com.altnoir.abysslib:AbyssLib-Reginth:1.0.0")`，
> 否则改用 [§9.3](#93-手写-json-的等价形式) 的手写 JSON。见 [§0.2](#02-reginth-是可选依赖)。

**不需要**自己写 `DatapackBuiltinEntriesProvider`：reginth 已经内置了这条通道。

```java
// ① grid profile（数据包注册表元素）
public final class MyGridProfiles {
    public static final ResourceKey<ALGridProfile> ROAD =
            ResourceKey.create(ALGridProfile.KEY, MyMod.loc("road"));

    public static void bootstrap(BootstrapContext<ALGridProfile> ctx) {
        //                                  spacing, separation, spreadType,                salt,      footprint_chunks
        ctx.register(ROAD, new ALGridProfile(64, 32, RandomSpreadType.LINEAR, 10387312, 8));
    }
}
```

```java
// ② 结构（注册进 Registries.STRUCTURE）
public static void bootstrap(BootstrapContext<Structure> ctx) {
    HolderGetter<Biome> biome = ctx.lookup(Registries.BIOME);
    HolderGetter<StructureTemplatePool> pool = ctx.lookup(Registries.TEMPLATE_POOL);
    Holder<ALGridProfile> profile = ctx.lookup(ALGridProfile.KEY).getOrThrow(MyGridProfiles.ROAD);

    ctx.register(MyStructures.ROAD, new ALJigsawStructure(
            new Structure.StructureSettings.Builder(biome.getOrThrow(MyTags.HAS_ROAD))
                    .generationStep(GenerationStep.Decoration.SURFACE_STRUCTURES)
                    .terrainAdapation(TerrainAdjustment.NONE)   // 道路（terrain_matching）用 NONE，见 §9.5
                    .build(),
            pool.getOrThrow(MyPools.ROAD_START),
            32,                                             // size（层深），AbyssLib 放宽到 128
            ConstantHeight.of(VerticalAnchor.absolute(0)),
            false,                                          // use_expansion_hack
            Heightmap.Types.WORLD_SURFACE_WG,
            128,                                            // max_distance_from_center（AbyssLib 上限 256）
            profile));
}
```

```java
// ③ 结构集（注册进 Registries.STRUCTURE_SET）
public static void bootstrap(BootstrapContext<StructureSet> ctx) {
    Holder<ALGridProfile> profile = ctx.lookup(ALGridProfile.KEY).getOrThrow(MyGridProfiles.ROAD);
    ctx.register(MyStructureSets.ROADS, new StructureSet(
            ctx.lookup(Registries.STRUCTURE).getOrThrow(MyStructures.ROAD),
            new ALGridPlacement(profile)));                 // 便利构造：其余用原版默认值
}
```

```java
// ④ 三行接进 reginth（放在模组入口构造器里即可，datagen 之外不会有副作用）
public MyMod(IEventBus modBus, ModContainer container) {
    DataProviderInitializer init = MyMod.reginth().getDataGenInitializer();
    init.add(ALGridProfile.KEY, MyGridProfiles::bootstrap);
    init.add(Registries.STRUCTURE, MyStructures::bootstrap);
    init.add(Registries.STRUCTURE_SET, MyStructureSets::bootstrap);
    // 模板池等照旧：init.add(Registries.TEMPLATE_POOL, MyPools::bootstrap);
}
```

产物路径（`runData` 后，与手写文件完全等价）：

```
src/generated/resources/data/<你的modid>/atlas/grid_profile/road.json
src/generated/resources/data/<你的modid>/worldgen/structure/road.json
src/generated/resources/data/<你的modid>/worldgen/structure_set/roads.json
```

实测生成的 profile JSON 形如：

```json
{ "spacing": 64, "separation": 32, "spread_type": "linear", "salt": 10387312, "footprint_chunks": 8 }
```

> ⚠️ **datagen 期间 Holder 尚未绑定**：`BootstrapContext.lookup(...)` 拿到的 `Holder` 在 bootstrap 过程中**不能**调 `value()`。
> 所以 `max_distance_from_center` 必须**显式传**，不能用 `profile.value().footprintChunks() * 16` 去推导
> （那会在 `runData` 时抛异常）。本库的 placement / 结构类型内部一律**运行时**才 `value()`，不存在这个问题。

#### 9.2.1 一键助手（可选，但省事）

嫌上面四段样板啰嗦时用 `ALStructureDatagen`：它把 profile + 结构 + **配套 structure_set** 一次登记，
并且 **structure_set 由 profile 自动派生**（同一 profile + `atlas:per_chunk`），
于是"两处 profile 必须一致"这件事**不可能写错**。

```java
public MyMod(IEventBus modBus, ModContainer container) {
    ALStructureDatagen.create()
        .profile(MyProfiles.ROAD, ALGridProfile.forRadius(256, 64, 10387312))   // 由半径自动派生 footprint_chunks
        .jigsaw(MyStructures.ROAD, MyStructureSets.ROADS, MyProfiles.ROAD,
                (biomes, pools, profile) -> new ALJigsawStructure(
                        new Structure.StructureSettings.Builder(biomes.getOrThrow(MyTags.HAS_ROAD))
                                .generationStep(GenerationStep.Decoration.SURFACE_STRUCTURES)
                                .terrainAdapation(TerrainAdjustment.NONE).build(),
                        pools.getOrThrow(MyPools.ROAD_START),
                        32, ConstantHeight.of(VerticalAnchor.absolute(0)), false,
                        Heightmap.Types.WORLD_SURFACE_WG, 256, profile))
        .register(MyMod.reginth());
}
```

两个助手各自解决的问题：

| 助手 | 解决什么 |
|---|---|
| `ALGridProfile.forRadius(maxDistance, spacing, salt)` | **自动派生 `footprint_chunks` = `ceil(半径 / 16)`**，并在**构造期**就校验 `spacing > separation` 与足迹是否重叠（抛 `IllegalArgumentException` 并给出建议值，比等数据包加载报错好定位） |
| `ALStructureDatagen` | 三条注册收成一处；`jigsaw(...)` 顺带把 set 建好 → **profile 只写一次**，杜绝两边不一致；不想用它也完全可以（等价写法见上面 §9.2 与 §9.3） |

> `ALStructureDatagen.structure(...)` 也需要 profile key（因为 `atlas:jigsaw` 一定引用 profile），
> 而工厂里拿到的 `Holder` 是 bootstrap 期间解析的 —— 见上面的 ⚠️。

### 9.3 手写 JSON 的等价形式

不用 datagen 时，三条 JSON 直接手写即可（路径同上）：

```json
// data/<ns>/atlas/grid_profile/road.json
{ "spacing": 64, "separation": 32, "spread_type": "linear", "salt": 10387312, "footprint_chunks": 8 }

// data/<ns>/worldgen/structure/road.json
{ "type": "atlas:jigsaw", "grid_profile": "<ns>:road",
  "start_pool": "<ns>:road/start", "size": 32, "max_distance_from_center": 128,
  "start_height": { "absolute": 0 }, "project_start_to_heightmap": "WORLD_SURFACE_WG",
  "use_expansion_hack": false, "terrain_adaptation": "none", "step": "surface_structures",
  "biomes": "#minecraft:is_overworld", "spawn_overrides": {} }

// data/<ns>/worldgen/structure_set/roads.json
{ "structures": [ { "structure": "<ns>:road", "weight": 1 } ],
  "placement": { "type": "atlas:per_chunk", "grid_profile": "<ns>:road" } }
```

### 9.4 字段与约束

**`grid_profile`（校验在加载期执行，写错会直接报错并给出行号级别的说明）**

| 字段 | 说明 |
|---|---|
| `spacing` | 网格单元边长（**chunk**），必须 > `separation`（否则原版算法里 `nextInt(0)` 抛异常） |
| `separation` | 中心在单元内的最小退让（chunk） |
| `spread_type` | `linear`（默认）/ `triangular`，与原版一致 |
| `salt` | 与原版一致的盐，决定中心落在单元内的哪个位置 |
| `footprint_chunks` | 结构中心到足迹边缘的 chunk 数 = `ceil(max_distance_from_center / 16)`；**必须满足 `footprint_chunks * 2 < spacing`**（否则相邻足迹重叠） |

**`atlas:jigsaw` 相对原版 `minecraft:jigsaw` 的差异**

| 字段 | 差异 |
|---|---|
| `grid_profile` | **新增且必填**（引用 `atlas:grid_profile`） |
| `max_distance_from_center` | **上限 256**（原版 128）—— 取舍与代价见 §9.4.1。同时受 `footprint_chunks * 16` 约束，超出会在运行时打一条 WARN |
| `size` | 上限 128（原版 20） |
| 其余字段 | 与原版 jigsaw 完全一致（`start_pool` / `size` / `start_height` / `use_expansion_hack` / `project_start_to_heightmap` / `pool_aliases` / `dimension_padding` / `liquid_settings` / `biomes` / `step` / `terrain_adaptation` / `spawn_overrides`） |

#### 9.4.1 `max_distance_from_center`：上限 **256**（原版 128）

| 取值 | `footprint_chunks` | 足迹面积 | 说明 |
|---|---|---|---|
| 128（原版上限） | 8 | (2·8+1)² = **289** chunk | 只够约 256 格跨度；**原版机制**下超过 128 格就开始掉 piece（本库有 per-chunk，不受此限） |
| **256（上限，也推荐）** | **16** | (2·16+1)² = **1089** chunk（覆盖 512×512 格） | **绝大多数巨型结构（长道路、大城堡、地牢）用这个**，代价可控 |

**为什么上限是 256 而不是 512。** 原版只能到 128 的根因是"邻居结构靠 references 传播，那个半径硬编码 8 chunk"——
**这个根因已经由 per-chunk 放置彻底解决**（见 §9.1 与 `HANDOFF.md` §11），所以"抬高上限"能拿到的收益
per-chunk 已经拿到了；再往上抬只是徒增最坏开销：

1. **足迹面积 ∝ r²**：足迹内**每一个** chunk 都会走一次我们的 `findGenerationPoint`（缓存命中 + 按 chunk 索引取片）。
   256 → 512 就是 **4 倍**（1089 → 4225 个 chunk）。上限钉在 256，最坏足迹就被钉在 1089。
2. **jigsaw 展开盒变大**：`JigsawPlacement` 的候选搜索空间随半径增长。好在**每个 cell 只展开一次**（有布局缓存），
   所以这是"每个结构一次"的成本，不是每 chunk 成本。
3. **布局本身更大**：piece 更多 → 缓存里每份布局更占内存（缓存有 256 条上限，超限整体清空）。
4. **`spacing` 被迫变大**：`footprint_chunks * 2 < spacing` 是硬校验 ⇒ 256 需要 `spacing > 32` chunk（≈520 格），
   即"巨型结构必须稀疏"。用 `ALGridProfile.forRadius(...)` 时会在**构造期**直接抛异常并告诉你需要多大 `spacing`。

常用配置参考：

```java
ALGridProfile.forRadius(256, 64,  10387312)   // 推荐：footprint=16，要求 spacing > 32 → 取 64 很宽松
ALGridProfile.forRadius(128, 32,  10387312)   // 小结构：footprint=8，要求 spacing > 16
```

**结论**：**按 256 设计**。真需要跨度超过 512 格的整体结构时，正确做法是**拆成多个相邻的结构**
（per-chunk 已经保证每个都能完整生成），而不是去抬这个上限。

> 两个"不会增加"的成本，可以放心：**客户端零成本**（结构数据不发给客户端）；也**不会级联生成远处 chunk**
> （per-chunk 只在你实际加载的 chunk 上付费，而不是像"放大 references 半径"那样让全世界每个 chunk 都多扫邻居）。

### 9.5 长道路与地形贴合

道路用 `projection: terrain_matching` 的模板池时，**per-chunk 与它完全兼容**：`GravityProcessor` 是**逐块**读高度图的，
不依赖"能不能看到别的 piece"，所以切片不会造成接缝，反而比原版更宽松（原版要求持有该块的 chunk 距起点 ≤8 chunk 且 piece 名义包围盒与其相交）。

三条要点：

1. **`terrain_adaptation` 填 `none`**：`Beardifier` 只处理 `projection: rigid` 的 piece，对 `terrain_matching` 的 piece 设 beard 等于没设。
2. **垂直位移有 ≈16 格（一个 chunk 写入半径）的硬上界**：`StructureTemplate.placeInWorld` 会按写入区裁剪，
   被贴地处理器挪出写入区的方块会被**静默跳过**。所以别用一条模板跨深谷/陡崖，改成分段 + 桥墩/支柱。
3. **`rigid` + `beard_thin`/`bury`（城堡那类）同样保住原版贴合**：本库的切片是"保留与本 chunk ±1 chunk 相交的 piece"，
   它与 FEATURES 的写入半径一致，因此 `Beardifier` 需要的"距本 chunk 12 格内的 piece"全部可见。

> 状态说明（照实记录）：上述 **datagen 链路已实测**（`runData` 能正确生成 profile/structure/set 三个 JSON）；
> **服务端实际生成**（结构落地、>128 格完整性、跨 chunk 无接缝）的端到端实测尚未完成，请以你自己的实测为准。
