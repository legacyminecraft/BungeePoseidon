package net.md_5.bungee.protocol.skip;

import io.netty.buffer.ByteBuf;

public class Jump extends Instruction {

    final int len;

    public Jump(int len) {
        if (len < 0) {
            throw new IndexOutOfBoundsException();
        }
        this.len = len;
    }

    @Override
    public void read(ByteBuf in) {
        in.skipBytes(len);
    }
}
