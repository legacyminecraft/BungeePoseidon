package net.md_5.bungee.protocol;

import io.netty.buffer.ByteBuf;
import net.md_5.bungee.protocol.packet.DefinedPacket;
import net.md_5.bungee.protocol.skip.Instruction;
import net.md_5.bungee.protocol.skip.PacketReader;

import java.lang.reflect.Constructor;

public interface Protocol {

    PacketReader getSkipper();

    DefinedPacket read(short packetId, ByteBuf buf);

    Instruction[][] getInstructions();

    Class<? extends DefinedPacket>[] getClasses();

    Constructor<? extends DefinedPacket>[] getConstructors();
}
