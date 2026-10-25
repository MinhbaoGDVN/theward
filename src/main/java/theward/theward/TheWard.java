package theward.theward;

import net.fabricmc.api.ModInitializer;

import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TheWard implements ModInitializer {

    public static final String MOD_ID = "the-ward";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Hello world!");
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
