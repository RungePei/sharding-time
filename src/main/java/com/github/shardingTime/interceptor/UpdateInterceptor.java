package com.github.shardingTime.interceptor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.github.shardingTime.common.Constants;
import com.github.shardingTime.common.GlobalCache;
import com.github.shardingTime.config.ShardingProperties;
import com.github.shardingTime.config.TableConfig;
import com.github.shardingTime.execption.CreatTableFailedException;
import com.github.shardingTime.type.Type;
import com.github.shardingTime.util.CommonUtils;
import com.github.shardingTime.util.MybatisUtil;
import com.github.shardingTime.util.SqlParseUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Slf4j
@Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
public class UpdateInterceptor implements Interceptor {
    @Autowired
    private ShardingProperties shardingProperties;

    private Set<String> tableSet;
    private Map<String, TableConfig> tableConfig;
    private final Map<String, List<String>> tableNames = GlobalCache.getTrueTableNames();
    private static final Map<String, Map<String, String>> tableDDL = GlobalCache.getTableDDL();


    @Override
    public Object intercept(Invocation invocation) throws Exception {
        if (CollUtil.isEmpty(tableSet))
            return invocation.proceed();
        Object[] args = invocation.getArgs();
        MappedStatement ms = ((MappedStatement) args[0]);
        Object parameter = args[1];
        BoundSql boundSql = ms.getBoundSql(parameter);

        String sql = boundSql.getSql();//预编译的sql,由?占位
        //需要分表的表名
        List<String> needSharding = SqlParseUtil.needShardingTable(sql, tableSet);

        if (CollUtil.isEmpty(needSharding))//不需要分表
            return invocation.proceed();
        String logicTable = needSharding.get(0);//逻辑表名
        List<String> trueTables = tableNames.get(logicTable);//缓存中取出真实表名列表
        Executor executor = (Executor) invocation.getTarget();

        TableConfig config = tableConfig.get(logicTable);

        sql = MybatisUtil.getSql(ms.getConfiguration(), boundSql);
        String oriValue = SqlParseUtil.getUpdateValueFromSql(sql, config.getColumn());//时间字符串
        String shardingValue = CommonUtils.removePreSuf(oriValue);//去除引号
        List<String> updateTables = new ArrayList<>();//要执行sql的表名
        if (StrUtil.isNotBlank(shardingValue)) {//根据生成的值执行指定表的更新
            Date shardingDate = DateUtil.parse(shardingValue);//转为date
            Type type = tableConfig.get(logicTable).getType().getType();
            String tableSuffix = type.getSuffix(shardingDate);
            String fullTableName = logicTable + Constants.Symbol.underLine + tableSuffix;
            if (trueTables.contains(fullTableName))//如果已经存在的表中包含解析出来的分表名,只更新该表
                updateTables.add(fullTableName);
            else {//如果缓存不包含该表名
                //如果是更新或删除;则更新所有表
                if (Arrays.asList(SqlCommandType.UPDATE, SqlCommandType.DELETE).contains(ms.getSqlCommandType()))
                    updateTables.addAll(trueTables);
                else {//如果是新增,需新建表
                    executeDDL(logicTable, fullTableName);
                    updateTables.add(fullTableName);
                }
            }
        } else //未解析出时间,执行所有表的更新
            updateTables.addAll(trueTables);

        List<String> newSqls = SqlParseUtil.updateTableName(sql, logicTable, updateTables);
        int res = 0;

        for (String newSql : newSqls) {
            //获取新的ms
            String newId = ms.getId() + trueTables.get(newSqls.indexOf(newSql));
            MappedStatement newMs = MybatisUtil.newMappedStatement(ms, newId, newSql);
            //执行sql
            res += executor.update(newMs, parameter);
        }
        return res;
    }

    @PostConstruct
    public void init() {
        tableConfig = shardingProperties.getTable();
        tableSet = tableConfig.keySet();
    }

    @Autowired
    private DataSource dataSource;

    /**
     * 新增表
     */
    private void executeDDL(String logicTable, String trueTable) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            Map<String, String> ddlMap = tableDDL.get(logicTable);
            String ddlTable = "";
            String oldDdlSql = "";
            for (Map.Entry<String, String> entry : ddlMap.entrySet()) {
                ddlTable = entry.getKey();
                oldDdlSql = entry.getValue();
            }

            String ddlSql = SqlParseUtil.updateTableName(oldDdlSql, ddlTable, Collections.singletonList(trueTable)).get(0);
            if (StrUtil.isBlank(ddlSql))
                throw new CreatTableFailedException("can not get ddl,please check whether the database has the table");
            statement.executeUpdate(ddlSql);
            log.info("sharding-time create new table {}", trueTable);
            tableNames.get(logicTable).add(trueTable);//更新缓存
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
