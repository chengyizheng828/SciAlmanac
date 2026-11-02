# 科研黄历 (Sci-Almanac)

实验室每日运势 + 挫折日记的「科研玄学」Android 应用，Miuix / 澎湃 HyperOS 视觉风格。

每天开实验前先算一卦：今日宜过硅胶柱还是忌碰手套箱，AI 结合你最近的翻车记录给出毒舌批注；翻车了记一笔，下次算卦时它还会翻你旧账。

## 功能

- **🔮 今日黄历**：每日运势等级（大吉~诸事不宜）、宜忌双栏、AI 批注；支持「重新算卦」（AI 基于原卦再算一签，会遭反噬）与今日科研问卦（流式回答 + 打字机效果）
- **📅 运势日历**：12 个月信息流，顶栏年份下拉（无限滚动）切换年份，每日运势色点 + 日记标记，点击看当日详情
- **📓 挫折日记**：实验翻车记录，标签分类（可自定义排序/增删），长按弹出编辑/删除菜单，本地 Room 数据库存储
- **⚙️ 设置**：AI 接口配置（支持拉取模型列表）、科研画像、问卦快捷选项、网络热梗库、界面小提示开关、JSON 备份/恢复、关于页

## 下载

见 [Releases](../../releases)：提供正式签名的 release 包（`app-release.apk`，推荐）与 debug 包（`app-debug.apk`）。

> release 与 debug 是不同 applicationId（`com.cyz.scialmanac` / `com.cyz.scialmanac.debug`），数据不互通，可共存。

## 技术栈

Kotlin · Jetpack Compose · [Miuix](https://github.com/YuKongA/Miuix) · Room · OkHttp (SSE 流式) · Material Icons

## 界面与代码借鉴说明

本项目的界面骨架与交互大量参考以下开源项目，在此明确致谢：

- **[tiann/KernelSU](https://github.com/tiann/KernelSU)**（GPL-3.0）：整体 Scaffold 骨架、液态玻璃悬浮底栏（Haze 毛玻璃）、顶部大标题栏、今日运势主卡片、日记卡片流、设置页分组列表等布局与交互模式，均借鉴其 manager 模块的 Miuix 实现
- **[YuKongA/Miuix](https://github.com/YuKongA/Miuix)**（Apache-2.0）：使用的 UI 组件库来源，弹窗、设置项（SuperArrow/SuperSwitch 等）、文本框等组件的标准用法参考其 example

## 开发说明

本项目由 **给我程碗饭（@chengyizheng828）** 主导，使用 **Kimi 搭配 Codex**（AI 编程助手）协作完成：需求设计、故事板与提示词由本人完成，代码编写、真机调试与迭代由 Codex 在 Redmi K70 Ultra 上自动化闭环完成。

## 构建

```bash
./gradlew assembleDebug    # 调试包
./gradlew assembleRelease  # 发布包（用 debug keystore 签名）
```

要求 JDK 21、Android SDK 33+。

## 免责

运势内容纯属 AI 根据科研画像与日记生成的娱乐向文本，不构成任何实验安全建议。高危操作请严格遵守实验室安全规范。
