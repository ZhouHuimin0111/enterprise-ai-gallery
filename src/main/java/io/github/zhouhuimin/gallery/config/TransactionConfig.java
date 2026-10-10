package io.github.zhouhuimin.gallery.config;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Configuration
public class TransactionConfig {
    @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
        var tx=new TransactionTemplate(manager);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);tx.setTimeout(20);return tx;
    }
}
