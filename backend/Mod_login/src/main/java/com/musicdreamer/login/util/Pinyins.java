package com.musicdreamer.login.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

/** bug75：歌手名 → 拼音用户名。只做"够用的全拼"：小写、无声调、多音字取第一个读音。 */
public final class Pinyins {

    private static final HanyuPinyinOutputFormat FMT = new HanyuPinyinOutputFormat();

    static {
        FMT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FMT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    private Pinyins() {
    }

    /** 中文转全拼；非中文字母数字保留（转小写），空格与标点丢弃；生僻字取不到拼音时原样保留。 */
    public static String full(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c < 128) {
                if (Character.isLetterOrDigit(c)) {
                    sb.append(Character.toLowerCase(c));
                }
                continue;
            }
            String[] arr = null;
            try {
                arr = PinyinHelper.toHanyuPinyinStringArray(c, FMT);
            } catch (BadHanyuPinyinOutputFormatCombination ignored) {
                // 格式固定合法，不会发生
            }
            if (arr != null && arr.length > 0) {
                sb.append(arr[0]);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
