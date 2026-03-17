//package com.lyhn.coreworkflowjava.queue;
//
//import com.lyhn.coreworkflowjava.workflow.flow.entity.StreamMessage;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
//
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicLong;
//
//@Slf4j
//@Component
//public class DualQueueManager {
//    // 接收队列：容量大，允许快速堆积
//    private final ConcurrentHashMap<String,LinkedBlockingQueue<StreamMessage>> receiveQueues
//        = new ConcurrentHashMap<>();
//
//    // 发送队列：容量适中，存放排好序的消息
//    private final ConcurrentHashMap<String,LinkedBlockingQueue<StreamMessage>> sendQueues
//        = new ConcurrentHashMap<>();
//
//    // 序号追踪器：记录每个会话期望的下一个序号
//    private final ConcurrentHashMap<String,AtomicLong> expectedSequences
//            = new ConcurrentHashMap<>();
//
//    // 乱序缓冲区：暂存提前到达的消息
//    private final ConcurrentHashMap<String,ConcurrentSkipListMap<Long,StreamMessage>> reorderBuffers
//        = new ConcurrentHashMap<>();
//
//    // 线程池
//    private final ExecutorService receiveExecutor = Executors.newCachedThreadPool();
//    private final ExecutorService sendExecutor = Executors.newCachedThreadPool();
//
//    /**
//     * 初始化会话的双队列
//     */
//    public void initSession(String sessionId, SseEmitter emitter) {
//        // 接收队列：容量 1000，允许上游快速写入
//        receiveQueues.put(sessionId, new LinkedBlockingQueue<>(1000));
//        // 发送队列：容量 100，发送速度相对稳定
//        sendQueues.put(sessionId, new LinkedBlockingQueue<>(100));
//        // 序号从 1 开始
//        expectedSequences.put(sessionId, new AtomicLong(1));
//        // 乱序缓冲区，按序号排序
//        reorderBuffers.put(sessionId, new ConcurrentSkipListMap<>());
//
//        // 启动处理线程：从接收队列取，处理后放入发送队列
//        receiveExecutor.submit(() -> processReceiveQueue(sessionId));
//
//        // 启动发送线程：从发送队列取，发送给前端
//        sendExecutor.submit(() -> processSendQueue(sessionId, emitter));
//    }
//
//    /**
//     * 上游数据入队（快速入队，不阻塞上游）
//     */
//    public boolean enqueue(String sessionId, StreamMessage message) {
//        BlockingQueue queue = receiveQueues.get(sessionId);
//        if (queue == null) {
//            return false;
//        }
//
//        // 使用 offer 而不是 put，避免阻塞
//        boolean success = queue.offer(message);
//        if (!success) {
//            log.warn("接收队列已满，消息被丢弃: sessionId={}, seq={}",
//                    sessionId, message.getSequence());
//        }
//        return success;
//    }
//
//    private void processReceiveQueue(String sessionId) {
//        BlockingQueue receiveQueue = receiveQueues.get(sessionId);
//        BlockingQueue sendQueue = sendQueues.get(sessionId);
//        AtomicLong expectedSeq = expectedSequences.get(sessionId);
//        ConcurrentSkipListMap reorderBuffer = reorderBuffers.get(sessionId);
//
//    }
//}