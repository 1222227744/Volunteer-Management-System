package com.volunteer.vms.notification;

import com.volunteer.vms.user.User;
import com.volunteer.vms.user.UserRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExternalNotificationService {
    private final ExternalNotificationTaskRepository taskRepository;
    private final UserRepository userRepository;

    public ExternalNotificationService(ExternalNotificationTaskRepository taskRepository,
                                       UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    @Async
    @Transactional
    public void enqueueForUser(Long userId, String title, String content) {
        userRepository.findById(userId).ifPresent(user -> {
            List<ExternalNotificationTask> tasks = List.of(
                    buildTask(user, ExternalNotificationChannel.EMAIL, title, content),
                    buildTask(user, ExternalNotificationChannel.SMS, title, content)
            );
            taskRepository.saveAll(tasks).forEach(this::sendOnce);
        });
    }

    @Transactional
    public ExternalNotificationTask retry(Long taskId) {
        ExternalNotificationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new com.volunteer.vms.common.BizException(org.springframework.http.HttpStatus.NOT_FOUND, "外部通知任务不存在"));
        sendOnce(task);
        return taskRepository.save(task);
    }

    @Transactional
    public int retryFailed() {
        List<ExternalNotificationTask> retryable = taskRepository.findByStatusOrderByCreatedAtAsc(ExternalNotificationStatus.FAILED)
                .stream()
                .filter(task -> task.getRetryCount() < task.getMaxRetries())
                .toList();
        retryable.forEach(this::sendOnce);
        taskRepository.saveAll(retryable);
        return retryable.size();
    }

    private ExternalNotificationTask buildTask(User user,
                                               ExternalNotificationChannel channel,
                                               String title,
                                               String content) {
        ExternalNotificationTask task = new ExternalNotificationTask();
        task.setUserId(user.getId());
        task.setChannel(channel);
        task.setTitle(title);
        task.setContent(content);
        task.setRecipient(resolveRecipient(user, channel));
        task.setMaxRetries(3);
        return task;
    }

    private void sendOnce(ExternalNotificationTask task) {
        task.setLastTriedAt(LocalDateTime.now());
        task.setRetryCount(task.getRetryCount() + 1);
        try {
            simulateSend(task);
            task.setStatus(ExternalNotificationStatus.SENT);
            task.setSentAt(LocalDateTime.now());
            task.setLastError(null);
        } catch (RuntimeException ex) {
            task.setStatus(ExternalNotificationStatus.FAILED);
            task.setLastError(ex.getMessage());
        }
    }

    private void simulateSend(ExternalNotificationTask task) {
        if (task.getRecipient() == null || task.getRecipient().isBlank()) {
            throw new IllegalStateException("缺少" + translateChannel(task.getChannel()) + "接收地址");
        }
    }

    private String resolveRecipient(User user, ExternalNotificationChannel channel) {
        if (channel == ExternalNotificationChannel.SMS) {
            return user.getPhone();
        }
        return user.getUsername() + "@example.local";
    }

    private String translateChannel(ExternalNotificationChannel channel) {
        return channel == ExternalNotificationChannel.SMS ? "短信" : "邮件";
    }
}
