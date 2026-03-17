package com.example.demo.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * MyBatis-Plus 配置类。
 * <p>
 * 配置目标：
 * 1. 开启下划线到驼峰命名转换。
 * 2. 开启 SQL 日志输出，便于联调排障。
 * 3. 注入 MyBatis-Plus 拦截器，预留分页等插件扩展点。
 * </p>
 */
@Configuration
@MapperScan("com.example.demo.mapper")
public class MyBatisPlusConfig {

    /**
     * MyBatis-Plus 插件拦截器。
     *
     * @return MybatisPlusInterceptor
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        return new MybatisPlusInterceptor();
    }

    /**
     * 自定义 SqlSessionFactory，统一配置驼峰转换与 SQL 日志。
     *
     * @param dataSource 数据源
     * @param mybatisPlusInterceptor MyBatis-Plus 拦截器
     * @return SqlSessionFactory
     * @throws Exception 创建失败时抛出
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource,
                                               MybatisPlusInterceptor mybatisPlusInterceptor) throws Exception {
        MybatisSqlSessionFactoryBean sessionFactoryBean = new MybatisSqlSessionFactoryBean();
        sessionFactoryBean.setDataSource(dataSource);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true); // 开启下划线转驼峰
        configuration.setLogImpl(StdOutImpl.class); // 打印 SQL 到控制台

        sessionFactoryBean.setConfiguration(configuration);
        sessionFactoryBean.setPlugins(mybatisPlusInterceptor);
        return sessionFactoryBean.getObject();
    }
}