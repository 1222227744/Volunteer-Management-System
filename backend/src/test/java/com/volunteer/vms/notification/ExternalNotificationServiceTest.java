package com.volunteer.vms.notification;

import com.volunteer.vms.user.User;
import com.volunteer.vms.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalNotificationServiceTest {
    @Mock
    private ExternalNotificationTaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    private ExternalNotificationService service;

    @BeforeEach
    void setUp() {
        service = new ExternalNotificationService(
                taskRepository,
                userRepository,
                new SimulatedExternalNotificationSender()
        );
    }

    @Test
    void enqueueForUserShouldCreateEmailAndSmsTasks() {
        User user = new User();
        user.setId(8L);
        user.setUsername("volunteer");
        user.setDisplayName("志愿者");
        user.setPhone("13800000000");
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        when(taskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.enqueueForUser(8L, "活动提醒", "请准时参加");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExternalNotificationTask>> captor = ArgumentCaptor.forClass(List.class);
        verify(taskRepository).saveAll(captor.capture());
        List<ExternalNotificationTask> tasks = captor.getValue();
        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(ExternalNotificationTask::getChannel)
                .containsExactly(ExternalNotificationChannel.EMAIL, ExternalNotificationChannel.SMS);
        assertThat(tasks).allSatisfy(task -> {
            assertThat(task.getStatus()).isEqualTo(ExternalNotificationStatus.SENT);
            assertThat(task.getRetryCount()).isEqualTo(1);
            assertThat(task.getLastError()).isNull();
        });
    }

    @Test
    void retryFailedShouldRecordFailureReasonAndSkipExhaustedTasks() {
        ExternalNotificationTask retryable = failedTask(ExternalNotificationChannel.EMAIL, "", 0, 2);
        ExternalNotificationTask exhausted = failedTask(ExternalNotificationChannel.SMS, "", 2, 2);
        when(taskRepository.findByStatusOrderByCreatedAtAsc(ExternalNotificationStatus.FAILED))
                .thenReturn(List.of(retryable, exhausted));
        when(taskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int retried = service.retryFailed();

        assertThat(retried).isEqualTo(1);
        assertThat(retryable.getRetryCount()).isEqualTo(1);
        assertThat(retryable.getStatus()).isEqualTo(ExternalNotificationStatus.FAILED);
        assertThat(retryable.getLastError()).isEqualTo("缺少邮件接收地址");
        assertThat(exhausted.getRetryCount()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExternalNotificationTask>> captor = ArgumentCaptor.forClass(List.class);
        verify(taskRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(retryable);
    }

    private ExternalNotificationTask failedTask(ExternalNotificationChannel channel,
                                                String recipient,
                                                int retryCount,
                                                int maxRetries) {
        ExternalNotificationTask task = new ExternalNotificationTask();
        task.setUserId(8L);
        task.setChannel(channel);
        task.setTitle("提醒");
        task.setContent("内容");
        task.setRecipient(recipient);
        task.setStatus(ExternalNotificationStatus.FAILED);
        task.setRetryCount(retryCount);
        task.setMaxRetries(maxRetries);
        return task;
    }
}
