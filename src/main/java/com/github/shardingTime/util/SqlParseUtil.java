package com.github.shardingTime.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.mysql.ast.clause.MySqlSelectIntoStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlDeleteStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlInsertStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlUpdateStatement;
import com.alibaba.druid.sql.dialect.mysql.parser.MySqlStatementParser;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitorAdapter;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlSchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;
import com.alibaba.druid.util.JdbcConstants;

import java.util.*;
import java.util.stream.Collectors;

public class SqlParseUtil {

    /**
     * 修改表名
     */
    public static List<String> updateTableName(String sql, String needUpdate, List<String> newNames) {
        if (StrUtil.isBlank(sql))
            return Collections.emptyList();
        List<String> res = new ArrayList<>();
        //解析sql
        List<SQLStatement> statements = SQLUtils.parseStatements(sql, JdbcConstants.MYSQL);
        SQLStatement sqlStatement = statements.get(0);

        //修改表名
        for (int i = 0; i < newNames.size(); i++) {
            if (i != 0)
                needUpdate = newNames.get(i - 1);
            RenameTableVisitor renameTableVisitor = RenameTableVisitor.getVisitor(needUpdate, newNames.get(i));
            sqlStatement.accept(renameTableVisitor);

            String newSql = SQLUtils.toMySqlString(sqlStatement);
            res.add(newSql);
        }
        return res;
    }


    /**
     * 更简单地获取表名
     */
    public static List<String> getAllTableNames(String sql) {
        if (StrUtil.isBlank(sql))
            return Collections.emptyList();
        List<SQLStatement> statements = SQLUtils.parseStatements(sql, JdbcConstants.MYSQL);
        SQLStatement sqlStatement = statements.get(0);

        MySqlSchemaStatVisitor visitor = new MySqlSchemaStatVisitor();
        sqlStatement.accept(visitor);
        Map<TableStat.Name, TableStat> tables = visitor.getTables();
        return tables.keySet().stream().map(TableStat.Name::getName).collect(Collectors.toList());
    }

    /**
     * 要执行的sql是否需要分表
     *
     * @return list 需要分表的表名
     */
    public static List<String> needShardingTable(String sql, Set<String> tableSet) {
        //sql中涉及的表名
        List<String> sqlUsedTables = SqlParseUtil.getAllTableNames(sql);
        if (CollUtil.isEmpty(sqlUsedTables) || CollUtil.isEmpty(tableSet))
            return Collections.emptyList();
        //是否包含
        List<String> list = new ArrayList<>();
        for (String tableName : sqlUsedTables) {
            for (String config : tableSet) {
                if (tableName.equals(config))
                    list.add(tableName);
            }
        }
        return list;
    }


    /**
     * 解析sql,获取其对应的列的值
     */
    public static String getUpdateValueFromSql(String sql, String column) {
        sql = sql.trim();
        MySqlStatementParser parser = new MySqlStatementParser(sql);
        SQLStatement sqlStatement = parser.parseStatement();
        //获取所有的列名
        MySqlSchemaStatVisitor visitor = new MySqlSchemaStatVisitor();
        sqlStatement.accept(visitor);
        List<TableStat.Column> columns = new ArrayList<>(visitor.getColumns());
        Integer index = null;
        for (TableStat.Column co : columns) {
            if (co.getName().equals(column)) {
                index = columns.indexOf(co);
                break;
            }
        }
        if (index == null)
            return "";
        //解析where
        SQLExpr whereExpr = null;
        if (sqlStatement instanceof MySqlUpdateStatement) {
            MySqlUpdateStatement updateStatement = (MySqlUpdateStatement) sqlStatement;
            whereExpr = updateStatement.getWhere();
        } else if (sqlStatement instanceof MySqlDeleteStatement) {
            MySqlDeleteStatement deleteStatement = (MySqlDeleteStatement) sqlStatement;
            whereExpr = deleteStatement.getWhere();
        } else if (sqlStatement instanceof MySqlInsertStatement) {
            MySqlInsertStatement insertStatement = (MySqlInsertStatement) sqlStatement;
            List<SQLInsertStatement.ValuesClause> valuesClauses = insertStatement.getValuesList();
            //解析sql column值
            for (SQLInsertStatement.ValuesClause valuesClause : valuesClauses) {//todo 批量插入时,分别查询不同的时间
                List<SQLExpr> values = valuesClause.getValues();
                SQLExpr sqlExpr = values.get(index);
                if (sqlExpr != null)
                    return String.valueOf(sqlExpr);
            }
            return "";
        }
        SQLBinaryOpExpr binaryOpExpr = (SQLBinaryOpExpr) whereExpr;
        return getValueOfColumn(binaryOpExpr, column);
    }

    private static String getValueOfColumn(SQLExpr sqlExpr, String column) {
        if (!(sqlExpr instanceof SQLBinaryOpExpr))
            return "";
        SQLBinaryOpExpr sqlBinaryOpExpr = (SQLBinaryOpExpr) sqlExpr;
        if (sqlBinaryOpExpr.getLeft() instanceof SQLIdentifierExpr) {
            SQLIdentifierExpr sqlIdentifierExpr = (SQLIdentifierExpr) sqlBinaryOpExpr.getLeft();
            if (sqlIdentifierExpr.getName().equals(column))
                return sqlBinaryOpExpr.getRight().toString();
        }
        String res = getValueOfColumn(sqlBinaryOpExpr.getLeft(), column);
        if (StrUtil.isNotBlank(res))
            return res;
        return getValueOfColumn(sqlBinaryOpExpr.getRight(), column);
    }


    public static void main(String[] args) {
        String sql = "update dev set id=2 where date=1 and time=3";
        System.out.println(getUpdateValueFromSql(sql, "dime"));
    }


    /**
     * 解析sql，获取sql中所有的表名
     */
    public static List<String> getAllTables(String sql) {
        if (StrUtil.isBlank(sql))
            return Collections.emptyList();
        //解析mysql源 sql
        List<SQLStatement> statements = SQLUtils.parseStatements(sql, JdbcConstants.MYSQL);
        //只有一句sql,强转为查询
        SQLSelectStatement sqlSelectStatement = (SQLSelectStatement) statements.get(0);
        SQLSelectQuery sqlSelectQuery = sqlSelectStatement.getSelect().getQuery();

        //非union语句
        SQLSelectQueryBlock sqlSelectQueryBlock = (SQLSelectQueryBlock) sqlSelectQuery;

        //获取表
        SQLTableSource sqlTableSource = sqlSelectQueryBlock.getFrom();
        List<String> tableNames = new ArrayList<>();
        getAllTable(tableNames, sqlTableSource);
        return tableNames;
    }

    //递归获取join表
    private static void getAllTable(List<String> tables, SQLTableSource table) {
        // 普通单表
        if (table instanceof SQLExprTableSource) {
//            tables.add(table.get)
            SQLExprTableSource sqlExprTableSource = (SQLExprTableSource) table;
            tables.add(sqlExprTableSource.getExpr().toString());
            // join多表
        } else if (table instanceof SQLJoinTableSource) {
            // 处理---------------------
            SQLJoinTableSource sqlJoinTableSource = (SQLJoinTableSource) table;
            getAllTable(tables, sqlJoinTableSource.getLeft());
            getAllTable(tables, sqlJoinTableSource.getRight());
        }
    }

    public static class RenameTableVisitor extends MySqlASTVisitorAdapter {
        private String newName;
        private String oldName;

        public static RenameTableVisitor getVisitor(String oldName, String newName) {
            RenameTableVisitor renameTableVisitor = new RenameTableVisitor();
            renameTableVisitor.setNewName(newName);
            renameTableVisitor.setOldName(oldName);
            return renameTableVisitor;
        }

        public void setNewName(String newName) {
            this.newName = newName;
        }

        public void setOldName(String oldName) {
            this.oldName = oldName;
        }

        @Override
        public boolean visit(SQLExprTableSource x) {
            if (CommonUtils.removePreSuf(x.getExpr().toString()).equals(oldName)) {
                x.setExpr(newName);//修改表名
                if (x.getParent() instanceof MySqlSelectIntoStatement)
                    if (StrUtil.isBlank(x.getAlias()))
                        x.setAlias(oldName);//无别名时,指定旧表名为别名;有别名,不需要修改
            }
            return true;
        }
    }
}
