package com.funandfree.events.user;

public record AppUser(
        long id,
        String firebaseUid,
        String email,
        String displayName,
        Role role) {

    public boolean isOrganizer() {
        return role == Role.ORGANIZER;
    }
}
