package com.github.shardingTime.type.impl;

import cn.hutool.core.date.DateUtil;
import com.github.shardingTime.type.Type;

import java.util.Date;

public class YearType implements Type {
    /**
     * 2021.7.31 18:59:55
     *
     * @return 21
     */
    @Override
    public String getSuffix(Date date) {
        return String.valueOf(DateUtil.year(date)).substring(2);
    }
}
