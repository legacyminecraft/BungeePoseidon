package net.md_5.bungee.protocol.skip;

import io.netty.buffer.ByteBuf;

public class ShortHeader extends Instruction {

    private final Instruction child;

    public ShortHeader(Instruction child) {
        this.child = child;
    }

    @Override
    public void read(ByteBuf in) {
        short size = in.readShort();
        for (short s = 0; s < size; s++) {
            child.read(in);
        }
    }
}
