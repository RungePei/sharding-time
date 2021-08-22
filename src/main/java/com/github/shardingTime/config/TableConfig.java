package com.github.shardingTime.config;

import com.github.shardingTime.type.ShardingTypeEnum;
import lombok.Data;

@Data
public class TableConfig {
    //分表的键
    private String column;
    /**
     * 时间分表的类型
     *
     * @see ShardingTypeEnum
     */
    private ShardingTypeEnum type;
}
