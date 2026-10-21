package org.chuhaijian.ime.keyboard;

/** 键盘面板的竖向尺寸。默认总高不超过 {@link #LIMIT_DP}。 */
public final class Panel {
    /** 模式按钮与候选共用的那一行。 */
    public static final int BAR_DP = 44;

    /** 有编码时，候选上方的编码细条。 */
    public static final int STRIP_DP = 18;

    public static final int DEFAULT_ROW_DP = 56;

    public static final int MIN_ROW_DP = 50;

    public static final int MAX_ROW_DP = 70;

    public static final int LANDSCAPE_ROW_DP = 44;

    public static final int LIMIT_DP = 290;

    private Panel() {}

    public static int rowDp(int saved, boolean landscape) {
        if (landscape) return LANDSCAPE_ROW_DP;
        return Math.max(MIN_ROW_DP, Math.min(MAX_ROW_DP, saved));
    }

    /** 顶栏 + 可选编码条 + 四行按键。 */
    public static int totalDp(int rowDp, boolean composing) {
        return BAR_DP + (composing ? STRIP_DP : 0) + rowDp * 4;
    }
}
