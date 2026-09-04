package net.md_5.bungee.protocol.packet;

import io.netty.buffer.ByteBuf;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = false)
public class Packet1Login extends DefinedPacket {

    protected int entityId;
    protected String username;
    protected long seed;
    protected byte dimension;

    protected Packet1Login() {
        super(0x01);
    }

    public Packet1Login(int entityId, String username, long seed, byte dimension) {
        this();
        this.entityId = entityId;
        this.username = username;
        this.seed = seed;
        this.dimension = dimension;
    }

    @Override
    public void read(ByteBuf buf) {
        entityId = buf.readInt();
        username = readString(buf);
        seed = buf.readLong();
        dimension = buf.readByte();
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeInt(entityId);
        writeString(username, buf);
        buf.writeLong(seed);
        buf.writeByte(dimension);
    }

    @Override
    public void handle(AbstractPacketHandler handler) throws Exception {
        handler.handle(this);
    }
}
