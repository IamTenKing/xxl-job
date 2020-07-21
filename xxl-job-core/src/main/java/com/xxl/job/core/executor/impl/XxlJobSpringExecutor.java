package com.xxl.job.core.executor.impl;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.glue.GlueFactory;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.annotation.JobHandler;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.handler.impl.MethodJobHandler;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * xxl-job executor (for spring)
 *
 * 客户端配置一个执行器
 * 通过实现 InitializingBean, DisposableBean实现启动前，销毁前做一些事情
 *
 * @author xuxueli 2018-11-01 09:24:52
 */
public class XxlJobSpringExecutor extends XxlJobExecutor implements ApplicationContextAware, InitializingBean, DisposableBean {


    // start
    @Override
    public void afterPropertiesSet() throws Exception {

        // init JobHandler Repository
        //扫描有jobhandler的注解的类注册到map
        initJobHandlerRepository(applicationContext);

        // init JobHandler Repository (for method)
        //扫描XxlJob注解的方法注册到map
        initJobHandlerMethodRepository(applicationContext);

        // refresh GlueFactory
        //SpringGlueFactory提供手动进行依赖注入的方法
        GlueFactory.refreshInstance(1);

        // super start
        //启动一个nettyserver
        super.start();
    }

    // destroy
    @Override
    public void destroy() {
        super.destroy();
    }


    private void initJobHandlerRepository(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            return;
        }

        // init job handler action
        //获取JobHandler注解的实现类
        Map<String, Object> serviceBeanMap = applicationContext.getBeansWithAnnotation(JobHandler.class);

        if (serviceBeanMap != null && serviceBeanMap.size() > 0) {
            for (Object serviceBean : serviceBeanMap.values()) {
                if (serviceBean instanceof IJobHandler) {
                    String name = serviceBean.getClass().getAnnotation(JobHandler.class).value();
                    IJobHandler handler = (IJobHandler) serviceBean;
                    if (loadJobHandler(name) != null) {
                        throw new RuntimeException("xxl-job jobhandler[" + name + "] naming conflicts.");
                    }
                    //jobhandler放到map中
                    registJobHandler(name, handler);
                }
            }
        }
    }

    private void initJobHandlerMethodRepository(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            return;
        }

        // init job handler from method
        //获取所有的beanDefinition
        String[] beanDefinitionNames = applicationContext.getBeanDefinitionNames();
        for (String beanDefinitionName : beanDefinitionNames) {
            //遍历所有bean
            Object bean = applicationContext.getBean(beanDefinitionName);
            Method[] methods = bean.getClass().getDeclaredMethods();
            //遍历所有方法，判断是否有XxlJob注解
            for (Method method: methods) {
                //得到方法上面的注解
                XxlJob xxlJob = AnnotationUtils.findAnnotation(method, XxlJob.class);
                if (xxlJob != null) {

                    // name
                    String name = xxlJob.value();
                    if (name.trim().length() == 0) {
                        throw new RuntimeException("xxl-job method-jobhandler name invalid, for[" + bean.getClass() + "#"+ method.getName() +"] .");
                    }
                    if (loadJobHandler(name) != null) {
                        throw new RuntimeException("xxl-job jobhandler[" + name + "] naming conflicts.");
                    }

                    // execute method
                    if (!(method.getParameterTypes()!=null && method.getParameterTypes().length==1 && method.getParameterTypes()[0].isAssignableFrom(String.class))) {
                        throw new RuntimeException("xxl-job method-jobhandler param-classtype invalid, for[" + bean.getClass() + "#"+ method.getName() +"] , " +
                                "The correct method format like \" public ReturnT<String> execute(String param) \" .");
                    }
                    if (!method.getReturnType().isAssignableFrom(ReturnT.class)) {
                        throw new RuntimeException("xxl-job method-jobhandler return-classtype invalid, for[" + bean.getClass() + "#"+ method.getName() +"] , " +
                                "The correct method format like \" public ReturnT<String> execute(String param) \" .");
                    }
                    //暴力反射
                    method.setAccessible(true);

                    // init and destory
                    Method initMethod = null;
                    Method destroyMethod = null;

                    if(xxlJob.init().trim().length() > 0) {
                        try {
                            //通过xxljob注解配置init方法名得到init方法
                            initMethod = bean.getClass().getDeclaredMethod(xxlJob.init());
                            initMethod.setAccessible(true);
                        } catch (NoSuchMethodException e) {
                            throw new RuntimeException("xxl-job method-jobhandler initMethod invalid, for[" + bean.getClass() + "#"+ method.getName() +"] .");
                        }
                    }
                    if(xxlJob.destroy().trim().length() > 0) {
                        try {
                            destroyMethod = bean.getClass().getDeclaredMethod(xxlJob.destroy());
                            destroyMethod.setAccessible(true);
                        } catch (NoSuchMethodException e) {
                            throw new RuntimeException("xxl-job method-jobhandler destroyMethod invalid, for[" + bean.getClass() + "#"+ method.getName() +"] .");
                        }
                    }

                    // registry jobhandler
                    //通过xxljob配置的init、destroy方法创建一个IJobHandler实现类并进行注册
                    registJobHandler(name, new MethodJobHandler(bean, method, initMethod, destroyMethod));
                }
            }
        }
    }

    // ---------------------- applicationContext ----------------------
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

}
