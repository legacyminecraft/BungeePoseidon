package net.md_5.bungee.protocol;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import net.md_5.bungee.protocol.packet.DefinedPacket;
import net.md_5.bungee.protocol.packet.Packet1Login;
import net.md_5.bungee.protocol.packet.Packet2Handshake;
import net.md_5.bungee.protocol.packet.Packet3Chat;
import net.md_5.bungee.protocol.packet.Packet9Respawn;
import net.md_5.bungee.protocol.packet.PacketFAPluginMessage;
import net.md_5.bungee.protocol.packet.PacketFFKick;
import net.md_5.bungee.protocol.skip.Instruction;
import net.md_5.bungee.protocol.skip.PacketReader;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static net.md_5.bungee.protocol.skip.Instruction.BLOCK_CHANGE_ARRAY;
import static net.md_5.bungee.protocol.skip.Instruction.BOOLEAN;
import static net.md_5.bungee.protocol.skip.Instruction.BYTE;
import static net.md_5.bungee.protocol.skip.Instruction.DOUBLE;
import static net.md_5.bungee.protocol.skip.Instruction.FLOAT;
import static net.md_5.bungee.protocol.skip.Instruction.INT;
import static net.md_5.bungee.protocol.skip.Instruction.INT_3;
import static net.md_5.bungee.protocol.skip.Instruction.INT_BYTE;
import static net.md_5.bungee.protocol.skip.Instruction.ITEM;
import static net.md_5.bungee.protocol.skip.Instruction.LONG;
import static net.md_5.bungee.protocol.skip.Instruction.METADATA;
import static net.md_5.bungee.protocol.skip.Instruction.OPTIONAL_MOTION;
import static net.md_5.bungee.protocol.skip.Instruction.SHORT;
import static net.md_5.bungee.protocol.skip.Instruction.SHORT_BYTE;
import static net.md_5.bungee.protocol.skip.Instruction.SHORT_ITEM;
import static net.md_5.bungee.protocol.skip.Instruction.STRING;
import static net.md_5.bungee.protocol.skip.Instruction.UBYTE_BYTE;

public class Vanilla implements Protocol {

    public static final byte PROTOCOL_VERSION = 14;
    public static final String GAME_VERSION = "b1.7.3";
    @Getter
    private static final Vanilla instance = new Vanilla();
    /*========================================================================*/
    @Getter
    private final Instruction[][] instructions = new Instruction[256][];
    @SuppressWarnings("unchecked")
    @Getter
    protected Class<? extends DefinedPacket>[] classes = new Class[256];
    @SuppressWarnings("unchecked")
    @Getter
    private Constructor<? extends DefinedPacket>[] constructors = new Constructor[256];
    @Getter
    protected PacketReader skipper;
    /*========================================================================*/

    public Vanilla() {
        classes[0x01] = Packet1Login.class;
        classes[0x02] = Packet2Handshake.class;
        classes[0x03] = Packet3Chat.class;
        classes[0x09] = Packet9Respawn.class;
        classes[0xFA] = PacketFAPluginMessage.class;
        classes[0xFF] = PacketFFKick.class;
        skipper = new PacketReader(this);
    }

    @Override
    public DefinedPacket read(short packetId, ByteBuf buf) {
        DefinedPacket packet = read(packetId, buf, this);
        if (packet == null && instructions[packetId] == null) {
            throw new BadPacketException("Unknown packet id " + packetId);
        }
        return packet;
    }

    public static DefinedPacket read(short id, ByteBuf buf, Protocol protocol) {
        DefinedPacket packet = packet(id, protocol);
        if (packet != null) {
            packet.read(buf);
            return packet;
        }
        protocol.getSkipper().tryRead(id, buf);
        return null;
    }

    public static DefinedPacket packet(short id, Protocol protocol) {
        DefinedPacket ret = null;
        Class<? extends DefinedPacket> clazz = protocol.getClasses()[id];

        if (clazz != null) {
            try {
                Constructor<? extends DefinedPacket> constructor = protocol.getConstructors()[id];
                if (constructor == null) {
                    constructor = clazz.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    protocol.getConstructors()[id] = constructor;
                }

                if (constructor != null) {
                    ret = constructor.newInstance();
                }
            } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            }
        }

        return ret;
    }

    {
        instructions[0x00] = new Instruction[]
                {
                };
        instructions[0x04] = new Instruction[]
                {
                        LONG
                };
        instructions[0x05] = new Instruction[]
                {
                        INT, SHORT, SHORT, SHORT
                };
        instructions[0x06] = new Instruction[]
                {
                        INT, INT, INT
                };
        instructions[0x07] = new Instruction[]
                {
                        INT, INT, BOOLEAN
                };
        instructions[0x08] = new Instruction[]
                {
                        SHORT
                };
        instructions[0x0A] = new Instruction[]
                {
                        BOOLEAN
                };
        instructions[0x0B] = new Instruction[]
                {
                        DOUBLE, DOUBLE, DOUBLE, DOUBLE, BOOLEAN
                };
        instructions[0x0C] = new Instruction[]
                {
                        FLOAT, FLOAT, BOOLEAN
                };
        instructions[0x0D] = new Instruction[]
                {
                        DOUBLE, DOUBLE, DOUBLE, DOUBLE, FLOAT, FLOAT, BOOLEAN
                };
        instructions[0x0E] = new Instruction[]
                {
                        BYTE, INT, BYTE, INT, BYTE
                };
        instructions[0x0F] = new Instruction[]
                {
                        INT, BYTE, INT, BYTE, ITEM
                };
        instructions[0x10] = new Instruction[]
                {
                        SHORT
                };
        instructions[0x11] = new Instruction[]
                {
                        INT, BYTE, INT, BYTE, INT
                };
        instructions[0x12] = new Instruction[]
                {
                        INT, BYTE
                };
        instructions[0x13] = new Instruction[]
                {
                        INT, BYTE
                };
        instructions[0x14] = new Instruction[]
                {
                        INT, STRING, INT, INT, INT, BYTE, BYTE, SHORT
                };
        instructions[0x15] = new Instruction[]
                {
                        INT, SHORT, BYTE, SHORT, INT, INT, INT, BYTE, BYTE, BYTE
                };
        instructions[0x16] = new Instruction[]
                {
                        INT, INT
                };
        instructions[0x17] = new Instruction[]
                {
                        INT, BYTE, INT, INT, INT, OPTIONAL_MOTION
                };
        instructions[0x18] = new Instruction[]
                {
                        INT, BYTE, INT, INT, INT, BYTE, BYTE, METADATA
                };
        instructions[0x19] = new Instruction[]
                {
                        INT, STRING, INT, INT, INT, INT
                };
        instructions[0x1B] = new Instruction[]
                {
                        FLOAT, FLOAT, FLOAT, FLOAT, BOOLEAN, BOOLEAN
                };
        instructions[0x1C] = new Instruction[]
                {
                        INT, SHORT, SHORT, SHORT
                };
        instructions[0x1D] = new Instruction[]
                {
                        INT
                };
        instructions[0x1E] = new Instruction[]
                {
                        INT
                };
        instructions[0x1F] = new Instruction[]
                {
                        INT, BYTE, BYTE, BYTE
                };
        instructions[0x20] = new Instruction[]
                {
                        INT, BYTE, BYTE
                };
        instructions[0x21] = new Instruction[]
                {
                        INT, BYTE, BYTE, BYTE, BYTE, BYTE
                };
        instructions[0x22] = new Instruction[]
                {
                        INT, INT, INT, INT, BYTE, BYTE
                };
        instructions[0x26] = new Instruction[]
                {
                        INT, BYTE
                };
        instructions[0x27] = new Instruction[]
                {
                        INT, INT
                };
        instructions[0x28] = new Instruction[]
                {
                        INT, METADATA
                };
        instructions[0x32] = new Instruction[]
                {
                        INT, INT, BYTE
                };
        instructions[0x33] = new Instruction[]
                {
                        INT, SHORT, INT, BYTE, BYTE, BYTE, INT_BYTE
                };
        instructions[0x34] = new Instruction[]
                {
                        INT, INT, BLOCK_CHANGE_ARRAY
                };
        instructions[0x35] = new Instruction[]
                {
                        INT, BYTE, INT, BYTE, BYTE
                };
        instructions[0x36] = new Instruction[]
                {
                        INT, SHORT, INT, BYTE, BYTE
                };
        instructions[0x3C] = new Instruction[]
                {
                        DOUBLE, DOUBLE, DOUBLE, FLOAT, INT_3
                };
        instructions[0x3D] = new Instruction[]
                {
                        INT, INT, BYTE, INT, INT
                };
        instructions[0x46] = new Instruction[]
                {
                        BYTE
                };
        instructions[0x47] = new Instruction[]
                {
                        INT, BYTE, INT, INT, INT
                };
        instructions[0x64] = new Instruction[]
                {
                        BYTE, BYTE, SHORT_BYTE, BYTE
                };
        instructions[0x65] = new Instruction[]
                {
                        BYTE
                };
        instructions[0x66] = new Instruction[]
                {
                        BYTE, SHORT, BYTE, SHORT, BOOLEAN, ITEM
                };
        instructions[0x67] = new Instruction[]
                {
                        BYTE, SHORT, ITEM
                };
        instructions[0x68] = new Instruction[]
                {
                        BYTE, SHORT_ITEM
                };
        instructions[0x69] = new Instruction[]
                {
                        BYTE, SHORT, SHORT
                };
        instructions[0x6A] = new Instruction[]
                {
                        BYTE, SHORT, BOOLEAN
                };
        instructions[0x82] = new Instruction[]
                {
                        INT, SHORT, INT, STRING, STRING, STRING, STRING
                };
        instructions[0x83] = new Instruction[]
                {
                        SHORT, SHORT, UBYTE_BYTE
                };
        instructions[0xC8] = new Instruction[]
                {
                        INT, BYTE
                };
    }
}
