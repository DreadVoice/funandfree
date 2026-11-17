package com.funandfree.events.spike;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AmqpSpike {

    private final RabbitTemplate rabbit;
    private final AmqpAdmin admin;
    private final boolean enabled;
    private final String queueName;

    AmqpSpike(RabbitTemplate rabbit,
              AmqpAdmin admin,
              @Value("${app.amqp.enabled}") boolean enabled,
              @Value("${app.amqp.queue}") String queueName) {
        this.rabbit = rabbit;
        this.admin = admin;
        this.enabled = enabled;
        this.queueName = queueName;
    }

    public void verify() {
        if (!enabled) {
            System.out.println("amqp spike skipped (app.amqp.enabled=false)");
            return;
        }

        String probe = queueName + ".spike";
        String payload = "{\"eventId\":1,\"userId\":2}";

        System.out.println("--- amqp spike start ---");
        admin.declareQueue(new Queue(probe, true, false, false));
        System.out.println("declared queue " + probe);

        rabbit.convertAndSend(probe, payload);
        System.out.println("published " + payload);

        Object received = rabbit.receiveAndConvert(probe, 5000);
        System.out.println("received " + received);

        admin.deleteQueue(probe);

        if (!payload.equals(received)) {
            throw new IllegalStateException("amqp round trip mismatch, received " + received);
        }
        System.out.println("--- amqp spike ok ---");
    }
}
