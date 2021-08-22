package com.github.shardingTime.config;

import com.github.shardingTime.common.GlobalCache;
import com.github.shardingTime.interceptor.QueryInterceptor;
import com.github.shardingTime.interceptor.UpdateInterceptor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Map;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "sharding.config")
@Import({QueryInterceptor.class, UpdateInterceptor.class, GlobalCache.class})
public class ShardingProperties {

    @Getter
    @Setter
    private Map<String, TableConfig> table;
}
