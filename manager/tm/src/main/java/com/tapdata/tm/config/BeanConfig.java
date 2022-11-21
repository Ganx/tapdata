package com.tapdata.tm.config;

import com.tapdata.manager.common.utils.DateUtil;
import com.tapdata.tm.dag.convert.DagDeserializeConvert;
import com.tapdata.tm.dag.convert.DagSerializeConvert;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

import javax.annotation.PostConstruct;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author lg<lirufei0808 @ gmail.com>
 * @date 2020/9/10 8:26 下午
 * @description
 */
@Configuration
@Slf4j
public class BeanConfig {
    @Value("${server.keepAliveTimeout:60000}")
    private String keepAliveTimeoutStr;
    @Value("${server.maxKeepAliveRequests:1024}")
    private String maxKeepAliveRequestsStr;

    private final MappingMongoConverter mappingMongoConverter;

    public BeanConfig(MappingMongoConverter mappingMongoConverter, MongoCustomConversions customConversions) {
        this.mappingMongoConverter = mappingMongoConverter;
    }

    @PostConstruct
    public void customConvert() {
        DefaultConversionService conversionService = (DefaultConversionService) mappingMongoConverter.getConversionService();
        conversionService.addConverter(new GenericConverter() {
            @Override
            public Set<ConvertiblePair> getConvertibleTypes() {
                return new HashSet<ConvertiblePair>(){{
                    add(new ConvertiblePair(Double.class, Date.class));
                    add(new ConvertiblePair(String.class, Date.class));
                }};
            }

            @Override
            public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
                if (source == null)
                    return null;
                if (targetType.getType() == Date.class) {
                    if (source instanceof Double) {
                        return new Date(((Double)source).longValue());
                    }
                    if (source instanceof String) {
                        try {
                            return DateUtil.parse((String)source);
                        } catch (ParseException e) {
                            log.error("Parse string ({}) as date failed", source);
                        }
                    }
                }
                log.warn("Parse {} as date failed, source type is {}", source, source.getClass().getSimpleName());
                return null;
            }
        });

        List<Object> converters = new ArrayList<>();
        converters.add(new DagSerializeConvert());
        converters.add(new DagDeserializeConvert());
        mappingMongoConverter.setCustomConversions(new MongoCustomConversions(converters));
        conversionService.addConverter(new DagSerializeConvert());
        conversionService.addConverter(new DagDeserializeConvert());
    }

    @Bean(name = "multipartResolver")
    public MultipartResolver multipartResolver() {
        CommonsMultipartResolver resolver = new CommonsMultipartResolver();
        resolver.setDefaultEncoding("UTF-8");
        return resolver;
    }

    @Bean
    public TomcatServletWebServerFactory tomcatServletWebServerFactory(){
        int keepAliveTimeout = parseInt(keepAliveTimeoutStr, 60000);
        int maxKeepAliveRequests = parseInt(maxKeepAliveRequestsStr, 1024);

        TomcatServletWebServerFactory tomcatServletWebServerFactory = new TomcatServletWebServerFactory();
        tomcatServletWebServerFactory.addConnectorCustomizers((connector)->{
            ProtocolHandler protocolHandler = connector.getProtocolHandler();
            if(protocolHandler instanceof Http11NioProtocol){
                Http11NioProtocol http11NioProtocol = (Http11NioProtocol)protocolHandler;
                http11NioProtocol.setKeepAliveTimeout(keepAliveTimeout);//millisecond
                http11NioProtocol.setMaxKeepAliveRequests(maxKeepAliveRequests);
            }
        });
        return tomcatServletWebServerFactory;
    }

    private Pattern pattern = Pattern.compile("^\\d+$");
    private int parseInt(String str, int defaultVal) {
        Matcher m = pattern.matcher(str);
        if (m.matches()) {
            return Integer.parseInt(str);
        }
        return defaultVal;
    }
}
