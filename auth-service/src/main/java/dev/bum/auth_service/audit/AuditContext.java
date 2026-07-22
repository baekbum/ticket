package dev.bum.auth_service.audit;

import dev.bum.auth_service.jpa.Auth;
import dev.bum.common.service.user.user.enums.UserRole;

public final class AuditContext {

    private static final ThreadLocal<Actor> ACTOR = new ThreadLocal<>();

    private AuditContext() {
    }

    public static void setActor(Auth auth) {
        if (auth == null) {
            return;
        }

        ACTOR.set(new Actor(auth.getUserId(), toActorType(auth.getRole())));
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
