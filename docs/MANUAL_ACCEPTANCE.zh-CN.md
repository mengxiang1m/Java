# 人工验收：你需要做的步骤

代码接入、自动测试、JDK/Gradle 准备、IDEA 安装包准备、插件打包和 Git 提交由代理完成。
你只需要处理自己的平台账号/密钥，以及实际界面的人工确认。

## 1. 运行终端向导

在本项目目录打开终端，运行：

```bash
bash scripts/manual-acceptance.sh
```

向导会依次完成下面三步。不需要手动修改 Java 代码。

## 2. 登录平台并提供配置

- 作业 PDF 同时列出同济平台和 DeepSeek，没有强制其中一个。
- 选 `tongji`：登录 https://agi.tongji.edu.cn ，从账号的 API 调用说明和模型列表复制 **兼容 OpenAI 的 base URL** 和 **可用模型 ID**。登录后的实际菜单以平台页面为准；不要把普通网页地址当接口地址。
- 选 `deepseek`：登录 https://platform.deepseek.com/api_keys ，创建或复制自己的 API key；向导会填好默认接口和模型。
- 在终端隐藏输入 API key；粘贴后看不到字符是正常的。密钥不写入仓库、配置文件或 Git。
- `mock` 只用于界面演示，不能据此判定真实 RAG 回答通过。

## 3. 在新 IDEA 沙箱中逐项检查

先关闭旧测试沙箱。若出现许可或账号界面，请本人处理。
打开 `integration-standalone/build/acceptance-materials` 文件夹作为项目；这些 Demo 文档由程序生成，不是真实课程课件。
选择 **View > Tool Windows > CST3492 Assistant**。

| 操作 | 预期结果 | 你的结果 |
|---|---|---|
| 点击 **Import materials**，选择三个 `JavaEnterprise-Demo` 文档 | 显示导入 3 个文档、6 个知识块 | 三份资料来源已截图；导入计数未单独截图 |
| 提问 `Explain JDBC transaction rollback` | 回答结合资料，至少一条真实来源指向 PDF 第 2 页或 PPT 第 2 张 | 用户已确认；问题和来源截图已归档 |
| 提问 `Explain java.time.LocalDate formatting` | 没有来源列表，并明确说明基于通用知识 | 用户已确认；声明截图已归档 |
| 打开 `Demo.java`，选中方法，右键 **Ask CST3492 Assistant** | 代码预览保留选中内容；主动发送后回答结合代码 | 修复后已重测；代码预览及回答截图已归档 |
| 连续提问、调整窗口宽度、切换明暗主题 | 不重复发送；内容可读；请求完成后按钮恢复 | 待人工确认 |

源码与资料不会因为选中或导入而发送给 API。主动发送问题时，会发送问题、附带代码和检索到的知识块。
知识库保存在当前 Tool Window 会话内；重启沙箱后需要重新导入。

记录实际结果，并保存“有来源回答”“通用知识回答”“右键代码预览”三张截图。
这些人工结果提供给组员 D，作为整体验收和英文报告的证据。

建议文件名为 `01-course-sources.png`、`02-general-knowledge.png`、
`03-selected-code.png`。截取 IDEA 的代码/聊天区域，课程回答要展开 Sources；
长回答不必全部截入同一张。不要截平台密钥页面。

本次人工测试曾发现：第三条回答有 `[1]`，同时显示通用知识声明。
该问题已修复，用户已重启沙箱并补拍第三项；截图可见选中代码和正确分支解释，
未出现原先的无效引用编号。

已收集的图片与可直接交给 D 的英文展示说明见：
[Presentation evidence](PRESENTATION_EVIDENCE.md)。前三项核心流程已有人工确认，
明暗主题、窗口缩放等额外视觉检查仍按表中状态记录，不应写成全部已完成。

如同济账号无法找到接口或可用模型，记录页面说明即可，不要猜地址，也不要把密钥发到聊天中。
