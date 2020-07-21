package com.xxl.job.admin.core.scheduler;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.thread.*;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.core.biz.ExecutorBiz;
import com.xxl.job.core.enums.ExecutorBlockStrategyEnum;
import com.xxl.rpc.remoting.invoker.call.CallType;
import com.xxl.rpc.remoting.invoker.reference.XxlRpcReferenceBean;
import com.xxl.rpc.remoting.invoker.route.LoadBalance;
import com.xxl.rpc.remoting.net.impl.netty_http.client.NettyHttpClient;
import com.xxl.rpc.serialize.impl.HessianSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author xuxueli 2018-10-28 00:18:17
 */

public class XxlJobScheduler  {
    private static final Logger logger = LoggerFactory.getLogger(XxlJobScheduler.class);


    public void init() throws Exception {
        // init i18n
        initI18n();

        // admin registry monitor run
        //注册监控，启动一个异步守护线程做
        JobRegistryMonitorHelper.getInstance().start();

        // admin monitor run
        //启动一个异步线程做监控警告
        JobFailMonitorHelper.getInstance().start();

        // admin trigger pool start
        //启动两个线程池去执行定时任务
        JobTriggerPoolHelper.toStart();

        // admin log report start
        //启动一个线程异步记录日志
        JobLogReportHelper.getInstance().start();

        // start-schedule
        JobScheduleHelper.getInstance().start();

        logger.info(">>>>>>>>> init xxl-job admin success.");
    }

    
    public void destroy() throws Exception {

        // stop-schedule
        JobScheduleHelper.getInstance().toStop();

        // admin log report stop
        JobLogReportHelper.getInstance().toStop();

        // admin trigger pool stop
        JobTriggerPoolHelper.toStop();

        // admin monitor stop
        JobFailMonitorHelper.getInstance().toStop();

        // admin registry stop
        JobRegistryMonitorHelper.getInstance().toStop();

    }

    // ---------------------- I18n ----------------------

    private void initI18n(){
        for (ExecutorBlockStrategyEnum item:ExecutorBlockStrategyEnum.values()) {
            item.setTitle(I18nUtil.getString("jobconf_block_".concat(item.name())));
        }
    }

    // ---------------------- executor-client ----------------------
    private static ConcurrentMap<String, ExecutorBiz> executorBizRepository = new ConcurrentHashMap<String, ExecutorBiz>();

    //xxl-RPC远程调用bean封装
    public static ExecutorBiz getExecutorBiz(String address) throws Exception {
        // valid
        if (address==null || address.trim().length()==0) {
            return null;
        }

        // load-cache
        address = address.trim();
        ExecutorBiz executorBiz = executorBizRepository.get(address);
        //当前地址已有执行器，直接返回，借鉴了spring三级缓存的设计理念
        if (executorBiz != null) {
            return executorBiz;
        }

        // set-cache
        //xxl-rpc-core：远程调用组件，xxl RPC框架组件
        XxlRpcReferenceBean referenceBean = new XxlRpcReferenceBean();
        //协议
        referenceBean.setClient(NettyHttpClient.class);
        //序列化方式
        referenceBean.setSerializer(HessianSerializer.class);
        //同步或异步调用方式
        referenceBean.setCallType(CallType.SYNC);
        //负载均衡策略
        referenceBean.setLoadBalance(LoadBalance.ROUND);
        //门面类
        referenceBean.setIface(ExecutorBiz.class);
        referenceBean.setVersion(null);
        //设置超时时间
        referenceBean.setTimeout(3000);
        //地址
        referenceBean.setAddress(address);
        //认证token
        referenceBean.setAccessToken(XxlJobAdminConfig.getAdminConfig().getAccessToken());
        //回调方法
        referenceBean.setInvokeCallback(null);

        referenceBean.setInvokerFactory(null);

        //此处通过动态代理返回代理类
        executorBiz = (ExecutorBiz) referenceBean.getObject();
        //执行器仓库
        executorBizRepository.put(address, executorBiz);
        return executorBiz;
    }

}
