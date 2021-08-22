package com.github.shardingTime.type.impl;

import cn.hutool.core.date.DateUtil;
import com.github.shardingTime.type.Type;
import com.github.shardingTime.util.CommonUtils;

import java.util.Date;

public class MonthType implements Type {
    @Override
    public String getSuffix(Date date) {
        String year = String.valueOf(DateUtil.year(date));
        String month = String.valueOf(DateUtil.month(date)+1);
        month = CommonUtils.addZeroString(month, 2);
        return year.substring(2) + month;
    }
}
