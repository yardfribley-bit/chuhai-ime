package org.chuhaijian.ime.keyboard;

/** 只向系统输入法壳传递按键，不在键盘控件内处理词库或纠错。 */
public interface Listener { void onKey(String key); }
