# pengyoudejiu

一个适用于 **Minecraft 1.20.1 / Forge 47.4.10 / Java 17** 的音乐唱片 Mod。

- Mod 作者：**xc**
- 唱片显示署名：**小助 — 朋友的酒**
- 内部 Mod ID：`examplemod`（为保留已有唱片和资源的兼容性，不作重命名）
- 唱片 ID：`examplemod:dance_disc`

## 功能

将本 Mod 的唱片放入**原版唱片机**即可播放音乐。本项目不新增独立的唱片机方块。

- **32 格球形范围**内符合条件的生物通过正常寻路靠近唱片机，不瞬移、不穿墙。
- 进入默认 **8 格舞蹈范围**后，生物脚底保持原位，模型竖直拉长、压缩并持续自转。
- 取出唱片、破坏唱片机或播放自然结束时，舞蹈和吸引停止，原有 AI 恢复控制。
- 支持多台唱片机，优先选择最近的有效播放源。
- 原版唱片不会触发本 Mod 的吸引和舞蹈。

唱片可在 `pengyoudejiu` 创造模式分页及原版“工具与实用物品”分页找到，也可使用命令：

```mcfunction
/give @s examplemod:dance_disc
```

吸引仅作用于已加载、正在运行 AI 且有可达路线的生物；战斗、逃命、坐下的宠物等特殊状态存在保护。舞蹈不改变实际碰撞箱。详细限制和配置参见 [音乐唱片与舞蹈说明](docs/音乐唱片与舞蹈.md)。

## 安装和升级

1. 安装 Minecraft 1.20.1 对应的 Forge；推荐使用本项目构建版本 **47.4.10**。
2. 将 `pengyoudejiu-1.0.0.jar` 放入游戏的 `mods` 文件夹。
3. 多人游戏中，服务器和客户端均安装本 Mod。
4. 更新时完全退出游戏后替换旧版 JAR，不要同时放入旧 `examplemod` JAR 和新版 JAR。

**从早期开发版升级前请备份世界。** 苹果糖和示例方块已经移除，旧世界中的这些物品或方块可能丢失，Forge 也可能提示缺失注册项。唱片 ID、声音 ID 和唱片相关配置文件名保持不变；本次清理不修改唱片功能。

## 构建与开发

使用 **JDK 17** 和仓库自带的 **Gradle Wrapper 8.8**。不要使用系统安装的 `gradle.bat`，也不要用不存在的 `run` 任务。

Windows（在项目根目录执行）：

```bat
gradlew.bat build
gradlew.bat runClient
```

Linux / macOS：

```sh
chmod +x gradlew
./gradlew build
./gradlew runClient
```

构建产物位于 `build/libs/pengyoudejiu-1.0.0.jar`。首次构建需要联网下载依赖。Forge Maven 证书或网络故障应通过检查网络、代理和 Java 信任库解决；仓库与 GitHub Actions 不永久关闭证书检查。本机已有缓存的临时离线方式见 [现有开发说明](docs/音乐唱片与舞蹈.md)。

## 验证

运行原有唱片/吸引回归测试以及示例清理测试：

```bat
gradlew.bat runGameTestServer
```

音乐导入和资源检查需要 Node.js，建议 22 LTS：

```sh
node --test tests/music-import.test.cjs tests/cleanup.test.cjs
```

纯 Java 动画数学测试的执行方式见 [GitHub Actions 工作流](.github/workflows/build.yml)。服务端测试不包含客户端画面的人工观感验证。

## 发布到 GitHub

仓库已经包含构建工作流：推送或提交 PR 后，自动执行构建和测试，并把构建成功的 JAR 保存为 Actions artifact；**不会自动发布 Release**。GitHub 云端运行结果需在实际推送后查看。

`.gitignore` 会忽略 IDE 文件、Gradle 缓存、构建产物、开发存档、日志和原始 MP3/MP4 素材。实际播放所需的 `dance_song.ogg`、唱片资源和 Gradle Wrapper **必须提交**。忽略原始素材不会删除本地文件。

创建一个空的 GitHub 仓库后，可手动执行以下命令；提交前检查 `git status`，不要包含账号凭据、存档或无关文件：

```sh
git init
git add .
git status --short
git commit -m "Prepare pengyoudejiu for GitHub"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/pengyoudejiu.git
git push -u origin main
```

将 `YOUR_USERNAME` 替换为自己的 GitHub 用户名。如目录已经是 Git 仓库或已有 `origin`，先检查现有配置，不要重复添加远程。将构建的 JAR 上传到 GitHub Release，源码仓库无需提交 `build/`。

## 目录与第三方声明

- [使用和学习说明](docs/音乐唱片与舞蹈.md)
- [项目文档入口](docs/README.md)
- [Forge 1.20.1 离线文档](docs/forge-1.20.1/index.html)
- `src/main/java/com/example/examplemod/`：Mod 入口及唱片、舞蹈、网络实现。
- `src/main/resources/`：音频、唱片模型、贴图、语言和其他必要资源。
- `tools/`、`tests/`：音乐导入、文档生成及自动化测试工具。

现有 Mod 元数据中的许可仍为 **All Rights Reserved**；发布到公开仓库不等于已为原创代码和音乐选择开源许可。发布前请确认希望采用的代码/音乐许可及音频分发权。

原 MDK 的 [LICENSE.txt](LICENSE.txt)、[CREDITS.txt](CREDITS.txt) 和原始说明保留，用于说明 Forge 等第三方内容的来源与许可，不能把它们误认为对原创音乐的统一授权。下载的 Forge 文档保留其自身 MIT 许可证。
