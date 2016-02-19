# hbase-increment-index
hbase+solr实现hbase的二级索引

###背景需求
现有一张Hbase的表，数据量千万级+，而且不断有新的数据插入，或者无效数据删除，每日新增大概几百万数据，现在已经有离线的hive映射hbase 
提供离线查询，但是由于性能比较低，且不支持全文检索，所以想提供一种OLAP实时在线分析的查询，并且支持常规的聚合统计和全文检索，性能在秒级别可接受 

###需求分析
hbase的目前的二级索引种类非常多，但大多数都不太稳定或成熟，基于Lucene的全文检索服务SolrCloud集群和ElasticSearch集群是二种比较可靠的方案，无论需求 
还是性能都能满足，而且支持容错，副本，扩容等功能，但是需要二次开发和定制。 

###架构拓扑
![架构拓扑](http://dl2.iteye.com/upload/attachment/0115/1660/15ae08c4-3b32-3ce6-9fe3-f0cae1f6993f.png) 

###技术实现步骤 
（1） 搭建一套solr或者es集群，并且提前定制好schemal，本例中用的是solr单节点存储索引, 
如果不知道怎么搭建solrcloud集群或者elasticsearch集群，请参考博客： <br/>
[solrcloud集群搭建](http://qindongliang.iteye.com/blog/2275990) <br/>
[elasticsearch集群搭建](http://qindongliang.iteye.com/blog/2250776) <br/>
（2） 开发自定义的协处理器<br/>
（3） 打包代码成一个main.jar <br/>
（4） 安装依赖jar给各个Hbase节点，可以拷贝到hbase的lib目录，也可以在hbase.env.sh里面配置CLASSPATH <br/>
```java
config-1.2.1.jar  
httpclient-4.3.1.jar  
httpcore-4.3.jar  
httpmime-4.3.1.jar  
noggit-0.6.jar  
solr-solrj-5.1.0.jar  
```
（5） 上传main.jar至HDFS目录 <br/>
（6） 建表： create 'c', NAME=>'cf' <br/>
（7） 禁用表 disable 'c' <br/>
（8） 添加协处理器的jar：<br/>
      ```java
      alter 'c', METHOD => 'table_att', 'coprocessor'=>'hdfs:///user/hbase_solr/hbase-increment-index.jar|com.hbase.easy.index.HbaseSolrIndexCoprocesser|1001|'  
      ```
<br/>（9）激活表 enable 'c' <br/>
（10）启动solr或者es集群， 然后在hbase shell或者 hbase java client进行put数据，然后等待查看索引里面是否正确添加数据，如果添加失败，查看hbase的regionserver的log，并根据提示解决<br/>
（11）如何卸载？<br/>
```
alter 'c',METHOD => 'table_att_unset',NAME =>'coprocessor$1' 
```
卸载，完成之后，激活表 

###典型异常
hbase的http-client组件与本例中用的最新的solr的http-client组件版本不一致导致，添加索引报错。 <br/>
解决办法： <br/>
使用solr的 <br/>
httpclient-4.3.1.jar <br/>
httpcore-4.3.jar <br/>
替换所有节点hbase/lib下的 <br/>
低版本的httpclient组件包，即可！ <br/>

###温馨提示
本项目主要所用技术有关hbasae协处理器，和solr或者elasticsearch集群的基本知识，如有不不熟悉者，
可以先从散仙的博客入门一下：
[我的Iteye博客](http://qindongliang.iteye.com/) <br/>

### QQ搜索技术交流群：206247899   公众号：我是攻城师（woshigcs） 如有问题，可在后台留言咨询
