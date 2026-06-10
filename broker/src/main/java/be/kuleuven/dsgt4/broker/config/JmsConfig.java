package be.kuleuven.dsgt4.broker.config;

import jakarta.jms.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

/*
   JMS wiring for the 'async' profile only: the embedded Artemis settings live in
   application-async.properties and OrderQueueListener consumes the order queue. The
   default (synchronous) profile starts no listener infrastructure at all.
*/
@Configuration
@EnableJms
@Profile("async")
public class JmsConfig {

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrency("1-2");
        return factory;
    }
}
