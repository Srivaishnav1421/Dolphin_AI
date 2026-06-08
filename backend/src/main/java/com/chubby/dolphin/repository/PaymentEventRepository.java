package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, String> {
    Optional<PaymentEvent> findByPaymentEventId(String eventId);
}
