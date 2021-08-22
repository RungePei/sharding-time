package com.github.shardingTime.interceptor;

import cn.hutool.core.collection.CollUtil;
import com.github.shardingTime.common.GlobalCache;
import com.github.shardingTime.execption.ShardingTimeQueryException;
import com.github.shardingTime.util.CommonUtils;
import com.github.shardingTime.util.SqlParseUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Intercepts(
        {
                @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
                @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        }
)
public class QueryInterceptor implements Interceptor {
    private final Set<String> tableSet = GlobalCache.getLogicTableNames();
    private final Map<String, List<String>> tableNames = GlobalCache.getTrueTableNames();

    @Override
    public Object intercept(Invocation invocation) throws Exception {
        if (CollUtil.isEmpty(tableSet))
            return invocation.proceed();
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];
        BoundSql boundSql;
        if (args.length == 4)//4 个参数时
            boundSql = ms.getBoundSql(parameter);
        else//6 个参数时
            boundSql = (BoundSql) args[5];


        String sql = boundSql.getSql();
        //需要分表的表名
        List<String> needSharding = SqlParseUtil.needShardingTable(sql, tableSet);

        if (CollUtil.isEmpty(needSharding))//不需要分表
            return invocation.proceed();
        if (needSharding.size() > 1)
            throw new ShardingTimeQueryException("un-support query sharding table more than one");

        Executor executor = (Executor) invocation.getTarget();
        ResultHandler<?> resultHandler = (ResultHandler<?>) args[3];

        //根据表名生成完整的sql
        String needShardingName = needSharding.get(0);
        List<String> trueTables = tableNames.get(needShardingName);
        List<String> newSqls = SqlParseUtil.updateTableName(sql, needShardingName, trueTables);

        List<Object> result = new ArrayList<>();
        Map<String, Object> additionalParameters = CommonUtils.cast(GlobalCache.getAdditionalParametersField().get(boundSql));

        for (String newSql : newSqls) {
            //反射设置ms的 id
            GlobalCache.getMsIdField().set(ms, ms.getId() + trueTables.get(newSqls.indexOf(newSql)));
            //获取新的boundSql
            BoundSql newBoundSql = new BoundSql(ms.getConfiguration(), newSql, boundSql.getParameterMappings(), parameter);
            for (String key : additionalParameters.keySet()) {
                newBoundSql.setAdditionalParameter(key, additionalParameters.get(key));
            }
            //获取新的cacheKey
            CacheKey newKey = executor.createCacheKey(ms, parameter, RowBounds.DEFAULT, boundSql);
            //执行sql
            List<Object> res = executor.query(ms, parameter, RowBounds.DEFAULT, resultHandler, newKey, newBoundSql);
            //拼接结果
            if (res != null && !CollUtil.isEmpty(res))
                result.addAll(res);
        }

        return result;
    }
}
