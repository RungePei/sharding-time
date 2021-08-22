package com.github.shardingTime.type.impl;

import cn.hutool.core.date.DateUtil;
import com.github.shardingTime.type.Type;
import com.github.shardingTime.util.CommonUtils;

import java.util.Date;

public class DayType implements Type {
    /**
     * 2021.7.31 18:59:55
     *
     * @return 212
     */
    @Override
    public String getSuffix(Date date) {
        String day = String.valueOf(DateUtil.dayOfYear(date));
        return CommonUtils.addZeroString(day, 3);
    }
}
