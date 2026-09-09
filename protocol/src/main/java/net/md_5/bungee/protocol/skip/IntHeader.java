package net.md_5.bungee.protocol.skip;

import io.netty.buffer.ByteBuf;

public class IntHeader extends Instruction {

    private final Instruction child;

    public IntHeader(Instruction child) {
        this.child = child;
    }

    @Override
    public void read(ByteBuf in) {
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            child.read(in);
        }
    }
}
