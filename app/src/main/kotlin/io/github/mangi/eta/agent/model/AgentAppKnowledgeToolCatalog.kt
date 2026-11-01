package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/** App 使用知识库工具：记住用户在各 App 的操作流程，并在需要时回忆。 */
internal object AgentAppKnowledgeToolCatalog {
    fun appendTo(tools: JSONArray) {
        tools.put(
            AgentToolSchema.function(
                name = "remember_app_usage",
                description = "记住用户在某个 App 中的操作流程，供下次自动执行。只在用户明确表示要记住/记下来/下次自动做时调用；调用前必须用自然语言向用户复述要记住的步骤并得到确认。steps_json 是 JSON 数组字符串，每个步骤形如 {\"action\":\"click|input|swipe|back|wait\",\"target\":\"目标描述\",\"text\":\"输入文本(仅input需要)\"}。同一 App 的相同操作会覆盖更新，不会重复。",
                parameters = JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject()
                            .put(
                                "package_name",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "App 包名，如 com.tencent.mm"),
                            )
                            .put(
                                "app_name",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "App 显示名称，如 微信"),
                            )
                            .put(
                                "action_description",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "自然语言描述这个操作是做什么的，如“给张三发微信红包”"),
                            )
                            .put(
                                "steps_json",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "操作步骤的 JSON 数组字符串"),
                            ),
                    )
                    .put("required", JSONArray().put("package_name").put("action_description").put("steps_json")),
            ),
        )
        tools.put(
            AgentToolSchema.function(
                name = "recall_app_usage",
                description = "回忆知识库中记住的 App 操作流程。当用户说“帮我在 XX App 里做 YY”时，先用本工具按 App 包名和用户意图检索；返回最匹配的操作记录（含步骤 JSON），按匹配度排序。若返回为空，说明没记住过，直接按正常流程手动操作，完成后可询问用户是否记住。",
                parameters = JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject()
                            .put(
                                "package_name",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "App 包名，如 com.tencent.mm"),
                            )
                            .put(
                                "query",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "用户意图的自然语言描述，如“发红包”"),
                            ),
                    )
                    .put("required", JSONArray().put("package_name").put("query")),
            ),
        )
    }
}
