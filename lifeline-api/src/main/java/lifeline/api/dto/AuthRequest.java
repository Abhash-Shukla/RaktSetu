package lifeline.api.dto;

public record AuthRequest(
    String username,
    String password
) {}
