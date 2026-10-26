package theward.theward;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class TheWard implements ModInitializer {

    public static final String MOD_ID = "the-ward";
    private static final RegistryKey<World> WARD_WORLD =
            RegistryKey.of(RegistryKeys.WORLD, new Identifier("theward", "ward"));
    private static final Identifier BASE_TEMPLATE_ID = new Identifier("theward", "cancuchinh");
    private static final String INITIAL_SPAWN_TAG = "theward.initial_spawn";
    private static final Set<Block> SOIL_BLOCKS = Set.of(
            Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.GRASS_BLOCK, Blocks.PODZOL,
            Blocks.MYCELIUM, Blocks.ROOTED_DIRT, Blocks.DIRT_PATH
    );

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(TheWard::placeMainBase);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sendNewPlayerToWard(handler.player, server));
        ServerChunkEvents.CHUNK_LOAD.register(TheWard::cleanWardChunk);
        ServerEntityEvents.ENTITY_LOAD.register(TheWard::removeVanillaMobs);
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    private static void cleanWardChunk(ServerWorld world, WorldChunk chunk) {
        if (!world.getRegistryKey().equals(WARD_WORLD)) {
            return;
        }

        WardChunkState state = world.getServer().getOverworld().getPersistentStateManager()
                .getOrCreate(WardChunkState::fromNbt, WardChunkState::new, "theward_cleaned_chunks");
        if (!state.cleanedChunks.add(chunk.getPos().toLong())) {
            return;
        }

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = chunk.getPos().getStartX() + localX;
                int z = chunk.getPos().getStartZ() + localZ;
                int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                for (int y = Math.max(world.getBottomY(), surfaceY - 8);
                     y < Math.min(world.getTopY(), surfaceY);
                     y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (SOIL_BLOCKS.contains(chunk.getBlockState(pos).getBlock())) {
                        chunk.setBlockState(pos, Blocks.END_STONE.getDefaultState(), false);
                    }
                }
            }
        }
        state.markDirty();
    }

    private static void removeVanillaMobs(Entity entity, ServerWorld world) {
        if (world.getRegistryKey().equals(WARD_WORLD)
                && entity instanceof MobEntity
                && Registries.ENTITY_TYPE.getId(entity.getType()).getNamespace().equals("minecraft")) {
            entity.discard();
        }
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
        int minChunkX = -base.getSize().getX() / 2 >> 4;
        int minChunkZ = -base.getSize().getZ() / 2 >> 4;
        int maxChunkX = (-base.getSize().getX() / 2 + base.getSize().getX() - 1) >> 4;
        int maxChunkZ = (-base.getSize().getZ() / 2 + base.getSize().getZ() - 1) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ward.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            }
        }

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

    private static void sendNewPlayerToWard(ServerPlayerEntity player, MinecraftServer server) {
        if (player.getCommandTags().contains(INITIAL_SPAWN_TAG)) {
            return;
        }

        ServerWorld ward = server.getWorld(WARD_WORLD);
        if (ward == null) {
            LOGGER.error("WARD dimension is missing; cannot spawn player {} there.", player.getName().getString());
            return;
        }

        Optional<StructureTemplate> template = server.getStructureTemplateManager().getTemplate(BASE_TEMPLATE_ID);
        if (template.isEmpty()) {
            LOGGER.error("Missing main base structure template: {}", BASE_TEMPLATE_ID);
            return;
        }

        int spawnX = 0;
        int spawnZ = template.get().getSize().getZ() / 2 + 2;
        ward.getChunkManager().getChunk(spawnX >> 4, spawnZ >> 4, ChunkStatus.FULL, true);
        int spawnY = ward.getTopY(Heightmap.Type.WORLD_SURFACE, spawnX, spawnZ) + 1;

        if (!player.teleport(ward, spawnX + 0.5, spawnY, spawnZ + 0.5,
                Set.<PositionFlag>of(), player.getYaw(), player.getPitch())) {
            LOGGER.error("Could not teleport player {} to the WARD main base.", player.getName().getString());
            return;
        }

        BlockPos spawnPos = new BlockPos(spawnX, spawnY, spawnZ);
        player.setSpawnPoint(WARD_WORLD, spawnPos, player.getYaw(), true, false);
        player.addCommandTag(INITIAL_SPAWN_TAG);
        LOGGER.info("Spawned player {} in WARD beside the main base.", player.getName().getString());
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

    private static final class WardChunkState extends PersistentState {
        private final Set<Long> cleanedChunks = new HashSet<>();

        private static WardChunkState fromNbt(NbtCompound nbt) {
            WardChunkState state = new WardChunkState();
            for (long chunk : nbt.getLongArray("cleaned_chunks")) {
                state.cleanedChunks.add(chunk);
            }
            return state;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            nbt.putLongArray("cleaned_chunks", cleanedChunks.stream().mapToLong(Long::longValue).toArray());
            return nbt;
        }
    }
}
