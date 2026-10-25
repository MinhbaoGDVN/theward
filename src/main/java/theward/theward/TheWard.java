package theward.theward;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class TheWard implements ModInitializer {

    public static final String MOD_ID = "the-ward";
    private static final RegistryKey<World> WARD_WORLD =
            RegistryKey.of(RegistryKeys.WORLD, new Identifier("theward", "ward"));
    private static final Identifier BASE_TEMPLATE_ID = new Identifier("theward", "cancuchinh");

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(TheWard::placeMainBase);
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    private static void placeMainBase(MinecraftServer server) {
        ServerWorld ward = server.getWorld(WARD_WORLD);
        if (ward == null) {
            LOGGER.error("WARD dimension is missing; cannot place the main base.");
            return;
        }

        MainBaseState state = server.getOverworld().getPersistentStateManager()
                .getOrCreate(MainBaseState::fromNbt, MainBaseState::new, "theward_main_base");
        if (state.placed) {
            return;
        }

        Optional<StructureTemplate> template = server.getStructureTemplateManager().getTemplate(BASE_TEMPLATE_ID);
        if (template.isEmpty()) {
            throw new IllegalStateException("Missing main base structure template: " + BASE_TEMPLATE_ID);
        }

        StructureTemplate base = template.get();
        int y = ward.getTopY(Heightmap.Type.WORLD_SURFACE, 0, 0);
        BlockPos origin = new BlockPos(-base.getSize().getX() / 2, y, -base.getSize().getZ() / 2);
        boolean placed = base.place(ward, origin, origin, new StructurePlacementData(),
                Random.create(), Block.NOTIFY_LISTENERS);
        if (!placed) {
            throw new IllegalStateException("Failed to place the main base at WARD (0, 0).");
        }

        state.placed = true;
        state.markDirty();
        LOGGER.info("Placed the WARD main base at X=0, Z=0.");
    }

    private static final class MainBaseState extends PersistentState {
        private boolean placed;

        private static MainBaseState fromNbt(NbtCompound nbt) {
            MainBaseState state = new MainBaseState();
            state.placed = nbt.getBoolean("placed");
            return state;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            nbt.putBoolean("placed", placed);
            return nbt;
        }
    }
}
