package com.xxl.job.core.glue.impl;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.core.glue.GlueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotationUtils;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * @author xuxueli 2018-11-01
 */
public class SpringGlueFactory extends GlueFactory {
    private static Logger logger = LoggerFactory.getLogger(SpringGlueFactory.class);


    /**
     * inject action of spring
     * @param instance
     */
    @Override
    public void injectService(Object instance){
        if (instance==null) {
            return;
        }

        if (XxlJobSpringExecutor.getApplicationContext() == null) {
            return;
        }
        //获取类属性
        Field[] fields = instance.getClass().getDeclaredFields();
        for (Field field : fields) {
            //跳过静态
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            Object fieldBean = null;
            // with bean-id, bean could be found by both @Resource and @Autowired, or bean could only be found by @Autowired

            //判断是否有@Resource
            if (AnnotationUtils.getAnnotation(field, Resource.class) != null) {
                try {
                    Resource resource = AnnotationUtils.getAnnotation(field, Resource.class);
                    if (resource.name()!=null && resource.name().length()>0){
                        //先按照@Resource注解属性获取bean
                        fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(resource.name());
                    } else {
                        //按照属性名获取bean
                        fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getName());
                    }
                } catch (Exception e) {
                }
                if (fieldBean==null ) {
                    //按照属性类型获取bean
                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getType());
                }
                //判断是否有@Autowired
            } else if (AnnotationUtils.getAnnotation(field, Autowired.class) != null) {
                Qualifier qualifier = AnnotationUtils.getAnnotation(field, Qualifier.class);
                if (qualifier!=null && qualifier.value()!=null && qualifier.value().length()>0) {
                    //有Qualifier注解的，按Qualifier获取
                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(qualifier.value());
                } else {
                    //有Qualifier注解的，按Qualifier获取
                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getType());
                }
            }

            if (fieldBean!=null) {
                field.setAccessible(true);
                try {
                    //手动注入field实例依赖
                    field.set(instance, fieldBean);
                } catch (IllegalArgumentException e) {
                    logger.error(e.getMessage(), e);
                } catch (IllegalAccessException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

}
