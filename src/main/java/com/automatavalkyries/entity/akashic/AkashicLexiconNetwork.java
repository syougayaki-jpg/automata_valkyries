package com.automatavalkyries.entity.akashic;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.item.AkashicLexiconItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * カスタムダメージ辞書・カスタム即死辞書 GUI 設定同期用ネットワークチャネル。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AkashicLexiconNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "akashic_lexicon"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static volatile boolean registered = false;

    private AkashicLexiconNetwork() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        ensureRegistered();
    }

    public static synchronized void ensureRegistered() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(UpdateLexiconPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpdateLexiconPacket::encode)
                .decoder(UpdateLexiconPacket::decode)
                .consumerMainThread(UpdateLexiconPacket::handle)
                .add();
    }

    public static void sendToServer(InteractionHand hand, AkashicCustomLexicon lexicon) {
        ensureRegistered();
        CHANNEL.sendToServer(new UpdateLexiconPacket(hand, lexicon.toTag()));
    }

    /** クライアントの辞書設定画面からサーバーの手持ち ItemStack へ設定 NBT を保存するパケット */
    public static final class UpdateLexiconPacket {
        private final InteractionHand hand;
        private final CompoundTag lexiconTag;

        public UpdateLexiconPacket(InteractionHand hand, CompoundTag lexiconTag) {
            this.hand = hand != null ? hand : InteractionHand.MAIN_HAND;
            this.lexiconTag = lexiconTag != null ? lexiconTag : new CompoundTag();
        }

        public static void encode(UpdateLexiconPacket pkt, FriendlyByteBuf buf) {
            buf.writeEnum(pkt.hand);
            buf.writeNbt(pkt.lexiconTag);
        }

        public static UpdateLexiconPacket decode(FriendlyByteBuf buf) {
            InteractionHand hand = buf.readEnum(InteractionHand.class);
            CompoundTag tag = buf.readNbt();
            return new UpdateLexiconPacket(hand, tag != null ? tag : new CompoundTag());
        }

        public static void handle(UpdateLexiconPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                applyToPlayerHand(sender, pkt.hand, pkt.lexiconTag);
            }
            ctx.setPacketHandled(true);
        }

        public static void applyToPlayerHand(ServerPlayer player, InteractionHand hand, CompoundTag tag) {
            ItemStack stack = player.getItemInHand(hand);
            AkashicLexiconItem lexiconItem;
            if (stack.getItem() instanceof AkashicLexiconItem primary) {
                lexiconItem = primary;
            } else {
                stack = player.getMainHandItem();
                if (stack.getItem() instanceof AkashicLexiconItem li) {
                    lexiconItem = li;
                } else {
                    stack = player.getOffhandItem();
                    if (stack.getItem() instanceof AkashicLexiconItem li2) {
                        lexiconItem = li2;
                    } else {
                        return;
                    }
                }
            }
            AkashicCustomLexicon parsed = AkashicCustomLexicon.fromTag(lexiconItem.kind(), tag);
            parsed.saveToStack(stack);
        }
    }
}
