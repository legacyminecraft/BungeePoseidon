package net.md_5.bungee.protocol.skip;

import io.netty.buffer.ByteBuf;

public class Item extends Instruction {

    @Override
    public void read(ByteBuf in) {
        short type = in.readShort();
        if (type >= 0) {
            in.skipBytes(3);
        }
    }
}
