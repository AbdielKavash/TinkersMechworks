package tmechworks.common;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.IGuiHandler;
import mantle.blocks.abstracts.InventoryLogic;
import tconstruct.smeltery.TinkerSmeltery;
import tmechworks.lib.ConfigCore;
import tmechworks.lib.TMechworksRegistry;
import tmechworks.lib.multiblock.MultiblockServerTickHandler;

public class CommonProxy implements IGuiHandler {

    public static int drawbridgeID = 0;
    public static int advDrawbridgeID = 1;

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID < 0) return null;

        if (ID < 100) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile != null && tile instanceof InventoryLogic) {
                return ((InventoryLogic) tile).getGuiContainer(player.inventory, world, x, y, z);
            }
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }

    public void registerTickHandler() {
        FMLCommonHandler.instance().bus().register(new MultiblockServerTickHandler());
    }

    public void registerRenderer() {}

    public void init() {
        TMechworksRegistry.initDrawbridgeBlackList(ConfigCore.drawbridgeBlackList);
        // Adds the default black list
        TMechworksRegistry.addItemToDBBlackList(MechContent.redstoneMachine);

        TMechworksRegistry.addItemToDBBlackList(TinkerSmeltery.lavaTank);
        TMechworksRegistry.addItemToDBBlackList(TinkerSmeltery.lavaTankNether);
    }

    public void postInit() {}
}
