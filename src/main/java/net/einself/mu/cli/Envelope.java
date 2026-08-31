package net.einself.mu.cli;

public record Envelope<T>(String command, T data) {
}