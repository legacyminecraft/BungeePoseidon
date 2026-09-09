package net.md_5.bungee.protocol.skip;

import io.netty.buffer.ByteBuf;
import net.md_5.bungee.protocol.Protocol;

public class PacketReader {

    private final Protocol protocol;

    public PacketReader(Protocol protocol) {
        this.protocol = protocol;
    }

    public void tryRead(short packetId, ByteBuf in) {
        Instruction[] packetDef = this.protocol.getInstructions()[packetId];

        if (packetDef != null) {
            for (Instruction instruction : packetDef) {
                instruction.read(in);
            }
        }
    }
}
