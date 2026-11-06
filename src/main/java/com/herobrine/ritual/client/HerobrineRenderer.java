package com.herobrine.ritual.client;

import com.herobrine.ritual.HasHerobrineVariant;
import com.herobrine.ritual.HerobrineMod;
import com.herobrine.ritual.HerobrineVariant;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/** Рендерить боса й компаньйона. Скін залежить від виду; очі світяться в темряві. */
public class HerobrineRenderer<T extends Mob & HasHerobrineVariant>
        extends HumanoidMobRenderer<T, HerobrineRenderer.State, HumanoidModel<HerobrineRenderer.State>> {

    /** Стан рендера з номером виду. */
    public static class State extends HumanoidRenderState {
        public int variant;
    }

    private static final Identifier[] TEXTURES = new Identifier[HerobrineVariant.values().length];
    private static final RenderType[] EYES = new RenderType[HerobrineVariant.values().length];

    static {
        HerobrineVariant[] variants = HerobrineVariant.values();
        for (int i = 0; i < variants.length; i++) {
            TEXTURES[i] = Identifier.fromNamespaceAndPath(
                    HerobrineMod.MODID, "textures/entity/herobrine_" + variants[i].id() + ".png");
            EYES[i] = RenderTypes.eyes(Identifier.fromNamespaceAndPath(
                    HerobrineMod.MODID, "textures/entity/herobrine_" + variants[i].id() + "_eyes.png"));
        }
    }

    private static int index(State state) {
        return Math.max(0, Math.min(TEXTURES.length - 1, state.variant));
    }

    public HerobrineRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
                context.getEquipmentRenderer()));
        this.addLayer(new GlowingEyesLayer(this));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.variant = entity.getVariant().ordinal();
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return TEXTURES[index(state)];
    }

    /** Світні очі (як у павука чи ендермена), колір залежить від виду. */
    private static class GlowingEyesLayer extends RenderLayer<State, HumanoidModel<State>> {
        GlowingEyesLayer(RenderLayerParent<State, HumanoidModel<State>> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state,
                           float yRot, float xRot) {
            collector.order(1).submitModel(this.getParentModel(), state, poseStack, EYES[index(state)],
                    lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
    }
}
