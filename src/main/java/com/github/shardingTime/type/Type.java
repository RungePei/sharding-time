package com.github.shardingTime.type;

import java.util.Date;

public interface Type {
    /**
     * 根据分表字段时间,获取表后缀
     */
    String getSuffix(Date date);
}
