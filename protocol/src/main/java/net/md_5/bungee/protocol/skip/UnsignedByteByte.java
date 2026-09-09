package net.md_5.bungee.protocol.skip;

import io.netty.buffer.ByteBuf;

public class UnsignedByteByte extends Instruction {

    @Override
    public void read(ByteBuf in) {
        int size = in.readUnsignedByte();
        in.skipBytes(size);
    }
}
