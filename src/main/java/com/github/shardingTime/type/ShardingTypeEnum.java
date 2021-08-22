package com.github.shardingTime.type;

import com.github.shardingTime.type.impl.DayType;
import com.github.shardingTime.type.impl.MonthType;
import com.github.shardingTime.type.impl.WeekType;
import com.github.shardingTime.type.impl.YearType;
import lombok.Getter;

@Getter
public enum ShardingTypeEnum {
    YEAR(1, new YearType()),
    MONTH(2, new MonthType()),
    WEEK(3, new WeekType()),
    DAY(4, new DayType());

    private final Integer code;
    private final Type type;

    ShardingTypeEnum(Integer code, Type type) {
        this.code = code;
        this.type = type;
    }
}
