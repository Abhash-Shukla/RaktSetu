package lifeline.api.service;

import lifeline.api.model.AuditLogEntity;
import lifeline.api.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(String actorUsername, String actorRole, String action, String entityType, String entityId, String details, String ipAddress) {
        AuditLogEntity entity = new AuditLogEntity(
            actorUsername,
            actorRole,
            action,
            entityType,
            entityId,
            details,
            ipAddress
        );
        auditLogRepository.save(entity);
    }
}
