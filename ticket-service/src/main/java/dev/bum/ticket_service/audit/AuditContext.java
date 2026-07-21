package dev.bum.ticket_service.audit;

import dev.bum.common.service.user.user.enums.UserRole;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.Map;

@NoArgsConstructor
public final class AuditContext {

    private static final ThreadLocal<Actor> ACTOR = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> BEFORE_DATA = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> AFTER_DATA = new ThreadLocal<>();

    public static void setActor(String actorId, UserRole role) {
        setActor(actorId, toActorType(role));
    }

    public static void setActor(String actorId, String actorType) {
        if (!StringUtils.hasText(actorId)) {
            return;
        }

        ACTOR.set(new Actor(actorId, actorType));
    }

    public static String getActorId() {
        Actor actor = ACTOR.get();
        return actor != null ? actor.getActorId() : null;
    }

    public static String getActorType() {
        Actor actor = ACTOR.get();
        return actor != null ? actor.getActorType() : null;
    }

    public static void setBeforeData(Map<String, Object> beforeData) {
        BEFORE_DATA.set(beforeData);
    }

    public static Map<String, Object> getBeforeData() {
        return BEFORE_DATA.get();
    }

    public static void setAfterData(Map<String, Object> afterData) {
        AFTER_DATA.set(afterData);
    }

    public static Map<String, Object> getAfterData() {
        return AFTER_DATA.get();
    }

    public static void clear() {
        ACTOR.remove();
        BEFORE_DATA.remove();
        AFTER_DATA.remove();
    }

    private static String toActorType(UserRole role) {
        if (role == UserRole.ROLE_ADMIN) {
            return "ADMIN";
        }

        return "USER";
    }

    private static class Actor {

        private final String actorId;
        private final String actorType;

        private Actor(String actorId, String actorType) {
            this.actorId = actorId;
            this.actorType = actorType;
        }

        private String getActorId() {
            return actorId;
        }

        private String getActorType() {
            return actorType;
        }
    }
}
