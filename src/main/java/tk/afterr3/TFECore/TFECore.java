package tk.afterr3.TFECore;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import tk.afterr3.TFECore.commands.CommandName;
import tk.afterr3.TFECore.common.ModUtils;
import tk.afterr3.TFECore.event.EventHandler;
import tk.afterr3.TFECore.network.packet.PacketTFEChange;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
@Mod(modid = TFECore.MODID, useMetadata = true)
public class TFECore
{
    static final String MODID = "TFECore";

     //The public INSTANCE for the mod
    @Mod.Instance(TFECore.MODID)
    public static TFECore INSTANCE;

    //The {@link Configuration} for Hide Names
    public Configuration config;

    //The channel that Hide Names uses for custom packets
    private String channel;
    private SimpleNetworkWrapper network;


    //All players currently in the file {@link #fileTFECore hidden.txt}
    public final Map<String, Boolean> hiddenPlayers = new HashMap<>();
    private final Logger LOGGER = Logger.getLogger("Minecraft");

    public static final int commandPermissionLevel = 0;
    public static final String commandName1 = "name";
    public static final String commandName2 = "names";


    //The path to the file hidden.txt
    private String fileTFECore;
    private final String fileName = "hidden.txt";
    private String serverFilePath;
    private String clientFilePath;

    public static boolean defaultHiddenStatus;
    public static boolean saveOfflinePlayers;
    public static boolean allowCommand;
    public static boolean showHideStatusOnLogin;

    private ModMetadata metadata;

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        metadata = event.getModMetadata();
        channel = metadata.modId;

        config = new Configuration(new File(event.getModConfigurationDirectory(), "/tfecore/main.cfg"));

        config.load();

        defaultHiddenStatus = config.get(Configuration.CATEGORY_GENERAL, "defaultHiddenStatus",
                false, "Default state for new players").getBoolean(false);
        showHideStatusOnLogin = config.get(Configuration.CATEGORY_GENERAL, "showHideStatusOnLogin",
                true, "Showing information about hide status after enter the game").getBoolean(true);
        saveOfflinePlayers = config.get(Configuration.CATEGORY_GENERAL, "saveOfflinePlayers",
                true, "Whether or not to keep players in 'hidden.txt' if they are offline - useful for big servers").getBoolean(true);
        allowCommand = config.get(Configuration.CATEGORY_GENERAL, "allowCommand",
                true, "Whether or not non-ops can use the /name command").getBoolean(true);
        serverFilePath = config.get(Configuration.CATEGORY_GENERAL, "serverFilePath",
                "", "Where the file 'hidden.txt' should be on a dedicated server - NOTE: all directories are located within the server folder").getString();
        clientFilePath = config.get(Configuration.CATEGORY_GENERAL, "clientFilePath",
                "/config/tlf", "Where the file 'hidden.txt' should be on a client/LAN server - NOTE: all directories are located within the '.minecraft' folder").getString();
        config.save();

        this.network = NetworkRegistry.INSTANCE.newSimpleChannel(this.channel);
        this.network.registerMessage(PacketTFEChange.Handler.class, PacketTFEChange.class, 0, Side.CLIENT);
    }

    @Mod.EventHandler
    public void onModInit(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new EventHandler());
    }

    @Mod.EventHandler
    public void onModLoad(FMLPostInitializationEvent event) {
        LOGGER.info(metadata.name + " " + metadata.version + " loaded!");
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandName());
        getFilePath();
        getHiddenPlayers();
    }

    /**
     * Checks to see if the server is a dedicated server. If it is, it sets {@link #fileTFECore} to {@link #serverFilePath} + {@link #fileName}.
     * Otherwise, its sets {@link #fileTFECore} to {@link #clientFilePath} + {@link #fileName}.
     */
    private void getFilePath() {
        if (FMLCommonHandler.instance().getMinecraftServerInstance().getServer().isDedicatedServer()) {
            if (!serverFilePath.endsWith("/")) {
                if (!fileName.startsWith("/")) {
                    fileTFECore = serverFilePath + "/" + fileName;
                } else {
                    fileTFECore = serverFilePath + fileName;
                }
            } else {
                if (!fileName.startsWith("/")) {
                    fileTFECore = serverFilePath + fileName;
                } else {
                    fileTFECore = serverFilePath.substring(0, serverFilePath.length() - 1) + fileName;
                }
            }
        } else {
            if (!clientFilePath.endsWith("/")) {
                if (!fileName.startsWith("/")) {
                    fileTFECore = clientFilePath + "/" + fileName;
                } else {
                    fileTFECore = clientFilePath + fileName;
                }
            } else {
                if (!fileName.startsWith("/")) {
                    fileTFECore = clientFilePath + fileName;
                } else {
                    fileTFECore = clientFilePath.substring(0, clientFilePath.length() - 1) + fileName;
                }
            }
        }

        if (fileTFECore.startsWith("/")) {
            fileTFECore = fileTFECore.substring(1);
        }
    }

    /**
     * Removes all players from the file {@link #fileTFECore} and {@link #hiddenPlayers}
     */
    public void clearHiddenPlayers() {
        hiddenPlayers.clear();
        new File(fileTFECore).delete();
    }

    /**
     * Refreshes the {@link java.util.HashMap HashMap} {@link #hiddenPlayers hiddenPlayers}
     */
    public void getHiddenPlayers() {
        try {
            BufferedReader br = new BufferedReader(new FileReader(fileTFECore));
            String line;
            hiddenPlayers.clear();
            while ((line = br.readLine()) != null) {
                if (line.length() > 0 && !line.substring(0, 1).contentEquals("#")) {
                    int seperator = line.lastIndexOf(":");
                    String username = line.substring(0, seperator).toLowerCase();
                    String hidden = line.substring(seperator + 1);
                    updateHiddenPlayers(username, (hidden.equalsIgnoreCase("true")));
                }
            }
            br.close();
        } catch (FileNotFoundException e) {
            LOGGER.log(Level.FINE, "Error: File " + fileTFECore + " not found.");
            LOGGER.log(Level.FINE, "Creating file " + fileTFECore);
            createFile(fileTFECore);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error: " + e.getMessage());
        }
    }

    /**
     * Called every time a user connects. If that user is not in {@link #fileTFECore hidden.txt}, then they are placed in {@link #fileTFECore hidden.txt} with the hidden status of whatever
     * {@link #defaultHiddenStatus defaultHiddenStatus} is set to. If they are in {@link #fileTFECore hidden.txt}, then their hidden status is whatever is
     * said in {@link #fileTFECore hidden.txt}
     *
     * @param @EntityPlayer#player
     */
    public void onClientConnect(EntityPlayer player) {
        for (String user : hiddenPlayers.keySet()) {
            this.network.sendTo(new PacketTFEChange(user, hiddenPlayers.get(user)), (EntityPlayerMP) player);
        }

        String username = player.getCommandSenderEntity().getName().toLowerCase();

        if (hiddenPlayers.get(username) == null) {
            updateHiddenPlayers(username, defaultHiddenStatus);
        } else {
            updateHiddenPlayers(username, hiddenPlayers.get(username));
        }

        if (TFECore.showHideStatusOnLogin) {
            player.addChatMessage(new TextComponentString("Your name is: " +
                    (hiddenPlayers.get(username) ? "\u00a7aHidden" : "\u00a74Visible")));
        }
    }

    /**
     * Creates a file at the location 'file'
     *
     * @param file The location to create the file
     */
    private void createFile(String file) {
        try {
            FileWriter fstream = new FileWriter(file);
            BufferedWriter out = new BufferedWriter(fstream);
            out.write("#List of Hidden Players");

            for (Object o : hiddenPlayers.entrySet()) {
                Map.Entry mEntry = (Map.Entry) o;
                out.write("\n" + mEntry.getKey() + ":" + ((Boolean) mEntry.getValue() ? "true" : "false"));
            }
            out.close();
            fstream.close();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error: " + e.getMessage());
        }
    }

    /**
     * Sets all players hidden status to the supplied boolean
     *
     * @param hidden The state to set all players to
     */
    public void setAll(String sender, boolean hidden) {
        List<String> users = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : hiddenPlayers.entrySet()) {
            String key = entry.getKey();
            users.add(key);
        }

        for (String username : users) {
            updateHiddenPlayers(username, hidden);

            if (!username.equalsIgnoreCase(sender)) {
                ModUtils.playerForName(username).addChatMessage(new TextComponentString(sender +
                        " set your name to be: " +
                        (hiddenPlayers.get(username) ? TextFormatting.GREEN + "Hidden" : TextFormatting.DARK_RED + "Visible")));
            }
        }
    }

    /**
     * Changes the state of the player 'username' to the state 'hidden'
     *
     * @param username The player to change
     * @param hidden   The state to change them to
     */
    public void updateHiddenPlayers(String username, boolean hidden) {
        Side side = FMLCommonHandler.instance().getEffectiveSide();

        if (side == Side.SERVER) {
            username = username.toLowerCase();

            hiddenPlayers.remove(username);
            hiddenPlayers.put(username, hidden);
            refreshFile(fileTFECore);

            this.network.sendToAll(new PacketTFEChange(username, hidden));
        }
    }

    public void removeOfflinePlayers() {
        String[] users = FMLCommonHandler.instance().getMinecraftServerInstance().getServer().getAllUsernames();

        Object[] keySet = hiddenPlayers.keySet().toArray();
        Boolean[] keepUsers = new Boolean[keySet.length];
        Boolean foundUser = false;
        Boolean foundDifferent = false;

        for (int i = 0; i < keySet.length; i++) {
            for (String user : users) {
                if (keySet[i].toString().equalsIgnoreCase(user)) {
                    foundUser = true;
                }
            }

            if (!foundUser) {
                foundDifferent = true;
            }

            keepUsers[i] = foundUser;
            foundUser = false;
        }

        for (int i = 0; i < keepUsers.length; i++) {
            if (!keepUsers[i]) {
                hiddenPlayers.remove(keySet[i]);
            }
        }

        if (foundDifferent) {
            refreshFile(fileTFECore);
        }
    }

    public void refreshFile(String fileName) {
        new File(fileName).delete();
        createFile(fileName);
    }

    public void checkFile() {
        File file = new File(fileTFECore);

        if (!file.exists()) {
            createFile(fileTFECore);
        }
    }

    public static String colorBool(boolean bool) {
        return (bool ? TextFormatting.GREEN + "true" : TextFormatting.DARK_RED + "false");
    }
}
