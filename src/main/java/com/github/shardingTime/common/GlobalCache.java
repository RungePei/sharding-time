package com.github.shardingTime.common;

import com.github.shardingTime.config.ShardingProperties;
import com.github.shardingTime.config.TableConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Slf4j
public class GlobalCache {
    @Autowired
    private ShardingProperties shardingProperties;

    @Getter
    private static Field additionalParametersField;//BoundSql中的属性
    @Getter
    private static Field msIdField;//MS id
    @Getter
    private static final Map<String, List<String>> trueTableNames = new HashMap<>();
    @Getter
    private static final Set<String> logicTableNames = new HashSet<>();
    //表结构缓存,key为逻辑表名;value为ddl信息:key为真实表名,value为ddl语句
    @Getter
    private static final Map<String, Map<String, String>> tableDDL = new HashMap<>();

    @Autowired
    private DataSource dataSource;

    @PostConstruct
    public void initTable() throws NoSuchFieldException {
        //初始化
        Map<String, TableConfig> tableConfig = shardingProperties.getTable();
        logicTableNames.addAll(shardingProperties.getTable().keySet());
        for (String s : tableConfig.keySet()) {
            trueTableNames.put(s, new ArrayList<>());
        }

        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()
        ) {
            //缓存所有分表的表名
            ResultSet tableNames = statement.executeQuery("show tables");
            while (tableNames.next()) {
                String name = tableNames.getString(1);
                for (String table : tableConfig.keySet()) {
                    List<String> tableName = trueTableNames.get(table);
                    if (name.contains(table + "_") && !tableName.contains(name))
                        tableName.add(name);
                }
            }

            //缓存ddl
            String ddlSql = "show create table ";
            for (Map.Entry<String, List<String>> entry : trueTableNames.entrySet()) {
                String logicTable = entry.getKey();
                String trueTable = entry.getValue().get(0);
                String ddlSqlFull = ddlSql + trueTable;
                ResultSet ddlResult = statement.executeQuery(ddlSqlFull);
                ddlResult.next();
                Map<String, String> trueDdl = new HashMap<>();
                trueDdl.put(trueTable, ddlResult.getString(2));
                tableDDL.put(logicTable, trueDdl);
            }
        } catch (SQLException e) {
            log.error(e.getSQLState(), e);
        }
        //缓存filed对象
        additionalParametersField = BoundSql.class.getDeclaredField("additionalParameters");
        additionalParametersField.setAccessible(true);

        msIdField = MappedStatement.class.getDeclaredField("id");
        msIdField.setAccessible(true);
    }
}
