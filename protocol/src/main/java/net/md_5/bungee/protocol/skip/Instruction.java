package net.md_5.bungee.protocol.skip;

import io.netty.buffer.ByteBuf;

public abstract class Instruction {

    public static final Instruction BOOLEAN = new Jump(1);
    public static final Instruction BYTE = new Jump(1);
    public static final Instruction DOUBLE = new Jump(8);
    public static final Instruction FLOAT = new Jump(4);
    public static final Instruction INT = new Jump(4);
    public static final Instruction INT_3 = new IntHeader(new Jump(3));
    public static final Instruction INT_BYTE = new IntHeader(BYTE);
    public static final Instruction ITEM = new Item();
    public static final Instruction LONG = new Jump(8);
    public static final Instruction METADATA = new MetaData();
    public static final Instruction OPTIONAL_MOTION = new OptionalMotion();
    public static final Instruction SHORT = new Jump(2);
    public static final Instruction SHORT_BYTE = new ShortHeader(BYTE);
    public static final Instruction SHORT_ITEM = new ShortHeader(ITEM);
    public static final Instruction STRING = new ShortHeader(new Jump(2));
    public static final Instruction UBYTE_BYTE = new UnsignedByteByte();
    public static final Instruction BLOCK_CHANGE_ARRAY = new BlockChangeArray();

    public abstract void read(ByteBuf in);
}
