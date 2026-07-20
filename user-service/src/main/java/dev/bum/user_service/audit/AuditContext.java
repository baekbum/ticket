package dev.bum.user_service.audit;

import dev.bum.common.service.user.user.enums.UserRole;
import org.springframework.util.StringUtils;

public final class AuditContext {

    private static final ThreadLocal<Actor> ACTOR = new ThreadLocal<>();

    private AuditContext() {
    }

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

    public static void clear() {
        ACTOR.remove();
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
