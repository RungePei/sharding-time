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

        String sql = boundSql.getSql();//????????????sql,??????????
        //?????????????????????
        List<String> needSharding = SqlParseUtil.needShardingTable(sql, tableSet);

        if (CollUtil.isEmpty(needSharding))//???????????????
            return invocation.proceed();
        String logicTable = needSharding.get(0);//????????????
        List<String> trueTables = tableNames.get(logicTable);//?????????????????????????????????
        Executor executor = (Executor) invocation.getTarget();

        TableConfig config = tableConfig.get(logicTable);

        sql = MybatisUtil.getSql(ms.getConfiguration(), boundSql);
        String oriValue = SqlParseUtil.getUpdateValueFromSql(sql, config.getColumn());//???????????????
        String shardingValue = CommonUtils.removePreSuf(oriValue);//????????????
        List<String> updateTables = new ArrayList<>();//?????????sql?????????
        if (StrUtil.isNotBlank(shardingValue)) {//??????????????????????????????????????????
            Date shardingDate = DateUtil.parse(shardingValue);//??????date
            Type type = tableConfig.get(logicTable).getType().getType();
            String tableSuffix = type.getSuffix(shardingDate);
            String fullTableName = logicTable + Constants.Symbol.underLine + tableSuffix;
            if (trueTables.contains(fullTableName))//?????????????????????????????????????????????????????????,???????????????
                updateTables.add(fullTableName);
            else {//??????????????????????????????
                //????????????????????????;??????????????????
                if (Arrays.asList(SqlCommandType.UPDATE, SqlCommandType.DELETE).contains(ms.getSqlCommandType()))
                    updateTables.addAll(trueTables);
                else {//???????????????,????????????
                    executeDDL(logicTable, fullTableName);
                    updateTables.add(fullTableName);
                }
            }
        } else //??????????????????,????????????????????????
            updateTables.addAll(trueTables);

        List<String> newSqls = SqlParseUtil.updateTableName(sql, logicTable, updateTables);
        int res = 0;

        for (String newSql : newSqls) {
            //????????????ms
            String newId = ms.getId() + trueTables.get(newSqls.indexOf(newSql));
            MappedStatement newMs = MybatisUtil.newMappedStatement(ms, newId, newSql);
            //??????sql
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
     * ?????????
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
            tableNames.get(logicTable).add(trueTable);//????????????
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
