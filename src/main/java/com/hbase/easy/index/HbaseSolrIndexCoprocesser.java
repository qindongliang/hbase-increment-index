package com.hbase.easy.index;

import com.hbase.easy.solr.SolrIndexTools;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.coprocessor.BaseRegionObserver;
import org.apache.hadoop.hbase.coprocessor.ObserverContext;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.regionserver.wal.WALEdit;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Created by qindongliang on 2016/2/15.
 *
 * 为hbase提供二级索引的协处理器 Coprocesser
 *
 */
public class HbaseSolrIndexCoprocesser extends BaseRegionObserver {
    //加载配置文件属性
    static Config config=ConfigFactory.load("application.properties");

    //log记录
    private static final Logger logger = LoggerFactory.getLogger(HbaseSolrIndexCoprocesser.class);


    @Override
    public void postPut(ObserverContext<RegionCoprocessorEnvironment> e, Put put, WALEdit edit, Durability durability) throws IOException {
        String rowkey = Bytes.toString(put.getRow());//得到rowkey
        SolrInputDocument doc =new SolrInputDocument();//实例化索引Doc
        doc.addField(config.getString("solr_hbase_rowkey_name"),rowkey);//添加主键
        for(String cf:config.getString("hbase_column_family").split(",")) {//遍历所有的列簇
            for (Cell kv : put.getFamilyCellMap().get(Bytes.toBytes(cf))) {
                String name=Bytes.toString(CellUtil.cloneQualifier(kv));//获取列名
                String value=Bytes.toString(kv.getValueArray());//获取列值 or CellUtil.cloneValue(kv)
                doc.addField(name,value);//添加到索引doc里面
            }
        }
        //发送数据到本地缓存
        SolrIndexTools.addDoc(doc);
    }

    @Override
    public void postDelete(ObserverContext<RegionCoprocessorEnvironment> e, Delete delete, WALEdit edit, Durability durability) throws IOException {
        //得到rowkey
        String rowkey = Bytes.toString(delete.getRow());
        //发送数据本地缓存
        SolrIndexTools.delDoc(rowkey);
    }
}