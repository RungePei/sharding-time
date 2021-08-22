package com.github.shardingTime.type.impl;

import cn.hutool.core.date.DateUtil;
import com.github.shardingTime.type.Type;
import com.github.shardingTime.util.CommonUtils;

import java.util.Date;

public class WeekType implements Type {
    /**
     * 2021.7.31 18:59:55
     *
     * @return 31
     */
    @Override
    public String getSuffix(Date date) {
        String week = String.valueOf(DateUtil.weekOfYear(date));
        return CommonUtils.addZeroString(week, 2);
    }
}
