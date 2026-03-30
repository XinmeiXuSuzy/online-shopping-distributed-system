package com.shop.service.sqs;

import com.shop.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageProducer {

    private final SqsAsyncClient sqsAsyncClient;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    /**
     * Asynchronously sends an order message to SQS.
     * Uses orderId as the deduplication group key (FIFO queues only; standard queues ignore it).
     *
     * @return CompletableFuture carrying the SQS message ID on success
     */
    public CompletableFuture<String> send(OrderMessage message) {
        String body = JsonUtil.toJson(message);

        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .messageGroupId(String.valueOf(message.getOrderId()))   // for FIFO; ignored on standard
                .build();

        return sqsAsyncClient.sendMessage(request)
                .thenApply(SendMessageResponse::messageId)
                .whenComplete((msgId, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send SQS message for orderId={}: {}",
                                message.getOrderId(), ex.getMessage());
                    } else {
                        log.info("SQS message sent: orderId={}, messageId={}", message.getOrderId(), msgId);
                    }
                });
    }
}
