package io.github.dsheirer.hydrasdr;

/** An RF component of a device, such as its tuner or clock generator. */
public record ComponentInfo(int type, String name, long registerCount) {
}
