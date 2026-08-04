package net.einself.mu.shared;

public record Envelope<T>(String command, T data) {
}
