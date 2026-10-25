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
| 点击 **Import materials**，选择三个 `JavaEnterprise-Demo` 文档 | 显示导入 3 个文档、6 个知识块 | 待人工确认 |
| 提问 `Explain JDBC transaction rollback` | 回答结合资料，至少一条真实来源指向 PDF 第 2 页或 PPT 第 2 张 | 待人工确认 |
| 提问 `Explain java.time.LocalDate formatting` | 没有来源列表，并明确说明基于通用知识 | 待人工确认 |
| 打开 `Demo.java`，选中方法，右键 **Ask CST3492 Assistant** | 代码预览保留选中内容；主动发送后回答结合代码 | 待人工确认 |
| 连续提问、调整窗口宽度、切换明暗主题 | 不重复发送；内容可读；请求完成后按钮恢复 | 待人工确认 |

源码与资料不会因为选中或导入而发送给 API。主动发送问题时，会发送问题、附带代码和检索到的知识块。
知识库保存在当前 Tool Window 会话内；重启沙箱后需要重新导入。

记录实际结果，并保存“有来源回答”“通用知识回答”“右键代码预览”三张截图。
这些人工结果提供给组员 D，作为整体验收和英文报告的证据。

如同济账号无法找到接口或可用模型，记录页面说明即可，不要猜地址，也不要把密钥发到聊天中。
