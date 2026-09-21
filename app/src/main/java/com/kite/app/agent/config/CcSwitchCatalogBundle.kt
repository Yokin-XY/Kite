package com.kite.app.agent.config

/**
 * CC Switch 供应商预置目录（构建期生成的 vendor 数据，请勿手改）。
 *
 * 上游：farion1231/cc-switch (MIT, Copyright (c) 2025 Jason Young)。
 * 再生成：tools/cc-switch-vendor/convert-all.mjs → convert-kite.mjs。
 * 解析方：[CcSwitchBundledCatalogParser]。
 */
internal object CcSwitchCatalogBundle {
    internal val CATALOG_JSON: String = """
{
 "schemaVersion": 1,
 "source": "farion1231/cc-switch@master (MIT)",
 "routes": {
  "claude-code": [
   {
    "id": "kimi",
    "displayName": "Kimi",
    "baseUrl": "https://api.moonshot.cn/anthropic",
    "models": [
     {
      "id": "kimi-k2.7-code",
      "displayName": "kimi-k2.7-code"
     }
    ],
    "vendorDisplayName": "Kimi",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.kimi.com?aff=cc-switch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "kimi-for-coding",
    "displayName": "Kimi For Coding",
    "baseUrl": "https://api.kimi.com/coding",
    "models": [
     {
      "id": "kimi-for-coding",
      "displayName": "kimi-for-coding"
     }
    ],
    "vendorDisplayName": "Kimi For Coding",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://www.kimi.com/code/?aff=cc-switch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "shengsuanyun",
    "displayName": "Shengsuanyun",
    "baseUrl": "https://router.shengsuanyun.com/api",
    "models": [
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "anthropic/claude-sonnet-5"
     }
    ],
    "vendorDisplayName": "Shengsuanyun",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.shengsuanyun.com/?from=CH_4HHXMRYF",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "ppio",
    "displayName": "PPIO",
    "baseUrl": "https://api.ppio.com/anthropic",
    "models": [
     {
      "id": "deepseek/deepseek-v4-flash-0731",
      "displayName": "deepseek/deepseek-v4-flash-0731"
     }
    ],
    "vendorDisplayName": "PPIO",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://ppio.com/activity/ccswitch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "火山-agent-plan",
    "displayName": "火山 Agent Plan",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/plan",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "ark-code-latest"
     }
    ],
    "vendorDisplayName": "火山 Agent Plan",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.volcengine.com/activity/agentplan?ac=MMAP8JTTCAQ2&rc=6J6FV5N2&utm_source=OWO&utm_medium=devrel-1&utm_campaign=hw&utm_term=ccswitch&utm_content=hw",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "火山-coding-plan",
    "displayName": "火山 Coding Plan",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/coding",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "ark-code-latest"
     }
    ],
    "vendorDisplayName": "火山 Coding Plan",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://www.volcengine.com/activity/codingplan?ac=MMAP8JTTCAQ2&rc=6J6FV5N2&utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "byteplus",
    "displayName": "BytePlus",
    "baseUrl": "https://ark.ap-southeast.bytepluses.com/api/coding",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "ark-code-latest"
     }
    ],
    "vendorDisplayName": "BytePlus",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.byteplus.com/en/product/modelark?utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "volcengine-doubao",
    "displayName": "Volcengine Doubao",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/compatible",
    "models": [
     {
      "id": "doubao-seed-2-1-pro-260628",
      "displayName": "doubao-seed-2-1-pro-260628"
     }
    ],
    "vendorDisplayName": "Volcengine Doubao",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey?apikey=%7B%7D&utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "siliconflow",
    "displayName": "SiliconFlow",
    "baseUrl": "https://api.siliconflow.cn",
    "models": [
     {
      "id": "Pro/MiniMaxAI/MiniMax-M2.5",
      "displayName": "Pro/MiniMaxAI/MiniMax-M2.5"
     }
    ],
    "vendorDisplayName": "SiliconFlow",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://cloud.siliconflow.cn/i/YflgU2Ve",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "siliconflow-en",
    "displayName": "SiliconFlow en",
    "baseUrl": "https://api.siliconflow.com",
    "models": [
     {
      "id": "MiniMaxAI/MiniMax-M3",
      "displayName": "MiniMaxAI/MiniMax-M3"
     }
    ],
    "vendorDisplayName": "SiliconFlow en",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://cloud.siliconflow.cn/i/YflgU2Ve",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "atlascloud",
    "displayName": "AtlasCloud",
    "baseUrl": "https://api.atlascloud.ai",
    "models": [
     {
      "id": "zai-org/glm-5.1",
      "displayName": "zai-org/glm-5.1"
     }
    ],
    "vendorDisplayName": "AtlasCloud",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.atlascloud.ai/console/coding-plan",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "gemini-native",
    "displayName": "Gemini Native",
    "baseUrl": "https://generativelanguage.googleapis.com",
    "models": [
     {
      "id": "gemini-3.6-flash",
      "displayName": "gemini-3.6-flash"
     }
    ],
    "vendorDisplayName": "Gemini Native",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aistudio.google.com/app/apikey",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "deepseek",
    "displayName": "DeepSeek",
    "baseUrl": "https://api.deepseek.com/anthropic",
    "models": [
     {
      "id": "deepseek-v4-pro",
      "displayName": "deepseek-v4-pro"
     }
    ],
    "vendorDisplayName": "DeepSeek",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.deepseek.com",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "opencode-go",
    "displayName": "OpenCode Go",
    "baseUrl": "https://opencode.ai/zen/go",
    "models": [
     {
      "id": "deepseek-v4-flash",
      "displayName": "deepseek-v4-flash"
     }
    ],
    "vendorDisplayName": "OpenCode Go",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://opencode.ai/go?ref=2YTRG2NGTX",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "tencent-token-plan",
    "displayName": "Tencent Token Plan",
    "baseUrl": "https://api.lkeap.cloud.tencent.com/plan/anthropic",
    "models": [
     {
      "id": "tc-code-latest",
      "displayName": "tc-code-latest"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "tencent-token-plan-intl",
    "displayName": "Tencent Token Plan (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/anthropic",
    "models": [
     {
      "id": "auto",
      "displayName": "auto"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "tencent-token-plan-enterprise-pro",
    "displayName": "Tencent Token Plan Enterprise Pro",
    "baseUrl": "https://tokenhub.tencentmaas.com/plan/anthropic",
    "models": [
     {
      "id": "auto",
      "displayName": "auto"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Pro",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan-e",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "tencent-token-plan-enterprise-pro-intl",
    "displayName": "Tencent Token Plan Enterprise Pro (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/anthropic",
    "models": [
     {
      "id": "auto",
      "displayName": "auto"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Pro (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan-e",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "tencent-token-plan-enterprise-lite",
    "displayName": "Tencent Token Plan Enterprise Lite",
    "baseUrl": "https://tokenhub.tencentmaas.com/plan/anthropic",
    "models": [
     {
      "id": "auto",
      "displayName": "auto"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Lite",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan-e",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "tencent-token-plan-enterprise-lite-intl",
    "displayName": "Tencent Token Plan Enterprise Lite (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/anthropic",
    "models": [
     {
      "id": "auto",
      "displayName": "auto"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Lite (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan-e",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "zhipu-glm",
    "displayName": "Zhipu GLM",
    "baseUrl": "https://open.bigmodel.cn/api/anthropic",
    "models": [
     {
      "id": "glm-5.1",
      "displayName": "glm-5.1"
     }
    ],
    "vendorDisplayName": "Zhipu GLM",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.bigmodel.cn/claude-code?ic=RRVJPB5SII",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "zhipu-glm-en",
    "displayName": "Zhipu GLM en",
    "baseUrl": "https://api.z.ai/api/anthropic",
    "models": [
     {
      "id": "glm-5.1",
      "displayName": "glm-5.1"
     }
    ],
    "vendorDisplayName": "Zhipu GLM en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://z.ai/subscribe?ic=8JVLJQFSKB",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "baidu-qianfan-coding-plan",
    "displayName": "Baidu Qianfan Coding Plan",
    "baseUrl": "https://qianfan.baidubce.com/anthropic/coding",
    "models": [
     {
      "id": "qianfan-code-latest",
      "displayName": "qianfan-code-latest"
     }
    ],
    "vendorDisplayName": "Baidu Qianfan Coding Plan",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "baidu-qianfan-token-plan",
    "displayName": "Baidu Qianfan Token Plan",
    "baseUrl": "https://qianfan.baidubce.com/anthropic/tokenplan/personal",
    "models": [
     {
      "id": "deepseek-v4-pro",
      "displayName": "deepseek-v4-pro"
     }
    ],
    "vendorDisplayName": "Baidu Qianfan Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.bce.baidu.com/qianfan/resource/token-plan",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "千问ai平台",
    "displayName": "千问AI平台",
    "baseUrl": "https://dashscope.aliyuncs.com/apps/anthropic",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "qwen3.8-max"
     }
    ],
    "vendorDisplayName": "千问AI平台",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.qianwenai.com/home/api-keys?utm_content=g_20000002972",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "千问ai平台-token-plan",
    "displayName": "千问AI平台 Token Plan",
    "baseUrl": "https://token-plan.cn-beijing.maas.aliyuncs.com/apps/anthropic",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "qwen3.8-max"
     }
    ],
    "vendorDisplayName": "千问AI平台 Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://platform.qianwenai.com/home/api-keys?utm_content=g_20000002978",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "qwencloud",
    "displayName": "QwenCloud",
    "baseUrl": "https://dashscope-intl.aliyuncs.com/apps/anthropic",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "qwen3.8-max"
     }
    ],
    "vendorDisplayName": "QwenCloud",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys?utm_content=g_20000002975",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "qwencloud-for-coding",
    "displayName": "QwenCloud For Coding",
    "baseUrl": "https://coding-intl.dashscope.aliyuncs.com/apps/anthropic",
    "models": [
     {
      "id": "qwen3.7-plus",
      "displayName": "qwen3.7-plus"
     }
    ],
    "vendorDisplayName": "QwenCloud For Coding",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "qwencloud-token-plan",
    "displayName": "QwenCloud Token Plan",
    "baseUrl": "https://token-plan.ap-southeast-1.maas.aliyuncs.com/apps/anthropic",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "qwen3.8-max"
     }
    ],
    "vendorDisplayName": "QwenCloud Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys?utm_content=g_20000002981",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "stepfun",
    "displayName": "StepFun",
    "baseUrl": "https://api.stepfun.com/step_plan",
    "models": [
     {
      "id": "step-3.5-flash-2603",
      "displayName": "step-3.5-flash-2603"
     }
    ],
    "vendorDisplayName": "StepFun",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.stepfun.com/interface-key",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "stepfun-en",
    "displayName": "StepFun en",
    "baseUrl": "https://api.stepfun.ai/step_plan",
    "models": [
     {
      "id": "step-3.5-flash-2603",
      "displayName": "step-3.5-flash-2603"
     }
    ],
    "vendorDisplayName": "StepFun en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.stepfun.ai/interface-key",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "modelscope",
    "displayName": "ModelScope",
    "baseUrl": "https://api-inference.modelscope.cn",
    "models": [
     {
      "id": "ZhipuAI/GLM-5.2",
      "displayName": "ZhipuAI/GLM-5.2"
     }
    ],
    "vendorDisplayName": "ModelScope",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://modelscope.cn",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "longcat",
    "displayName": "Longcat",
    "baseUrl": "https://api.longcat.chat/anthropic",
    "models": [
     {
      "id": "LongCat-2.0",
      "displayName": "LongCat-2.0"
     }
    ],
    "vendorDisplayName": "Longcat",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://longcat.chat/platform/api_keys",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "minimax",
    "displayName": "MiniMax",
    "baseUrl": "https://api.minimaxi.com/anthropic",
    "models": [
     {
      "id": "MiniMax-M3[1M]",
      "displayName": "MiniMax-M3[1M]"
     }
    ],
    "vendorDisplayName": "MiniMax",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.minimaxi.com/subscribe/coding-plan",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "minimax-en",
    "displayName": "MiniMax en",
    "baseUrl": "https://api.minimax.io/anthropic",
    "models": [
     {
      "id": "MiniMax-M3[1M]",
      "displayName": "MiniMax-M3[1M]"
     }
    ],
    "vendorDisplayName": "MiniMax en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.minimax.io/subscribe/coding-plan",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "bailing",
    "displayName": "BaiLing",
    "baseUrl": "https://api.tbox.cn/api/anthropic",
    "models": [
     {
      "id": "Ling-2.5-1T",
      "displayName": "Ling-2.5-1T"
     }
    ],
    "vendorDisplayName": "BaiLing",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://alipaytbox.yuque.com/sxs0ba/ling/get_started",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "cherryin",
    "displayName": "CherryIN",
    "baseUrl": "https://open.cherryin.net",
    "models": [
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "anthropic/claude-sonnet-5"
     }
    ],
    "vendorDisplayName": "CherryIN",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://open.cherryin.ai/console/token",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "openrouter",
    "displayName": "OpenRouter",
    "baseUrl": "https://openrouter.ai/api",
    "models": [
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "anthropic/claude-sonnet-5"
     }
    ],
    "vendorDisplayName": "OpenRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://openrouter.ai/keys",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "therouter",
    "displayName": "TheRouter",
    "baseUrl": "https://api.therouter.ai",
    "models": [
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "anthropic/claude-sonnet-5"
     }
    ],
    "vendorDisplayName": "TheRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://dashboard.therouter.ai",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "novita-ai",
    "displayName": "Novita AI",
    "baseUrl": "https://api.novita.ai/anthropic",
    "models": [
     {
      "id": "zai-org/glm-5.1",
      "displayName": "zai-org/glm-5.1"
     }
    ],
    "vendorDisplayName": "Novita AI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://novita.ai",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "github-copilot",
    "displayName": "GitHub Copilot",
    "baseUrl": "https://api.githubcopilot.com",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "claude-sonnet-5"
     }
    ],
    "vendorDisplayName": "GitHub Copilot",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://github.com/features/copilot",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "xai-grok",
    "displayName": "xAI (Grok)",
    "baseUrl": "https://api.x.ai/v1",
    "models": [
     {
      "id": "grok-4.5",
      "displayName": "grok-4.5"
     }
    ],
    "vendorDisplayName": "xAI (Grok)",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://x.ai/grok",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "nvidia",
    "displayName": "Nvidia",
    "baseUrl": "https://integrate.api.nvidia.com",
    "models": [
     {
      "id": "moonshotai/kimi-k2.5",
      "displayName": "moonshotai/kimi-k2.5"
     }
    ],
    "vendorDisplayName": "Nvidia",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://build.nvidia.com/settings/api-keys",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "pipellm",
    "displayName": "PIPELLM",
    "baseUrl": "https://cc-api.pipellm.ai",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "claude-opus-5"
     }
    ],
    "vendorDisplayName": "PIPELLM",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://code.pipellm.ai/login?ref=uvw650za",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "xiaomi-mimo",
    "displayName": "Xiaomi MiMo",
    "baseUrl": "https://api.xiaomimimo.com/anthropic",
    "models": [
     {
      "id": "mimo-v2.5-pro",
      "displayName": "mimo-v2.5-pro"
     }
    ],
    "vendorDisplayName": "Xiaomi MiMo",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.xiaomimimo.com/#/console/api-keys",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "xiaomi-mimo-token-plan-china",
    "displayName": "Xiaomi MiMo Token Plan (China)",
    "baseUrl": "https://token-plan-cn.xiaomimimo.com/anthropic",
    "models": [
     {
      "id": "mimo-v2.5-pro",
      "displayName": "mimo-v2.5-pro"
     }
    ],
    "vendorDisplayName": "Xiaomi MiMo Token Plan (China)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://platform.xiaomimimo.com/#/console/plan-manage",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "jiekou-ai",
    "displayName": "JieKou AI",
    "baseUrl": "https://api.jiekou.ai/anthropic",
    "models": [
     {
      "id": "claude-fable-5",
      "displayName": "claude-fable-5"
     }
    ],
    "vendorDisplayName": "JieKou AI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://jiekou.ai/settings/key-management",
    "apiFormat": "anthropic-messages"
   }
  ],
  "codex": [
   {
    "id": "kimi",
    "displayName": "Kimi",
    "baseUrl": "https://api.moonshot.cn/v1",
    "models": [
     {
      "id": "kimi-k3",
      "displayName": "Kimi K3",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "low",
        "high",
        "max"
       ]
      }
     },
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code",
      "capability": {
       "contextWindow": 262144,
       "reasoningLevels": [
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "Kimi",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.kimi.com/console/api-keys?aff=cc-switch",
    "apiFormat": "codex-responses"
   },
   {
    "id": "kimi-for-coding",
    "displayName": "Kimi For Coding",
    "baseUrl": "https://api.kimi.com/coding/v1",
    "models": [
     {
      "id": "kimi-for-coding",
      "displayName": "Kimi For Coding",
      "capability": {
       "contextWindow": 262144,
       "reasoningLevels": [
        "high"
       ]
      }
     },
     {
      "id": "kimi-for-coding-highspeed",
      "displayName": "Kimi For Coding HighSpeed",
      "capability": {
       "contextWindow": 262144,
       "reasoningLevels": [
        "high"
       ]
      }
     },
     {
      "id": "k3",
      "displayName": "Kimi K3",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "low",
        "high",
        "max"
       ]
      }
     },
     {
      "id": "k3-256k",
      "displayName": "Kimi K3 256K",
      "capability": {
       "contextWindow": 262144,
       "reasoningLevels": [
        "low",
        "high",
        "max"
       ]
      }
     }
    ],
    "vendorDisplayName": "Kimi For Coding",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://www.kimi.com/code/?aff=cc-switch",
    "apiFormat": "codex-responses"
   },
   {
    "id": "ppio",
    "displayName": "PPIO",
    "baseUrl": "https://api.ppio.com/openai/v1",
    "models": [
     {
      "id": "deepseek/deepseek-v4-flash-0731",
      "displayName": "Deepseek V4 Flash 0731",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576
      }
     }
    ],
    "vendorDisplayName": "PPIO",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://ppio.com/activity/ccswitch",
    "apiFormat": "codex-responses"
   },
   {
    "id": "火山-agent-plan",
    "displayName": "火山 Agent Plan",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/plan/v3",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "Ark Code Latest",
      "capability": {
       "contextWindow": 256000,
       "reasoningLevels": [
        "low",
        "medium",
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "火山 Agent Plan",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.volcengine.com/activity/agentplan?ac=MMAP8JTTCAQ2&rc=6J6FV5N2&utm_source=OWO&utm_medium=devrel-1&utm_campaign=hw&utm_term=ccswitch&utm_content=hw",
    "apiFormat": "codex-responses"
   },
   {
    "id": "火山-coding-plan",
    "displayName": "火山 Coding Plan",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/coding/v3",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "Ark Code Latest",
      "capability": {
       "contextWindow": 256000,
       "reasoningLevels": [
        "low",
        "medium",
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "火山 Coding Plan",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://www.volcengine.com/activity/codingplan?ac=MMAP8JTTCAQ2&rc=6J6FV5N2&utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": "codex-responses"
   },
   {
    "id": "byteplus",
    "displayName": "BytePlus",
    "baseUrl": "https://ark.ap-southeast.bytepluses.com/api/coding/v3",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "Ark Code Latest",
      "capability": {
       "contextWindow": 256000,
       "reasoningLevels": [
        "low",
        "medium",
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "BytePlus",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.byteplus.com/en/product/modelark?utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": "codex-responses"
   },
   {
    "id": "volcengine-doubao",
    "displayName": "Volcengine Doubao",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/v3",
    "models": [
     {
      "id": "doubao-seed-2-1-pro-260628",
      "displayName": "Doubao Seed 2.1 Pro",
      "capability": {
       "contextWindow": 262144,
       "reasoningLevels": [
        "minimal",
        "low",
        "medium",
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "Volcengine Doubao",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey?apikey=%7B%7D&utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": "codex-responses"
   },
   {
    "id": "siliconflow",
    "displayName": "SiliconFlow",
    "baseUrl": "https://api.siliconflow.cn/v1",
    "models": [
     {
      "id": "deepseek-ai/DeepSeek-V4-Flash",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "high",
        "max"
       ]
      }
     }
    ],
    "vendorDisplayName": "SiliconFlow",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://cloud.siliconflow.cn/i/YflgU2Ve",
    "apiFormat": "codex-responses"
   },
   {
    "id": "siliconflow-en",
    "displayName": "SiliconFlow en",
    "baseUrl": "https://api.siliconflow.com/v1",
    "models": [
     {
      "id": "MiniMaxAI/MiniMax-M3",
      "displayName": "MiniMax M3",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "SiliconFlow en",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://cloud.siliconflow.cn/i/YflgU2Ve",
    "apiFormat": "codex-responses"
   },
   {
    "id": "atlascloud",
    "displayName": "AtlasCloud",
    "baseUrl": "https://api.atlascloud.ai/v1",
    "models": [
     {
      "id": "zai-org/glm-5.2",
      "displayName": "GLM 5.2",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "AtlasCloud",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.atlascloud.ai/console/coding-plan",
    "apiFormat": "codex-responses"
   },
   {
    "id": "deepseek",
    "displayName": "DeepSeek",
    "baseUrl": "https://api.deepseek.com",
    "models": [
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "low",
        "high",
        "max"
       ]
      }
     },
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "low",
        "high",
        "max"
       ]
      }
     }
    ],
    "vendorDisplayName": "DeepSeek",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.deepseek.com/api_keys",
    "apiFormat": "codex-responses"
   },
   {
    "id": "zhipu-glm",
    "displayName": "Zhipu GLM",
    "baseUrl": "https://open.bigmodel.cn/api/v1",
    "models": [
     {
      "id": "glm-5.3",
      "displayName": "GLM-5.3",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "low",
        "high",
        "max"
       ]
      }
     },
     {
      "id": "glm-5-turbo",
      "displayName": "GLM-5-Turbo",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 204800,
       "reasoningLevels": [
        "max"
       ]
      }
     }
    ],
    "vendorDisplayName": "Zhipu GLM",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.bigmodel.cn/claude-code?ic=RRVJPB5SII",
    "apiFormat": "codex-responses"
   },
   {
    "id": "zhipu-glm-en",
    "displayName": "Zhipu GLM en",
    "baseUrl": "https://api.z.ai/api/v1",
    "models": [
     {
      "id": "glm-5.3",
      "displayName": "GLM-5.3",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "low",
        "high",
        "max"
       ]
      }
     }
    ],
    "vendorDisplayName": "Zhipu GLM en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://z.ai/subscribe?ic=8JVLJQFSKB",
    "apiFormat": "codex-responses"
   },
   {
    "id": "baidu-qianfan-coding-plan",
    "displayName": "Baidu Qianfan Coding Plan",
    "baseUrl": "https://qianfan.baidubce.com/v2/coding",
    "models": [
     {
      "id": "qianfan-code-latest",
      "displayName": "Qianfan Code Latest",
      "capability": {
       "contextWindow": 131072,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "Baidu Qianfan Coding Plan",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application",
    "apiFormat": "codex-responses"
   },
   {
    "id": "baidu-qianfan-token-plan",
    "displayName": "Baidu Qianfan Token Plan",
    "baseUrl": "https://qianfan.baidubce.com/v2/tokenplan/personal",
    "models": [
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high",
        "max"
       ]
      }
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high",
        "max"
       ]
      }
     },
     {
      "id": "deepseek-v4-flash-0731",
      "displayName": "DeepSeek V4 Flash 0731",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576
      }
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "contextWindow": 1048576
      }
     },
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1",
      "capability": {
       "contextWindow": 198000,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "kimi-k2.6",
      "displayName": "Kimi K2.6",
      "capability": {
       "contextWindow": 262144
      }
     }
    ],
    "vendorDisplayName": "Baidu Qianfan Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.bce.baidu.com/qianfan/resource/token-plan",
    "apiFormat": "codex-responses"
   },
   {
    "id": "千问ai平台",
    "displayName": "千问AI平台",
    "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max",
      "capability": {
       "contextWindow": 983616,
       "reasoningLevels": [
        "low",
        "medium",
        "xhigh"
       ]
      }
     }
    ],
    "vendorDisplayName": "千问AI平台",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.qianwenai.com/home/api-keys?utm_content=g_20000002972",
    "apiFormat": "codex-responses"
   },
   {
    "id": "千问ai平台-token-plan",
    "displayName": "千问AI平台 Token Plan",
    "baseUrl": "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max",
      "capability": {
       "contextWindow": 983616,
       "reasoningLevels": [
        "low",
        "medium",
        "xhigh"
       ]
      }
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash",
      "capability": {
       "contextWindow": 983616,
       "reasoningLevels": [
        "low",
        "medium",
        "xhigh"
       ]
      }
     }
    ],
    "vendorDisplayName": "千问AI平台 Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://platform.qianwenai.com/home/api-keys?utm_content=g_20000002978",
    "apiFormat": "codex-responses"
   },
   {
    "id": "qwencloud",
    "displayName": "QwenCloud",
    "baseUrl": "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max",
      "capability": {
       "contextWindow": 983616,
       "reasoningLevels": [
        "low",
        "medium",
        "xhigh"
       ]
      }
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash",
      "capability": {
       "contextWindow": 983616,
       "reasoningLevels": [
        "low",
        "medium",
        "xhigh"
       ]
      }
     },
     {
      "id": "qwen3.7-max",
      "displayName": "Qwen3.7 Max",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "QwenCloud",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys?utm_content=g_20000002975",
    "apiFormat": "codex-responses"
   },
   {
    "id": "qwencloud-for-coding",
    "displayName": "QwenCloud For Coding",
    "baseUrl": "https://coding-intl.dashscope.aliyuncs.com/v1",
    "models": [
     {
      "id": "qwen3.7-plus",
      "displayName": "Qwen3.7 Plus",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "qwen3.6-plus",
      "displayName": "Qwen3.6 Plus",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "qwen3-coder-plus",
      "displayName": "Qwen3 Coder Plus",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 131072
      }
     }
    ],
    "vendorDisplayName": "QwenCloud For Coding",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys",
    "apiFormat": "codex-responses"
   },
   {
    "id": "qwencloud-token-plan",
    "displayName": "QwenCloud Token Plan",
    "baseUrl": "https://token-plan.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max",
      "capability": {
       "contextWindow": 983616,
       "reasoningLevels": [
        "low",
        "medium",
        "xhigh"
       ]
      }
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash",
      "capability": {
       "contextWindow": 983616,
       "reasoningLevels": [
        "low",
        "medium",
        "xhigh"
       ]
      }
     },
     {
      "id": "qwen3.7-max",
      "displayName": "Qwen3.7 Max",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "QwenCloud Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys?utm_content=g_20000002981",
    "apiFormat": "codex-responses"
   },
   {
    "id": "tencent-hunyuan",
    "displayName": "Tencent Hunyuan",
    "baseUrl": "https://tokenhub.tencentmaas.com/v1",
    "models": [
     {
      "id": "hy3",
      "displayName": "Hy3",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 256000,
       "reasoningLevels": [
        "low",
        "high"
       ]
      }
     },
     {
      "id": "hy3-preview",
      "displayName": "Hy3 Preview",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 256000,
       "reasoningLevels": [
        "low",
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "Tencent Hunyuan",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/apikey",
    "apiFormat": "codex-responses"
   },
   {
    "id": "tencent-token-plan",
    "displayName": "Tencent Token Plan",
    "baseUrl": "https://api.lkeap.cloud.tencent.com/plan/v3",
    "models": [
     {
      "id": "tc-code-latest",
      "displayName": "Auto",
      "capability": {
       "contextWindow": 196608,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "minimax-m2.7",
      "displayName": "MiniMax M2.7",
      "capability": {
       "contextWindow": 200000,
       "reasoningLevels": [
        "high"
       ]
      }
     },
     {
      "id": "glm-5",
      "displayName": "GLM-5",
      "capability": {
       "contextWindow": 200000,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1",
      "capability": {
       "contextWindow": 200000,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "hy3",
      "displayName": "Hy3",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 256000,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "hy3-preview",
      "displayName": "Hy3 Preview",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 256000,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan",
    "apiFormat": "codex-responses"
   },
   {
    "id": "tencent-token-plan-intl",
    "displayName": "Tencent Token Plan (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto",
      "capability": {
       "contextWindow": 196608,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "kimi-k2.6",
      "displayName": "Kimi K2.6",
      "capability": {
       "contextWindow": 262144,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax M3",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan",
    "apiFormat": "codex-responses"
   },
   {
    "id": "tencent-token-plan-enterprise-pro",
    "displayName": "Tencent Token Plan Enterprise Pro",
    "baseUrl": "https://tokenhub.tencentmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto",
      "capability": {
       "contextWindow": 196608,
       "reasoningLevels": [
        "high"
       ]
      }
     },
     {
      "id": "glm-5.3",
      "displayName": "GLM-5.3",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "low",
        "high",
        "max"
       ]
      }
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "glm-5",
      "displayName": "GLM-5",
      "capability": {
       "contextWindow": 200000,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1",
      "capability": {
       "contextWindow": 200000,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "glm-5-turbo",
      "displayName": "GLM-5 Turbo",
      "capability": {
       "contextWindow": 200000,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code",
      "capability": {
       "contextWindow": 262144,
       "reasoningLevels": [
        "high"
       ]
      }
     },
     {
      "id": "kimi-k2.7-code-highspeed",
      "displayName": "Kimi K2.7 Code HighSpeed",
      "capability": {
       "contextWindow": 262144,
       "reasoningLevels": [
        "high"
       ]
      }
     },
     {
      "id": "kimi-k2.6",
      "displayName": "Kimi K2.6",
      "capability": {
       "contextWindow": 262144,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "minimax-m2.7",
      "displayName": "MiniMax M2.7",
      "capability": {
       "contextWindow": 200000,
       "reasoningLevels": [
        "high"
       ]
      }
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax M3",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "deepseek-v4-flash-0731",
      "displayName": "DeepSeek V4 Flash 0731 GA",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "deepseek-v4-pro-0813",
      "displayName": "DeepSeek V4 Pro 0813 GA",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash Official",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro Official",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Pro",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan-e",
    "apiFormat": "codex-responses"
   },
   {
    "id": "tencent-token-plan-enterprise-pro-intl",
    "displayName": "Tencent Token Plan Enterprise Pro (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto",
      "capability": {
       "contextWindow": 196608,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "glm-5.3",
      "displayName": "GLM-5.3",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "low",
        "high",
        "max"
       ]
      }
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax M3",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code",
      "capability": {
       "contextWindow": 262144,
       "reasoningLevels": [
        "high"
       ]
      }
     },
     {
      "id": "kimi-k2.7-code-highspeed",
      "displayName": "Kimi K2.7 Code HighSpeed",
      "capability": {
       "contextWindow": 262144,
       "reasoningLevels": [
        "high"
       ]
      }
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "deepseek-v4-flash-0731",
      "displayName": "DeepSeek V4 Flash 0731 GA",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "deepseek-v4-pro-0813",
      "displayName": "DeepSeek V4 Pro 0813 GA",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash Official",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro Official",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Pro (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan-e",
    "apiFormat": "codex-responses"
   },
   {
    "id": "tencent-token-plan-enterprise-lite",
    "displayName": "Tencent Token Plan Enterprise Lite",
    "baseUrl": "https://tokenhub.tencentmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto",
      "capability": {
       "contextWindow": 196608,
       "reasoningLevels": [
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Lite",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan-e",
    "apiFormat": "codex-responses"
   },
   {
    "id": "tencent-token-plan-enterprise-lite-intl",
    "displayName": "Tencent Token Plan Enterprise Lite (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto",
      "capability": {
       "contextWindow": 196608,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Lite (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan-e",
    "apiFormat": "codex-responses"
   },
   {
    "id": "stepfun",
    "displayName": "StepFun",
    "baseUrl": "https://api.stepfun.com/step_plan/v1",
    "models": [
     {
      "id": "step-3.7-flash",
      "displayName": "Step 3.7 Flash",
      "capability": {
       "contextWindow": 262144,
       "reasoningLevels": [
        "low",
        "medium",
        "high"
       ]
      }
     },
     {
      "id": "step-3.5-flash-2603",
      "displayName": "Step 3.5 Flash 2603",
      "capability": {
       "contextWindow": 262144,
       "reasoningLevels": [
        "low",
        "high"
       ]
      }
     },
     {
      "id": "step-3.5-flash",
      "displayName": "Step 3.5 Flash",
      "capability": {
       "contextWindow": 262144
      }
     }
    ],
    "vendorDisplayName": "StepFun",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.stepfun.com/interface-key",
    "apiFormat": "codex-responses"
   },
   {
    "id": "stepfun-en",
    "displayName": "StepFun en",
    "baseUrl": "https://api.stepfun.ai/step_plan/v1",
    "models": [
     {
      "id": "step-3.7-flash",
      "displayName": "Step 3.7 Flash",
      "capability": {
       "contextWindow": 262144,
       "reasoningLevels": [
        "low",
        "medium",
        "high"
       ]
      }
     },
     {
      "id": "step-3.5-flash-2603",
      "displayName": "Step 3.5 Flash 2603",
      "capability": {
       "contextWindow": 262144,
       "reasoningLevels": [
        "low",
        "high"
       ]
      }
     },
     {
      "id": "step-3.5-flash",
      "displayName": "Step 3.5 Flash",
      "capability": {
       "contextWindow": 262144
      }
     }
    ],
    "vendorDisplayName": "StepFun en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.stepfun.ai/interface-key",
    "apiFormat": "codex-responses"
   },
   {
    "id": "modelscope",
    "displayName": "ModelScope",
    "baseUrl": "https://api-inference.modelscope.cn/v1",
    "models": [
     {
      "id": "ZhipuAI/GLM-5.2",
      "displayName": "ZhipuAI / GLM-5.2",
      "capability": {
       "contextWindow": 200000
      }
     }
    ],
    "vendorDisplayName": "ModelScope",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://modelscope.cn/my/myaccesstoken",
    "apiFormat": "codex-responses"
   },
   {
    "id": "longcat",
    "displayName": "Longcat",
    "baseUrl": "https://api.longcat.chat/openai/v1",
    "models": [
     {
      "id": "LongCat-2.0",
      "displayName": "LongCat 2.0",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "Longcat",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://longcat.chat/platform/api_keys",
    "apiFormat": "codex-responses"
   },
   {
    "id": "minimax",
    "displayName": "MiniMax",
    "baseUrl": "https://api.minimaxi.com/v1",
    "models": [
     {
      "id": "MiniMax-M3",
      "displayName": "MiniMax-M3",
      "capability": {
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "MiniMax",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.minimaxi.com/subscribe/coding-plan",
    "apiFormat": "codex-responses"
   },
   {
    "id": "minimax-en",
    "displayName": "MiniMax en",
    "baseUrl": "https://api.minimax.io/v1",
    "models": [
     {
      "id": "MiniMax-M3",
      "displayName": "MiniMax-M3",
      "capability": {
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "MiniMax en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.minimax.io/subscribe/coding-plan",
    "apiFormat": "codex-responses"
   },
   {
    "id": "bailing",
    "displayName": "BaiLing",
    "baseUrl": "https://api.tbox.cn/api/llm/v1",
    "models": [
     {
      "id": "Ling-2.6-1T",
      "displayName": "Ling-2.6-1T",
      "capability": {
       "contextWindow": 262144
      }
     }
    ],
    "vendorDisplayName": "BaiLing",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://ling.tbox.cn/open",
    "apiFormat": "codex-responses"
   },
   {
    "id": "xiaomi-mimo",
    "displayName": "Xiaomi MiMo",
    "baseUrl": "https://api.xiaomimimo.com/v1",
    "models": [
     {
      "id": "mimo-v2.5-pro",
      "displayName": "MiMo V2.5 Pro",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "mimo-v2.5",
      "displayName": "MiMo V2.5",
      "capability": {
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "Xiaomi MiMo",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.xiaomimimo.com/#/console/api-keys",
    "apiFormat": "codex-responses"
   },
   {
    "id": "xiaomi-mimo-token-plan-china",
    "displayName": "Xiaomi MiMo Token Plan (China)",
    "baseUrl": "https://token-plan-cn.xiaomimimo.com/v1",
    "models": [
     {
      "id": "mimo-v2.5-pro",
      "displayName": "MiMo V2.5 Pro",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     },
     {
      "id": "mimo-v2.5",
      "displayName": "MiMo V2.5",
      "capability": {
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "none",
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "Xiaomi MiMo Token Plan (China)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://platform.xiaomimimo.com/#/console/plan-manage",
    "apiFormat": "codex-responses"
   },
   {
    "id": "novita-ai",
    "displayName": "Novita AI",
    "baseUrl": "https://api.novita.ai/openai/v1",
    "models": [
     {
      "id": "zai-org/glm-5.3",
      "displayName": "GLM-5.3",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "high"
       ]
      }
     }
    ],
    "vendorDisplayName": "Novita AI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://novita.ai",
    "apiFormat": "codex-responses"
   },
   {
    "id": "xai-grok",
    "displayName": "xAI (Grok)",
    "baseUrl": "https://api.x.ai/v1",
    "models": [
     {
      "id": "grok-4.5",
      "displayName": "Grok 4.5",
      "capability": {
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 500000,
       "reasoningLevels": [
        "low",
        "medium",
        "high",
        "xhigh"
       ]
      }
     }
    ],
    "vendorDisplayName": "xAI (Grok)",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://console.x.ai",
    "apiFormat": "codex-responses"
   },
   {
    "id": "xai-grok-oauth",
    "displayName": "xAI (Grok) OAuth",
    "baseUrl": "https://api.x.ai/v1",
    "models": [
     {
      "id": "grok-4.5",
      "displayName": "Grok 4.5",
      "capability": {
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 500000,
       "reasoningLevels": [
        "low",
        "medium",
        "high",
        "xhigh"
       ]
      }
     }
    ],
    "vendorDisplayName": "xAI (Grok) OAuth",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://x.ai/grok",
    "apiFormat": "codex-responses"
   },
   {
    "id": "nvidia",
    "displayName": "Nvidia",
    "baseUrl": "https://integrate.api.nvidia.com/v1",
    "models": [
     {
      "id": "moonshotai/kimi-k3",
      "displayName": "Kimi K3",
      "capability": {
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "low",
        "high",
        "max"
       ]
      }
     }
    ],
    "vendorDisplayName": "Nvidia",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://build.nvidia.com/settings/api-keys",
    "apiFormat": "codex-responses"
   },
   {
    "id": "opencode-go",
    "displayName": "OpenCode Go",
    "baseUrl": "https://opencode.ai/zen/go/v1",
    "models": [
     {
      "id": "glm-5.3",
      "displayName": "GLM 5.3",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "reasoningLevels": [
        "low",
        "high",
        "max"
       ]
      }
     },
     {
      "id": "glm-5.3-flash",
      "displayName": "GLM 5.3 Flash",
      "capability": {
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "reasoningLevels": [
        "low",
        "high",
        "max"
       ]
      }
     },
     {
      "id": "kimi-k3",
      "displayName": "Kimi K3",
      "capability": {
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1048576,
       "reasoningLevels": [
        "max"
       ]
      }
     },
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "high",
        "max"
       ]
      }
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "contextWindow": 1048576,
       "reasoningLevels": [
        "low",
        "high",
        "max"
       ]
      }
     },
     {
      "id": "mimo-v2.5-pro",
      "displayName": "MiMo V2.5 Pro",
      "capability": {
       "contextWindow": 1048576
      }
     }
    ],
    "vendorDisplayName": "OpenCode Go",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://opencode.ai/go?ref=2YTRG2NGTX",
    "apiFormat": "codex-responses"
   },
   {
    "id": "jiekou-ai",
    "displayName": "JieKou AI",
    "baseUrl": "https://api.jiekou.ai/openai/v1",
    "models": [
     {
      "id": "claude-fable-5",
      "displayName": "Claude Fable 5",
      "capability": {
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "JieKou AI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://jiekou.ai/settings/key-management",
    "apiFormat": "codex-responses"
   }
  ],
  "gemini-cli": [],
  "opencode": [
   {
    "id": "kimi",
    "displayName": "Kimi",
    "baseUrl": "https://api.moonshot.cn/v1",
    "models": [
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code"
     },
     {
      "id": "kimi-k3",
      "displayName": "Kimi K3"
     }
    ],
    "vendorDisplayName": "Kimi",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.kimi.com/console/api-keys?aff=cc-switch",
    "apiFormat": null
   },
   {
    "id": "kimi-for-coding",
    "displayName": "Kimi For Coding",
    "baseUrl": "https://api.kimi.com/coding/v1",
    "models": [
     {
      "id": "kimi-for-coding",
      "displayName": "Kimi For Coding"
     }
    ],
    "vendorDisplayName": "Kimi For Coding",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://platform.kimi.com/console/api-keys?aff=cc-switch",
    "apiFormat": null
   },
   {
    "id": "packycode",
    "displayName": "PackyCode",
    "baseUrl": "https://www.packyapi.ai/v1",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     }
    ],
    "vendorDisplayName": "PackyCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.packyapi.ai/register?aff=cc-switch",
    "apiFormat": null
   },
   {
    "id": "zetaapi",
    "displayName": "ZetaAPI",
    "baseUrl": "https://api.zetaapi.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "ZetaAPI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://zetaapi.ai/go/u117",
    "apiFormat": null
   },
   {
    "id": "apinebula",
    "displayName": "APINebula",
    "baseUrl": "https://apinebula.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "APINebula",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://apinebula.ai/VjM74M",
    "apiFormat": null
   },
   {
    "id": "aicodemirror",
    "displayName": "AICodeMirror",
    "baseUrl": "https://api.aicodemirror.ai/api/claudecode",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     }
    ],
    "vendorDisplayName": "AICodeMirror",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.aicodemirror.ai/register?invitecode=9915W3",
    "apiFormat": null
   },
   {
    "id": "fennoai",
    "displayName": "FennoAI",
    "baseUrl": "https://api.fenno.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "FennoAI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://api.fenno.ai/register?redirect=/purchase?tab=subscription%26group=16&aff=P9MR3D3PLCNL",
    "apiFormat": null
   },
   {
    "id": "runapi",
    "displayName": "RunAPI",
    "baseUrl": "https://runapi.host",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "RunAPI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://runapi.host/register?aff=iOKB",
    "apiFormat": null
   },
   {
    "id": "shengsuanyun",
    "displayName": "Shengsuanyun",
    "baseUrl": "https://router.shengsuanyun.com/api/v1",
    "models": [
     {
      "id": "anthropic/claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     }
    ],
    "vendorDisplayName": "Shengsuanyun",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.shengsuanyun.com/?from=CH_4HHXMRYF",
    "apiFormat": null
   },
   {
    "id": "aigocode",
    "displayName": "AIGoCode",
    "baseUrl": "https://api.aigocode.app",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     }
    ],
    "vendorDisplayName": "AIGoCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aigocode.app/invite/CC-SWITCH",
    "apiFormat": null
   },
   {
    "id": "qiniu",
    "displayName": "Qiniu",
    "baseUrl": "https://api.qnaigc.com/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "Qiniu",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://s.qiniu.com/nMvAvy",
    "apiFormat": null
   },
   {
    "id": "aicoding",
    "displayName": "AICoding",
    "baseUrl": "https://api.aicoding.inc",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     }
    ],
    "vendorDisplayName": "AICoding",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aicoding.inc/i/CCSWITCH",
    "apiFormat": null
   },
   {
    "id": "subrouter",
    "displayName": "SubRouter",
    "baseUrl": "https://subrouter.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "SubRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://subrouter.ai/register?aff=l3ri",
    "apiFormat": null
   },
   {
    "id": "apikey-fun",
    "displayName": "APIKEY.FUN",
    "baseUrl": "https://api.apikey.fan/v1",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "APIKEY.FUN",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://apikey.fan/register?aff=CCSwitch",
    "apiFormat": null
   },
   {
    "id": "9527code",
    "displayName": "9527CODE",
    "baseUrl": "https://9527.codes/v1",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "9527CODE",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://9527.codes/register?aff=e5zI",
    "apiFormat": null
   },
   {
    "id": "code0",
    "displayName": "Code0",
    "baseUrl": "https://code0.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "Code0",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://code0.ai/agent/register/B2XHxGjGmRvqgznY",
    "apiFormat": null
   },
   {
    "id": "teamorouter",
    "displayName": "TeamoRouter",
    "baseUrl": "https://api.teamorouter.cn/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "TeamoRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://teamorouter.cn/?utm_source=cc_switch&utm_medium=referral&utm_campaign=ai_directory",
    "apiFormat": null
   },
   {
    "id": "ppio",
    "displayName": "PPIO",
    "baseUrl": "https://api.ppio.com/openai/v1",
    "models": [
     {
      "id": "deepseek/deepseek-v4-flash-0731",
      "displayName": "Deepseek V4 Flash 0731"
     }
    ],
    "vendorDisplayName": "PPIO",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://ppio.com/activity/ccswitch",
    "apiFormat": null
   },
   {
    "id": "claudecn",
    "displayName": "ClaudeCN",
    "baseUrl": "https://claudecn.top",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "ClaudeCN",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://claudecn.ai/register?aff=HEL9",
    "apiFormat": null
   },
   {
    "id": "火山-agent-plan",
    "displayName": "火山 Agent Plan",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/plan/v3",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "Ark Code Latest"
     }
    ],
    "vendorDisplayName": "火山 Agent Plan",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.volcengine.com/activity/agentplan?ac=MMAP8JTTCAQ2&rc=6J6FV5N2&utm_source=OWO&utm_medium=devrel-1&utm_campaign=hw&utm_term=ccswitch&utm_content=hw",
    "apiFormat": null
   },
   {
    "id": "火山-coding-plan",
    "displayName": "火山 Coding Plan",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/coding/v3",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "Ark Code Latest"
     }
    ],
    "vendorDisplayName": "火山 Coding Plan",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://www.volcengine.com/activity/codingplan?ac=MMAP8JTTCAQ2&rc=6J6FV5N2&utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": null
   },
   {
    "id": "byteplus",
    "displayName": "BytePlus",
    "baseUrl": "https://ark.ap-southeast.bytepluses.com/api/coding/v3",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "Ark Code Latest"
     }
    ],
    "vendorDisplayName": "BytePlus",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.byteplus.com/en/product/modelark?utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": null
   },
   {
    "id": "volcengine-doubao",
    "displayName": "Volcengine Doubao",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/v3",
    "models": [
     {
      "id": "doubao-seed-2-1-pro-260628",
      "displayName": "Doubao Seed 2.1 Pro"
     }
    ],
    "vendorDisplayName": "Volcengine Doubao",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey?apikey=%7B%7D&utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": null
   },
   {
    "id": "a6api",
    "displayName": "A6API",
    "baseUrl": "https://api.a6api.com/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "A6API",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://a6api.com/register?aff=AqNr",
    "apiFormat": null
   },
   {
    "id": "ccsub",
    "displayName": "CCSub",
    "baseUrl": "https://www.ccsub.net/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "CCSub",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.ccsub.net/register?ref=Y6Z8DXEA",
    "apiFormat": null
   },
   {
    "id": "sssaicode",
    "displayName": "SSSAiCode",
    "baseUrl": "https://node-hk.sssaicodeapi.com/api/v1",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     }
    ],
    "vendorDisplayName": "SSSAiCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://sssaicodeapi.com/register?ref=DCP0SM",
    "apiFormat": null
   },
   {
    "id": "soleapi",
    "displayName": "SoleAPI",
    "baseUrl": "https://soleapi.com/v1",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "SoleAPI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://soleapi.com/r/ccswitch",
    "apiFormat": null
   },
   {
    "id": "micu",
    "displayName": "Micu",
    "baseUrl": "https://www.micuapi.ai/v1",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     }
    ],
    "vendorDisplayName": "Micu",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.micuapi.ai/register?aff=aOYQ",
    "apiFormat": null
   },
   {
    "id": "rightcode",
    "displayName": "RightCode",
    "baseUrl": "https://www.rightapi.ai/codex/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "RightCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.rightapi.ai/register?aff=CCSWITCH",
    "apiFormat": null
   },
   {
    "id": "etok-ai",
    "displayName": "ETok.ai",
    "baseUrl": "https://api.etok.ai/v1",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     }
    ],
    "vendorDisplayName": "ETok.ai",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://etok.ai",
    "apiFormat": null
   },
   {
    "id": "cubence",
    "displayName": "Cubence",
    "baseUrl": "https://api.cubence.com/v1",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     }
    ],
    "vendorDisplayName": "Cubence",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://cubence.com/signup?code=CCSWITCH&source=ccs",
    "apiFormat": null
   },
   {
    "id": "crazyrouter",
    "displayName": "CrazyRouter",
    "baseUrl": "https://cn.crazyrouter.com",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     }
    ],
    "vendorDisplayName": "CrazyRouter",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.crazyrouter.com/register?aff=OZcm&ref=cc-switch",
    "apiFormat": null
   },
   {
    "id": "dmxapi",
    "displayName": "DMXAPI",
    "baseUrl": "https://www.dmxapi.cn/v1",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     }
    ],
    "vendorDisplayName": "DMXAPI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.dmxapi.cn",
    "apiFormat": null
   },
   {
    "id": "sudocode-chat",
    "displayName": "SudoCode.chat",
    "baseUrl": "https://api.sudocode.chat/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "SudoCode.chat",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://sudocode.chat/sign-up?aff=CC-SWITCH&utm_source=cc-switch&utm_medium=sponsor&utm_campaign=ccswitch",
    "apiFormat": null
   },
   {
    "id": "sudocode-us",
    "displayName": "SudoCode.us",
    "baseUrl": "https://sudocode.us/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "SudoCode.us",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://sudocode.us",
    "apiFormat": null
   },
   {
    "id": "xycai",
    "displayName": "XycAi",
    "baseUrl": "https://apicdn.xycai.us/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "XycAi",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://xycai.us/register?aff=Uhu9",
    "apiFormat": null
   },
   {
    "id": "amux",
    "displayName": "Amux",
    "baseUrl": "https://api.amux.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "Amux",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://amux.ai",
    "apiFormat": null
   },
   {
    "id": "atlascloud",
    "displayName": "AtlasCloud",
    "baseUrl": "https://api.atlascloud.ai/v1",
    "models": [
     {
      "id": "zai-org/glm-5.1",
      "displayName": "GLM 5.1"
     }
    ],
    "vendorDisplayName": "AtlasCloud",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.atlascloud.ai/console/coding-plan",
    "apiFormat": null
   },
   {
    "id": "deepseek",
    "displayName": "DeepSeek",
    "baseUrl": "https://api.deepseek.com/v1",
    "models": [
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro"
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash"
     }
    ],
    "vendorDisplayName": "DeepSeek",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.deepseek.com/api_keys",
    "apiFormat": null
   },
   {
    "id": "zhipu-glm",
    "displayName": "Zhipu GLM",
    "baseUrl": "https://open.bigmodel.cn/api/coding/paas/v4",
    "models": [
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1"
     }
    ],
    "vendorDisplayName": "Zhipu GLM",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.bigmodel.cn/claude-code?ic=RRVJPB5SII",
    "apiFormat": null
   },
   {
    "id": "zhipu-glm-en",
    "displayName": "Zhipu GLM en",
    "baseUrl": "https://api.z.ai/api/coding/paas/v4",
    "models": [
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1"
     }
    ],
    "vendorDisplayName": "Zhipu GLM en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://z.ai/subscribe?ic=8JVLJQFSKB",
    "apiFormat": null
   },
   {
    "id": "tencent-token-plan",
    "displayName": "Tencent Token Plan",
    "baseUrl": "https://api.lkeap.cloud.tencent.com/plan/v3",
    "models": [
     {
      "id": "tc-code-latest",
      "displayName": "Auto"
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash"
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro"
     },
     {
      "id": "minimax-m2.7",
      "displayName": "MiniMax M2.7"
     },
     {
      "id": "glm-5",
      "displayName": "GLM-5"
     },
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1"
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2"
     },
     {
      "id": "hy3",
      "displayName": "Hy3"
     },
     {
      "id": "hy3-preview",
      "displayName": "Hy3 Preview"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan",
    "apiFormat": null
   },
   {
    "id": "tencent-token-plan-intl",
    "displayName": "Tencent Token Plan (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto"
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2"
     },
     {
      "id": "kimi-k2.6",
      "displayName": "Kimi K2.6"
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro"
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash"
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax M3"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan",
    "apiFormat": null
   },
   {
    "id": "tencent-token-plan-enterprise-pro",
    "displayName": "Tencent Token Plan Enterprise Pro",
    "baseUrl": "https://tokenhub.tencentmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto"
     },
     {
      "id": "glm-5.3",
      "displayName": "GLM-5.3"
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2"
     },
     {
      "id": "glm-5",
      "displayName": "GLM-5"
     },
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1"
     },
     {
      "id": "glm-5-turbo",
      "displayName": "GLM-5 Turbo"
     },
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code"
     },
     {
      "id": "kimi-k2.7-code-highspeed",
      "displayName": "Kimi K2.7 Code HighSpeed"
     },
     {
      "id": "kimi-k2.6",
      "displayName": "Kimi K2.6"
     },
     {
      "id": "minimax-m2.7",
      "displayName": "MiniMax M2.7"
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax M3"
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash"
     },
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro"
     },
     {
      "id": "deepseek-v4-flash-0731",
      "displayName": "DeepSeek V4 Flash 0731 GA"
     },
     {
      "id": "deepseek-v4-pro-0813",
      "displayName": "DeepSeek V4 Pro 0813 GA"
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash Official"
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro Official"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Pro",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan-e",
    "apiFormat": null
   },
   {
    "id": "tencent-token-plan-enterprise-pro-intl",
    "displayName": "Tencent Token Plan Enterprise Pro (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto"
     },
     {
      "id": "glm-5.3",
      "displayName": "GLM-5.3"
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2"
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax M3"
     },
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code"
     },
     {
      "id": "kimi-k2.7-code-highspeed",
      "displayName": "Kimi K2.7 Code HighSpeed"
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash"
     },
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro"
     },
     {
      "id": "deepseek-v4-flash-0731",
      "displayName": "DeepSeek V4 Flash 0731 GA"
     },
     {
      "id": "deepseek-v4-pro-0813",
      "displayName": "DeepSeek V4 Pro 0813 GA"
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash Official"
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro Official"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Pro (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan-e",
    "apiFormat": null
   },
   {
    "id": "tencent-token-plan-enterprise-lite",
    "displayName": "Tencent Token Plan Enterprise Lite",
    "baseUrl": "https://tokenhub.tencentmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Lite",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan-e",
    "apiFormat": null
   },
   {
    "id": "tencent-token-plan-enterprise-lite-intl",
    "displayName": "Tencent Token Plan Enterprise Lite (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Lite (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan-e",
    "apiFormat": null
   },
   {
    "id": "baidu-qianfan-token-plan",
    "displayName": "Baidu Qianfan Token Plan",
    "baseUrl": "https://qianfan.baidubce.com/v2/tokenplan/personal",
    "models": [
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro"
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash"
     },
     {
      "id": "deepseek-v4-flash-0731",
      "displayName": "DeepSeek V4 Flash 0731"
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2"
     },
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1"
     },
     {
      "id": "kimi-k2.6",
      "displayName": "Kimi K2.6"
     }
    ],
    "vendorDisplayName": "Baidu Qianfan Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.bce.baidu.com/qianfan/resource/token-plan",
    "apiFormat": null
   },
   {
    "id": "千问ai平台",
    "displayName": "千问AI平台",
    "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max"
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash"
     }
    ],
    "vendorDisplayName": "千问AI平台",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.qianwenai.com/home/api-keys?utm_content=g_20000002972",
    "apiFormat": null
   },
   {
    "id": "千问ai平台-token-plan",
    "displayName": "千问AI平台 Token Plan",
    "baseUrl": "https://token-plan.cn-beijing.maas.aliyuncs.com/apps/anthropic/v1",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max"
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash"
     }
    ],
    "vendorDisplayName": "千问AI平台 Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://platform.qianwenai.com/home/api-keys?utm_content=g_20000002978",
    "apiFormat": null
   },
   {
    "id": "qwencloud",
    "displayName": "QwenCloud",
    "baseUrl": "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max"
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash"
     },
     {
      "id": "qwen3.7-max",
      "displayName": "Qwen3.7 Max"
     }
    ],
    "vendorDisplayName": "QwenCloud",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys?utm_content=g_20000002975",
    "apiFormat": null
   },
   {
    "id": "qwencloud-for-coding",
    "displayName": "QwenCloud For Coding",
    "baseUrl": "https://coding-intl.dashscope.aliyuncs.com/apps/anthropic/v1",
    "models": [
     {
      "id": "qwen3.7-plus",
      "displayName": "Qwen3.7 Plus"
     },
     {
      "id": "qwen3.6-plus",
      "displayName": "Qwen3.6 Plus"
     },
     {
      "id": "qwen3-coder-plus",
      "displayName": "Qwen3 Coder Plus"
     }
    ],
    "vendorDisplayName": "QwenCloud For Coding",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys",
    "apiFormat": null
   },
   {
    "id": "qwencloud-token-plan",
    "displayName": "QwenCloud Token Plan",
    "baseUrl": "https://token-plan.ap-southeast-1.maas.aliyuncs.com/apps/anthropic/v1",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max"
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash"
     },
     {
      "id": "qwen3.7-max",
      "displayName": "Qwen3.7 Max"
     }
    ],
    "vendorDisplayName": "QwenCloud Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys?utm_content=g_20000002981",
    "apiFormat": null
   },
   {
    "id": "stepfun",
    "displayName": "StepFun",
    "baseUrl": "https://api.stepfun.com/step_plan/v1",
    "models": [
     {
      "id": "step-3.5-flash-2603",
      "displayName": "Step 3.5 Flash 2603"
     },
     {
      "id": "step-3.5-flash",
      "displayName": "Step 3.5 Flash"
     }
    ],
    "vendorDisplayName": "StepFun",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.stepfun.com/interface-key",
    "apiFormat": null
   },
   {
    "id": "stepfun-en",
    "displayName": "StepFun en",
    "baseUrl": "https://api.stepfun.ai/step_plan/v1",
    "models": [
     {
      "id": "step-3.5-flash-2603",
      "displayName": "Step 3.5 Flash 2603"
     },
     {
      "id": "step-3.5-flash",
      "displayName": "Step 3.5 Flash"
     }
    ],
    "vendorDisplayName": "StepFun en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.stepfun.ai/interface-key",
    "apiFormat": null
   },
   {
    "id": "stepfun-step-plan",
    "displayName": "StepFun Step Plan",
    "baseUrl": "https://api.stepfun.com/step_plan/v1",
    "models": [
     {
      "id": "step-3.5-flash",
      "displayName": "Step 3.5 Flash"
     }
    ],
    "vendorDisplayName": "StepFun Step Plan",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.stepfun.com/interface-key",
    "apiFormat": null
   },
   {
    "id": "modelscope",
    "displayName": "ModelScope",
    "baseUrl": "https://api-inference.modelscope.cn/v1",
    "models": [
     {
      "id": "ZhipuAI/GLM-5.2",
      "displayName": "GLM-5.2"
     }
    ],
    "vendorDisplayName": "ModelScope",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://modelscope.cn/my/myaccesstoken",
    "apiFormat": null
   },
   {
    "id": "longcat",
    "displayName": "Longcat",
    "baseUrl": "https://api.longcat.chat/openai/v1",
    "models": [
     {
      "id": "LongCat-2.0",
      "displayName": "LongCat 2.0"
     }
    ],
    "vendorDisplayName": "Longcat",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://longcat.chat/platform/api_keys",
    "apiFormat": null
   },
   {
    "id": "minimax",
    "displayName": "MiniMax",
    "baseUrl": "https://api.minimaxi.com/v1",
    "models": [
     {
      "id": "MiniMax-M3",
      "displayName": "MiniMax M3",
      "capability": {
       "reasoning": true
      }
     }
    ],
    "vendorDisplayName": "MiniMax",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.minimaxi.com/subscribe/coding-plan",
    "apiFormat": null
   },
   {
    "id": "minimax-en",
    "displayName": "MiniMax en",
    "baseUrl": "https://api.minimax.io/v1",
    "models": [
     {
      "id": "MiniMax-M3",
      "displayName": "MiniMax M3",
      "capability": {
       "reasoning": true
      }
     }
    ],
    "vendorDisplayName": "MiniMax en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.minimax.io/subscribe/coding-plan",
    "apiFormat": null
   },
   {
    "id": "bailing",
    "displayName": "BaiLing",
    "baseUrl": "https://api.tbox.cn/v1",
    "models": [
     {
      "id": "Ling-2.5-1T",
      "displayName": "Ling 2.5-1T"
     }
    ],
    "vendorDisplayName": "BaiLing",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://alipaytbox.yuque.com/sxs0ba/ling/get_started",
    "apiFormat": null
   },
   {
    "id": "xiaomi-mimo",
    "displayName": "Xiaomi MiMo",
    "baseUrl": "https://api.xiaomimimo.com/v1",
    "models": [
     {
      "id": "mimo-v2.5-pro",
      "displayName": "MiMo V2.5 Pro"
     },
     {
      "id": "mimo-v2.5",
      "displayName": "MiMo V2.5"
     }
    ],
    "vendorDisplayName": "Xiaomi MiMo",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.xiaomimimo.com/#/console/api-keys",
    "apiFormat": null
   },
   {
    "id": "xiaomi-mimo-token-plan-china",
    "displayName": "Xiaomi MiMo Token Plan (China)",
    "baseUrl": "https://token-plan-cn.xiaomimimo.com/v1",
    "models": [
     {
      "id": "mimo-v2.5-pro",
      "displayName": "MiMo V2.5 Pro"
     },
     {
      "id": "mimo-v2.5",
      "displayName": "MiMo V2.5"
     }
    ],
    "vendorDisplayName": "Xiaomi MiMo Token Plan (China)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://platform.xiaomimimo.com/#/console/plan-manage",
    "apiFormat": null
   },
   {
    "id": "opencode-go",
    "displayName": "OpenCode Go",
    "baseUrl": "https://opencode.ai/zen/go/v1",
    "models": [
     {
      "id": "glm-5.2",
      "displayName": "GLM 5.2"
     },
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code"
     },
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro"
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash"
     },
     {
      "id": "mimo-v2.5-pro",
      "displayName": "MiMo V2.5 Pro"
     }
    ],
    "vendorDisplayName": "OpenCode Go",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://opencode.ai/go?ref=2YTRG2NGTX",
    "apiFormat": null
   },
   {
    "id": "aihubmix",
    "displayName": "AiHubMix",
    "baseUrl": "https://aihubmix.com/v1",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     }
    ],
    "vendorDisplayName": "AiHubMix",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aihubmix.com",
    "apiFormat": null
   },
   {
    "id": "cherryin",
    "displayName": "CherryIN",
    "baseUrl": "https://open.cherryin.net/v1",
    "models": [
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "anthropic/claude-opus-5",
      "displayName": "Claude Opus 5"
     }
    ],
    "vendorDisplayName": "CherryIN",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://open.cherryin.ai/console/token",
    "apiFormat": null
   },
   {
    "id": "openrouter",
    "displayName": "OpenRouter",
    "baseUrl": "https://openrouter.ai/api/v1",
    "models": [
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "anthropic/claude-opus-5",
      "displayName": "Claude Opus 5"
     }
    ],
    "vendorDisplayName": "OpenRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://openrouter.ai/keys",
    "apiFormat": null
   },
   {
    "id": "therouter",
    "displayName": "TheRouter",
    "baseUrl": "https://api.therouter.ai/v1",
    "models": [
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "openai/gpt-5.3-codex",
      "displayName": "GPT-5.3 Codex"
     },
     {
      "id": "openai/gpt-5.2",
      "displayName": "GPT-5.2"
     },
     {
      "id": "google/gemini-3.6-flash",
      "displayName": "Gemini 3.6 Flash"
     },
     {
      "id": "qwen/qwen3-coder-480b",
      "displayName": "Qwen3 Coder 480B"
     }
    ],
    "vendorDisplayName": "TheRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://dashboard.therouter.ai",
    "apiFormat": null
   },
   {
    "id": "novita-ai",
    "displayName": "Novita AI",
    "baseUrl": "https://api.novita.ai/openai",
    "models": [
     {
      "id": "zai-org/glm-5.1",
      "displayName": "GLM-5.1"
     }
    ],
    "vendorDisplayName": "Novita AI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://novita.ai",
    "apiFormat": null
   },
   {
    "id": "nvidia",
    "displayName": "Nvidia",
    "baseUrl": "https://integrate.api.nvidia.com/v1",
    "models": [
     {
      "id": "moonshotai/kimi-k2.5",
      "displayName": "Kimi K2.5"
     }
    ],
    "vendorDisplayName": "Nvidia",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://build.nvidia.com/settings/api-keys",
    "apiFormat": null
   },
   {
    "id": "pipellm",
    "displayName": "PIPELLM",
    "baseUrl": "https://cc-api.pipellm.ai",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "claude-opus-5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "claude-sonnet-5"
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "claude-haiku-4-5-20251001"
     }
    ],
    "vendorDisplayName": "PIPELLM",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://code.pipellm.ai/login?ref=uvw650za",
    "apiFormat": null
   },
   {
    "id": "e-flowcode",
    "displayName": "E-FlowCode",
    "baseUrl": "https://e-flowcode.cc/v1",
    "models": [
     {
      "id": "gpt-5.2-codex",
      "displayName": "gpt-5.2-codex"
     },
     {
      "id": "gpt-5.3-codex",
      "displayName": "gpt-5.3-codex"
     }
    ],
    "vendorDisplayName": "E-FlowCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://e-flowcode.cc",
    "apiFormat": null
   },
   {
    "id": "jiekou-ai",
    "displayName": "JieKou AI",
    "baseUrl": "https://api.jiekou.ai/openai/v1",
    "models": [
     {
      "id": "claude-fable-5",
      "displayName": "Claude Fable 5"
     }
    ],
    "vendorDisplayName": "JieKou AI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://jiekou.ai/settings/key-management",
    "apiFormat": null
   },
   {
    "id": "aicodewith",
    "displayName": "AICodeWith",
    "baseUrl": "https://api.aicodewith.ai/v1",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     }
    ],
    "vendorDisplayName": "AICodeWith",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aicodewith.ai/login?tab=register",
    "apiFormat": null
   }
  ],
  "openclaw": [
   {
    "id": "kimi",
    "displayName": "Kimi",
    "baseUrl": "https://api.moonshot.cn/v1",
    "models": [
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code",
      "capability": {
       "contextWindow": 262144
      }
     },
     {
      "id": "kimi-k3",
      "displayName": "Kimi K3",
      "capability": {
       "contextWindow": 1048576
      }
     }
    ],
    "vendorDisplayName": "Kimi",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.kimi.com/console/api-keys?aff=cc-switch",
    "apiFormat": "openai-completions"
   },
   {
    "id": "kimi-for-coding",
    "displayName": "Kimi For Coding",
    "baseUrl": "https://api.kimi.com/coding/v1",
    "models": [
     {
      "id": "kimi-for-coding",
      "displayName": "Kimi For Coding",
      "capability": {
       "contextWindow": 131072
      }
     }
    ],
    "vendorDisplayName": "Kimi For Coding",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://platform.kimi.com/console/api-keys?aff=cc-switch",
    "apiFormat": "openai-completions"
   },
   {
    "id": "packycode",
    "displayName": "PackyCode",
    "baseUrl": "https://www.packyapi.ai",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "PackyCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.packyapi.ai/register?aff=cc-switch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "zetaapi",
    "displayName": "ZetaAPI",
    "baseUrl": "https://api.zetaapi.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "contextWindow": 400000
      }
     }
    ],
    "vendorDisplayName": "ZetaAPI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://zetaapi.ai/go/u117",
    "apiFormat": "openai-completions"
   },
   {
    "id": "apinebula",
    "displayName": "APINebula",
    "baseUrl": "https://apinebula.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "APINebula",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://apinebula.ai/VjM74M",
    "apiFormat": "openai-completions"
   },
   {
    "id": "aicodemirror",
    "displayName": "AICodeMirror",
    "baseUrl": "https://api.aicodemirror.ai/api/claudecode",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "AICodeMirror",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.aicodemirror.ai/register?invitecode=9915W3",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "fennoai",
    "displayName": "FennoAI",
    "baseUrl": "https://api.fenno.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "contextWindow": 400000
      }
     }
    ],
    "vendorDisplayName": "FennoAI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://api.fenno.ai/register?redirect=/purchase?tab=subscription%26group=16&aff=P9MR3D3PLCNL",
    "apiFormat": "openai-completions"
   },
   {
    "id": "runapi",
    "displayName": "RunAPI",
    "baseUrl": "https://runapi.host",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5",
      "capability": {
       "contextWindow": 200000
      }
     }
    ],
    "vendorDisplayName": "RunAPI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://runapi.host/register?aff=iOKB",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "shengsuanyun",
    "displayName": "Shengsuanyun",
    "baseUrl": "https://router.shengsuanyun.com/api",
    "models": [
     {
      "id": "anthropic/claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "Shengsuanyun",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.shengsuanyun.com/?from=CH_4HHXMRYF",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "aigocode",
    "displayName": "AIGoCode",
    "baseUrl": "https://api.aigocode.app",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "AIGoCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aigocode.app/invite/CC-SWITCH",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "qiniu",
    "displayName": "Qiniu",
    "baseUrl": "https://api.qnaigc.com/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "contextWindow": 400000
      }
     }
    ],
    "vendorDisplayName": "Qiniu",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://s.qiniu.com/nMvAvy",
    "apiFormat": "openai-completions"
   },
   {
    "id": "aicoding",
    "displayName": "AICoding",
    "baseUrl": "https://api.aicoding.inc",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "AICoding",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aicoding.inc/i/CCSWITCH",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "subrouter",
    "displayName": "SubRouter",
    "baseUrl": "https://subrouter.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "contextWindow": 400000
      }
     }
    ],
    "vendorDisplayName": "SubRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://subrouter.ai/register?aff=l3ri",
    "apiFormat": "openai-completions"
   },
   {
    "id": "apikey-fun",
    "displayName": "APIKEY.FUN",
    "baseUrl": "https://api.apikey.fan",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5",
      "capability": {
       "contextWindow": 200000
      }
     }
    ],
    "vendorDisplayName": "APIKEY.FUN",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://apikey.fan/register?aff=CCSwitch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "9527code",
    "displayName": "9527CODE",
    "baseUrl": "https://9527.codes",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5",
      "capability": {
       "contextWindow": 200000
      }
     }
    ],
    "vendorDisplayName": "9527CODE",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://9527.codes/register?aff=e5zI",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "code0",
    "displayName": "Code0",
    "baseUrl": "https://code0.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "contextWindow": 400000
      }
     }
    ],
    "vendorDisplayName": "Code0",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://code0.ai/agent/register/B2XHxGjGmRvqgznY",
    "apiFormat": "openai-completions"
   },
   {
    "id": "teamorouter",
    "displayName": "TeamoRouter",
    "baseUrl": "https://api.teamorouter.cn/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "contextWindow": 400000
      }
     }
    ],
    "vendorDisplayName": "TeamoRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://teamorouter.cn/?utm_source=cc_switch&utm_medium=referral&utm_campaign=ai_directory",
    "apiFormat": "openai-completions"
   },
   {
    "id": "ppio",
    "displayName": "PPIO",
    "baseUrl": "https://api.ppio.com/openai/v1",
    "models": [
     {
      "id": "deepseek/deepseek-v4-flash-0731",
      "displayName": "Deepseek V4 Flash 0731",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 393216
      }
     }
    ],
    "vendorDisplayName": "PPIO",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://ppio.com/activity/ccswitch",
    "apiFormat": "openai-completions"
   },
   {
    "id": "claudecn",
    "displayName": "ClaudeCN",
    "baseUrl": "https://claudecn.top",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5",
      "capability": {
       "contextWindow": 200000
      }
     }
    ],
    "vendorDisplayName": "ClaudeCN",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://claudecn.ai/register?aff=HEL9",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "火山-agent-plan",
    "displayName": "火山 Agent Plan",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/plan/v3",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "Ark Code Latest",
      "capability": {
       "contextWindow": 256000
      }
     }
    ],
    "vendorDisplayName": "火山 Agent Plan",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.volcengine.com/activity/agentplan?ac=MMAP8JTTCAQ2&rc=6J6FV5N2&utm_source=OWO&utm_medium=devrel-1&utm_campaign=hw&utm_term=ccswitch&utm_content=hw",
    "apiFormat": "openai-completions"
   },
   {
    "id": "火山-coding-plan",
    "displayName": "火山 Coding Plan",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/coding/v3",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "Ark Code Latest",
      "capability": {
       "contextWindow": 256000
      }
     }
    ],
    "vendorDisplayName": "火山 Coding Plan",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://www.volcengine.com/activity/codingplan?ac=MMAP8JTTCAQ2&rc=6J6FV5N2&utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": "openai-completions"
   },
   {
    "id": "byteplus",
    "displayName": "BytePlus",
    "baseUrl": "https://ark.ap-southeast.bytepluses.com/api/coding/v3",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "Ark Code Latest",
      "capability": {
       "contextWindow": 256000
      }
     }
    ],
    "vendorDisplayName": "BytePlus",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.byteplus.com/en/product/modelark?utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": "openai-completions"
   },
   {
    "id": "volcengine-doubao",
    "displayName": "Volcengine Doubao",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/v3",
    "models": [
     {
      "id": "doubao-seed-2-1-pro-260628",
      "displayName": "DouBao Seed 2.1 Pro",
      "capability": {
       "contextWindow": 262144
      }
     }
    ],
    "vendorDisplayName": "Volcengine Doubao",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey?apikey=%7B%7D&utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": "openai-completions"
   },
   {
    "id": "siliconflow",
    "displayName": "SiliconFlow",
    "baseUrl": "https://api.siliconflow.cn/v1",
    "models": [
     {
      "id": "Pro/MiniMaxAI/MiniMax-M2.5",
      "displayName": "MiniMax M2.5",
      "capability": {
       "contextWindow": 196608
      }
     }
    ],
    "vendorDisplayName": "SiliconFlow",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://cloud.siliconflow.cn/i/YflgU2Ve",
    "apiFormat": "openai-completions"
   },
   {
    "id": "siliconflow-en",
    "displayName": "SiliconFlow en",
    "baseUrl": "https://api.siliconflow.com/v1",
    "models": [
     {
      "id": "MiniMaxAI/MiniMax-M3",
      "displayName": "MiniMax M3",
      "capability": {
       "contextWindow": 1048576
      }
     }
    ],
    "vendorDisplayName": "SiliconFlow en",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://cloud.siliconflow.cn/i/YflgU2Ve",
    "apiFormat": "openai-completions"
   },
   {
    "id": "a6api",
    "displayName": "A6API",
    "baseUrl": "https://api.a6api.com/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "contextWindow": 400000
      }
     }
    ],
    "vendorDisplayName": "A6API",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://a6api.com/register?aff=AqNr",
    "apiFormat": "openai-completions"
   },
   {
    "id": "compshare",
    "displayName": "Compshare",
    "baseUrl": "https://api.modelverse.cn/v1",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "Compshare",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.compshare.cn/coding-plan?ytag=GPU_YY_YX_git_cc-switch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "compshare-coding-plan",
    "displayName": "Compshare Coding Plan",
    "baseUrl": "https://cp.compshare.cn/v1",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "Compshare Coding Plan",
    "category": "Aggregator",
    "accessChannel": "CodingPlan",
    "market": "Unspecified",
    "documentationUrl": "https://www.compshare.cn/coding-plan?ytag=GPU_YY_YX_git_cc-switch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "ccsub",
    "displayName": "CCSub",
    "baseUrl": "https://www.ccsub.net/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "contextWindow": 400000
      }
     }
    ],
    "vendorDisplayName": "CCSub",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.ccsub.net/register?ref=Y6Z8DXEA",
    "apiFormat": "openai-completions"
   },
   {
    "id": "sssaicode",
    "displayName": "SSSAiCode",
    "baseUrl": "https://node-hk.sssaicodeapi.com/api",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "SSSAiCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://sssaicodeapi.com/register?ref=DCP0SM",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "soleapi",
    "displayName": "SoleAPI",
    "baseUrl": "https://soleapi.com",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "Claude Haiku 4.5",
      "capability": {
       "contextWindow": 200000
      }
     }
    ],
    "vendorDisplayName": "SoleAPI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://soleapi.com/r/ccswitch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "micu",
    "displayName": "Micu",
    "baseUrl": "https://www.micuapi.ai",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "Micu",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.micuapi.ai/register?aff=aOYQ",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "rightcode",
    "displayName": "RightCode",
    "baseUrl": "https://www.rightapi.ai/claude",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "RightCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.rightapi.ai/register?aff=CCSWITCH",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "etok-ai",
    "displayName": "ETok.ai",
    "baseUrl": "https://api.etok.ai",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "ETok.ai",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://etok.ai",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "cubence",
    "displayName": "Cubence",
    "baseUrl": "https://api.cubence.com",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "Cubence",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://cubence.com/signup?code=CCSWITCH&source=ccs",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "crazyrouter",
    "displayName": "CrazyRouter",
    "baseUrl": "https://cn.crazyrouter.com/v1",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "CrazyRouter",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.crazyrouter.com/register?aff=OZcm&ref=cc-switch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "dmxapi",
    "displayName": "DMXAPI",
    "baseUrl": "https://www.dmxapi.cn",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "DMXAPI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.dmxapi.cn",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "sudocode-chat",
    "displayName": "SudoCode.chat",
    "baseUrl": "https://api.sudocode.chat/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "SudoCode.chat",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://sudocode.chat/sign-up?aff=CC-SWITCH&utm_source=cc-switch&utm_medium=sponsor&utm_campaign=ccswitch",
    "apiFormat": "openai-responses"
   },
   {
    "id": "sudocode-us",
    "displayName": "SudoCode.us",
    "baseUrl": "https://sudocode.us/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "SudoCode.us",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://sudocode.us",
    "apiFormat": "openai-responses"
   },
   {
    "id": "xycai",
    "displayName": "XycAi",
    "baseUrl": "https://apicdn.xycai.us/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "contextWindow": 400000
      }
     }
    ],
    "vendorDisplayName": "XycAi",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://xycai.us/register?aff=Uhu9",
    "apiFormat": "openai-completions"
   },
   {
    "id": "amux",
    "displayName": "Amux",
    "baseUrl": "https://api.amux.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "contextWindow": 400000
      }
     }
    ],
    "vendorDisplayName": "Amux",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://amux.ai",
    "apiFormat": "openai-completions"
   },
   {
    "id": "atlascloud",
    "displayName": "AtlasCloud",
    "baseUrl": "https://api.atlascloud.ai/v1",
    "models": [
     {
      "id": "zai-org/glm-5.1",
      "displayName": "GLM 5.1"
     }
    ],
    "vendorDisplayName": "AtlasCloud",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.atlascloud.ai/console/coding-plan",
    "apiFormat": "openai-completions"
   },
   {
    "id": "deepseek",
    "displayName": "DeepSeek",
    "baseUrl": "https://api.deepseek.com/v1",
    "models": [
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "DeepSeek",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.deepseek.com/api_keys",
    "apiFormat": "openai-completions"
   },
   {
    "id": "zhipu-glm",
    "displayName": "Zhipu GLM",
    "baseUrl": "https://open.bigmodel.cn/api/coding/paas/v4",
    "models": [
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1",
      "capability": {
       "contextWindow": 128000
      }
     }
    ],
    "vendorDisplayName": "Zhipu GLM",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.bigmodel.cn/claude-code?ic=RRVJPB5SII",
    "apiFormat": "openai-completions"
   },
   {
    "id": "zhipu-glm-en",
    "displayName": "Zhipu GLM en",
    "baseUrl": "https://api.z.ai/api/coding/paas/v4",
    "models": [
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1",
      "capability": {
       "contextWindow": 128000
      }
     }
    ],
    "vendorDisplayName": "Zhipu GLM en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://z.ai/subscribe?ic=8JVLJQFSKB",
    "apiFormat": "openai-completions"
   },
   {
    "id": "tencent-token-plan",
    "displayName": "Tencent Token Plan",
    "baseUrl": "https://api.lkeap.cloud.tencent.com/plan/v3",
    "models": [
     {
      "id": "tc-code-latest",
      "displayName": "Auto",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 196608,
       "maxTokens": 32768
      }
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000
      }
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000
      }
     },
     {
      "id": "minimax-m2.7",
      "displayName": "MiniMax M2.7",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5",
      "displayName": "GLM-5",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 202752,
       "maxTokens": 16384
      }
     },
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "hy3",
      "displayName": "Hy3",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 256000,
       "maxTokens": 131072
      }
     },
     {
      "id": "hy3-preview",
      "displayName": "Hy3 Preview",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 262144,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan",
    "apiFormat": "openai-completions"
   },
   {
    "id": "tencent-token-plan-intl",
    "displayName": "Tencent Token Plan (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 196608,
       "maxTokens": 32768
      }
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "kimi-k2.6",
      "displayName": "Kimi K2.6",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144
      }
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000
      }
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000
      }
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax M3",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 32768
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan",
    "apiFormat": "openai-completions"
   },
   {
    "id": "tencent-token-plan-enterprise-pro",
    "displayName": "Tencent Token Plan Enterprise Pro",
    "baseUrl": "https://tokenhub.tencentmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 196608,
       "maxTokens": 32768
      }
     },
     {
      "id": "glm-5.3",
      "displayName": "GLM-5.3",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5",
      "displayName": "GLM-5",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 202752,
       "maxTokens": 16384
      }
     },
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5-turbo",
      "displayName": "GLM-5 Turbo",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144
      }
     },
     {
      "id": "kimi-k2.7-code-highspeed",
      "displayName": "Kimi K2.7 Code HighSpeed",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144
      }
     },
     {
      "id": "kimi-k2.6",
      "displayName": "Kimi K2.6",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144
      }
     },
     {
      "id": "minimax-m2.7",
      "displayName": "MiniMax M2.7",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax M3",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 32768
      }
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 393216
      }
     },
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 393216
      }
     },
     {
      "id": "deepseek-v4-flash-0731",
      "displayName": "DeepSeek V4 Flash 0731 GA",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 393216
      }
     },
     {
      "id": "deepseek-v4-pro-0813",
      "displayName": "DeepSeek V4 Pro 0813 GA",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 393216
      }
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash Official",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 393216
      }
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro Official",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 393216
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Pro",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan-e",
    "apiFormat": "openai-completions"
   },
   {
    "id": "tencent-token-plan-enterprise-pro-intl",
    "displayName": "Tencent Token Plan Enterprise Pro (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 196608,
       "maxTokens": 32768
      }
     },
     {
      "id": "glm-5.3",
      "displayName": "GLM-5.3",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax M3",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 32768
      }
     },
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144
      }
     },
     {
      "id": "kimi-k2.7-code-highspeed",
      "displayName": "Kimi K2.7 Code HighSpeed",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144
      }
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 393216
      }
     },
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 393216
      }
     },
     {
      "id": "deepseek-v4-flash-0731",
      "displayName": "DeepSeek V4 Flash 0731 GA",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 393216
      }
     },
     {
      "id": "deepseek-v4-pro-0813",
      "displayName": "DeepSeek V4 Pro 0813 GA",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 393216
      }
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash Official",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 393216
      }
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro Official",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 393216
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Pro (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan-e",
    "apiFormat": "openai-completions"
   },
   {
    "id": "tencent-token-plan-enterprise-lite",
    "displayName": "Tencent Token Plan Enterprise Lite",
    "baseUrl": "https://tokenhub.tencentmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 196608,
       "maxTokens": 32768
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Lite",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan-e",
    "apiFormat": "openai-completions"
   },
   {
    "id": "tencent-token-plan-enterprise-lite-intl",
    "displayName": "Tencent Token Plan Enterprise Lite (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 196608,
       "maxTokens": 32768
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Lite (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan-e",
    "apiFormat": "openai-completions"
   },
   {
    "id": "baidu-qianfan-token-plan",
    "displayName": "Baidu Qianfan Token Plan",
    "baseUrl": "https://qianfan.baidubce.com/v2/tokenplan/personal",
    "models": [
     {
      "id": "deepseek-v4-pro",
      "displayName": "deepseek-v4-pro",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 98304,
       "maxTokens": 65536
      }
     }
    ],
    "vendorDisplayName": "Baidu Qianfan Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.bce.baidu.com/qianfan/resource/token-plan",
    "apiFormat": "openai-completions"
   },
   {
    "id": "千问ai平台",
    "displayName": "千问AI平台",
    "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max",
      "capability": {
       "contextWindow": 983616
      }
     }
    ],
    "vendorDisplayName": "千问AI平台",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.qianwenai.com/home/api-keys?utm_content=g_20000002972",
    "apiFormat": "openai-completions"
   },
   {
    "id": "千问ai平台-token-plan",
    "displayName": "千问AI平台 Token Plan",
    "baseUrl": "https://token-plan.cn-beijing.maas.aliyuncs.com/apps/anthropic",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 983616,
       "maxTokens": 131072
      }
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 983616,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "千问AI平台 Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://platform.qianwenai.com/home/api-keys?utm_content=g_20000002978",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "qwencloud",
    "displayName": "QwenCloud",
    "baseUrl": "https://dashscope-intl.aliyuncs.com/apps/anthropic/v1",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 983616,
       "maxTokens": 131072
      }
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 983616,
       "maxTokens": 131072
      }
     },
     {
      "id": "qwen3.7-max",
      "displayName": "Qwen3.7 Max",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 65536
      }
     }
    ],
    "vendorDisplayName": "QwenCloud",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys?utm_content=g_20000002975",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "qwencloud-for-coding",
    "displayName": "QwenCloud For Coding",
    "baseUrl": "https://coding-intl.dashscope.aliyuncs.com/apps/anthropic/v1",
    "models": [
     {
      "id": "qwen3.7-plus",
      "displayName": "Qwen3.7 Plus",
      "capability": {
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 65536
      }
     },
     {
      "id": "qwen3.6-plus",
      "displayName": "Qwen3.6 Plus",
      "capability": {
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 65536
      }
     },
     {
      "id": "qwen3-coder-plus",
      "displayName": "Qwen3 Coder Plus",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 131072,
       "maxTokens": 65536
      }
     }
    ],
    "vendorDisplayName": "QwenCloud For Coding",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "qwencloud-token-plan",
    "displayName": "QwenCloud Token Plan",
    "baseUrl": "https://token-plan.ap-southeast-1.maas.aliyuncs.com/apps/anthropic/v1",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 983616,
       "maxTokens": 131072
      }
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 983616,
       "maxTokens": 131072
      }
     },
     {
      "id": "qwen3.7-max",
      "displayName": "Qwen3.7 Max",
      "capability": {
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 65536
      }
     }
    ],
    "vendorDisplayName": "QwenCloud Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys?utm_content=g_20000002981",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "stepfun",
    "displayName": "StepFun",
    "baseUrl": "https://api.stepfun.com/step_plan/v1",
    "models": [
     {
      "id": "step-3.5-flash-2603",
      "displayName": "Step 3.5 Flash 2603",
      "capability": {
       "contextWindow": 262144
      }
     },
     {
      "id": "step-3.5-flash",
      "displayName": "Step 3.5 Flash",
      "capability": {
       "contextWindow": 262144
      }
     }
    ],
    "vendorDisplayName": "StepFun",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.stepfun.com/interface-key",
    "apiFormat": "openai-completions"
   },
   {
    "id": "stepfun-en",
    "displayName": "StepFun en",
    "baseUrl": "https://api.stepfun.ai/step_plan/v1",
    "models": [
     {
      "id": "step-3.5-flash-2603",
      "displayName": "Step 3.5 Flash 2603",
      "capability": {
       "contextWindow": 262144
      }
     },
     {
      "id": "step-3.5-flash",
      "displayName": "Step 3.5 Flash",
      "capability": {
       "contextWindow": 262144
      }
     }
    ],
    "vendorDisplayName": "StepFun en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.stepfun.ai/interface-key",
    "apiFormat": "openai-completions"
   },
   {
    "id": "minimax",
    "displayName": "MiniMax",
    "baseUrl": "https://api.minimaxi.com/v1",
    "models": [
     {
      "id": "MiniMax-M3",
      "displayName": "MiniMax M3",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "MiniMax",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.minimaxi.com/subscribe/coding-plan",
    "apiFormat": "openai-completions"
   },
   {
    "id": "minimax-en",
    "displayName": "MiniMax en",
    "baseUrl": "https://api.minimax.io/v1",
    "models": [
     {
      "id": "MiniMax-M3",
      "displayName": "MiniMax M3",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "MiniMax en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.minimax.io/subscribe/coding-plan",
    "apiFormat": "openai-completions"
   },
   {
    "id": "longcat",
    "displayName": "Longcat",
    "baseUrl": "https://api.longcat.chat/openai/v1",
    "models": [
     {
      "id": "LongCat-2.0",
      "displayName": "LongCat 2.0",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "Longcat",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://longcat.chat/platform/api_keys",
    "apiFormat": "openai-completions"
   },
   {
    "id": "bailing",
    "displayName": "BaiLing",
    "baseUrl": "https://api.tbox.cn/v1",
    "models": [
     {
      "id": "Ling-2.5-1T",
      "displayName": "Ling 2.5 1T",
      "capability": {
       "contextWindow": 128000
      }
     }
    ],
    "vendorDisplayName": "BaiLing",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://alipaytbox.yuque.com/sxs0ba/ling/get_started",
    "apiFormat": "openai-completions"
   },
   {
    "id": "xiaomi-mimo",
    "displayName": "Xiaomi MiMo",
    "baseUrl": "https://api.xiaomimimo.com/v1",
    "models": [
     {
      "id": "mimo-v2.5-pro",
      "displayName": "MiMo V2.5 Pro",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "Xiaomi MiMo",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.xiaomimimo.com/#/console/api-keys",
    "apiFormat": "openai-completions"
   },
   {
    "id": "xiaomi-mimo-token-plan-china",
    "displayName": "Xiaomi MiMo Token Plan (China)",
    "baseUrl": "https://token-plan-cn.xiaomimimo.com/v1",
    "models": [
     {
      "id": "mimo-v2.5-pro",
      "displayName": "MiMo V2.5 Pro",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "mimo-v2.5",
      "displayName": "MiMo V2.5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "Xiaomi MiMo Token Plan (China)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://platform.xiaomimimo.com/#/console/plan-manage",
    "apiFormat": "openai-completions"
   },
   {
    "id": "aihubmix",
    "displayName": "AiHubMix",
    "baseUrl": "https://aihubmix.com",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "AiHubMix",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aihubmix.com",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "cherryin",
    "displayName": "CherryIN",
    "baseUrl": "https://open.cherryin.net",
    "models": [
     {
      "id": "anthropic/claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "CherryIN",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://open.cherryin.ai/console/token",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "openrouter",
    "displayName": "OpenRouter",
    "baseUrl": "https://openrouter.ai/api/v1",
    "models": [
     {
      "id": "anthropic/claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     }
    ],
    "vendorDisplayName": "OpenRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://openrouter.ai/keys",
    "apiFormat": "openai-completions"
   },
   {
    "id": "therouter",
    "displayName": "TheRouter",
    "baseUrl": "https://api.therouter.ai/v1",
    "models": [
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "openai/gpt-5.3-codex",
      "displayName": "GPT-5.3 Codex",
      "capability": {
       "contextWindow": 400000
      }
     },
     {
      "id": "openai/gpt-5.2",
      "displayName": "GPT-5.2",
      "capability": {
       "contextWindow": 400000
      }
     },
     {
      "id": "google/gemini-3.6-flash",
      "displayName": "Gemini 3.6 Flash",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "qwen/qwen3-coder-480b",
      "displayName": "Qwen3 Coder 480B",
      "capability": {
       "contextWindow": 262144
      }
     }
    ],
    "vendorDisplayName": "TheRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://dashboard.therouter.ai",
    "apiFormat": "openai-completions"
   },
   {
    "id": "modelscope",
    "displayName": "ModelScope",
    "baseUrl": "https://api-inference.modelscope.cn/v1",
    "models": [
     {
      "id": "ZhipuAI/GLM-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "contextWindow": 128000
      }
     }
    ],
    "vendorDisplayName": "ModelScope",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://modelscope.cn/my/myaccesstoken",
    "apiFormat": "openai-completions"
   },
   {
    "id": "novita-ai",
    "displayName": "Novita AI",
    "baseUrl": "https://api.novita.ai/openai",
    "models": [
     {
      "id": "zai-org/glm-5.1",
      "displayName": "GLM-5.1",
      "capability": {
       "contextWindow": 202800
      }
     }
    ],
    "vendorDisplayName": "Novita AI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://novita.ai",
    "apiFormat": "openai-completions"
   },
   {
    "id": "nvidia",
    "displayName": "Nvidia",
    "baseUrl": "https://integrate.api.nvidia.com/v1",
    "models": [
     {
      "id": "moonshotai/kimi-k2.5",
      "displayName": "Kimi K2.5",
      "capability": {
       "contextWindow": 131072
      }
     }
    ],
    "vendorDisplayName": "Nvidia",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://build.nvidia.com/settings/api-keys",
    "apiFormat": "openai-completions"
   },
   {
    "id": "pipellm",
    "displayName": "PIPELLM",
    "baseUrl": "https://cc-api.pipellm.ai",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "claude-opus-5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "claude-sonnet-5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "claude-haiku-4-5-20251001",
      "capability": {
       "contextWindow": 200000
      }
     }
    ],
    "vendorDisplayName": "PIPELLM",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://code.pipellm.ai/login?ref=uvw650za",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "e-flowcode",
    "displayName": "E-FlowCode",
    "baseUrl": "https://e-flowcode.cc/v1",
    "models": [
     {
      "id": "gpt-5.3-codex",
      "displayName": "gpt-5.3-codex",
      "capability": {
       "contextWindow": 200000,
       "maxTokens": 32000
      }
     },
     {
      "id": "gpt-5.6-sol",
      "displayName": "gpt-5.6-sol"
     },
     {
      "id": "gpt-5.2-codex",
      "displayName": "gpt-5.2-codex"
     },
     {
      "id": "gpt-5.2",
      "displayName": "gpt-5.2"
     }
    ],
    "vendorDisplayName": "E-FlowCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://e-flowcode.cc",
    "apiFormat": "openai-responses"
   },
   {
    "id": "aws-bedrock",
    "displayName": "AWS Bedrock",
    "baseUrl": "https://bedrock-runtime.us-west-2.amazonaws.com",
    "models": [
     {
      "id": "anthropic.claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "anthropic.claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "contextWindow": 1000000
      }
     },
     {
      "id": "anthropic.claude-haiku-4-5-20251022-v1:0",
      "displayName": "Claude Haiku 4.5",
      "capability": {
       "contextWindow": 200000
      }
     }
    ],
    "vendorDisplayName": "AWS Bedrock",
    "category": "CloudProvider",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aws.amazon.com/bedrock/",
    "apiFormat": "bedrock-converse-stream"
   },
   {
    "id": "jiekou-ai",
    "displayName": "JieKou AI",
    "baseUrl": "https://api.jiekou.ai/openai/v1",
    "models": [
     {
      "id": "claude-fable-5",
      "displayName": "Claude Fable 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000
      }
     }
    ],
    "vendorDisplayName": "JieKou AI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://jiekou.ai/settings/key-management",
    "apiFormat": "openai-completions"
   },
   {
    "id": "aicodewith",
    "displayName": "AICodeWith",
    "baseUrl": "https://api.aicodewith.ai/chatgpt/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "AICodeWith",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aicodewith.ai/login?tab=register",
    "apiFormat": "openai-responses"
   }
  ],
  "hermes": [
   {
    "id": "kimi",
    "displayName": "Kimi",
    "baseUrl": "https://api.moonshot.cn/v1",
    "models": [
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code"
     },
     {
      "id": "kimi-k3",
      "displayName": "Kimi K3"
     }
    ],
    "vendorDisplayName": "Kimi",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.kimi.com?aff=cc-switch",
    "apiFormat": "chat_completions"
   },
   {
    "id": "kimi-for-coding",
    "displayName": "Kimi For Coding",
    "baseUrl": "https://api.kimi.com/coding",
    "models": [
     {
      "id": "kimi-for-coding",
      "displayName": "Kimi For Coding"
     }
    ],
    "vendorDisplayName": "Kimi For Coding",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://www.kimi.com/code/?aff=cc-switch",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "packycode",
    "displayName": "PackyCode",
    "baseUrl": "https://www.packyapi.ai",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "PackyCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.packyapi.ai/register?aff=cc-switch",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "zetaapi",
    "displayName": "ZetaAPI",
    "baseUrl": "https://api.zetaapi.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "ZetaAPI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://zetaapi.ai/go/u117",
    "apiFormat": "chat_completions"
   },
   {
    "id": "apinebula",
    "displayName": "APINebula",
    "baseUrl": "https://apinebula.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "APINebula",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://apinebula.ai/VjM74M",
    "apiFormat": "chat_completions"
   },
   {
    "id": "aicodemirror",
    "displayName": "AICodeMirror",
    "baseUrl": "https://api.aicodemirror.ai/api/claudecode",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "AICodeMirror",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.aicodemirror.ai/register?invitecode=9915W3",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "fennoai",
    "displayName": "FennoAI",
    "baseUrl": "https://api.fenno.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "FennoAI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://api.fenno.ai/register?redirect=/purchase?tab=subscription%26group=16&aff=P9MR3D3PLCNL",
    "apiFormat": "chat_completions"
   },
   {
    "id": "runapi",
    "displayName": "RunAPI",
    "baseUrl": "https://runapi.host",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "RunAPI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://runapi.host/register?aff=iOKB",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "shengsuanyun",
    "displayName": "Shengsuanyun",
    "baseUrl": "https://router.shengsuanyun.com/api/v1",
    "models": [
     {
      "id": "openai/gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "Shengsuanyun",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.shengsuanyun.com/?from=CH_4HHXMRYF",
    "apiFormat": "chat_completions"
   },
   {
    "id": "aigocode",
    "displayName": "AIGoCode",
    "baseUrl": "https://api.aigocode.app",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "AIGoCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aigocode.app/invite/CC-SWITCH",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "qiniu",
    "displayName": "Qiniu",
    "baseUrl": "https://api.qnaigc.com/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "Qiniu",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://s.qiniu.com/nMvAvy",
    "apiFormat": "chat_completions"
   },
   {
    "id": "aicoding",
    "displayName": "AICoding",
    "baseUrl": "https://api.aicoding.inc",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "AICoding",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aicoding.inc/i/CCSWITCH",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "subrouter",
    "displayName": "SubRouter",
    "baseUrl": "https://subrouter.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "SubRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://subrouter.ai/register?aff=l3ri",
    "apiFormat": "chat_completions"
   },
   {
    "id": "apikey-fun",
    "displayName": "APIKEY.FUN",
    "baseUrl": "https://api.apikey.fan",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "APIKEY.FUN",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://apikey.fan/register?aff=CCSwitch",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "9527code",
    "displayName": "9527CODE",
    "baseUrl": "https://9527.codes",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "9527CODE",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://9527.codes/register?aff=e5zI",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "code0",
    "displayName": "Code0",
    "baseUrl": "https://code0.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "Code0",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://code0.ai/agent/register/B2XHxGjGmRvqgznY",
    "apiFormat": "chat_completions"
   },
   {
    "id": "teamorouter",
    "displayName": "TeamoRouter",
    "baseUrl": "https://api.teamorouter.cn/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "TeamoRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://teamorouter.cn/?utm_source=cc_switch&utm_medium=referral&utm_campaign=ai_directory",
    "apiFormat": "chat_completions"
   },
   {
    "id": "ppio",
    "displayName": "PPIO",
    "baseUrl": "https://api.ppio.com/openai/v1",
    "models": [
     {
      "id": "deepseek/deepseek-v4-flash-0731",
      "displayName": "Deepseek V4 Flash 0731"
     }
    ],
    "vendorDisplayName": "PPIO",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://ppio.com/activity/ccswitch",
    "apiFormat": "chat_completions"
   },
   {
    "id": "claudecn",
    "displayName": "ClaudeCN",
    "baseUrl": "https://claudecn.top",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "ClaudeCN",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://claudecn.ai/register?aff=HEL9",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "火山-agent-plan",
    "displayName": "火山 Agent Plan",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/plan",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "Ark Code Latest"
     }
    ],
    "vendorDisplayName": "火山 Agent Plan",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.volcengine.com/activity/agentplan?ac=MMAP8JTTCAQ2&rc=6J6FV5N2&utm_source=OWO&utm_medium=devrel-1&utm_campaign=hw&utm_term=ccswitch&utm_content=hw",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "火山-coding-plan",
    "displayName": "火山 Coding Plan",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/coding",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "Ark Code Latest"
     }
    ],
    "vendorDisplayName": "火山 Coding Plan",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://www.volcengine.com/activity/codingplan?ac=MMAP8JTTCAQ2&rc=6J6FV5N2&utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "byteplus",
    "displayName": "BytePlus",
    "baseUrl": "https://ark.ap-southeast.bytepluses.com/api/coding",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "Ark Code Latest"
     }
    ],
    "vendorDisplayName": "BytePlus",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.byteplus.com/en/product/modelark?utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "volcengine-doubao",
    "displayName": "Volcengine Doubao",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/compatible",
    "models": [
     {
      "id": "doubao-seed-2-1-pro-260628",
      "displayName": "Doubao Seed 2.1 Pro"
     }
    ],
    "vendorDisplayName": "Volcengine Doubao",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey?apikey=%7B%7D&utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "siliconflow",
    "displayName": "SiliconFlow",
    "baseUrl": "https://api.siliconflow.cn/v1",
    "models": [
     {
      "id": "Pro/MiniMaxAI/MiniMax-M2.5",
      "displayName": "Pro / MiniMax M2.5"
     }
    ],
    "vendorDisplayName": "SiliconFlow",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://cloud.siliconflow.cn/i/YflgU2Ve",
    "apiFormat": "chat_completions"
   },
   {
    "id": "siliconflow-en",
    "displayName": "SiliconFlow en",
    "baseUrl": "https://api.siliconflow.com/v1",
    "models": [
     {
      "id": "MiniMaxAI/MiniMax-M3",
      "displayName": "MiniMax M3"
     }
    ],
    "vendorDisplayName": "SiliconFlow en",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://cloud.siliconflow.cn/i/YflgU2Ve",
    "apiFormat": "chat_completions"
   },
   {
    "id": "a6api",
    "displayName": "A6API",
    "baseUrl": "https://api.a6api.com/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "A6API",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://a6api.com/register?aff=AqNr",
    "apiFormat": "chat_completions"
   },
   {
    "id": "compshare",
    "displayName": "Compshare",
    "baseUrl": "https://api.modelverse.cn/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "Compshare",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.compshare.cn/coding-plan?ytag=GPU_YY_YX_git_cc-switch",
    "apiFormat": "chat_completions"
   },
   {
    "id": "compshare-coding-plan",
    "displayName": "Compshare Coding Plan",
    "baseUrl": "https://cp.compshare.cn/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "Compshare Coding Plan",
    "category": "Aggregator",
    "accessChannel": "CodingPlan",
    "market": "Unspecified",
    "documentationUrl": "https://www.compshare.cn/coding-plan?ytag=GPU_YY_YX_git_cc-switch",
    "apiFormat": "chat_completions"
   },
   {
    "id": "ccsub",
    "displayName": "CCSub",
    "baseUrl": "https://www.ccsub.net/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "CCSub",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.ccsub.net/register?ref=Y6Z8DXEA",
    "apiFormat": "chat_completions"
   },
   {
    "id": "sssaicode",
    "displayName": "SSSAiCode",
    "baseUrl": "https://node-hk.sssaicodeapi.com/api",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "SSSAiCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://sssaicodeapi.com/register?ref=DCP0SM",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "soleapi",
    "displayName": "SoleAPI",
    "baseUrl": "https://soleapi.com",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "SoleAPI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://soleapi.com/r/ccswitch",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "micu",
    "displayName": "Micu",
    "baseUrl": "https://www.micuapi.ai",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "Micu",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.micuapi.ai/register?aff=aOYQ",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "rightcode",
    "displayName": "RightCode",
    "baseUrl": "https://www.rightapi.ai/claude",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "RightCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.rightapi.ai/register?aff=CCSWITCH",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "etok-ai",
    "displayName": "ETok.ai",
    "baseUrl": "https://api.etok.ai",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "ETok.ai",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://etok.ai",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "cubence",
    "displayName": "Cubence",
    "baseUrl": "https://api.cubence.com",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "Cubence",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://cubence.com/signup?code=CCSWITCH&source=ccs",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "crazyrouter",
    "displayName": "CrazyRouter",
    "baseUrl": "https://cn.crazyrouter.com",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "CrazyRouter",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.crazyrouter.com/register?aff=OZcm&ref=cc-switch",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "dmxapi",
    "displayName": "DMXAPI",
    "baseUrl": "https://www.dmxapi.cn/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "DMXAPI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.dmxapi.cn",
    "apiFormat": "chat_completions"
   },
   {
    "id": "sudocode-chat",
    "displayName": "SudoCode.chat",
    "baseUrl": "https://api.sudocode.chat/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "SudoCode.chat",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://sudocode.chat/sign-up?aff=CC-SWITCH&utm_source=cc-switch&utm_medium=sponsor&utm_campaign=ccswitch",
    "apiFormat": "codex_responses"
   },
   {
    "id": "sudocode-us",
    "displayName": "SudoCode.us",
    "baseUrl": "https://sudocode.us/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "SudoCode.us",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://sudocode.us",
    "apiFormat": "codex_responses"
   },
   {
    "id": "xycai",
    "displayName": "XycAi",
    "baseUrl": "https://apicdn.xycai.us/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "XycAi",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://xycai.us/register?aff=Uhu9",
    "apiFormat": "chat_completions"
   },
   {
    "id": "amux",
    "displayName": "Amux",
    "baseUrl": "https://api.amux.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "Amux",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://amux.ai",
    "apiFormat": "chat_completions"
   },
   {
    "id": "atlascloud",
    "displayName": "AtlasCloud",
    "baseUrl": "https://api.atlascloud.ai/v1",
    "models": [
     {
      "id": "zai-org/glm-5.1",
      "displayName": "GLM 5.1"
     }
    ],
    "vendorDisplayName": "AtlasCloud",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.atlascloud.ai/console/coding-plan",
    "apiFormat": "chat_completions"
   },
   {
    "id": "openrouter",
    "displayName": "OpenRouter",
    "baseUrl": "https://openrouter.ai/api/v1",
    "models": [
     {
      "id": "anthropic/claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "anthropic/claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5"
     },
     {
      "id": "openai/gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     },
     {
      "id": "google/gemini-3.6-flash",
      "displayName": "Gemini 3.6 Flash"
     }
    ],
    "vendorDisplayName": "OpenRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://openrouter.ai/keys",
    "apiFormat": "chat_completions"
   },
   {
    "id": "deepseek",
    "displayName": "DeepSeek",
    "baseUrl": "https://api.deepseek.com",
    "models": [
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro"
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash"
     }
    ],
    "vendorDisplayName": "DeepSeek",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.deepseek.com/api_keys",
    "apiFormat": "chat_completions"
   },
   {
    "id": "together-ai",
    "displayName": "Together AI",
    "baseUrl": "https://api.together.xyz/v1",
    "models": [
     {
      "id": "Qwen/Qwen3-Coder-480B-A35B-Instruct",
      "displayName": "Qwen3 Coder 480B"
     },
     {
      "id": "deepseek-ai/DeepSeek-V3.2",
      "displayName": "DeepSeek V3.2"
     },
     {
      "id": "meta-llama/Llama-4-Maverick-17B-128E-Instruct-FP8",
      "displayName": "Llama 4 Maverick"
     }
    ],
    "vendorDisplayName": "Together AI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://api.together.ai/settings/api-keys",
    "apiFormat": "chat_completions"
   },
   {
    "id": "zhipu-glm",
    "displayName": "Zhipu GLM",
    "baseUrl": "https://open.bigmodel.cn/api/coding/paas/v4",
    "models": [
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1"
     }
    ],
    "vendorDisplayName": "Zhipu GLM",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.bigmodel.cn/claude-code?ic=RRVJPB5SII",
    "apiFormat": "chat_completions"
   },
   {
    "id": "zhipu-glm-en",
    "displayName": "Zhipu GLM en",
    "baseUrl": "https://api.z.ai/api/coding/paas/v4",
    "models": [
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1"
     }
    ],
    "vendorDisplayName": "Zhipu GLM en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://z.ai/subscribe?ic=8JVLJQFSKB",
    "apiFormat": "chat_completions"
   },
   {
    "id": "tencent-token-plan",
    "displayName": "Tencent Token Plan",
    "baseUrl": "https://api.lkeap.cloud.tencent.com/plan/v3",
    "models": [
     {
      "id": "tc-code-latest",
      "displayName": "Auto"
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash"
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro"
     },
     {
      "id": "minimax-m2.7",
      "displayName": "MiniMax M2.7"
     },
     {
      "id": "glm-5",
      "displayName": "GLM-5"
     },
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1"
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2"
     },
     {
      "id": "hy3",
      "displayName": "Hy3"
     },
     {
      "id": "hy3-preview",
      "displayName": "Hy3 Preview"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan",
    "apiFormat": "chat_completions"
   },
   {
    "id": "tencent-token-plan-intl",
    "displayName": "Tencent Token Plan (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto"
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2"
     },
     {
      "id": "kimi-k2.6",
      "displayName": "Kimi K2.6"
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro"
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash"
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax M3"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan",
    "apiFormat": "chat_completions"
   },
   {
    "id": "tencent-token-plan-enterprise-pro",
    "displayName": "Tencent Token Plan Enterprise Pro",
    "baseUrl": "https://tokenhub.tencentmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto"
     },
     {
      "id": "glm-5.3",
      "displayName": "GLM-5.3"
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2"
     },
     {
      "id": "glm-5",
      "displayName": "GLM-5"
     },
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1"
     },
     {
      "id": "glm-5-turbo",
      "displayName": "GLM-5 Turbo"
     },
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code"
     },
     {
      "id": "kimi-k2.7-code-highspeed",
      "displayName": "Kimi K2.7 Code HighSpeed"
     },
     {
      "id": "kimi-k2.6",
      "displayName": "Kimi K2.6"
     },
     {
      "id": "minimax-m2.7",
      "displayName": "MiniMax M2.7"
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax M3"
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash"
     },
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro"
     },
     {
      "id": "deepseek-v4-flash-0731",
      "displayName": "DeepSeek V4 Flash 0731 GA"
     },
     {
      "id": "deepseek-v4-pro-0813",
      "displayName": "DeepSeek V4 Pro 0813 GA"
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash Official"
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro Official"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Pro",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan-e",
    "apiFormat": "chat_completions"
   },
   {
    "id": "tencent-token-plan-enterprise-pro-intl",
    "displayName": "Tencent Token Plan Enterprise Pro (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto"
     },
     {
      "id": "glm-5.3",
      "displayName": "GLM-5.3"
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2"
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax M3"
     },
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code"
     },
     {
      "id": "kimi-k2.7-code-highspeed",
      "displayName": "Kimi K2.7 Code HighSpeed"
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash"
     },
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro"
     },
     {
      "id": "deepseek-v4-flash-0731",
      "displayName": "DeepSeek V4 Flash 0731 GA"
     },
     {
      "id": "deepseek-v4-pro-0813",
      "displayName": "DeepSeek V4 Pro 0813 GA"
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash Official"
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro Official"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Pro (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan-e",
    "apiFormat": "chat_completions"
   },
   {
    "id": "tencent-token-plan-enterprise-lite",
    "displayName": "Tencent Token Plan Enterprise Lite",
    "baseUrl": "https://tokenhub.tencentmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Lite",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan-e",
    "apiFormat": "chat_completions"
   },
   {
    "id": "tencent-token-plan-enterprise-lite-intl",
    "displayName": "Tencent Token Plan Enterprise Lite (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto"
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Lite (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan-e",
    "apiFormat": "chat_completions"
   },
   {
    "id": "baidu-qianfan-token-plan",
    "displayName": "Baidu Qianfan Token Plan",
    "baseUrl": "https://qianfan.baidubce.com/v2/tokenplan/personal",
    "models": [
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro"
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash"
     },
     {
      "id": "deepseek-v4-flash-0731",
      "displayName": "DeepSeek V4 Flash 0731"
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2"
     },
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1"
     },
     {
      "id": "kimi-k2.6",
      "displayName": "Kimi K2.6"
     }
    ],
    "vendorDisplayName": "Baidu Qianfan Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.bce.baidu.com/qianfan/resource/token-plan",
    "apiFormat": "chat_completions"
   },
   {
    "id": "千问ai平台",
    "displayName": "千问AI平台",
    "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max"
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash"
     }
    ],
    "vendorDisplayName": "千问AI平台",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.qianwenai.com/home/api-keys?utm_content=g_20000002972",
    "apiFormat": "chat_completions"
   },
   {
    "id": "千问ai平台-coding-plan",
    "displayName": "千问AI平台 Coding Plan",
    "baseUrl": "https://coding.dashscope.aliyuncs.com/apps/anthropic",
    "models": [
     {
      "id": "qwen3-coder-plus",
      "displayName": "Qwen3 Coder Plus"
     },
     {
      "id": "qwen3-max",
      "displayName": "Qwen3 Max"
     }
    ],
    "vendorDisplayName": "千问AI平台 Coding Plan",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://bailian.console.aliyun.com",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "千问ai平台-token-plan",
    "displayName": "千问AI平台 Token Plan",
    "baseUrl": "https://token-plan.cn-beijing.maas.aliyuncs.com/apps/anthropic",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max"
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash"
     }
    ],
    "vendorDisplayName": "千问AI平台 Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://platform.qianwenai.com/home/api-keys?utm_content=g_20000002978",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "qwencloud",
    "displayName": "QwenCloud",
    "baseUrl": "https://dashscope-intl.aliyuncs.com/apps/anthropic",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max"
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash"
     },
     {
      "id": "qwen3.7-max",
      "displayName": "Qwen3.7 Max"
     }
    ],
    "vendorDisplayName": "QwenCloud",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys?utm_content=g_20000002975",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "qwencloud-for-coding",
    "displayName": "QwenCloud For Coding",
    "baseUrl": "https://coding-intl.dashscope.aliyuncs.com/apps/anthropic",
    "models": [
     {
      "id": "qwen3.7-plus",
      "displayName": "Qwen3.7 Plus"
     },
     {
      "id": "qwen3.6-plus",
      "displayName": "Qwen3.6 Plus"
     },
     {
      "id": "qwen3-coder-plus",
      "displayName": "Qwen3 Coder Plus"
     }
    ],
    "vendorDisplayName": "QwenCloud For Coding",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "qwencloud-token-plan",
    "displayName": "QwenCloud Token Plan",
    "baseUrl": "https://token-plan.ap-southeast-1.maas.aliyuncs.com/apps/anthropic",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max"
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash"
     },
     {
      "id": "qwen3.7-max",
      "displayName": "Qwen3.7 Max"
     },
     {
      "id": "qwen3.7-plus",
      "displayName": "Qwen3.7 Plus"
     },
     {
      "id": "qwen3.6-plus",
      "displayName": "Qwen3.6 Plus"
     },
     {
      "id": "qwen3.6-flash",
      "displayName": "Qwen3.6 Flash"
     }
    ],
    "vendorDisplayName": "QwenCloud Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys?utm_content=g_20000002981",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "stepfun",
    "displayName": "StepFun",
    "baseUrl": "https://api.stepfun.ai/v1",
    "models": [
     {
      "id": "step-3.5-flash",
      "displayName": "Step 3.5 Flash"
     }
    ],
    "vendorDisplayName": "StepFun",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.stepfun.ai/interface-key",
    "apiFormat": "chat_completions"
   },
   {
    "id": "modelscope",
    "displayName": "ModelScope",
    "baseUrl": "https://api-inference.modelscope.cn/v1",
    "models": [
     {
      "id": "ZhipuAI/GLM-5.2",
      "displayName": "ZhipuAI / GLM-5.2"
     }
    ],
    "vendorDisplayName": "ModelScope",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://modelscope.cn",
    "apiFormat": "chat_completions"
   },
   {
    "id": "longcat",
    "displayName": "Longcat",
    "baseUrl": "https://api.longcat.chat/openai/v1",
    "models": [
     {
      "id": "LongCat-2.0",
      "displayName": "LongCat 2.0"
     }
    ],
    "vendorDisplayName": "Longcat",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://longcat.chat/platform/api_keys",
    "apiFormat": "chat_completions"
   },
   {
    "id": "minimax",
    "displayName": "MiniMax",
    "baseUrl": "https://api.minimaxi.com/v1",
    "models": [
     {
      "id": "MiniMax-M3",
      "displayName": "MiniMax M3"
     }
    ],
    "vendorDisplayName": "MiniMax",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.minimaxi.com/subscribe/coding-plan",
    "apiFormat": "chat_completions"
   },
   {
    "id": "minimax-en",
    "displayName": "MiniMax en",
    "baseUrl": "https://api.minimax.io/v1",
    "models": [
     {
      "id": "MiniMax-M3",
      "displayName": "MiniMax M3"
     }
    ],
    "vendorDisplayName": "MiniMax en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.minimax.io/subscribe/coding-plan",
    "apiFormat": "chat_completions"
   },
   {
    "id": "bailing",
    "displayName": "BaiLing",
    "baseUrl": "https://api.tbox.cn/api/anthropic",
    "models": [
     {
      "id": "Ling-2.5-1T",
      "displayName": "Ling 2.5 1T"
     }
    ],
    "vendorDisplayName": "BaiLing",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://alipaytbox.yuque.com/sxs0ba/ling/get_started",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "aihubmix",
    "displayName": "AiHubMix",
    "baseUrl": "https://aihubmix.com/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "AiHubMix",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aihubmix.com",
    "apiFormat": "chat_completions"
   },
   {
    "id": "cherryin",
    "displayName": "CherryIN",
    "baseUrl": "https://open.cherryin.net",
    "models": [
     {
      "id": "anthropic/claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     }
    ],
    "vendorDisplayName": "CherryIN",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://open.cherryin.ai/console/token",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "e-flowcode",
    "displayName": "E-FlowCode",
    "baseUrl": "https://e-flowcode.cc",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "E-FlowCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://e-flowcode.cc",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "therouter",
    "displayName": "TheRouter",
    "baseUrl": "https://api.therouter.ai/v1",
    "models": [
     {
      "id": "openai/gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     },
     {
      "id": "openai/gpt-5.4-mini",
      "displayName": "GPT-5.4 mini"
     },
     {
      "id": "openai/gpt-5.4-nano",
      "displayName": "GPT-5.4 nano"
     }
    ],
    "vendorDisplayName": "TheRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://dashboard.therouter.ai",
    "apiFormat": "chat_completions"
   },
   {
    "id": "novita-ai",
    "displayName": "Novita AI",
    "baseUrl": "https://api.novita.ai/v3/openai",
    "models": [
     {
      "id": "zai-org/glm-5.1",
      "displayName": "Zai-Org / GLM-5.1"
     }
    ],
    "vendorDisplayName": "Novita AI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://novita.ai",
    "apiFormat": "chat_completions"
   },
   {
    "id": "nvidia",
    "displayName": "Nvidia",
    "baseUrl": "https://integrate.api.nvidia.com",
    "models": [
     {
      "id": "moonshotai/kimi-k2.5",
      "displayName": "Moonshot Kimi K2.5"
     }
    ],
    "vendorDisplayName": "Nvidia",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://build.nvidia.com/settings/api-keys",
    "apiFormat": "chat_completions"
   },
   {
    "id": "pipellm",
    "displayName": "PIPELLM",
    "baseUrl": "https://cc-api.pipellm.ai",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5"
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5"
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "Claude Haiku 4.5"
     }
    ],
    "vendorDisplayName": "PIPELLM",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://code.pipellm.ai/login?ref=uvw650za",
    "apiFormat": "anthropic_messages"
   },
   {
    "id": "xiaomi-mimo",
    "displayName": "Xiaomi MiMo",
    "baseUrl": "https://api.xiaomimimo.com/v1",
    "models": [
     {
      "id": "mimo-v2.5-pro",
      "displayName": "MiMo v2.5 Pro"
     }
    ],
    "vendorDisplayName": "Xiaomi MiMo",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.xiaomimimo.com/#/console/api-keys",
    "apiFormat": "chat_completions"
   },
   {
    "id": "xiaomi-mimo-token-plan-china",
    "displayName": "Xiaomi MiMo Token Plan (China)",
    "baseUrl": "https://token-plan-cn.xiaomimimo.com/v1",
    "models": [
     {
      "id": "mimo-v2.5-pro",
      "displayName": "MiMo v2.5 Pro"
     },
     {
      "id": "mimo-v2.5",
      "displayName": "MiMo v2.5"
     }
    ],
    "vendorDisplayName": "Xiaomi MiMo Token Plan (China)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://platform.xiaomimimo.com/#/console/plan-manage",
    "apiFormat": "chat_completions"
   },
   {
    "id": "jiekou-ai",
    "displayName": "JieKou AI",
    "baseUrl": "https://api.jiekou.ai/openai/v1",
    "models": [
     {
      "id": "claude-fable-5",
      "displayName": "Claude Fable 5"
     }
    ],
    "vendorDisplayName": "JieKou AI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://jiekou.ai/settings/key-management",
    "apiFormat": "chat_completions"
   },
   {
    "id": "aicodewith",
    "displayName": "AICodeWith",
    "baseUrl": "https://api.aicodewith.ai/chatgpt/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol"
     }
    ],
    "vendorDisplayName": "AICodeWith",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aicodewith.ai/login?tab=register",
    "apiFormat": "codex_responses"
   }
  ],
  "pi-coding-agent": [
   {
    "id": "kimi",
    "displayName": "Kimi",
    "baseUrl": "https://api.moonshot.cn/v1",
    "models": [
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144,
       "thinkingLevelMap": {
        "off": null
       }
      }
     },
     {
      "id": "kimi-k3",
      "displayName": "Kimi K3",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072,
       "thinkingLevelMap": {
        "off": null,
        "minimal": null,
        "low": "low",
        "medium": null,
        "high": "high",
        "xhigh": null,
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "Kimi",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.kimi.com/console/api-keys?aff=cc-switch",
    "apiFormat": "openai-completions"
   },
   {
    "id": "kimi-for-coding",
    "displayName": "Kimi For Coding",
    "baseUrl": "https://api.kimi.com/coding",
    "models": [
     {
      "id": "kimi-for-coding",
      "displayName": "Kimi For Coding",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 32768
      }
     }
    ],
    "vendorDisplayName": "Kimi For Coding",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://platform.kimi.com/console/api-keys?aff=cc-switch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "packycode",
    "displayName": "PackyCode",
    "baseUrl": "https://www.packyapi.ai",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "PackyCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.packyapi.ai/register?aff=cc-switch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "zetaapi",
    "displayName": "ZetaAPI",
    "baseUrl": "https://api.zetaapi.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 272000,
       "maxTokens": 128000
      }
     }
    ],
    "vendorDisplayName": "ZetaAPI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://zetaapi.ai/go/u117",
    "apiFormat": "openai-completions"
   },
   {
    "id": "apinebula",
    "displayName": "APINebula",
    "baseUrl": "https://apinebula.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 272000,
       "maxTokens": 128000
      }
     }
    ],
    "vendorDisplayName": "APINebula",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://apinebula.ai/VjM74M",
    "apiFormat": "openai-completions"
   },
   {
    "id": "aicodemirror",
    "displayName": "AICodeMirror",
    "baseUrl": "https://api.aicodemirror.ai/api/claudecode",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "AICodeMirror",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.aicodemirror.ai/register?invitecode=9915W3",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "fennoai",
    "displayName": "FennoAI",
    "baseUrl": "https://api.fenno.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 272000,
       "maxTokens": 128000
      }
     }
    ],
    "vendorDisplayName": "FennoAI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://api.fenno.ai/register?redirect=/purchase?tab=subscription%26group=16&aff=P9MR3D3PLCNL",
    "apiFormat": "openai-completions"
   },
   {
    "id": "runapi",
    "displayName": "RunAPI",
    "baseUrl": "https://runapi.co",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5 (latest)",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 200000,
       "maxTokens": 64000
      }
     }
    ],
    "vendorDisplayName": "RunAPI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://runapi.co/register?aff=iOKB",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "shengsuanyun",
    "displayName": "Shengsuanyun",
    "baseUrl": "https://router.shengsuanyun.com/api",
    "models": [
     {
      "id": "anthropic/claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "Shengsuanyun",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.shengsuanyun.com/?from=CH_4HHXMRYF",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "aigocode",
    "displayName": "AIGoCode",
    "baseUrl": "https://api.aigocode.app",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "AIGoCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aigocode.app/invite/CC-SWITCH",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "qiniu",
    "displayName": "Qiniu",
    "baseUrl": "https://api.qnaigc.com/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 272000,
       "maxTokens": 128000
      }
     }
    ],
    "vendorDisplayName": "Qiniu",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://s.qiniu.com/nMvAvy",
    "apiFormat": "openai-completions"
   },
   {
    "id": "aicoding",
    "displayName": "AICoding",
    "baseUrl": "https://api.aicoding.inc",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "AICoding",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aicoding.inc/i/CCSWITCH",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "subrouter",
    "displayName": "SubRouter",
    "baseUrl": "https://subrouter.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 272000,
       "maxTokens": 128000
      }
     }
    ],
    "vendorDisplayName": "SubRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://subrouter.ai/register?aff=l3ri",
    "apiFormat": "openai-completions"
   },
   {
    "id": "apikey-fun",
    "displayName": "APIKEY.FUN",
    "baseUrl": "https://api.apikey.fan",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5 (latest)",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 200000,
       "maxTokens": 64000
      }
     }
    ],
    "vendorDisplayName": "APIKEY.FUN",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://apikey.fan/register?aff=CCSwitch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "9527code",
    "displayName": "9527CODE",
    "baseUrl": "https://9527.codes",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5 (latest)",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 200000,
       "maxTokens": 64000
      }
     }
    ],
    "vendorDisplayName": "9527CODE",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://9527.codes/register?aff=e5zI",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "code0",
    "displayName": "Code0",
    "baseUrl": "https://code0.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 272000,
       "maxTokens": 128000
      }
     }
    ],
    "vendorDisplayName": "Code0",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://code0.ai/agent/register/B2XHxGjGmRvqgznY",
    "apiFormat": "openai-completions"
   },
   {
    "id": "teamorouter",
    "displayName": "TeamoRouter",
    "baseUrl": "https://api.teamorouter.cn/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 272000,
       "maxTokens": 128000
      }
     }
    ],
    "vendorDisplayName": "TeamoRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://teamorouter.cn/?utm_source=cc_switch&utm_medium=referral&utm_campaign=ai_directory",
    "apiFormat": "openai-completions"
   },
   {
    "id": "ppio",
    "displayName": "PPIO",
    "baseUrl": "https://api.ppio.com/openai/v1",
    "models": [
     {
      "id": "deepseek/deepseek-v4-flash-0731",
      "displayName": "Deepseek V4 Flash 0731",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 393216,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "PPIO",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://ppio.com/activity/ccswitch",
    "apiFormat": "openai-completions"
   },
   {
    "id": "claudecn",
    "displayName": "ClaudeCN",
    "baseUrl": "https://claudecn.top",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-haiku-4-5",
      "displayName": "Claude Haiku 4.5 (latest)",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 200000,
       "maxTokens": 64000
      }
     }
    ],
    "vendorDisplayName": "ClaudeCN",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://claudecn.ai/register?aff=HEL9",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "火山agentplan",
    "displayName": "火山Agentplan",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/coding/v3",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "Ark Code Latest",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 128000,
       "maxTokens": 16384
      }
     }
    ],
    "vendorDisplayName": "火山Agentplan",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.volcengine.com/activity/codingplan?ac=MMAP8JTTCAQ2&rc=6J6FV5N2&utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": "openai-completions"
   },
   {
    "id": "byteplus",
    "displayName": "BytePlus",
    "baseUrl": "https://ark.ap-southeast.bytepluses.com/api/coding/v3",
    "models": [
     {
      "id": "ark-code-latest",
      "displayName": "Ark Code Latest",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 128000,
       "maxTokens": 16384
      }
     }
    ],
    "vendorDisplayName": "BytePlus",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.byteplus.com/en/product/modelark?utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": "openai-completions"
   },
   {
    "id": "volcengine-doubao",
    "displayName": "Volcengine Doubao",
    "baseUrl": "https://ark.cn-beijing.volces.com/api/v3",
    "models": [
     {
      "id": "doubao-seed-2-1-pro-260628",
      "displayName": "Doubao Seed 2.1 Pro",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 128000,
       "maxTokens": 16384
      }
     }
    ],
    "vendorDisplayName": "Volcengine Doubao",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey?apikey=%7B%7D&utm_campaign=hw&utm_content=ccswitch&utm_medium=devrel_tool_web&utm_source=OWO&utm_term=ccswitch",
    "apiFormat": "openai-completions"
   },
   {
    "id": "a6api",
    "displayName": "A6API",
    "baseUrl": "https://api.a6api.com/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 272000,
       "maxTokens": 128000
      }
     }
    ],
    "vendorDisplayName": "A6API",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://a6api.com/register?aff=AqNr",
    "apiFormat": "openai-completions"
   },
   {
    "id": "ccsub",
    "displayName": "CCSub",
    "baseUrl": "https://www.ccsub.net/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 272000,
       "maxTokens": 128000
      }
     }
    ],
    "vendorDisplayName": "CCSub",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.ccsub.net/register?ref=Y6Z8DXEA",
    "apiFormat": "openai-completions"
   },
   {
    "id": "sssaicode",
    "displayName": "SSSAiCode",
    "baseUrl": "https://node-hk.sssaicodeapi.com/api",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "SSSAiCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://sssaicodeapi.com/register?ref=DCP0SM",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "soleapi",
    "displayName": "SoleAPI",
    "baseUrl": "https://soleapi.com",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "Claude Haiku 4.5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 200000,
       "maxTokens": 64000
      }
     }
    ],
    "vendorDisplayName": "SoleAPI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://soleapi.com/r/ccswitch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "micu",
    "displayName": "Micu",
    "baseUrl": "https://www.micuapi.ai",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "Micu",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.micuapi.ai/register?aff=aOYQ",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "rightcode",
    "displayName": "RightCode",
    "baseUrl": "https://www.rightapi.ai/codex/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 272000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "off": "none",
        "minimal": null,
        "low": "low",
        "medium": "medium",
        "high": "high",
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "RightCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.rightapi.ai/register?aff=CCSWITCH",
    "apiFormat": "openai-responses"
   },
   {
    "id": "etok-ai",
    "displayName": "ETok.ai",
    "baseUrl": "https://api.etok.ai",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "ETok.ai",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://etok.ai",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "cubence",
    "displayName": "Cubence",
    "baseUrl": "https://api.cubence.com",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "Cubence",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://cubence.com/signup?code=CCSWITCH&source=ccs",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "crazyrouter",
    "displayName": "CrazyRouter",
    "baseUrl": "https://cn.crazyrouter.com",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "CrazyRouter",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.crazyrouter.com/register?aff=OZcm&ref=cc-switch",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "dmxapi",
    "displayName": "DMXAPI",
    "baseUrl": "https://www.dmxapi.cn",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "DMXAPI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.dmxapi.cn",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "sudocode-chat",
    "displayName": "SudoCode.chat",
    "baseUrl": "https://api.sudocode.chat/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 272000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "off": "none",
        "minimal": null,
        "low": "low",
        "medium": "medium",
        "high": "high",
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "SudoCode.chat",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://sudocode.chat/sign-up?aff=CC-SWITCH&utm_source=cc-switch&utm_medium=sponsor&utm_campaign=ccswitch",
    "apiFormat": "openai-responses"
   },
   {
    "id": "sudocode-us",
    "displayName": "SudoCode.us",
    "baseUrl": "https://sudocode.us/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 272000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "off": "none",
        "minimal": null,
        "low": "low",
        "medium": "medium",
        "high": "high",
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "SudoCode.us",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://sudocode.us",
    "apiFormat": "openai-responses"
   },
   {
    "id": "amux",
    "displayName": "Amux",
    "baseUrl": "https://api.amux.ai/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "GPT-5.6 Sol",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 272000,
       "maxTokens": 128000
      }
     }
    ],
    "vendorDisplayName": "Amux",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://amux.ai",
    "apiFormat": "openai-completions"
   },
   {
    "id": "atlascloud",
    "displayName": "AtlasCloud",
    "baseUrl": "https://api.atlascloud.ai/v1",
    "models": [
     {
      "id": "zai-org/glm-5.1",
      "displayName": "GLM 5.1",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "AtlasCloud",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://www.atlascloud.ai/console/coding-plan",
    "apiFormat": "openai-completions"
   },
   {
    "id": "deepseek",
    "displayName": "DeepSeek",
    "baseUrl": "https://api.deepseek.com/v1",
    "models": [
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "DeepSeek",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.deepseek.com/api_keys",
    "apiFormat": "openai-completions"
   },
   {
    "id": "zhipu-glm",
    "displayName": "Zhipu GLM",
    "baseUrl": "https://open.bigmodel.cn/api/coding/paas/v4",
    "models": [
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "Zhipu GLM",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://www.bigmodel.cn/claude-code?ic=RRVJPB5SII",
    "apiFormat": "openai-completions"
   },
   {
    "id": "zhipu-glm-en",
    "displayName": "Zhipu GLM en",
    "baseUrl": "https://api.z.ai/api/coding/paas/v4",
    "models": [
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "Zhipu GLM en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://z.ai/subscribe?ic=8JVLJQFSKB",
    "apiFormat": "openai-completions"
   },
   {
    "id": "千问ai平台",
    "displayName": "千问AI平台",
    "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 983616,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "千问AI平台",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.qianwenai.com/home/api-keys?utm_content=g_20000002972",
    "apiFormat": "openai-completions"
   },
   {
    "id": "千问ai平台-token-plan",
    "displayName": "千问AI平台 Token Plan",
    "baseUrl": "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 983616,
       "maxTokens": 131072
      }
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 983616,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "千问AI平台 Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://platform.qianwenai.com/home/api-keys?utm_content=g_20000002978",
    "apiFormat": "openai-completions"
   },
   {
    "id": "qwencloud",
    "displayName": "QwenCloud",
    "baseUrl": "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 983616,
       "maxTokens": 131072
      }
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 983616,
       "maxTokens": 131072
      }
     },
     {
      "id": "qwen3.7-max",
      "displayName": "Qwen3.7 Max",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "QwenCloud",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys?utm_content=g_20000002975",
    "apiFormat": "openai-completions"
   },
   {
    "id": "qwencloud-for-coding",
    "displayName": "QwenCloud For Coding",
    "baseUrl": "https://coding-intl.dashscope.aliyuncs.com/apps/anthropic",
    "models": [
     {
      "id": "qwen3.7-plus",
      "displayName": "Qwen3.7 Plus",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 65536
      }
     },
     {
      "id": "qwen3-coder-plus",
      "displayName": "Qwen3 Coder Plus",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 131072,
       "maxTokens": 65536
      }
     }
    ],
    "vendorDisplayName": "QwenCloud For Coding",
    "category": "ChinaOfficial",
    "accessChannel": "CodingPlan",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "qwencloud-token-plan",
    "displayName": "QwenCloud Token Plan",
    "baseUrl": "https://token-plan.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1",
    "models": [
     {
      "id": "qwen3.8-max",
      "displayName": "Qwen3.8 Max",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 983616,
       "maxTokens": 131072
      }
     },
     {
      "id": "qwen3.8-flash",
      "displayName": "Qwen3.8 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 983616,
       "maxTokens": 131072
      }
     },
     {
      "id": "qwen3.7-max",
      "displayName": "Qwen3.7 Max",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "QwenCloud Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://home.qwencloud.com/api-keys?utm_content=g_20000002981",
    "apiFormat": "openai-completions"
   },
   {
    "id": "stepfun",
    "displayName": "StepFun",
    "baseUrl": "https://api.stepfun.com/step_plan/v1",
    "models": [
     {
      "id": "step-3.5-flash-2603",
      "displayName": "Step 3.5 Flash 2603",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 256000,
       "maxTokens": 256000
      }
     },
     {
      "id": "step-3.5-flash",
      "displayName": "Step 3.5 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 256000,
       "maxTokens": 256000
      }
     }
    ],
    "vendorDisplayName": "StepFun",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.stepfun.com/interface-key",
    "apiFormat": "openai-completions"
   },
   {
    "id": "stepfun-en",
    "displayName": "StepFun en",
    "baseUrl": "https://api.stepfun.ai/step_plan/v1",
    "models": [
     {
      "id": "step-3.5-flash-2603",
      "displayName": "Step 3.5 Flash 2603",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 256000,
       "maxTokens": 256000
      }
     },
     {
      "id": "step-3.5-flash",
      "displayName": "Step 3.5 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 256000,
       "maxTokens": 256000
      }
     }
    ],
    "vendorDisplayName": "StepFun en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.stepfun.ai/interface-key",
    "apiFormat": "openai-completions"
   },
   {
    "id": "stepfun-step-plan",
    "displayName": "StepFun Step Plan",
    "baseUrl": "https://api.stepfun.com/step_plan/v1",
    "models": [
     {
      "id": "step-3.5-flash",
      "displayName": "Step 3.5 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 256000,
       "maxTokens": 256000
      }
     }
    ],
    "vendorDisplayName": "StepFun Step Plan",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.stepfun.com/interface-key",
    "apiFormat": "openai-completions"
   },
   {
    "id": "modelscope",
    "displayName": "ModelScope",
    "baseUrl": "https://api-inference.modelscope.cn/v1",
    "models": [
     {
      "id": "ZhipuAI/GLM-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "ModelScope",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://modelscope.cn/my/myaccesstoken",
    "apiFormat": "openai-completions"
   },
   {
    "id": "longcat",
    "displayName": "Longcat",
    "baseUrl": "https://api.longcat.chat/openai/v1",
    "models": [
     {
      "id": "LongCat-2.0",
      "displayName": "LongCat 2.0",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "Longcat",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://longcat.chat/platform/api_keys",
    "apiFormat": "openai-completions"
   },
   {
    "id": "minimax",
    "displayName": "MiniMax",
    "baseUrl": "https://api.minimaxi.com/v1",
    "models": [
     {
      "id": "MiniMax-M3",
      "displayName": "MiniMax-M3",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "MiniMax",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.minimaxi.com/subscribe/coding-plan",
    "apiFormat": "openai-completions"
   },
   {
    "id": "minimax-en",
    "displayName": "MiniMax en",
    "baseUrl": "https://api.minimax.io/v1",
    "models": [
     {
      "id": "MiniMax-M3",
      "displayName": "MiniMax-M3",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "MiniMax en",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.minimax.io/subscribe/coding-plan",
    "apiFormat": "openai-completions"
   },
   {
    "id": "bailing",
    "displayName": "BaiLing",
    "baseUrl": "https://api.tbox.cn/v1",
    "models": [
     {
      "id": "Ling-2.5-1T",
      "displayName": "Ling 2.5-1T",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 16384
      }
     }
    ],
    "vendorDisplayName": "BaiLing",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://alipaytbox.yuque.com/sxs0ba/ling/get_started",
    "apiFormat": "openai-completions"
   },
   {
    "id": "xiaomi-mimo",
    "displayName": "Xiaomi MiMo",
    "baseUrl": "https://api.xiaomimimo.com/v1",
    "models": [
     {
      "id": "mimo-v2.5-pro",
      "displayName": "MiMo-V2.5-Pro",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "mimo-v2.5",
      "displayName": "MiMo-V2.5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "Xiaomi MiMo",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://platform.xiaomimimo.com/#/console/api-keys",
    "apiFormat": "openai-completions"
   },
   {
    "id": "xiaomi-mimo-token-plan-china",
    "displayName": "Xiaomi MiMo Token Plan (China)",
    "baseUrl": "https://token-plan-cn.xiaomimimo.com/v1",
    "models": [
     {
      "id": "mimo-v2.5-pro",
      "displayName": "MiMo-V2.5-Pro",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "mimo-v2.5",
      "displayName": "MiMo-V2.5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "Xiaomi MiMo Token Plan (China)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://platform.xiaomimimo.com/#/console/plan-manage",
    "apiFormat": "openai-completions"
   },
   {
    "id": "opencode-go",
    "displayName": "OpenCode Go",
    "baseUrl": "https://opencode.ai/zen/go/v1",
    "models": [
     {
      "id": "glm-5.2",
      "displayName": "GLM 5.2",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 131072,
       "thinkingLevelMap": {
        "off": null,
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "xhigh": null,
        "max": "max"
       }
      }
     },
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144
      }
     },
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "mimo-v2.5-pro",
      "displayName": "MiMo-V2.5-Pro",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "OpenCode Go",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://opencode.ai/go?ref=2YTRG2NGTX",
    "apiFormat": "openai-completions"
   },
   {
    "id": "aihubmix",
    "displayName": "AiHubMix",
    "baseUrl": "https://aihubmix.com",
    "models": [
     {
      "id": "claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "AiHubMix",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aihubmix.com",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "cherryin",
    "displayName": "CherryIN",
    "baseUrl": "https://open.cherryin.net",
    "models": [
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "anthropic/claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "CherryIN",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://open.cherryin.ai/console/token",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "openrouter",
    "displayName": "OpenRouter",
    "baseUrl": "https://openrouter.ai/api",
    "models": [
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "anthropic/claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "OpenRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://openrouter.ai/keys",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "therouter",
    "displayName": "TheRouter",
    "baseUrl": "https://api.therouter.ai/v1",
    "models": [
     {
      "id": "anthropic/claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000
      }
     },
     {
      "id": "openai/gpt-5.3-codex",
      "displayName": "GPT-5.3 Codex",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 400000,
       "maxTokens": 128000
      }
     },
     {
      "id": "openai/gpt-5.2",
      "displayName": "GPT-5.2",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 400000,
       "maxTokens": 128000
      }
     },
     {
      "id": "google/gemini-3.6-flash",
      "displayName": "Gemini 3.6 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1048576,
       "maxTokens": 65536
      }
     },
     {
      "id": "qwen/qwen3-coder-480b",
      "displayName": "Qwen3 Coder 480B",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 262144,
       "maxTokens": 65536
      }
     }
    ],
    "vendorDisplayName": "TheRouter",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://dashboard.therouter.ai",
    "apiFormat": "openai-completions"
   },
   {
    "id": "novita-ai",
    "displayName": "Novita AI",
    "baseUrl": "https://api.novita.ai/openai",
    "models": [
     {
      "id": "zai-org/glm-5.1",
      "displayName": "GLM-5.1",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "Novita AI",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://novita.ai",
    "apiFormat": "openai-completions"
   },
   {
    "id": "nvidia",
    "displayName": "Nvidia",
    "baseUrl": "https://integrate.api.nvidia.com/v1",
    "models": [
     {
      "id": "moonshotai/kimi-k2.5",
      "displayName": "Kimi K2.5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144
      }
     }
    ],
    "vendorDisplayName": "Nvidia",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://build.nvidia.com/settings/api-keys",
    "apiFormat": "openai-completions"
   },
   {
    "id": "pipellm",
    "displayName": "PIPELLM",
    "baseUrl": "https://cc-api.pipellm.ai",
    "models": [
     {
      "id": "claude-opus-5",
      "displayName": "claude-opus-5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-sonnet-5",
      "displayName": "claude-sonnet-5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "claude-haiku-4-5-20251001",
      "displayName": "claude-haiku-4-5-20251001",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 200000,
       "maxTokens": 64000
      }
     }
    ],
    "vendorDisplayName": "PIPELLM",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://code.pipellm.ai/login?ref=uvw650za",
    "apiFormat": "anthropic-messages"
   },
   {
    "id": "aicodewith",
    "displayName": "AICodeWith",
    "baseUrl": "https://api.aicodewith.ai/chatgpt/v1",
    "models": [
     {
      "id": "gpt-5.6-sol",
      "displayName": "gpt-5.6-sol",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 272000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "off": "none",
        "minimal": null,
        "low": "low",
        "medium": "medium",
        "high": "high",
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "AICodeWith",
    "category": "Aggregator",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aicodewith.ai/login?tab=register",
    "apiFormat": "openai-responses"
   },
   {
    "id": "e-flowcode",
    "displayName": "E-FlowCode",
    "baseUrl": "https://e-flowcode.cc/v1",
    "models": [
     {
      "id": "gpt-5.2-codex",
      "displayName": "gpt-5.2-codex",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 400000,
       "maxTokens": 128000
      }
     },
     {
      "id": "gpt-5.3-codex",
      "displayName": "gpt-5.3-codex",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 400000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "off": "none",
        "minimal": null,
        "low": "low",
        "medium": "medium",
        "high": "high",
        "xhigh": "xhigh",
        "max": null
       }
      }
     }
    ],
    "vendorDisplayName": "E-FlowCode",
    "category": "ThirdParty",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://e-flowcode.cc",
    "apiFormat": "openai-responses"
   },
   {
    "id": "aws-bedrock",
    "displayName": "AWS Bedrock",
    "baseUrl": "https://bedrock-runtime.us-east-1.amazonaws.com",
    "models": [
     {
      "id": "global.anthropic.claude-opus-5",
      "displayName": "Claude Opus 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "global.anthropic.claude-sonnet-5",
      "displayName": "Claude Sonnet 5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000,
       "thinkingLevelMap": {
        "xhigh": "xhigh",
        "max": "max"
       }
      }
     },
     {
      "id": "global.anthropic.claude-haiku-4-5-20251001-v1:0",
      "displayName": "Claude Haiku 4.5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 200000,
       "maxTokens": 64000
      }
     },
     {
      "id": "us.amazon.nova-pro-v1:0",
      "displayName": "Amazon Nova Pro",
      "capability": {
       "reasoning": false,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 300000,
       "maxTokens": 8192
      }
     },
     {
      "id": "us.meta.llama4-maverick-17b-instruct-v1:0",
      "displayName": "Meta Llama 4 Maverick",
      "capability": {
       "reasoning": false,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 16384
      }
     },
     {
      "id": "us.deepseek.r1-v1:0",
      "displayName": "DeepSeek R1",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 128000,
       "maxTokens": 32768
      }
     }
    ],
    "vendorDisplayName": "AWS Bedrock",
    "category": "CloudProvider",
    "accessChannel": "Api",
    "market": "Unspecified",
    "documentationUrl": "https://aws.amazon.com/bedrock/",
    "apiFormat": "bedrock-converse-stream"
   },
   {
    "id": "tencent-token-plan",
    "displayName": "Tencent Token Plan",
    "baseUrl": "https://api.lkeap.cloud.tencent.com/plan/v3",
    "models": [
     {
      "id": "tc-code-latest",
      "displayName": "Auto",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 196608,
       "maxTokens": 32768
      }
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash Official",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro Official",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "minimax-m2.7",
      "displayName": "MiniMax-M2.7",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 204800,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5",
      "displayName": "GLM-5",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 131072
      }
     },
     {
      "id": "hy3",
      "displayName": "Hy3",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 256000,
       "maxTokens": 128000
      }
     },
     {
      "id": "hy3-preview",
      "displayName": "Hy3 Preview",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 256000,
       "maxTokens": 128000
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan",
    "apiFormat": "openai-completions"
   },
   {
    "id": "tencent-token-plan-intl",
    "displayName": "Tencent Token Plan (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 196608,
       "maxTokens": 32768
      }
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 131072
      }
     },
     {
      "id": "kimi-k2.6",
      "displayName": "Kimi K2.6",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144
      }
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro Official",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash Official",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax-M3",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan",
    "apiFormat": "openai-completions"
   },
   {
    "id": "tencent-token-plan-enterprise-pro",
    "displayName": "Tencent Token Plan Enterprise Pro",
    "baseUrl": "https://tokenhub.tencentmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 196608,
       "maxTokens": 32768
      }
     },
     {
      "id": "glm-5.3",
      "displayName": "GLM-5.3",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5",
      "displayName": "GLM-5",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5-turbo",
      "displayName": "GLM-5 Turbo",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144,
       "thinkingLevelMap": {
        "off": null
       }
      }
     },
     {
      "id": "kimi-k2.7-code-highspeed",
      "displayName": "Kimi K2.7 Code HighSpeed",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144,
       "thinkingLevelMap": {
        "off": null
       }
      }
     },
     {
      "id": "kimi-k2.6",
      "displayName": "Kimi K2.6",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144
      }
     },
     {
      "id": "minimax-m2.7",
      "displayName": "MiniMax-M2.7",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 204800,
       "maxTokens": 131072
      }
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax-M3",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000
      }
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-flash-0731",
      "displayName": "DeepSeek V4 Flash 0731 GA",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-pro-0813",
      "displayName": "DeepSeek V4 Pro 0813 GA",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash Official",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro Official",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Pro",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan-e",
    "apiFormat": "openai-completions"
   },
   {
    "id": "tencent-token-plan-enterprise-pro-intl",
    "displayName": "Tencent Token Plan Enterprise Pro (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 196608,
       "maxTokens": 32768
      }
     },
     {
      "id": "glm-5.3",
      "displayName": "GLM-5.3",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 131072
      }
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax-M3",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000
      }
     },
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144,
       "thinkingLevelMap": {
        "off": null
       }
      }
     },
     {
      "id": "kimi-k2.7-code-highspeed",
      "displayName": "Kimi K2.7 Code HighSpeed",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144,
       "thinkingLevelMap": {
        "off": null
       }
      }
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-flash-0731",
      "displayName": "DeepSeek V4 Flash 0731 GA",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-pro-0813",
      "displayName": "DeepSeek V4 Pro 0813 GA",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash Official",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro Official",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Pro (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan-e",
    "apiFormat": "openai-completions"
   },
   {
    "id": "tencent-token-plan-enterprise-lite",
    "displayName": "Tencent Token Plan Enterprise Lite",
    "baseUrl": "https://tokenhub.tencentmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 196608,
       "maxTokens": 32768
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Lite",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/tokenplan-e",
    "apiFormat": "openai-completions"
   },
   {
    "id": "tencent-token-plan-enterprise-lite-intl",
    "displayName": "Tencent Token Plan Enterprise Lite (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/plan/v3",
    "models": [
     {
      "id": "auto",
      "displayName": "Auto",
      "capability": {
       "reasoning": false,
       "input": [
        "text"
       ],
       "contextWindow": 196608,
       "maxTokens": 32768
      }
     }
    ],
    "vendorDisplayName": "Tencent Token Plan Enterprise Lite (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "TokenPlan",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/tokenplan-e",
    "apiFormat": "openai-completions"
   },
   {
    "id": "tencent-tokenhub",
    "displayName": "Tencent TokenHub",
    "baseUrl": "https://tokenhub.tencentmaas.com/v1",
    "models": [
     {
      "id": "hy4-preview",
      "displayName": "Hy4 Preview",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 65536
      }
     },
     {
      "id": "hy3",
      "displayName": "Hy3",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 256000,
       "maxTokens": 128000
      }
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash Official",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro Official",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek/deepseek-v4-flash-vision-exp",
      "displayName": "DeepSeek V4 Flash Vision Exp",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-flash-0731",
      "displayName": "DeepSeek V4 Flash 0731 GA",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-pro-0813",
      "displayName": "DeepSeek V4 Pro 0813 GA",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "glm-5.3-flash",
      "displayName": "GLM-5.3 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5.3",
      "displayName": "GLM-5.3",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5v-turbo",
      "displayName": "GLM-5V Turbo",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5-turbo",
      "displayName": "GLM-5 Turbo",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5",
      "displayName": "GLM-5",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "kimi-k2.7-code-highspeed",
      "displayName": "Kimi K2.7 Code HighSpeed",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144,
       "thinkingLevelMap": {
        "off": null
       }
      }
     },
     {
      "id": "kimi-k3",
      "displayName": "Kimi K3",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144,
       "thinkingLevelMap": {
        "off": null
       }
      }
     },
     {
      "id": "kimi-k2.6",
      "displayName": "Kimi K2.6",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144
      }
     },
     {
      "id": "kimi-k2.5",
      "displayName": "Kimi K2.5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144
      }
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax-M3",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000
      }
     },
     {
      "id": "minimax-m2.7",
      "displayName": "MiniMax-M2.7",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 204800,
       "maxTokens": 131072
      }
     },
     {
      "id": "mimo-v2.5-pro",
      "displayName": "MiMo-V2.5-Pro",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "Tencent TokenHub",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://console.cloud.tencent.com/tokenhub/apikey",
    "apiFormat": "openai-completions"
   },
   {
    "id": "tencent-tokenhub-intl",
    "displayName": "Tencent TokenHub (Intl)",
    "baseUrl": "https://tokenhub-intl.tencentcloudmaas.com/v1",
    "models": [
     {
      "id": "hy4-preview",
      "displayName": "Hy4 Preview",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 65536
      }
     },
     {
      "id": "hy3",
      "displayName": "Hy3",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 256000,
       "maxTokens": 128000
      }
     },
     {
      "id": "deepseek-v4-flash-202605",
      "displayName": "DeepSeek V4 Flash Official",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-pro-202606",
      "displayName": "DeepSeek V4 Pro Official",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek/deepseek-v4-flash-vision-exp",
      "displayName": "DeepSeek V4 Flash Vision Exp",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-flash-0731",
      "displayName": "DeepSeek V4 Flash 0731 GA",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-pro-0813",
      "displayName": "DeepSeek V4 Pro 0813 GA",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-flash",
      "displayName": "DeepSeek V4 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v4-pro",
      "displayName": "DeepSeek V4 Pro",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 384000,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "deepseek-v3.2",
      "displayName": "DeepSeek V3.2",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 131072,
       "maxTokens": 32768,
       "thinkingLevelMap": {
        "minimal": null,
        "low": null,
        "medium": null,
        "high": "high",
        "max": "max"
       }
      }
     },
     {
      "id": "glm-5.3",
      "displayName": "GLM-5.3",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5.3-flash",
      "displayName": "GLM-5.3 Flash",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5.2",
      "displayName": "GLM-5.2",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1000000,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5",
      "displayName": "GLM-5",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5-turbo",
      "displayName": "GLM-5 Turbo",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5v-turbo",
      "displayName": "GLM-5V Turbo",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "glm-5.1",
      "displayName": "GLM-5.1",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 200000,
       "maxTokens": 131072
      }
     },
     {
      "id": "kimi-k3",
      "displayName": "Kimi K3",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     },
     {
      "id": "kimi-k2.7-code-highspeed",
      "displayName": "Kimi K2.7 Code HighSpeed",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144,
       "thinkingLevelMap": {
        "off": null
       }
      }
     },
     {
      "id": "kimi-k2.7-code",
      "displayName": "Kimi K2.7 Code",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144,
       "thinkingLevelMap": {
        "off": null
       }
      }
     },
     {
      "id": "kimi-k2.6",
      "displayName": "Kimi K2.6",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144
      }
     },
     {
      "id": "kimi-k2.5",
      "displayName": "Kimi K2.5",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 262144,
       "maxTokens": 262144
      }
     },
     {
      "id": "minimax-m3",
      "displayName": "MiniMax-M3",
      "capability": {
       "reasoning": true,
       "input": [
        "text",
        "image"
       ],
       "contextWindow": 1000000,
       "maxTokens": 128000
      }
     },
     {
      "id": "minimax-m2.7",
      "displayName": "MiniMax-M2.7",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 204800,
       "maxTokens": 131072
      }
     },
     {
      "id": "mimo-v2.5-pro",
      "displayName": "MiMo-V2.5-Pro",
      "capability": {
       "reasoning": true,
       "input": [
        "text"
       ],
       "contextWindow": 1048576,
       "maxTokens": 131072
      }
     }
    ],
    "vendorDisplayName": "Tencent TokenHub (Intl)",
    "category": "ChinaOfficial",
    "accessChannel": "Api",
    "market": "China",
    "documentationUrl": "https://console.tencentcloud.com/tokenhub/apikey",
    "apiFormat": "openai-completions"
   }
  ]
 }
}
    """.trimIndent()
}
