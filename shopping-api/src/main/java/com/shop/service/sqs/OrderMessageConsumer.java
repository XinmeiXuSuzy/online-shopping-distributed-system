package com.shop.service.sqs;

import com.shop.domain.TccTransaction;
import com.shop.domain.enums.TccStatus;
import com.shop.mapper.TccTransactionMapper;
import com.shop.service.tcc.TccInventoryService;
import com.shop.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumer {

    private final SqsAsyncClient sqsAsyncClient;
    private final TccInventoryService tccInventoryService;
    private final TccTransactionMapper tccTransactionMapper;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    @Value("${aws.sqs.consumer.max-messages:10}")
    private int maxMessages;

    @Value("${aws.sqs.consumer.wait-time-seconds:20}")
    private int waitTimeSeconds;

    /**
     * Polls SQS every second (long-polling via waitTimeSeconds=20 means the call blocks
     * up to 20 seconds at the SQS side, so the scheduler interval of 1s is effectively
     * the gap between polls when the queue is empty).
     *
     * At-least-once delivery: messages are only deleted AFTER successful TCC confirm/cancel.
     * Idempotency: TccInventoryServiceImpl checks existing TCC status before acting.
     */
    @Scheduled(fixedDelay = 1000)
    public void poll() {
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(maxMessages)
                .waitTimeSeconds(waitTimeSeconds)
                .build();

        try {
            ReceiveMessageResponse response = sqsAsyncClient.receiveMessage(receiveRequest).join();
            List<Message> messages = response.messages();

            if (!messages.isEmpty()) {
                log.debug("Received {} SQS messages", messages.size());
            }

            for (Message msg : messages) {
                processMessage(msg);
            }
        } catch (Exception e) {
            log.error("Error polling SQS: {}", e.getMessage(), e);
        }
    }

    private void processMessage(Message msg) {
        OrderMessage orderMessage;
        try {
            orderMessage = JsonUtil.fromJson(msg.body(), OrderMessage.class);
        } catch (Exception e) {
            log.error("Unparseable SQS message body, discarding: {}", msg.body());
            deleteMessage(msg.receiptHandle());
            return;
        }

        Long orderId = orderMessage.getOrderId();
        log.info("Processing SQS message: orderId={}", orderId);

        try {
            // Idempotency guard: check TCC status before processing
            Optional<TccTransaction> tccOpt = tccTransactionMapper.findByOrderId(orderId);
            if (tccOpt.isPresent() && tccOpt.get().getStatus() != TccStatus.TRYING) {
                log.warn("Order {} already processed (status={}), skipping", orderId, tccOpt.get().getStatus());
                deleteMessage(msg.receiptHandle());
                return;
            }

            // Business validation (payment, fraud check, etc.) — extend here
            boolean isValid = validateOrder(orderMessage);

            if (isValid) {
                tccInventoryService.confirm(orderId);
                log.info("Order {} confirmed successfully", orderId);
            } else {
                tccInventoryService.cancel(orderId, "Order validation failed");
                log.warn("Order {} cancelled — validation failed", orderId);
            }

            // Delete only after successful TCC phase — ensures at-least-once processing
            deleteMessage(msg.receiptHandle());

        } catch (Exception e) {
            log.error("Error processing orderId={}: {}", orderId, e.getMessage(), e);
            // Do NOT delete — SQS will redeliver up to maxReceiveCount times, then route to DLQ
        }
    }

    /**
     * Stub for business validation logic.
     * In production, replace with: payment gateway call, fraud score check, address validation, etc.
     */
    private boolean validateOrder(OrderMessage message) {
        return true;
    }

    private void deleteMessage(String receiptHandle) {
        sqsAsyncClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build())
                .whenComplete((resp, ex) -> {
                    if (ex != null) {
                        log.error("Failed to delete SQS message: {}", ex.getMessage());
                    }
                });
    }
}
