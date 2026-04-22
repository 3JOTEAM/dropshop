package com.example.dropshop.domain.queue.service;


import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.example.dropshop.common.exception.ServiceException;
import com.example.dropshop.domain.drops.repository.DropsRepository;
import com.example.dropshop.domain.queue.repository.QueueRepository;
import com.example.dropshop.domain.queue.repository.QueueTokenRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

  @Mock
  private QueueRepository queueRepository;

  @Mock
  private QueueTokenRepository queueTokenRepository;

  @Mock
  private DropsRepository dropsRepository;

  @InjectMocks
  private QueueService queueService;

  @Test
  void 드랍이_존재하지_않음() {
    // given
    given(dropsRepository.findById(1L)).willReturn(Optional.empty());

    // when && then
    assertThrows(ServiceException.class,
        () -> queueService.decideQueue(1L, 1L));
  }


}