package com.funandfree.events.jobs;

import java.util.List;

public record ResendEmailRequest(
        String from,
        List<String> to,
        String subject,
        String text) {
}
