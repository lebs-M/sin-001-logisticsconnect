package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

public class StagePublisher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(StagePublisher.class);

    private final ObjectMapper mapper = new ObjectMapper();

    private Connection connection;
    private Session session;
    private MessageProducer producer;

    public synchronized void publish(StageEvent event) {
        try {
            ensureConnected();
            TextMessage message = session.createTextMessage(mapper.writeValueAsString(event));
            producer.send(message);
            log.info("Published stage update to {}: {}", MqConfig.TOPIC, message.getText());
        } catch (Exception e) {
            log.warn("Could not publish to {}: {}", MqConfig.TOPIC, e.getMessage());
            closeConnection();
        }
    }

    private void ensureConnected() throws JMSException {
        if (connection != null) {
            return;
        }

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        connection = factory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        producer = session.createProducer(session.createTopic(MqConfig.TOPIC));
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        log.info("Connected to broker {} for topic {}", MqConfig.BROKER_URL, MqConfig.TOPIC);
    }

    private synchronized void closeConnection() {
        Connection current = connection;
        connection = null;
        session = null;
        producer = null;
        if (current != null) {
            try {
                current.close();
            } catch (JMSException e) {
                log.debug("Error closing broker connection: {}", e.getMessage());
            }
        }
    }

    @Override
    public synchronized void close() {
        closeConnection();
    }
}
