# sharding-time

***
该插件为针对MyBatis及Mysql的分表插件  
主要应用于针对一个时间字段进行分表的场景，当随着时间累计数据量过大导致查询缓慢时，如大量日志等  
可通过该插件非常方便地进行分表存储及查询

## 使用方法

该插件使用方法非常简单，仅需两步  
1.引入该jar包，maven形式

```
<dependency>  
  <groupId>com.github</groupId>  
  <artifactId>sharding-time</artifactId>  
  <version>${latest-version}</version>  
</dependency>
```

2.配置文件配置要分的表及分表的依据字段和分表类型  
如：

```
sharding.config.table.record.column=date  
sharding.config.table.record.type=month
```

其中  
1) sharding.config.table为配置的前缀  
2) record为分表的逻辑表名
3) column为分表的依据字段，在数据库中的类型可为date、datetime、string，只要符合日期格式即可
4) type为按时间分表的类型，会相应地在逻辑表名后拼接后缀成为真实表名，目前支持四种
   1) year：一年生成一张表；真实表名形如 record_21，意为2021年的record表
   2) month：每个月生成一张表；真实表名形如record_2108，意为2021年8月的record表
   3) week：每周生成一张表；真实表名形如record_2108，意为2021年第8周的record表
   4) day：每天成成一张表；真实表名形如record_2108，意为2021年第8天的record表
5) 支持多张表分表，根据需求重复以上两行配置即可

通过以上配置即可使用分表功能，项目启动前需在数据库中至少存在一张符合逻辑表名前缀的表  
启动时，sharding-time根据匹配逻辑表名前缀来管理所需的表

## sharding-time的注意事项  
1.在代码中增删查改分表数据时，使用逻辑表名  
2.不支持pageHelper：pageHelper为对执行的sql拦截查询count实现，无法汇聚分表的数据  
3.动态创建表，无需自行维护：当插入数据的分表字段不符合已存在的表时，会根据已存在的表结构创建新的表