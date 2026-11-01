package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/** 文本输入、等待与系统操作工具 schema。 */
internal object AgentTextSystemToolCatalog {
    fun appendTo(tools: JSONArray) {
        tools
            .put(
                AgentToolSchema.function(
                    name = "type_text",
                    description = "向输入框写入文本，结果会读回校验。默认 mode=replace 把整个输入框替换为 text，text 为空即清空；" +
                        "mode=append 在当前光标处插入。指定 index 时写入该 editable 节点，不必先点击；不指定时写入当前输入焦点。" +
                        "submit=true 写入后按输入法回车，用于搜索、发送；结果 submitted=false 时说明文本已写入但未提交，改为点击发送按钮，不要重复输入。" +
                        "中文、emoji、长文本都直接传入，无需借助剪贴板。",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("text", JSONObject().put("type", "string").put("maxLength", 4_000))
                                .put(
                                    "mode",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("enum", JSONArray().put("replace").put("append"))
                                        .put("description", "replace 整体替换（默认），append 在光标处插入。"),
                                )
                                .put(
                                    "index",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put("description", "可选，editable 节点 index；仅 replace 可用，必须同时传入同一次观察的 observation_id。"),
                                )
                                .put(
                                    "observation_id",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("description", "指定 index 时必传，与 index 来自同一次最近的观察。"),
                                )
                                .put(
                                    "submit",
                                    JSONObject()
                                        .put("type", "boolean")
                                        .put("description", "写入后按输入法回车提交，默认 false。"),
                                )
                        )
                        .put("required", JSONArray().put("text"))
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "set_clipboard",
                    description = "把文本写入系统剪贴板。适合准备粘贴长文本、中文、emoji 或特殊字符。",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "text",
                                    JSONObject().put("type", "string").put("maxLength", 20_000),
                                )
                        )
                        .put("required", JSONArray().put("text"))
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "get_clipboard",
                    description = "读取系统剪贴板文本。Android 版本或后台限制可能导致读取失败。",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject())
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "press_key",
                    description = "按系统按键或全局动作。BACK/HOME/RECENTS/NOTIFICATIONS/QUICK_SETTINGS 优先走无障碍全局动作；ENTER 优先走输入法回车。",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "button",
                                    JSONObject()
                                        .put("type", "string")
                                        .put(
                                            "enum",
                                            JSONArray()
                                                .put("BACK")
                                                .put("HOME")
                                                .put("ENTER")
                                                .put("RECENTS")
                                                .put("PASTE")
                                                .put("NOTIFICATIONS")
                                                .put("QUICK_SETTINGS")
                                        )
                                )
                        )
                        .put("required", JSONArray().put("button"))
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "wait",
                    description = "等待一段时间，让动画、网络加载或页面跳转完成。不要用它代替 wait_for_text/wait_for_package 的可验证等待。",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "duration_ms",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put("description", "等待时长，100 到 30000，默认 1000。")
                                )
                        )
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "wait_for_text",
                    description = "等待当前屏幕出现指定文本或描述，适合点击后确认页面已到达、列表加载完成、弹窗出现。",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("text", JSONObject().put("type", "string"))
                                .put(
                                    "timeout_ms",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put("description", "最长等待时间，500 到 60000，默认 10000。")
                                )
                                .put(
                                    "include_desc",
                                    JSONObject()
                                        .put("type", "boolean")
                                        .put("description", "是否匹配 content-desc，默认 true。")
                                )
                                .put(
                                    "match",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("enum", JSONArray().put("contains").put("exact").put("prefix").put("regex"))
                                        .put("description", "匹配方式，默认 contains。")
                                )
                        )
                        .put("required", JSONArray().put("text"))
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "wait_for_package",
                    description = "等待指定 Android package 到前台，适合 launch_app/open_uri 后确认目标应用已打开。",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("package_name", JSONObject().put("type", "string"))
                                .put(
                                    "timeout_ms",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put("description", "最长等待时间，500 到 60000，默认 10000。")
                                )
                        )
                        .put("required", JSONArray().put("package_name"))
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "open_system_panel",
                    description = "打开通知栏或快捷设置面板。",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "panel",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("enum", JSONArray().put("notifications").put("quick_settings"))
                                )
                        )
                        .put("required", JSONArray().put("panel"))
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "ask_user_choice",
                    description = "当需要用户做选择时调用：弹出悬浮选择卡片展示选项，用户点选后自动返回结果。" +
                        "适合二选一/多选一场景（如先清哪个应用的缓存、执行哪个操作），无需用户打字回复。" +
                        "调用后会阻塞等待用户点选（最长 120 秒），返回选项序号 index（从 0 开始）和标题 title。" +
                        "options 最多 4 项，每项含 title（标题）、desc（描述，可空）、recommend（是否推荐，推荐项会高亮绿边）。",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("title", JSONObject().put("type", "string").put("description", "卡片标题，如\"请选择\""))
                                .put("sub", JSONObject().put("type", "string").put("description", "副标题说明，可空"))
                                .put(
                                    "options",
                                    JSONObject()
                                        .put("type", "array")
                                        .put("maxItems", 4)
                                        .put("minItems", 1)
                                        .put(
                                            "items",
                                            JSONObject()
                                                .put("type", "object")
                                                .put(
                                                    "properties",
                                                    JSONObject()
                                                        .put("title", JSONObject().put("type", "string"))
                                                        .put("desc", JSONObject().put("type", "string"))
                                                        .put("recommend", JSONObject().put("type", "boolean"))
                                                )
                                                .put("required", JSONArray().put("title"))
                                        )
                                )
                        )
                        .put("required", JSONArray().put("title").put("options"))
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "show_copyable_text",
                    description = "当生成了需要用户复制的内容（长文本、代码、链接、中文段落等）时调用：" +
                        "弹出悬浮卡片展示文本并提供一键复制按钮，用户复制或关闭后返回结果。" +
                        "调用后会阻塞等待用户操作（最长 120 秒），返回 copied 表示用户是否点了复制。",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("title", JSONObject().put("type", "string").put("description", "卡片标题，可空"))
                                .put("text", JSONObject().put("type", "string").put("maxLength", 20_000))
                        )
                        .put("required", JSONArray().put("text"))
                )
            )
    }
}
