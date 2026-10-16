package dev.dragarmor;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import dev.dragarmor.compat.Compat;

/** Canal "dragarmor:control": 1 byte, 1 = desativar, 0 = reativar. */
public record ControlPayload(boolean disable) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ControlPayload> TYPE = Compat.controlType();

    public static final StreamCodec<FriendlyByteBuf, ControlPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByte(payload.disable() ? 1 : 0),
            buf -> {
                boolean value = buf.readableBytes() > 0 && buf.readByte() != 0;
                buf.skipBytes(buf.readableBytes());
                return new ControlPayload(value);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
