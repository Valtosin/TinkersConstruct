package slimeknights.tconstruct.library.client.model.block;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import io.github.fabricators_of_create.porting_lib.models.geometry.IGeometryLoader;
import io.github.fabricators_of_create.porting_lib.models.geometry.IUnbakedGeometry;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import slimeknights.mantle.client.model.fluid.FluidCuboid;
import slimeknights.mantle.client.model.util.SimpleBlockModel;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Similar to {@link slimeknights.mantle.client.model.fluid.FluidsModel}, but arranges cuboids in the channel.
 * Used since there is no easy way to handle multipart in the fluid cuboid system.
 */
public class ChannelModel implements IUnbakedGeometry<ChannelModel> {
	/** Model loader instance */
	public static final Loader LOADER = new Loader();

	/** Base block model */
	private final SimpleBlockModel model;
	/** Map of all fluid parts of the model */
	private final Map<ChannelModelPart,FluidCuboid> fluids;

	public ChannelModel(SimpleBlockModel model, Map<ChannelModelPart,FluidCuboid> fluids) {
		this.model = model;
		this.fluids = fluids;
	}

	@Override
	public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, BlockModel owner) {
    model.resolveParents(modelGetter, owner);
	}

	@Override
	public BakedModel bake(BlockModel owner, ModelBaker baker, Function<Material,TextureAtlasSprite> spriteGetter, ModelState transform, ItemOverrides overrides, ResourceLocation location, boolean isGui3d) {
		BakedModel baked = this.model.bakeModel(owner, transform, overrides, spriteGetter, location);
		return new Baked(baked, this.fluids);
	}

	/**
	 * Baked model wrapper for cistern models
	 */
	public static class Baked extends ForwardingBakedModel {
		private final Map<ChannelModelPart,FluidCuboid> fluids;
		private Baked(BakedModel originalModel, Map<ChannelModelPart,FluidCuboid> fluids) {
			wrapped = originalModel;
			this.fluids = fluids;
		}

		/** Gets the cuboid for flowing down */
		public FluidCuboid getDownFluid() {
			return this.fluids.get(ChannelModelPart.DOWN);
		}

		/**
		 * Gets the cuboid for the center
		 * @param flowing  If true, the center is flowing
		 */
		public FluidCuboid getCenterFluid(boolean flowing) {
			return this.fluids.get(flowing ? ChannelModelPart.CENTER_FLOWING : ChannelModelPart.CENTER_STILL);
		}

		/**
		 * Gets the flowing fluid for the side
		 * @return  Cuboid for center
		 */
		public FluidCuboid getSideFlow(boolean out) {
			return this.fluids.get(out ? ChannelModelPart.SIDE_OUT : ChannelModelPart.SIDE_IN);
		}

		/** Gets the cuboid for still side */
		public FluidCuboid getSideStill() {
			return this.fluids.get(ChannelModelPart.SIDE_STILL);
		}

		/** Gets the cuboid for side edge */
		public FluidCuboid getSideEdge() {
			return this.fluids.get(ChannelModelPart.SIDE_EDGE);
		}
	}

	/** Model loader */
	private static class Loader implements IGeometryLoader<ChannelModel> {
		@Override
		public ChannelModel read(JsonObject modelContents, JsonDeserializationContext deserializationContext) {
			SimpleBlockModel model = SimpleBlockModel.deserialize(deserializationContext, modelContents);

			// parse fluid cuboid for each side
			JsonObject fluidJson = GsonHelper.getAsJsonObject(modelContents, "fluids");
			Map<ChannelModelPart,FluidCuboid> fluids = new EnumMap<>(ChannelModelPart.class);
			fluids.put(ChannelModelPart.DOWN, FluidCuboid.fromJson(GsonHelper.getAsJsonObject(fluidJson, "down")));
			// center
			JsonObject centerJson = GsonHelper.getAsJsonObject(fluidJson, "center");
			fluids.put(ChannelModelPart.CENTER_STILL, FluidCuboid.fromJson(GsonHelper.getAsJsonObject(centerJson, "still")));
			fluids.put(ChannelModelPart.CENTER_FLOWING, FluidCuboid.fromJson(GsonHelper.getAsJsonObject(centerJson, "flowing")));
			// side
			JsonObject sideJson = GsonHelper.getAsJsonObject(fluidJson, "side");
			fluids.put(ChannelModelPart.SIDE_STILL, FluidCuboid.fromJson(GsonHelper.getAsJsonObject(sideJson, "still")));
			fluids.put(ChannelModelPart.SIDE_IN, FluidCuboid.fromJson(GsonHelper.getAsJsonObject(sideJson, "in")));
			fluids.put(ChannelModelPart.SIDE_OUT, FluidCuboid.fromJson(GsonHelper.getAsJsonObject(sideJson, "out")));
			fluids.put(ChannelModelPart.SIDE_EDGE, FluidCuboid.fromJson(GsonHelper.getAsJsonObject(sideJson, "edge")));

			return new ChannelModel(model, fluids);
		}
	}

	/** Enum to hold each of the 7 relevant cuboids */
	private enum ChannelModelPart {
		CENTER_STILL,
		CENTER_FLOWING,
		SIDE_STILL,
		SIDE_IN,
		SIDE_OUT,
		SIDE_EDGE,
		DOWN;
	}
}
