package co.wethinkcode.logisticsconnect.mq;

import co.wethinkcode.logisticsconnect.StageEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.function.Consumer;

public class StageSubscriber implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(StageSubscriber.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final Consumer<StageEvent> handler;
    private final Thread thread;

    private volatile boolean running = true;
    private volatile Connection connection;

    public StageSubscriber(Consumer<StageEvent> handler) {
        this.handler = handler;
        this.thread = new Thread(this::run, "package-status-subscriber");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    private void run() {
        while (running) {
            try {
                connect();
                while (running && connection != null) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            } catch (Exception e) {
                if (running) {
                    log.warn("Broker unavailable at {}: {}. Retrying in 5s",
                            MqConfig.BROKER_URL, e.getMessage());
                }
            } finally {
                closeConnection();
            }
            pause(5000);
        }
    }

    private void connect() throws JMSException {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        Connection newConnection = factory.createConnection();
        try {
            newConnection.setExceptionListener(e -> {
                log.warn("Broker connection lost: {}", e.getMessage());
                closeConnection();
            });

            Session session = newConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = session.createConsumer(session.createTopic(MqConfig.TOPIC));
            consumer.setMessageListener(message -> {
                try {
                    if (message instanceof TextMessage text) {
                        StageEvent event = mapper.readValue(text.getText(), StageEvent.class);
                        handler.accept(event);
                        log.info("Received stage update for {}: stage {}",
                                event.hubId(), event.stage());
                    }
                } catch (Exception e) {
                    log.warn("Could not process stage message: {}", e.getMessage());
                }
            });

            newConnection.start();
            connection = newConnection;
            log.info("Subscribed to {} at {}", MqConfig.TOPIC, MqConfig.BROKER_URL);
        } catch (JMSException | RuntimeException e) {
            try {
                newConnection.close();
            } catch (JMSException ignored) {
            }
            throw e;
        }
    }

    private synchronized void closeConnection() {
        Connection current = connection;
        connection = null;
        if (current != null) {
            try {
                current.close();
            } catch (JMSException e) {
                log.debug("Error closing broker connection: {}", e.getMessage());
            }
        }
    }

    private void pause(long millis) {
        if (!running) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    @Override
    public void close() {
        running = false;
        thread.interrupt();
        closeConnection();
    }
}
