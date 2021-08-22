package com.github.shardingTime.util;

import cn.hutool.core.util.StrUtil;
import com.github.shardingTime.common.Constants;

public class CommonUtils {
    @SuppressWarnings("unchecked")
    public static <T> T cast(Object object) {
        return (T) object;
    }

    public static String addZeroString(String s, int length) {
        if (StrUtil.isBlank(s))
            s = "";
        return StrUtil.fillBefore(s, Constants.zero, length);
    }

    /**
     * 去除字符串开始结尾处的引号
     */
    public static String removePreSuf(String s) {
        if (StrUtil.isBlank(s) || s.length() < 2)
            return "";
        if (Constants.Symbol.quoteList.contains(s.charAt(0)))
            s = s.substring(1);
        if (Constants.Symbol.quoteList.contains(s.charAt(s.length() - 1)))
            s = s.substring(0, s.length() - 1);
        return s;
    }
}
