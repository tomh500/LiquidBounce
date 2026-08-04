import {writable} from "svelte/store";

export const clientLanguage = writable("en_us");

const ZH_CN: Record<string, string> = {
    "Settings": "设置",
    "Global Settings": "全局设置",
    "Language": "语言",
    "ClientLanguage": "客户端语言",
    "Commands": "命令",
    "Prefix": "前缀",
    "HintCount": "提示数量",
    "Targets": "目标",
    "Combat": "战斗目标",
    "Visual": "视觉目标",
    "VapeRotations": "Vape 转头",
    "Movement": "移动修正",
    "3rdPersonAimView": "第三人称瞄准视角",
    "AimIndicator": "瞄准指示线",
    "UseReach": "使用 Reach 距离",
    "UseHitboxes": "使用 Hitbox 扩展",
    "BlinkManager": "Blink 管理器",
    "Esp": "轨迹显示",
    "Line": "轨迹线",
    "AutoTranslate": "自动翻译",
    "Provider": "翻译服务",
    "GuiRenderer": "界面渲染器",
    "Quality": "渲染质量",
    "AcceleratedPaint": "加速绘制",
    "AcceleratedPaint(BETA)": "加速绘制（测试）",
    "Renderer": "渲染设置",
    "Fps": "帧率",
    "SyncGameFps": "同步游戏帧率",
    "ClientChat": "客户端聊天",
    "Enabled": "启用",
    "JwtToken": "JWT 令牌",
    "RichPresence": "Discord 状态",
    "ActivityType": "活动类型",
    "StatusDisplayType": "状态显示类型",
    "Separator": "分隔符",
    "DetailsParts": "详情内容",
    "StateParts": "状态内容",
    "LargeImage": "大图标",
    "SmallImage": "小图标",
    "Asset": "图标资源",
    "Parts": "显示内容",
};

export function settingsText(text: string, language: string): string {
    return language.toLowerCase() === "zh_cn" ? ZH_CN[text] ?? text : text;
}
