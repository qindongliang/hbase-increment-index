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
###性能分析
当前的版本的拓扑架构并不是最优的：<br/>

从可靠性上看：<br/>

它并不是高可靠的，因为这个版本仅仅是一个初级的版本，虽然优化了批处理的方式向索引提交数据，但是它使用的
jdk的容器类，所有的数据都会临时存在内存中，如果regionserver某一刻宕机，那么不能保证不会有数据丢失。

从性能上看：<br/>

它的吞吐性能比较低，因为考虑上索引批处理提交完，会清空临时缓存数据，而这一动作是需要加锁的，因为这个版本中，有两种自动提交索引的方式
<br/>第一种是达到某个阈值时提交<br/>
第二种是每间隔一定秒数提交<br/>
从而保证所有数据在常量时间内，肯定会被推送到索引中，当然前提是没有宕机或者其他的故障发生时，需要注意的是，这两个提交的Action发生后，都会清空缓存数据，以确保数据不会被重复提交，为了达到这个目的，在提交索引时，对方法进行了加锁和通过信号量控制线程协作，从而确保任何时候只有一个提交动作发生，产生了同步之后，在大批量插入数据时，性能会大幅度降低。
<br/>如何优化？<br/>
使用异步方式提交数据到一个队列中，如kakfa，然后索引数据时，从队列中读取，这样以来通过队列来中转保证数据的可靠性，索引线程不再需要加锁，对性能和吞吐也会比较大的提升。 有需要的朋友可以仿照这种思路扩展改进一下。


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


###   公众号：我是攻城师（woshigcs） 如有问题，可在后台留言咨询
