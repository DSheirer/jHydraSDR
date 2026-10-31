package io.github.dsheirer.hydrasdr;

/** Counters accumulated since streaming started. */
public record StreamingStats(long buffersReceived, long buffersProcessed, long buffersDropped) {
}
