package com.blackcat.common.config;

import com.blackcat.common.config.properties.OssProperties;
import com.blackcat.common.config.template.OssTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author Jason
 * @date 2022/5/9
 * hello ashen one
 */
@EnableConfigurationProperties(
        {
                OssProperties.class,
        }
)

public class CloudDiskAutoConfiguration {
        @Bean
        public OssTemplate ossTemplate(OssProperties ossProperties){
                return  new OssTemplate(ossProperties);
        }
}
