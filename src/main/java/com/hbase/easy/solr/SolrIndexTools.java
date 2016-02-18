package com.hbase.easy.solr;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

/**
 * Created by qindongliang on 2016/2/15.
 * solr索引处理客户端
 * 注意问题，并发提交时，需要线程协作资源
 */
public class SolrIndexTools  {
    //加载配置文件属性
    static Config config= ConfigFactory.load("application.properties");
    //log记录
    private static final Logger logger = LoggerFactory.getLogger(SolrIndexTools.class);
    //实例化solr的client，如果用的是cloud模式，
    static SolrClient client=null;
    //添加批处理阈值
    static int add_batchCount=config.getInt("add_batchCount");
    //删除的批处理阈值
    static int del_batchCount=config.getInt("del_batchCount");
    //添加的集合缓冲
    static List<SolrInputDocument> add_docs=new ArrayList<SolrInputDocument>();
    //删除的集合缓冲
    static List<String> del_docs=new ArrayList<String>();

    static {
        logger.info("初始化索引调度........");
        if(config.getBoolean("is_solrcloud")){
            client=new CloudSolrClient(config.getString("solr_url"));//cloud模式
        }else{
            client=new HttpSolrClient(config.getString("solr_url"));//单机模式
        }
        //启动定时任务，第一次延迟1s执行,之后每隔指定时间30S执行一次
        Timer timer = new Timer();
        timer.schedule(new SolrCommit(), config.getInt("first_delay") * 1000, config.getInt("interval_commit_index") * 1000);
    }

    public static class SolrCommit extends TimerTask{
        @Override
        public void run() {

            logger.info("索引线程运行中........");
            //只有等于true时才执行下面的提交代码
                try {
                    semp.acquire();//获取信号量
                    if (add_docs.size() > 0) {
                        client.add(add_docs);//添加
                    }
                    if (del_docs.size() > 0) {
                        client.deleteById(del_docs);//删除
                    }
                    //确保都有数据才提交
                    if (add_docs.size() > 0 || del_docs.size() > 0) {
                        client.commit();//共用一个提交策略
                        //清空缓冲区的添加和删除数据
                        add_docs.clear();
                        del_docs.clear();
                    } else {
                        logger.info("暂无索引数据，跳过commit，继续监听......");
                    }
                } catch (Exception e) {
                    logger.error("间隔提交索引数据出错！", e);
                }finally {
                    semp.release();//释放信号量
                }


        }
    }




    /**
     * 添加数据到临时存储中，如果
     * 大于等于batchCount时，就提交一次，
     * 再清空集合,其他情况下走对应的时间间隔提交
     * @param doc 单个document对象
     * */
    public  static   void addDoc(SolrInputDocument doc){
        commitIndex(add_docs,add_batchCount,doc,true);
    }



    /***
     * 删除的数据添加到临时存储中，如果大于
     * 对应的批处理就直接提交，再清空集合，
     * 其他情况下走对应的时间间隔提交
     * @param rowkey 删除的rowkey
     */
    public static    void delDoc(String rowkey){
        commitIndex(del_docs,del_batchCount,rowkey,false);
    }

    // 任何时候，保证只能有一个线程在提交索引，并清空集合
    final static Semaphore semp = new Semaphore(1);


    /***
     * 此方法需要加锁，并且提交索引时，与时间间隔提交是互斥的
     * 百分百确保不会丢失数据
     * @param datas 用来提交的数据集合
     * @param count 对应的集合提交数量
     * @param doc   添加的单个doc
     * @param isAdd 是否为添加动作
     */
    public synchronized static void commitIndex(List datas,int count,Object doc,boolean isAdd){
        try {
            semp.acquire();//获取信号量
            if (datas.size() >= count) {

                    if (isAdd) {
                        client.add(datas);//添加数据到服务端中
                    } else {
                        client.deleteById(datas);//删除数据
                    }
                    client.commit();//提交数据

                    datas.clear();//清空临时集合


            }
        }catch (Exception e){
            logger.error("按阈值"+(isAdd==true?"添加":"删除")+"操作索引数据出错！",e);
        }finally {
            datas.add(doc);//添加单条数据
            semp.release();//释放信号量
        }

    }







}
