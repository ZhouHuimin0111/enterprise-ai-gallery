package io.github.zhouhuimin.gallery.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseConfig {
    @Bean MybatisPlusInterceptor pagination() {
        var interceptor=new MybatisPlusInterceptor();
        var page=new PaginationInnerInterceptor(DbType.MYSQL); page.setMaxLimit(50L); page.setOverflow(false);
        interceptor.addInnerInterceptor(page); return interceptor;
    }
}
